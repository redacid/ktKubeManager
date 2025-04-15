// src/main/kotlin/Main.kt (Повертаємо перемикання контексту через Config.autoConfigure(name))
import androidx.compose.desktop.ui.tooling.preview.Preview // Можна прибрати, якщо не потрібен preview
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
// --- Імпорти для Fabric8 ---
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.api.model.Namespace // Модель Fabric8
// --- Повертаємо імпорт KubeConfig Utils (не обов'язково KubeConfig, якщо є інший спосіб знайти шлях) ---
import io.fabric8.kubernetes.client.internal.KubeConfigUtils // Для findKubeConfigPath (внутрішній клас, але працює)
import java.io.FileReader
// ---------------------------
// Імпорти для Coroutines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // Використовуємо повне ім'я
import kotlinx.coroutines.CoroutineScope
// Імпорт для логера
import org.slf4j.LoggerFactory
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
private val logger = LoggerFactory.getLogger("MainKtFabric8Final")

// --- Функція для завантаження Namespaces з Fabric8 ---
suspend fun loadNamespacesFabric8(client: KubernetesClient?): Result<List<Namespace>> {
    if (client == null) return Result.failure(IllegalStateException("Клієнт Kubernetes не ініціалізовано"))
    logger.info("Завантаження Namespaces (Fabric8)...")
    return try {
        val namespaces = kotlinx.coroutines.withContext(Dispatchers.IO) { // <--- Перевіряємо компіляцію
            logger.info("[IO] Виклик client.namespaces().list()...")
            client.namespaces().list().items ?: emptyList()
        }
        logger.info("Завантажено ${namespaces.size} Namespaces.")
        Result.success(namespaces.sortedBy { it.metadata?.name ?: "" })
    } catch (e: KubernetesClientException) { logger.error("KubeExc loading Namespaces: ${e.message}", e); Result.failure(e) }
    catch (e: Exception) { logger.error("Error loading Namespaces: ${e.message}", e); Result.failure(e) }
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
    var activeClient by remember { mutableStateOf<KubernetesClient?>(null) } // Знову KubernetesClient
    var connectionStatus by remember { mutableStateOf("Завантаження конфігурації...") }
    var isLoading by remember { mutableStateOf(false) } // Загальний індикатор
    var resourceLoadError by remember { mutableStateOf<String?>(null) }
    var namespacesList by remember { mutableStateOf<List<Namespace>>(emptyList()) } // Модель Fabric8
    // ------------------------------

    val coroutineScope = rememberCoroutineScope()

    // --- Завантаження контекстів через Config.autoConfigure(null).contexts ---
    LaunchedEffect(Unit) {
        logger.info("LaunchedEffect: Starting context load via Config.autoConfigure(null)...")
        isLoading = true; connectionStatus = "Завантаження Kubeconfig..."; /*...*/
        var loadError: Exception? = null; var loadedContextNames: List<String> = emptyList()
        try {
            loadedContextNames = kotlinx.coroutines.withContext(Dispatchers.IO) { // <--- Перевірка компіляції №1
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

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f)) {
                // --- Ліва панель ---
                Column( modifier = Modifier.fillMaxHeight().width(300.dp).padding(8.dp) ) {
                    Text("Контексти Kubernetes:", style = MaterialTheme.typography.h6); Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.weight(1f).border(1.dp, Color.Gray)) {
                        if (isLoading && contexts.isEmpty()) { /*...*/ } else if (!isLoading && contexts.isEmpty()) { /*...*/ } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(contexts) { contextName ->
                                    Text(text = contextName, modifier = Modifier.fillMaxWidth().clickable(enabled = !isLoading) {
                                        if (selectedContext != contextName) {
                                            logger.info("Клікнуто на контекст: $contextName. Запуск підключення (через Config.autoConfigure(name))...")
                                            // --- ЛОГІКА ПІДКЛЮЧЕННЯ з Config.autoConfigure(contextName) ---
                                            coroutineScope.launch {
                                                isLoading = true; connectionStatus = "Підключення до '$contextName'..."; /* ... скидання стану ... */ activeClient?.close(); activeClient = null; selectedResourceType = null; namespacesList = emptyList(); resourceLoadError = null;
                                                var connectError: Exception? = null; var newClient: KubernetesClient? = null; var serverVersion: String? = null

                                                try {
                                                    val resultPair: Pair<KubernetesClient, String> = kotlinx.coroutines.withContext(Dispatchers.IO) { // <--- Перевірка компіляції №2
                                                        logger.info("[IO] Створення конфігу та клієнта для '$contextName' через Config.autoConfigure...")
                                                        // Створюємо конфіг відразу для потрібного контексту
                                                        val config = Config.autoConfigure(contextName)
                                                            ?: throw KubernetesClientException("Не вдалося автоматично налаштувати конфігурацію для контексту '$contextName'")
                                                        logger.info("[IO] Config context: ${config.currentContext?.name}. Namespace: ${config.namespace}")

                                                        // Створюємо клієнт З ЦІЄЮ КОНКРЕТНОЮ КОНФІГУРАЦІЄЮ
                                                        val client = KubernetesClientBuilder().withConfig(config).build()
                                                        logger.info("[IO] Fabric8 client created. Checking version...")
                                                        val ver = client.kubernetesVersion?.gitVersion ?: client.version?.gitVersion ?: "невідомо"
                                                        logger.info("[IO] Версія сервера: $ver для '$contextName'")
                                                        Pair(client, ver)
                                                    }
                                                    newClient = resultPair.first
                                                    serverVersion = resultPair.second
                                                } catch (e: Exception) { connectError = e; logger.error("[Connect] Помилка: ${e.message}", e) }
                                                finally {
                                                    isLoading = false
                                                    if (connectError == null && newClient != null) { activeClient = newClient; selectedContext = contextName; connectionStatus = "Підключено до: $contextName (v$serverVersion)"; errorMessage = null }
                                                    else { connectionStatus = "Помилка підключення до '$contextName'"; errorMessage = connectError?.localizedMessage ?: "Невідома помилка"; activeClient = null; selectedContext = null }
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
                                if (activeClient != null && !isLoading) {
                                    selectedResourceType = nodeId; resourceLoadError = null; namespacesList = emptyList()
                                    when (nodeId) {
                                        "Namespaces" -> {
                                            connectionStatus = "Завантаження Namespaces..."; isLoading = true
                                            coroutineScope.launch {
                                                val result = loadNamespacesFabric8(activeClient) // Викликаємо Fabric8 функцію
                                                result.onSuccess { namespacesList = it; connectionStatus = "Завантажено Namespaces: ${it.size}"; resourceLoadError = null }.onFailure { resourceLoadError = "Помилка: ${it.message}"; connectionStatus = "Помилка завантаження" }; isLoading = false
                                            }
                                        } else -> { logger.warn("Обробник '$nodeId' не реалізовано."); connectionStatus = "Вибрано '$nodeId' (не реалізовано)"; selectedResourceType = null }
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
                        isLoading && selectedResourceType != null -> { /*...*/ }
                        resourceLoadError != null -> { /*...*/ }
                        selectedResourceType == "Namespaces" && namespacesList.isNotEmpty() -> {
                            Column {
                                Text("Namespaces (${namespacesList.size}):", style = MaterialTheme.typography.h6)
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(namespacesList) { ns ->
                                        val name = ns.metadata?.name ?: "N/A"
                                        val status = ns.status?.phase ?: "-"
                                        Text("$name (Status: $status)", modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            }
                        }
                        activeClient != null -> { Text("Підключено до $selectedContext.\nВиберіть тип ресурсу.", modifier = Modifier.align(Alignment.Center)) }
                        isLoading && contexts.isEmpty() -> { Text("Завантаження конфігурації...", modifier = Modifier.align(Alignment.Center)) }
                        else -> { Text(errorMessage ?: "Виберіть контекст.", modifier = Modifier.align(Alignment.Center)) }
                    }
                } // Кінець правої панелі
            } // Кінець Row
            // --- Статус-бар ---
            Divider(); Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = connectionStatus, modifier = Modifier.weight(1f), style = MaterialTheme.typography.caption); if (isLoading) { CircularProgressIndicator(modifier = Modifier.size(16.dp)) }
        }
            // ---------------
        } // Кінець Column
    } // Кінець MaterialTheme
}

@Composable
fun ResourceTreeView(
    rootIds: List<String>, // <<< ОСЬ ЦЕЙ ПАРАМЕТР
    expandedNodes: MutableMap<String, Boolean>,
    onNodeClick: (id: String, isLeaf: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(start = 8.dp)) {
        // І він використовується тут:
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
// ---

// --- Головна функція ---
fun main() = application {
    // initializeFabric8HttpClient() // Не потрібна
    Window(onCloseRequest = ::exitApplication, title = "Kotlin Kube Manager v1.5 - Fabric8 AutoConf") { App() }
}

// --- Константа для версії Fabric8 (додайте на початок файлу або перед main) ---
const val FABRIC8_VERSION = "6.13.5" // Версія, яку ми використовуємо