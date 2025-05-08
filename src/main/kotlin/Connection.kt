import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.fabric8.kubernetes.api.model.AuthInfo
import io.fabric8.kubernetes.api.model.ExecConfig
import io.fabric8.kubernetes.api.model.ExecEnvVar
import io.fabric8.kubernetes.api.model.NamedAuthInfo
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.OAuthTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.SdkHttpFullRequest
import software.amazon.awssdk.http.SdkHttpMethod
import software.amazon.awssdk.http.auth.aws.signer.AwsV4aHttpSigner
import software.amazon.awssdk.http.auth.aws.signer.RegionSet
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity



import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest



class EksTokenProvider(
    private val clusterName: String,
    private val region: String,
    private val awsProfile: String? = null,
    private val accessKeyId: String? = null,
    private val secretAccessKey: String? = null
) : OAuthTokenProvider {
    private val logger = LoggerFactory.getLogger(EksTokenProvider::class.java)

    private val credentialsProvider: AwsCredentialsProvider = when {
        accessKeyId != null && secretAccessKey != null -> {
            logger.info("Using static AWS credentials for eks authentication")
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey)
            )
        }
        awsProfile != null -> {
            logger.info("Using AWS profile '$awsProfile' for eks authentication")
            ProfileCredentialsProvider.builder().profileName(awsProfile).build()
        }
        else -> {
            logger.info("Using the standard AWS providers' chain for EKS authentication")
            DefaultCredentialsProvider.create()
        }
    }


    override fun getToken(): String {
        try {
            logger.debug("Генерація нового токену для EKS кластера '$clusterName'")

            val credentials = credentialsProvider.resolveCredentials()
            val now = Instant.now()
            val amzDate = DateTimeFormatter
                .ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneId.of("UTC"))
                .format(now)
            val datestamp = amzDate.substring(0, 8)

            val host = "sts.$region.amazonaws.com"

            // Формуємо параметри запиту в правильному порядку
            val queryParams = listOf(
                "Action=GetCallerIdentity",
                "Version=2011-06-15",
                "X-Amz-Algorithm=AWS4-HMAC-SHA256",
                "X-Amz-Credential=${credentials.accessKeyId()}%2F$datestamp%2F$region%2Fsts%2Faws4_request",
                "X-Amz-Date=$amzDate",
                "X-Amz-Expires=60",
                "X-Amz-SignedHeaders=host%3Bx-k8s-aws-id"
            ).sorted().joinToString("&")

            // Канонічні заголовки повинні бути відсортовані
            val canonicalHeaders = "host:$host\nx-k8s-aws-id:$clusterName\n"
            val signedHeaders = "host;x-k8s-aws-id"

            // Формуємо канонічний запит
            val canonicalRequest = listOf(
                "GET",
                "/",
                queryParams,
                canonicalHeaders,
                signedHeaders,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            ).joinToString("\n")

            val stringToSign = listOf(
                "AWS4-HMAC-SHA256",
                amzDate,
                "$datestamp/$region/sts/aws4_request",
                sha256Hex(canonicalRequest)
            ).joinToString("\n")

            val kSecret = ("AWS4" + credentials.secretAccessKey()).toByteArray(StandardCharsets.UTF_8)
            val kDate = hmacSha256(datestamp.toByteArray(StandardCharsets.UTF_8), kSecret)
            val kRegion = hmacSha256(region.toByteArray(StandardCharsets.UTF_8), kDate)
            val kService = hmacSha256("sts".toByteArray(StandardCharsets.UTF_8), kRegion)
            val kSigning = hmacSha256("aws4_request".toByteArray(StandardCharsets.UTF_8), kService)

            val signature = bytesToHex(hmacSha256(stringToSign.toByteArray(StandardCharsets.UTF_8), kSigning))

            // Формуємо кінцевий URL з усіма параметрами
            val presignedUrl = buildString {
                append("https://$host/?")
                append(queryParams)
                append("&X-Amz-Signature=")
                append(signature)
            }

            val token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(presignedUrl.toByteArray(StandardCharsets.UTF_8))

            return "k8s-aws-v1.$token"
        } catch (e: Exception) {
            val errorMsg = "Failed to generate eks token: ${e.message}"
            logger.error(errorMsg, e)
            throw RuntimeException(errorMsg, e)
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytesToHex(hash)
    }

    private fun hmacSha256(data: ByteArray, key: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = "0123456789abcdef"[v ushr 4]
            hexChars[j * 2 + 1] = "0123456789abcdef"[v and 0x0F]
        }
        return String(hexChars)
    }



}

