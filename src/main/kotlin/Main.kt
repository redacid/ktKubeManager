// src/main/kotlin/Main.kt (На базі збереженої версії + всі ресурси)
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
// --- Імпорти для Fabric8 ---
import io.fabric8.kubernetes.client.Config // Потрібен Config з client-api
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
// Моделі Kubernetes
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.kubernetes.api.model.apps.DaemonSet
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.api.model.batch.v1.Job
import io.fabric8.kubernetes.api.model.batch.v1.CronJob
import io.fabric8.kubernetes.api.model.networking.v1.Ingress
import io.fabric8.kubernetes.api.model.PersistentVolume
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.api.model.storage.StorageClass
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.api.model.rbac.Role
import io.fabric8.kubernetes.api.model.rbac.RoleBinding
import io.fabric8.kubernetes.api.model.rbac.ClusterRole
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding
import io.fabric8.kubernetes.api.model.HasMetadata // Для сортування
// Імпорти для Coroutines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // Повне ім'я
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
// Імпорт для логера
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

// --- Дані для дерева ресурсів ---
val resourceTreeData: Map<String, List<String>> = mapOf(
    "" to listOf("Cluster", "Workloads", "Network", "Storage", "Configuration", "Access Control"),
    "Cluster" to listOf("Namespaces", "Nodes"),
    "Workloads" to listOf("Pods", "Deployments", "StatefulSets", "DaemonSets", "ReplicaSets", "Jobs", "CronJobs"),
    "Network" to listOf("Services", "Ingresses"),
    "Storage" to listOf("PersistentVolumes", "PersistentVolumeClaims", "StorageClasses"),
    "Configuration" to listOf("ConfigMaps", "Secrets"),
    "Access Control" to listOf("ServiceAccounts", "Roles", "RoleBindings", "ClusterRoles", "ClusterRoleBindings")
)
val resourceLeafNodes: Set<String> = setOf(
    "Namespaces", "Nodes", "Pods", "Deployments", "StatefulSets", "DaemonSets", "ReplicaSets", "Jobs", "CronJobs",
    "Services", "Ingresses", "PersistentVolumes", "PersistentVolumeClaims", "StorageClasses", "ConfigMaps", "Secrets",
    "ServiceAccounts", "Roles", "RoleBindings", "ClusterRoles", "ClusterRoleBindings"
)
// Логер
private val logger = LoggerFactory.getLogger("MainKtFabric8SavedBaseline")
// --- Константи ---
const val MAX_CONNECT_RETRIES = 3
const val RETRY_DELAY_MS = 1000L
const val CONNECTION_TIMEOUT_MS = 5000
const val REQUEST_TIMEOUT_MS = 10000
const val FABRIC8_VERSION = "6.13.5"
// --- Функції завантаження ресурсів ---

// Обгортка для безпечного виклику API (БЕЗ inline)
suspend fun <T> fetchK8sResource(
    client: KubernetesClient?,
    resourceType: String,
    apiCall: suspend (KubernetesClient) -> List<T>?
): Result<List<T>> {
    if (client == null) {
        logger.warn("fetchK8sResource<$resourceType>: Client is null.")
        return Result.failure(IllegalStateException("Клієнт Kubernetes не ініціалізовано"))
    }
    logger.info("Завантаження списку $resourceType (Fabric8)...")
    return try {
        val items = kotlinx.coroutines.withContext(Dispatchers.IO) { // Перевіряємо компіляцію
            logger.info("[IO] Виклик API для $resourceType...")
            apiCall(client) ?: emptyList()
        }
        logger.info("Завантажено ${items.size} $resourceType.")
        try {
            //@Suppress("UNCHECKED_CAST")
            val sortedItems = items.sortedBy { (it as? HasMetadata)?.metadata?.name ?: "" }
            Result.success(sortedItems)
        } catch (e: ClassCastException) {
            logger.warn("Не вдалося сортувати $resourceType за іменем, повертається оригінальний список.")
            Result.success(items)
        }
    } catch (e: KubernetesClientException) {
        logger.error("KubernetesClientException під час завантаження $resourceType: ${e.message}", e)
        Result.failure(e)
    } catch (e: Exception) {
        logger.error("Загальна помилка завантаження $resourceType: ${e.message}", e)
        Result.failure(e)
    }
}

