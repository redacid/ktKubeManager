// src/main/kotlin/Main.kt
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
// --- Імпорти для Official Kubernetes Java Client ---
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.Configuration // Важливо: інший Configuration!
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.CoreV1Api // Для перевірки версії та завантаження Namespace
import io.kubernetes.client.util.ClientBuilder
import io.kubernetes.client.util.KubeConfig // Для роботи з kubeconfig
import io.kubernetes.client.openapi.models.V1Namespace // <-- Модель для Namespace
// import io.kubernetes.client.util.credentials.ClientCertificateAuthentication // Може знадобитись
// ---------------------------------------------------
// Імпорти для Coroutines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
// Імпорт для логера
import org.slf4j.LoggerFactory
// Інші імпорти
import java.io.FileReader
import java.io.IOException

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
// ------------------------------------------------

// Логер
private val logger = LoggerFactory.getLogger("MainKtOfficialClient")

// --- Функція для завантаження Namespaces з офіційним клієнтом ---
suspend fun loadNamespacesOfficial(apiClient: ApiClient?): Result<List<V1Namespace>> {
    if (apiClient == null) {
        logger.warn("loadNamespacesOfficial: Спроба завантажити без активного ApiClient.")
        return Result.failure(IllegalStateException("ApiClient не ініціалізовано"))
    }
    logger.info("Завантаження списку Namespaces (official client)...")
    return try {
        // Використовуємо withContext для IO операції
        val namespaceList = kotlinx.coroutines.withContext(Dispatchers.IO) { // <--- Перевірка №N
            logger.info("[IO] Створення CoreV1Api та виклик listNamespace...")
            val coreApi = CoreV1Api(apiClient)
            // Вказуємо null для параметрів, які нам не потрібні (pretty, allowWatchBookmarks і т.д.)
            // _continue та fieldSelector теж null
            // labelSelector null
            // limit null (без обмеження)
            // resourceVersion null
            // resourceVersionMatch null
            // timeoutSeconds null
            // watch false
            coreApi.listNamespace(null, null, null, null, null, null, null, null, null, false).items ?: emptyList()
        }
        logger.info("Завантажено ${namespaceList.size} Namespaces.")
        // Сортуємо за іменем з V1ObjectMeta
        Result.success(namespaceList.sortedBy { it.metadata?.name ?: "" })
    } catch (e: ApiException) {
        logger.error("ApiException під час завантаження Namespaces: code=${e.code}, body=${e.responseBody}", e)
        Result.failure(e)
    }
    catch (e: Exception) {
        logger.error("Загальна помилка завантаження Namespaces: ${e.message}", e)
        Result.failure(e)
    }
}
// ---