suspend fun connectToSavedCluster(config: ClusterConfig): Result<Pair<KubernetesClient, String>> {
    return try {
        logger.info("Підключення до збереженого EKS кластера: ${config.alias}")

        val clientConfig = Config.empty().apply {
            masterUrl = config.endpoint
            caCertData = config.certificateAuthority
            connectionTimeout = CONNECTION_TIMEOUT_MS
            requestTimeout = REQUEST_TIMEOUT_MS
            namespace = null

            // Використовуємо EksTokenProvider замість анонімного об'єкту
            oauthTokenProvider = EksTokenProvider(
                clusterName = config.clusterName,
                region = config.region,
                accessKeyId = config.accessKeyId,
                secretAccessKey = config.secretAccessKey
            )

            // Обнуляємо конфліктуючі методи аутентифікації
            username = null
            password = null
            oauthToken = null
            authProvider = null
            clientCertFile = null
            clientCertData = null
            clientKeyFile = null
            clientKeyData = null
        }

        Result.success(withContext(Dispatchers.IO) {
            val client = KubernetesClientBuilder()
                .withConfig(clientConfig)
                .build()
            val version = client.kubernetesVersion?.gitVersion ?: "невідомо"
            Pair(client, version)
        })

    } catch (e: Exception) {
        logger.error("Помилка підключення до збереженого EKS кластера ${config.alias}: ${e.message}")
        Result.failure(e)
    }
}