// Специфічні функції завантаження
suspend fun loadNamespacesFabric8(client: KubernetesClient?) = fetchK8sResource(client, "Namespaces") { it.namespaces().list().items }
suspend fun loadNodesFabric8(client: KubernetesClient?) = fetchK8sResource(client, "Nodes") { it.nodes().list().items }
suspend fun loadPodsFabric8(client: KubernetesClient?) = fetchK8sResource(client, "Pods") { it.pods().inAnyNamespace().list().items }
suspend fun loadDeploymentsFabric8(client: KubernetesClient?) = fetchK8sResource(client, "Deployments") { it.apps().deployments().inAnyNamespace().list().items }
suspend fun loadStatefulSetsFabric8(client: KubernetesClient?) = fetchK8sResource(client, "StatefulSets") { it.apps().statefulSets().inAnyNamespace().list().items }
suspend fun loadDaemonSetsFabric8(client: KubernetesClient?) = fetchK8sResource(client, "DaemonSets") { it.apps().daemonSets().inAnyNamespace().list().items }
suspend fun loadReplicaSetsFabric8(client: KubernetesClient?) = fetchK8sResource(client, "ReplicaSets") { it.apps().replicaSets().inAnyNamespace().list().items }
suspend fun loadJobsFabric8(client: KubernetesClient?) = fetchK8sResource(client, "Jobs") { it.batch().v1().jobs().inAnyNamespace().list().items }
suspend fun loadCronJobsFabric8(client: KubernetesClient?) = fetchK8sResource(client, "CronJobs") { it.batch().v1().cronjobs().inAnyNamespace().list().items }
suspend fun loadServicesFabric8(client: KubernetesClient?) = fetchK8sResource(client, "Services") { it.services().inAnyNamespace().list().items }
suspend fun loadIngressesFabric8(client: KubernetesClient?) = fetchK8sResource(client, "Ingresses") { it.network().v1().ingresses().inAnyNamespace().list().items }
suspend fun loadPVsFabric8(client: KubernetesClient?) = fetchK8sResource(client, "PersistentVolumes") { it.persistentVolumes().list().items }
suspend fun loadPVCsFabric8(client: KubernetesClient?) = fetchK8sResource(client, "PersistentVolumeClaims") { it.persistentVolumeClaims().inAnyNamespace().list().items }
suspend fun loadStorageClassesFabric8(client: KubernetesClient?) = fetchK8sResource(client, "StorageClasses") { it.storage().v1().storageClasses().list().items }
suspend fun loadConfigMapsFabric8(client: KubernetesClient?) = fetchK8sResource(client, "ConfigMaps") { it.configMaps().inAnyNamespace().list().items }
suspend fun loadSecretsFabric8(client: KubernetesClient?) = fetchK8sResource(client, "Secrets") { it.secrets().inAnyNamespace().list().items }
suspend fun loadServiceAccountsFabric8(client: KubernetesClient?) = fetchK8sResource(client, "ServiceAccounts") { it.serviceAccounts().inAnyNamespace().list().items }
suspend fun loadRolesFabric8(client: KubernetesClient?) = fetchK8sResource(client, "Roles") { it.rbac().roles().inAnyNamespace().list().items }
suspend fun loadRoleBindingsFabric8(client: KubernetesClient?) = fetchK8sResource(client, "RoleBindings") { it.rbac().roleBindings().inAnyNamespace().list().items }
suspend fun loadClusterRolesFabric8(client: KubernetesClient?) = fetchK8sResource(client, "ClusterRoles") { it.rbac().clusterRoles().list().items }
suspend fun loadClusterRoleBindingsFabric8(client: KubernetesClient?) = fetchK8sResource(client, "ClusterRoleBindings") { it.rbac().clusterRoleBindings().list().items }
// ---