@Composable
@Preview
fun App() {
    // --- Стани ---
    var contexts by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) } // Для помилок завантаження/підключення
    var selectedContext by remember { mutableStateOf<String?>(null) }
    var selectedResourceType by remember { mutableStateOf<String?>(null) }
    val expandedNodes = remember { mutableStateMapOf<String, Boolean>() }
    var activeApiClient by remember { mutableStateOf<ApiClient?>(null) } // Зберігаємо ApiClient
    var connectionStatus by remember { mutableStateOf("Завантаження конфігурації...") }
    var isLoading by remember { mutableStateOf(false) } // Загальний індикатор завантаження

    // --- Стани для ресурсів ---
    var resourceLoadError by remember { mutableStateOf<String?>(null) } // Помилка завантаження ресурсів
    var namespacesList by remember { mutableStateOf<List<V1Namespace>>(emptyList()) } // Використовуємо V1Namespace
    // Додайте подібні стани для інших типів ресурсів пізніше
    // ------------------------------

    val coroutineScope = rememberCoroutineScope()

    // --- Завантаження контекстів з використанням client-java ---
    LaunchedEffect(Unit) {
        logger.info("LaunchedEffect: Starting context load using client-java...")
        isLoading = true
        connectionStatus = "Завантаження Kubeconfig..."
        var loadError: Exception? = null
        var loadedContextNames: List<String> = emptyList()

        try {
            loadedContextNames = kotlinx.coroutines.withContext(Dispatchers.IO) {
                logger.info("[IO] Reading kubeconfig using KubeConfig.loadKubeConfig()...")
                val kubeConfigPath = KubeConfig.findKubeConfigPath() ?: throw IOException("Kubeconfig path not found")
                logger.info("[IO] Kubeconfig path: $kubeConfigPath")
                val kubeConfig = KubeConfig.loadKubeConfig(FileReader(kubeConfigPath))
                val names = kubeConfig.contexts?.mapNotNull { it.name }?.sorted() ?: emptyList()
                logger.info("[IO] Знайдено контекстів: ${names.size}")
                names
            }
            contexts = loadedContextNames
            errorMessage = if (loadedContextNames.isEmpty()) "Контексти не знайдено" else null
            connectionStatus = if (loadedContextNames.isEmpty()) "Контексти не знайдено" else "Виберіть контекст"
        } catch (e: IOException) {
            logger.error("[LaunchedEffect] IO Помилка завантаження Kubeconfig: ${e.message}", e)
            loadError = e
        }
        catch (e: Exception) {
            logger.error("[LaunchedEffect] Загальна помилка завантаження Kubeconfig: ${e.message}", e)
            loadError = e
        } finally {
            if (loadError != null) {
                errorMessage = "Помилка завантаження конфігурації: ${loadError.message}"
                connectionStatus = "Помилка завантаження"
            }
            isLoading = false
            logger.info("LaunchedEffect: Finished context load attempt.")
        }
    }
    // --- Кінець LaunchedEffect ---

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
                                            logger.info("Клікнуто на контекст: $contextName. Запуск підключення (official client)...")
                                            // --- ЛОГІКА ПІДКЛЮЧЕННЯ з official client ---
                                            coroutineScope.launch {
                                                isLoading = true
                                                connectionStatus = "Підключення до '$contextName'..."
                                                activeApiClient = null // Скидаємо старий ApiClient
                                                selectedResourceType = null; namespacesList = emptyList(); resourceLoadError = null;
                                                var connectError: Exception? = null
                                                var newApiClient: ApiClient? = null
                                                var serverVersion: String? = null

                                                try {
                                                    val resultPair = kotlinx.coroutines.withContext(Dispatchers.IO) { // <--- Перевіряємо компіляцію
                                                        logger.info("[IO] Створення ApiClient для '$contextName'...")
                                                        val kubeConfigPath = KubeConfig.findKubeConfigPath() ?: throw IOException("Kubeconfig path not found")
                                                        val kubeConfig = KubeConfig.loadKubeConfig(FileReader(kubeConfigPath))
                                                        val client: ApiClient = ClientBuilder.kubeconfig(kubeConfig).setContext(contextName).build()
                                                        logger.info("[IO] ApiClient створено. Перевірка версії...")
                                                        val coreApi = CoreV1Api(client) // Створюємо API з цим клієнтом
                                                        val versionInfo = coreApi.code
                                                        val ver = versionInfo?.gitVersion ?: "невідомо"
                                                        logger.info("[IO] Версія сервера: $ver для '$contextName'")
                                                        Pair(client, ver) // Повертаємо ApiClient і версію
                                                    }
                                                    newApiClient = resultPair.first
                                                    serverVersion = resultPair.second
                                                } catch (e: ApiException) {
                                                    logger.error("[Connect] ApiException: code=${e.code}, body=${e.responseBody}", e)
                                                    connectError = e
                                                } catch (e: Exception) {
                                                    logger.error("[Connect] Загальна помилка: ${e.message}", e)
                                                    connectError = e
                                                } finally {
                                                    isLoading = false
                                                    if (connectError == null && newApiClient != null) {
                                                        activeApiClient = newApiClient
                                                        selectedContext = contextName
                                                        connectionStatus = "Підключено до: $contextName (v$serverVersion)"
                                                        errorMessage = null
                                                        // Не встановлюємо Configuration.setDefaultApiClient(newApiClient) глобально
                                                    } else {
                                                        connectionStatus = "Помилка підключення до '$contextName'"
                                                        errorMessage = connectError?.localizedMessage ?: "Невідома помилка"
                                                        activeApiClient = null
                                                        selectedContext = null
                                                    }
                                                    logger.info("Спроба підключення до '$contextName' завершена.")
                                                }
                                            } // --- Кінець coroutineScope.launch ---
                                        }
                                    }.padding(8.dp), color = if (contextName == selectedContext) MaterialTheme.colors.primary else LocalContentColor.current)
                                }
                            }
                        }
                    } // Кінець Box списку
                    Spacer(modifier = Modifier.height(16.dp)); Text("Ресурси Кластера:", style = MaterialTheme.typography.h6); Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.weight(2f).border(1.dp, Color.Gray)) { // Дерево ресурсів
                        ResourceTreeView(rootIds = resourceTreeData[""] ?: emptyList(), expandedNodes = expandedNodes, onNodeClick = { nodeId, isLeaf ->
                            logger.info("Клікнуто на вузол: $nodeId, Це листок: $isLeaf")
                            if (isLeaf) {
                                if (activeApiClient != null && !isLoading) {
                                    selectedResourceType = nodeId; resourceLoadError = null; namespacesList = emptyList()
                                    // --- ЗАПУСК ЗАВАНТАЖЕННЯ РЕСУРСІВ ---
                                    when (nodeId) {
                                        "Namespaces" -> {
                                            connectionStatus = "Завантаження Namespaces..."
                                            isLoading = true
                                            coroutineScope.launch {
                                                val result = loadNamespacesOfficial(activeApiClient) // Викликаємо нову функцію
                                                result.onSuccess { loadedNamespaces ->
                                                    namespacesList = loadedNamespaces // Зберігаємо V1Namespace
                                                    connectionStatus = "Завантажено Namespaces: ${loadedNamespaces.size}"
                                                    resourceLoadError = null
                                                }.onFailure { error ->
                                                    resourceLoadError = "Помилка завантаження Namespaces: ${error.message}"
                                                    connectionStatus = "Помилка завантаження Namespaces"
                                                }
                                                isLoading = false
                                            }
                                        }
                                        // TODO: Додати обробку для інших типів ресурсів
                                        else -> { logger.warn("Обробник '$nodeId' не реалізовано."); connectionStatus = "Вибрано '$nodeId' (не реалізовано)"; selectedResourceType = null }
                                    }
                                } else if (activeApiClient == null) { logger.warn("Немає підключення."); connectionStatus = "Підключіться до кластера!"; selectedResourceType = null }
                            } else { expandedNodes[nodeId] = !(expandedNodes[nodeId] ?: false) }
                        })
                    }
                } // Кінець лівої панелі

                Divider(/*...*/)

                // --- Права панель ---
                Box(modifier = Modifier.fillMaxHeight().weight(1f).padding(16.dp)) {
                    when {
                        isLoading && selectedResourceType != null -> { /* ... індикатор ... */ }
                        resourceLoadError != null -> { Text(resourceLoadError!!, color = MaterialTheme.colors.error, modifier = Modifier.align(Alignment.Center)) }
                        // --- Оновлено відображення Namespaces ---
                        selectedResourceType == "Namespaces" && namespacesList.isNotEmpty() -> {
                            Column {
                                Text("Namespaces (${namespacesList.size}):", style = MaterialTheme.typography.h6)
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(namespacesList) { ns ->
                                        // Використовуємо поля з V1Namespace / V1ObjectMeta
                                        val name = ns.metadata?.name ?: "N/A"
                                        val status = ns.status?.phase ?: "-"
                                        val creationTimestamp = ns.metadata?.creationTimestamp
                                        // Простий вивід, можна додати форматування дати/статусу
                                        Text("$name (Status: $status)", modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            }
                        }
                        // ---------------------------------------
                        activeApiClient != null -> { Text("Підключено до $selectedContext.\nВиберіть тип ресурсу.", modifier = Modifier.align(Alignment.Center)) }
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

// --- Composable для дерева ресурсів ---
@Composable
fun ResourceTreeView(
    rootIds: List<String>,
    expandedNodes: MutableMap<String, Boolean>,
    onNodeClick: (id: String, isLeaf: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(start = 8.dp)) {
        rootIds.forEach { nodeId ->
            item {
                ResourceTreeNode(
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
fun ResourceTreeNode(
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
            !isLeaf && children?.isNotEmpty() == true -> if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight
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
                ResourceTreeNode(
                    nodeId = childId,
                    level = level + 1,
                    expandedNodes = expandedNodes,
                    onNodeClick = onNodeClick
                )
            }
        }
    }
}

// --- Головна функція ---
fun main() = application {

    Window(onCloseRequest = ::exitApplication, title = "Kotlin Kube Manager v0.9 - Official Client") {
        App()
    }
}