suspend fun connectWithRetries(contextName: String?): Result<Pair<KubernetesClient, String>> {
    val targetContext = if (contextName.isNullOrBlank()) null else contextName
    var lastError: Exception? = null
    // targetContext тут може бути null, autoConfigure сам визначить поточний
    val contextNameToLog = targetContext ?: "(default)"

    for (attempt in 1..MAX_CONNECT_RETRIES) {
        logger.info("Спроба підключення до '$contextNameToLog' (спроба $attempt/$MAX_CONNECT_RETRIES)...")
        try {
            val resultPair: Pair<KubernetesClient, String> = withContext(Dispatchers.IO) {
                logger.info("[IO] Створення базового конфігу та клієнта для '$contextNameToLog' через Config.autoConfigure...")
                // Отримуємо оброблену конфігурацію
                val resolvedConfig: Config =
                    Config.autoConfigure(targetContext) ?: throw KubernetesClientException(
                        "Не вдалося автоматично налаштувати конфігурацію для контексту '$contextNameToLog'"
                    )

                // --- Починаємо аналіз сирого KubeConfig для перевірки ExecConfig ---
                try {
                    // Створюємо власну логіку для отримання та аналізу KubeConfig
                    // Стандартні місця розташування kubeconfig
                    val kubeConfigPath =
                        System.getenv("KUBECONFIG") ?: "${System.getProperty("user.home")}/.kube/config"

                    val kubeConfigFile = File(kubeConfigPath)
                    if (!kubeConfigFile.exists()) {
                        throw KubernetesClientException("Файл KubeConfig не знайдено за шляхом: $kubeConfigPath")
                    }

                    logger.info("Аналіз файлу KubeConfig: ${kubeConfigFile.absolutePath}")

                    // Використовуємо Jackson для розбору YAML файлу
                    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
                    val kubeConfigModel: io.fabric8.kubernetes.api.model.Config =
                        mapper.readValue(kubeConfigFile, io.fabric8.kubernetes.api.model.Config::class.java)

                    // Визначаємо ім'я контексту, яке було фактично використано
                    val actualContextName = resolvedConfig.currentContext?.name
                        ?: kubeConfigModel.currentContext // Беремо з resolvedConfig, або з моделі якщо там null
                        ?: throw KubernetesClientException("Не вдалося визначити поточний контекст")

                    // Знаходимо NamedContext у сирій моделі
                    val namedContext: NamedContext? = kubeConfigModel.contexts?.find { it.name == actualContextName }
                    val contextInfo = namedContext?.context
                        ?: throw KubernetesClientException("Не знайдено деталей для контексту '$actualContextName' у KubeConfig моделі")

                    // Отримуємо ім'я користувача з контексту
                    val userName: String? = contextInfo.user

                    if (userName != null) {
                        // Отримуємо список користувачів (NamedAuthInfo) з сирої моделі
                        val usersList: List<NamedAuthInfo> =
                            kubeConfigModel.users ?: emptyList() // У моделі поле називається 'users'

                        // Знаходимо NamedAuthInfo для нашого користувача
                        val namedAuthInfo: NamedAuthInfo? = usersList.find { it.name == userName }
                        val userAuth: AuthInfo? = namedAuthInfo?.user // AuthInfo з сирої моделі

                        // Отримуємо ExecConfig
                        val execConfig: ExecConfig? = userAuth?.exec

                        // 9. Перевіряємо, чи це EKS exec
                        if (execConfig != null && (execConfig.command == "aws" || execConfig.command.endsWith("/aws"))) {
                            logger.info("Виявлено EKS конфігурацію з exec command: '${execConfig.command}'. Спроба використання кастомного TokenProvider.")

                            val execArgs: List<String> = execConfig.args ?: emptyList()
                            val execEnv: List<ExecEnvVar> =
                                execConfig.env ?: emptyList() // Тип з моделі

                            // Функції findArgumentValue/findEnvValue потрібно буде адаптувати під List<ExecEnvVar> з моделі
                            val clusterName = findArgumentValue(execArgs, "--cluster-name") ?: findEnvValueModel(
                                execEnv,
                                "AWS_CLUSTER_NAME"
                            ) // Використовуємо адаптовану функцію
                            ?: throw KubernetesClientException("Не вдалося знайти 'cluster-name' в exec config для '$userName'")

                            val region = findArgumentValue(execArgs, "--region") ?: findEnvValueModel(
                                execEnv,
                                "AWS_REGION"
                            ) // Використовуємо адаптовану функцію
                            ?: throw KubernetesClientException("Не вдалося знайти 'region' в exec config для '$userName'")

                            val awsProfile = findArgumentValue(execArgs, "--profile") ?: findEnvValueModel(
                                execEnv,
                                "AWS_PROFILE"
                            ) // Використовуємо адаптовану функцію

                            logger.info("Параметри для EKS TokenProvider: cluster='$clusterName', region='$region', profile='${awsProfile ?: "(default)"}'")

                            //Створюємо та встановлюємо наш кастомний провайдер токенів в ОБРОБЛЕНУ конфігурацію
                            resolvedConfig.oauthTokenProvider = EksTokenProvider(clusterName, region, awsProfile)

                            // Обнуляємо конфліктуючі методи аутентифікації в ОБРОБЛЕНІЙ конфігурації
                            // Достатньо обнулити поля верхнього рівня в resolvedConfig
                            resolvedConfig.username = null
                            resolvedConfig.password = null
                            resolvedConfig.oauthToken = null
                            resolvedConfig.authProvider = null
                            resolvedConfig.clientCertFile = null
                            resolvedConfig.clientCertData = null
                            resolvedConfig.clientKeyFile = null
                            resolvedConfig.clientKeyData = null

                            logger.info("Кастомний EksTokenProvider встановлено для контексту '$actualContextName'.")
                        }
                    } else {
                        logger.warn("У контексті '$actualContextName' не вказано ім'я користувача (user). Неможливо перевірити ExecConfig.")
                    }

                } catch (kubeConfigEx: Exception) {
                    // Логуємо помилку завантаження/аналізу KubeConfig, але продовжуємо з resolvedConfig
                    logger.warn("Не вдалося проаналізувати KubeConfig для перевірки Exec: ${kubeConfigEx.message}")
                    // Можливо, варто тут перервати, якщо EKS є критичним? Залежить від вимог.
                    // throw KubernetesClientException("Помилка аналізу KubeConfig: ${kubeConfigEx.message}", kubeConfigEx)
                }
                // --- Кінець аналізу сирого KubeConfig ---


                // Використовуємо resolvedConfig (потенційно модифікований для EKS) для створення клієнта
                resolvedConfig.connectionTimeout = CONNECTION_TIMEOUT_MS
                resolvedConfig.requestTimeout = REQUEST_TIMEOUT_MS
                logger.info("[IO] Config context: ${resolvedConfig.currentContext?.name ?: "(не вказано)"}. Namespace: ${resolvedConfig.namespace}")

                val client = KubernetesClientBuilder().withConfig(resolvedConfig).build()
                logger.info("[IO] Fabric8 client created. Checking version...")
                val ver = client.kubernetesVersion?.gitVersion ?: "невідомо"
                logger.info("[IO] Версія сервера: $ver для '${resolvedConfig.currentContext?.name ?: contextNameToLog}'")
                Pair(client, ver)
            }
            logger.info("Підключення до '${resultPair.first.configuration.currentContext?.name ?: contextNameToLog}' успішне (спроба $attempt).")
            return Result.success(resultPair)
        } catch (e: Exception) {
            lastError = e
            logger.warn("Помилка підключення '$contextNameToLog' (спроба $attempt): ${e.message}")
            //if (attempt < .MAX_CONNECT_RETRIES) { kotlinx.coroutines.delay(RETRY_DELAY_MS) }
        }
    }
    logger.error("Не вдалося підключитися до '$contextNameToLog' після $MAX_CONNECT_RETRIES спроб.")
    return Result.failure(lastError ?: IOException("Невідома помилка підключення"))
}

// Потрібно адаптувати або створити нову функцію для роботи з List<io.fabric8.kubernetes.api.model.ExecEnvVar>
fun findEnvValueModel(envVars: List<ExecEnvVar>, key: String): String? {
    return envVars.find { it.name == key }?.value
}

fun findArgumentValue(args: List<String>, argName: String): String? {
    val index = args.indexOf(argName)
    return if (index != -1 && index + 1 < args.size) {
        args[index + 1]
    } else {
        null
    }
}

private fun findEnvValue(envList: List<ExecEnvVar>?, key: String): String? {
    return envList?.find { it.name == key }?.value
}

private val ClusterConfig.awsProfile: String?
    get() = if (profileName.isNullOrBlank()) null else profileName

const val MAX_CONNECT_RETRIES = 1

//const val RETRY_DELAY_MS = 1000L
const val CONNECTION_TIMEOUT_MS = 5000
const val REQUEST_TIMEOUT_MS = 15000