// --- Функція підключення з ретраями (використовує Config.autoConfigure(contextName)) ---
suspend fun connectWithRetries(contextName: String?): Result<Pair<KubernetesClient, String>> {
    val targetContext = if (contextName.isNullOrBlank()) null else contextName
    var lastError: Exception? = null
    val contextNameToLog = targetContext ?: "(default)"

    for (attempt in 1..MAX_CONNECT_RETRIES) {
        logger.info("Спроба підключення до '$contextNameToLog' (спроба $attempt/$MAX_CONNECT_RETRIES)...")
        try {
            val resultPair: Pair<KubernetesClient, String> = kotlinx.coroutines.withContext(Dispatchers.IO) { // Перевіряємо компіляцію
                logger.info("[IO] Створення конфігу та клієнта для '$contextNameToLog' через Config.autoConfigure...")
                val config = Config.autoConfigure(targetContext)
                    ?: throw KubernetesClientException("Не вдалося автоматично налаштувати конфігурацію для контексту '$contextNameToLog'")
                config.connectionTimeout = CONNECTION_TIMEOUT_MS
                config.requestTimeout = REQUEST_TIMEOUT_MS
                logger.info("[IO] Config context: ${config.currentContext?.name ?: "(не вказано)"}. Namespace: ${config.namespace}")

                val client = KubernetesClientBuilder().withConfig(config).build()
                logger.info("[IO] Fabric8 client created. Checking version...")
                val ver = client.kubernetesVersion?.gitVersion ?: client.kubernetesVersion?.gitVersion ?: "невідомо"
                logger.info("[IO] Версія сервера: $ver для '$contextNameToLog'")
                Pair(client, ver)
            }
            logger.info("Підключення до '$contextNameToLog' успішне (спроба $attempt).")
            return Result.success(resultPair)
        } catch (e: Exception) {
            lastError = e; logger.warn("Помилка підключення '$contextNameToLog' (спроба $attempt): ${e.message}")
            if (attempt < MAX_CONNECT_RETRIES) { kotlinx.coroutines.delay(RETRY_DELAY_MS) }
        }
    }
    logger.error("Не вдалося підключитися до '$contextNameToLog' після $MAX_CONNECT_RETRIES спроб.")
    return Result.failure(lastError ?: IOException("Невідома помилка підключення"))
}
// ---

@Composable
@Preview
fun App() {
    // --- Стани ---
    var contexts by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedContext by remember { mutableStateOf<String?>(null) }
    var selectedResourceType by remember { mutableStateOf<String?>(null) }
    val expandedNodes = remember { mutableStateMapOf<String, Boolean>() }
    var activeClient by remember { mutableStateOf<KubernetesClient?>(null) }
    var connectionStatus by remember { mutableStateOf("Завантаження конфігурації...") }
    var isLoading by remember { mutableStateOf(false) } // Загальний індикатор
    var resourceLoadError by remember { mutableStateOf<String?>(null) }
    // Стан для всіх типів ресурсів
    var namespacesList by remember { mutableStateOf<List<Namespace>>(emptyList()) }
    var nodesList by remember { mutableStateOf<List<Node>>(emptyList()) }
    var podsList by remember { mutableStateOf<List<Pod>>(emptyList()) }
    var deploymentsList by remember { mutableStateOf<List<Deployment>>(emptyList()) }
    var statefulSetsList by remember { mutableStateOf<List<StatefulSet>>(emptyList()) }
    var daemonSetsList by remember { mutableStateOf<List<DaemonSet>>(emptyList()) }
    var replicaSetsList by remember { mutableStateOf<List<ReplicaSet>>(emptyList()) }
    var jobsList by remember { mutableStateOf<List<Job>>(emptyList()) }
    var cronJobsList by remember { mutableStateOf<List<CronJob>>(emptyList()) }
    var servicesList by remember { mutableStateOf<List<Service>>(emptyList()) }
    var ingressesList by remember { mutableStateOf<List<Ingress>>(emptyList()) }
    var pvsList by remember { mutableStateOf<List<PersistentVolume>>(emptyList()) }
    var pvcsList by remember { mutableStateOf<List<PersistentVolumeClaim>>(emptyList()) }
    var storageClassesList by remember { mutableStateOf<List<StorageClass>>(emptyList()) }
    var configMapsList by remember { mutableStateOf<List<ConfigMap>>(emptyList()) }
    var secretsList by remember { mutableStateOf<List<Secret>>(emptyList()) }
    var serviceAccountsList by remember { mutableStateOf<List<ServiceAccount>>(emptyList()) }
    var rolesList by remember { mutableStateOf<List<Role>>(emptyList()) }
    var roleBindingsList by remember { mutableStateOf<List<RoleBinding>>(emptyList()) }
    var clusterRolesList by remember { mutableStateOf<List<ClusterRole>>(emptyList()) }
    var clusterRoleBindingsList by remember { mutableStateOf<List<ClusterRoleBinding>>(emptyList()) }
    // Діалог
    val showErrorDialog = remember { mutableStateOf(false) }
    val dialogErrorMessage = remember { mutableStateOf("") }
    // ------------------------------

    val coroutineScope = rememberCoroutineScope()

    // --- Функція для очищення всіх списків ресурсів ---
    fun clearResourceLists() {
        namespacesList = emptyList(); nodesList = emptyList(); podsList = emptyList(); deploymentsList = emptyList(); statefulSetsList = emptyList(); daemonSetsList = emptyList(); replicaSetsList = emptyList(); jobsList = emptyList(); cronJobsList = emptyList(); servicesList = emptyList(); ingressesList = emptyList(); pvsList = emptyList(); pvcsList = emptyList(); storageClassesList = emptyList(); configMapsList = emptyList(); secretsList = emptyList(); serviceAccountsList = emptyList(); rolesList = emptyList(); roleBindingsList = emptyList(); clusterRolesList = emptyList(); clusterRoleBindingsList = emptyList();
    }
    // ---

    // --- Завантаження контекстів через Config.autoConfigure(null).contexts ---
    LaunchedEffect(Unit) {
        logger.info("LaunchedEffect: Starting context load via Config.autoConfigure(null)...")
        isLoading = true; connectionStatus = "Завантаження Kubeconfig...";
        var loadError: Exception? = null; var loadedContextNames: List<String> = emptyList()
        try {
            loadedContextNames = kotlinx.coroutines.withContext(Dispatchers.IO) { // Чи компілюється це?
                logger.info("[IO] Calling Config.autoConfigure(null)...")
                val config = Config.autoConfigure(null) ?: throw IOException("Не вдалося завантажити Kubeconfig")
                val names = config.contexts?.mapNotNull { it.name }?.sorted() ?: emptyList()
                logger.info("[IO] Знайдено контекстів: ${names.size}")
                names
            }
            contexts = loadedContextNames; errorMessage = if (loadedContextNames.isEmpty()) "Контексти не знайдено" else null; connectionStatus = if (loadedContextNames.isEmpty()) "Контексти не знайдено" else "Виберіть контекст"
        } catch (e: Exception) { loadError = e; logger.error("Помилка завантаження контекстів: ${e.message}", e) }
        finally { if (loadError != null) { errorMessage = "Помилка завантаження: ${loadError.message}"; connectionStatus = "Помилка завантаження" }; isLoading = false }
    }
    // --- Кінець LaunchedEffect ---

    // --- Діалогове вікно помилки ---
    if (showErrorDialog.value) {
        AlertDialog(
            onDismissRequest = { showErrorDialog.value = false },
            title = { Text("Помилка Підключення") },
            text = { Text(dialogErrorMessage.value) },
            confirmButton = { Button(onClick = { showErrorDialog.value = false }) { Text("OK") } }
        )
    }
    // ---

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f)) {
                // --- Ліва панель ---
                Column( modifier = Modifier.fillMaxHeight().width(300.dp).padding(8.dp) ) {
                    Text("Контексти Kubernetes:", style = MaterialTheme.typography.h6); Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.weight(1f).border(1.dp, Color.Gray)) {
                        if (isLoading && contexts.isEmpty()) { CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) }
                        else if (!isLoading && contexts.isEmpty()) { Text(errorMessage ?: "Контексти не знайдено", modifier = Modifier.align(Alignment.Center)) }
                        else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(contexts) { contextName ->
                                    Text(text = contextName, modifier = Modifier.fillMaxWidth().clickable(enabled = !isLoading) {
                                        if (selectedContext != contextName) {
                                            logger.info("Клікнуто на контекст: $contextName. Запуск connectWithRetries...")
                                            coroutineScope.launch {
                                                isLoading = true; connectionStatus = "Підключення до '$contextName' (спроба 1/$MAX_CONNECT_RETRIES)..."; activeClient?.close(); activeClient = null; selectedResourceType = null; clearResourceLists(); resourceLoadError = null; errorMessage = null;
                                                val connectionResult = connectWithRetries(contextName) // Викликаємо функцію
                                                isLoading = false

                                                connectionResult.onSuccess { (newClient, serverVersion) -> activeClient = newClient; selectedContext = contextName; connectionStatus = "Підключено до: $contextName (v$serverVersion)"; errorMessage = null; logger.info("UI State updated on Success for $contextName") }
                                                    .onFailure { error -> connectionStatus = "Помилка підключення до '$contextName'"; errorMessage = error.localizedMessage ?: "Невід. помилка"; logger.info("Setting up error dialog for: $contextName. Error: ${error.message}"); dialogErrorMessage.value = "Не вдалося підключитися до '$contextName' після $MAX_CONNECT_RETRIES спроб:\n${error.message}"; showErrorDialog.value = true; activeClient = null; selectedContext = null }
                                                logger.info("Спроба підключення до '$contextName' завершена (результат оброблено).")
                                            }
                                        }
                                    }.padding(8.dp), color = if (contextName == selectedContext) MaterialTheme.colors.primary else LocalContentColor.current)
                                }
                            }
                        }
                    } // Кінець Box списку
                    Spacer(modifier = Modifier.height(16.dp)); Text("Ресурси Кластера:", style = MaterialTheme.typography.h6); Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.weight(2f).border(1.dp, Color.Gray)) { // Дерево ресурсів
                        resourceTreeView(rootIds = resourceTreeData[""] ?: emptyList(), expandedNodes = expandedNodes, onNodeClick = { nodeId, isLeaf ->
                            logger.info("Клікнуто на вузол: $nodeId, Це листок: $isLeaf")
                            if (isLeaf) {
                                if (activeClient != null && !isLoading) {
                                    selectedResourceType = nodeId; resourceLoadError = null; clearResourceLists() // Очищаємо перед завантаженням
                                    connectionStatus = "Завантаження $nodeId..."; isLoading = true
                                    coroutineScope.launch { // Запускаємо завантаження в корутині
                                        var loadOk = false
                                        var errorMsg: String? = null
                                        // --- ВИКЛИК ВІДПОВІДНОЇ ФУНКЦІЇ ЗАВАНТАЖЕННЯ ---
                                        when (nodeId) {
                                            "Namespaces" -> loadNamespacesFabric8(activeClient).onSuccess { namespacesList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "Nodes" -> loadNodesFabric8(activeClient).onSuccess { nodesList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "Pods" -> loadPodsFabric8(activeClient).onSuccess { podsList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "Deployments" -> loadDeploymentsFabric8(activeClient).onSuccess { deploymentsList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "StatefulSets" -> loadStatefulSetsFabric8(activeClient).onSuccess { statefulSetsList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "DaemonSets" -> loadDaemonSetsFabric8(activeClient).onSuccess { daemonSetsList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "ReplicaSets" -> loadReplicaSetsFabric8(activeClient).onSuccess { replicaSetsList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "Jobs" -> loadJobsFabric8(activeClient).onSuccess { jobsList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "CronJobs" -> loadCronJobsFabric8(activeClient).onSuccess { cronJobsList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "Services" -> loadServicesFabric8(activeClient).onSuccess { servicesList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "Ingresses" -> loadIngressesFabric8(activeClient).onSuccess { ingressesList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "PersistentVolumes" -> loadPVsFabric8(activeClient).onSuccess { pvsList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "PersistentVolumeClaims" -> loadPVCsFabric8(activeClient).onSuccess { pvcsList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "StorageClasses" -> loadStorageClassesFabric8(activeClient).onSuccess { storageClassesList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "ConfigMaps" -> loadConfigMapsFabric8(activeClient).onSuccess { configMapsList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "Secrets" -> loadSecretsFabric8(activeClient).onSuccess { secretsList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "ServiceAccounts" -> loadServiceAccountsFabric8(activeClient).onSuccess { serviceAccountsList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "Roles" -> loadRolesFabric8(activeClient).onSuccess { rolesList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "RoleBindings" -> loadRoleBindingsFabric8(activeClient).onSuccess { roleBindingsList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "ClusterRoles" -> loadClusterRolesFabric8(activeClient).onSuccess { clusterRolesList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            "ClusterRoleBindings" -> loadClusterRoleBindingsFabric8(activeClient).onSuccess { clusterRoleBindingsList = it; loadOk = true }.onFailure { errorMsg = it.message }
                                            else -> { logger.warn("Обробник '$nodeId' не реалізовано."); loadOk = false; errorMsg = "Не реалізовано" }
                                        }
                                        // Оновлюємо статус після завершення
                                        if (loadOk) { connectionStatus = "Завантажено $nodeId" }
                                        else { resourceLoadError = "Помилка завантаження $nodeId: ${errorMsg ?: "Невідома помилка"}"; connectionStatus = "Помилка завантаження $nodeId" }
                                        isLoading = false
                                    }
                                } else if (activeClient == null) { logger.warn("Немає підключення."); connectionStatus = "Підключіться до кластера!"; selectedResourceType = null }
                            } else { expandedNodes[nodeId] = !(expandedNodes[nodeId] ?: false) }
                        })
                    }
                } // Кінець лівої панелі

                Divider(modifier = Modifier.fillMaxHeight().width(1.dp), color = Color.LightGray)

                // --- Права панель ---
                Box(modifier = Modifier.fillMaxHeight().weight(1f).padding(16.dp)) {
                    when {
                        isLoading && selectedResourceType != null -> { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) { CircularProgressIndicator(); Spacer(modifier = Modifier.height(8.dp)); Text("Завантаження $selectedResourceType...") } }
                        resourceLoadError != null -> { Text(resourceLoadError!!, color = MaterialTheme.colors.error, modifier = Modifier.align(Alignment.Center)) }

                        // --- Відображення всіх ресурсів ---
                        selectedResourceType == "Namespaces" -> ResourceListComposable(title = "Namespaces", items = namespacesList) { "${it.metadata?.name} (Status: ${it.status?.phase ?: '-'})" }
                        selectedResourceType == "Nodes" -> ResourceListComposable(title = "Nodes", items = nodesList) { "${it.metadata?.name} (OS: ${it.status?.nodeInfo?.osImage ?: '-'})" }
                        selectedResourceType == "Pods" -> ResourceListComposable(title = "Pods", items = podsList) { "${it.metadata?.namespace}/${it.metadata?.name} (Status: ${it.status?.phase ?: '-'})" }
                        selectedResourceType == "Deployments" -> ResourceListComposable(title = "Deployments", items = deploymentsList) { "${it.metadata?.namespace}/${it.metadata?.name} (${it.status?.readyReplicas ?: 0}/${it.spec?.replicas ?: 0})" }
                        selectedResourceType == "StatefulSets" -> ResourceListComposable(title = "StatefulSets", items = statefulSetsList) { "${it.metadata?.namespace}/${it.metadata?.name} (${it.status?.readyReplicas ?: 0}/${it.spec?.replicas ?: 0})" }
                        selectedResourceType == "DaemonSets" -> ResourceListComposable(title = "DaemonSets", items = daemonSetsList) { "${it.metadata?.namespace}/${it.metadata?.name} (Ready: ${it.status?.numberReady ?: 0})" }
                        selectedResourceType == "ReplicaSets" -> ResourceListComposable(title = "ReplicaSets", items = replicaSetsList) { "${it.metadata?.namespace}/${it.metadata?.name} (${it.status?.readyReplicas ?: 0}/${it.spec?.replicas ?: 0})" }
                        selectedResourceType == "Jobs" -> ResourceListComposable(title = "Jobs", items = jobsList) { "${it.metadata?.namespace}/${it.metadata?.name} (Succeeded: ${it.status?.succeeded ?: 0})" }
                        selectedResourceType == "CronJobs" -> ResourceListComposable(title = "CronJobs", items = cronJobsList) { "${it.metadata?.namespace}/${it.metadata?.name} (Schedule: ${it.spec?.schedule ?: '-'})" }
                        selectedResourceType == "Services" -> ResourceListComposable(title = "Services", items = servicesList) { "${it.metadata?.namespace}/${it.metadata?.name} (Type: ${it.spec?.type ?: '-'})" }
                        selectedResourceType == "Ingresses" -> ResourceListComposable(title = "Ingresses", items = ingressesList) { "${it.metadata?.namespace}/${it.metadata?.name}" }
                        selectedResourceType == "PersistentVolumes" -> ResourceListComposable(title = "PersistentVolumes", items = pvsList) { "${it.metadata?.name} (Phase: ${it.status?.phase ?: '-'})" }
                        selectedResourceType == "PersistentVolumeClaims" -> ResourceListComposable(title = "PersistentVolumeClaims", items = pvcsList) { "${it.metadata?.namespace}/${it.metadata?.name} (Phase: ${it.status?.phase ?: '-'})" }
                        selectedResourceType == "StorageClasses" -> ResourceListComposable(title = "StorageClasses", items = storageClassesList) { "${it.metadata?.name} (Prov: ${it.provisioner ?: '-'})" }
                        selectedResourceType == "ConfigMaps" -> ResourceListComposable(title = "ConfigMaps", items = configMapsList) { "${it.metadata?.namespace}/${it.metadata?.name} (Keys: ${it.data?.size ?: 0})" }
                        selectedResourceType == "Secrets" -> ResourceListComposable(title = "Secrets", items = secretsList) { "${it.metadata?.namespace}/${it.metadata?.name} (Type: ${it.type ?: '-'})" }
                        selectedResourceType == "ServiceAccounts" -> ResourceListComposable(title = "ServiceAccounts", items = serviceAccountsList) { "${it.metadata?.namespace}/${it.metadata?.name}" }
                        selectedResourceType == "Roles" -> ResourceListComposable(title = "Roles", items = rolesList) { "${it.metadata?.namespace}/${it.metadata?.name}" }
                        selectedResourceType == "RoleBindings" -> ResourceListComposable(title = "RoleBindings", items = roleBindingsList) { "${it.metadata?.namespace}/${it.metadata?.name}" }
                        selectedResourceType == "ClusterRoles" -> ResourceListComposable(title = "ClusterRoles", items = clusterRolesList) { "${it.metadata?.name}" }
                        selectedResourceType == "ClusterRoleBindings" -> ResourceListComposable(title = "ClusterRoleBindings", items = clusterRoleBindingsList) { "${it.metadata?.name}" }
                        // ---

                        activeClient != null -> { Text("Підключено до $selectedContext.\nВиберіть тип ресурсу.", modifier = Modifier.align(Alignment.Center)) }
                        isLoading && contexts.isEmpty() -> { Text("Завантаження конфігурації...", modifier = Modifier.align(Alignment.Center)) }
                        else -> { Text(errorMessage ?: "Виберіть контекст.", modifier = Modifier.align(Alignment.Center)) }
                    }
                } // Кінець правої панелі
            } // Кінець Row
            // --- Статус-бар ---
            Divider()
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = connectionStatus, modifier = Modifier.weight(1f), style = MaterialTheme.typography.caption)
                if (isLoading) { CircularProgressIndicator(modifier = Modifier.size(16.dp)) }
            }
            // ---------------
        } // Кінець Column
    } // Кінець MaterialTheme
}

// --- Універсальний Composable для відображення списку ресурсів ---
@Composable
fun <T> ResourceListComposable(title: String, items: List<T>, itemToString: (T) -> String) {
    Column {
        Text("$title (${items.size}):", style = MaterialTheme.typography.h6)
        Spacer(modifier = Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text("Немає ресурсів цього типу.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items) { item ->
                    // TODO: Додати можливість кліку на елемент для показу деталей
                    Text(itemToString(item), modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}
// ---

// --- Composable для дерева ресурсів ---
@Composable
fun resourceTreeView(
    rootIds: List<String>,
    expandedNodes: MutableMap<String, Boolean>,
    onNodeClick: (id: String, isLeaf: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(start = 8.dp)) {
        rootIds.forEach { nodeId ->
            item {
                resourceTreeNode(
                    nodeId = nodeId,
                    level = 0,
                    expandedNodes = expandedNodes,
                    onNodeClick = onNodeClick
                )
            }
        }
    }
}

@Composable
fun resourceTreeNode(
    nodeId: String,
    level: Int,
    expandedNodes: MutableMap<String, Boolean>,
    onNodeClick: (id: String, isLeaf: Boolean) -> Unit
) {
    val isLeaf = resourceLeafNodes.contains(nodeId)
    val children = resourceTreeData[nodeId]
    val isExpanded = expandedNodes[nodeId] ?: false

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (level * 16).dp)
            .clickable { onNodeClick(nodeId, isLeaf) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when {
            !isLeaf && children?.isNotEmpty() == true -> if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight
            !isLeaf -> Icons.Filled.Place // Ваша іконка
            else -> Icons.Filled.Info     // Ваша іконка
        }
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(nodeId, style = MaterialTheme.typography.body1)
    }

    if (!isLeaf && isExpanded && children != null) {
        Column {
            children.sorted().forEach { childId ->
                resourceTreeNode(
                    nodeId = childId,
                    level = level + 1,
                    expandedNodes = expandedNodes,
                    onNodeClick = onNodeClick
                )
            }
        }
    }
}
// ---

// --- Головна функція ---
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Kotlin Kube Manager v1.13 - All Resources (No Helpers)") { App() }
}
