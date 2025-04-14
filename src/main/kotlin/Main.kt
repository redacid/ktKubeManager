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
// Імпорти для Fabric8
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.api.model.NamedContext // <-- Імпорт для NamedContext
// Імпорти для Coroutines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // Використовуємо повне ім'я kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
// Імпорт для логера
import org.slf4j.LoggerFactory


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
private val logger = LoggerFactory.getLogger("MainKtWithCurrentContext")

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
    var isConnecting by remember { mutableStateOf(false) }
    // ---------------------------------

    val coroutineScope = rememberCoroutineScope()

    // --- Завантаження контекстів ВБУДОВАНО в LaunchedEffect ---
    LaunchedEffect(Unit) {
        logger.info("LaunchedEffect: Starting context load...")
        isConnecting = true
        connectionStatus = "Завантаження Kubeconfig..."
        var loadError: Exception? = null
        var loadedContexts: List<String> = emptyList()

        try {
            loadedContexts = kotlinx.coroutines.withContext(Dispatchers.IO) { // Використовуємо повне ім'я
                logger.info("[IO] Завантаження Kubeconfig...")
                val config: Config = Config.autoConfigure(null)
                val ctxList = config.contexts ?: emptyList()
                val names = ctxList.mapNotNull { it.name }.sorted()
                logger.info("[IO] Знайдено контекстів: ${names.size}")
                names
            }
            contexts = loadedContexts
            errorMessage = if (loadedContexts.isEmpty()) "Контексти не знайдено" else null
            connectionStatus = if (loadedContexts.isEmpty()) "Контексти не знайдено" else "Виберіть контекст"
        } catch (e: Exception) { // Ловимо загальну помилку тут
            logger.error("[LaunchedEffect] Помилка завантаження Kubeconfig: ${e.message}", e)
            loadError = e
        } finally {
            if (loadError != null) {
                errorMessage = "Помилка завантаження конфігурації: ${loadError.message}"
                connectionStatus = "Помилка завантаження"
            }
            isConnecting = false
            logger.info("LaunchedEffect: Finished context load attempt.")
        }
    }
    // --- Кінець LaunchedEffect ---

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f)) {

                // --- Ліва панель ---
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp)
                        .padding(8.dp)
                ) {
                    Text("Контексти Kubernetes:", style = MaterialTheme.typography.h6)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Список контекстів
                    Box(modifier = Modifier.weight(1f).border(1.dp, Color.Gray)) {
                        if (isConnecting && contexts.isEmpty()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else if (!isConnecting && contexts.isEmpty()) {
                            Text(errorMessage ?: "Контексти не знайдено", modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(contexts) { contextName ->
                                    Text(
                                        text = contextName,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !isConnecting) {
                                                if (selectedContext != contextName) {
                                                    logger.info("Клікнуто на контекст: $contextName. Запуск підключення (через withCurrentContext)...")
                                                    // --- ЛОГІКА ПІДКЛЮЧЕННЯ З withCurrentContext ---
                                                    coroutineScope.launch {
                                                        isConnecting = true
                                                        connectionStatus = "Підключення до '$contextName'..."
                                                        activeClient?.close()
                                                        activeClient = null
                                                        selectedResourceType = null
                                                        var connectError: Exception? = null
                                                        var newClient: KubernetesClient? = null
                                                        var serverVersion: String? = null

                                                        try {
                                                            // Крок 1: Завантажуємо базову конфігурацію (IO)
                                                            val baseConfig = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                                                logger.info("[IO] Завантаження базової конфігурації...")
                                                                Config.autoConfigure(null)
                                                            }
                                                            // baseConfig тепер доступний

                                                            // Крок 2: Знаходимо об'єкт NamedContext
                                                            logger.info("Пошук NamedContext для '$contextName'...")
                                                            val targetNamedContext = baseConfig?.contexts?.find { it.name == contextName }

                                                            if (targetNamedContext == null) {
                                                                logger.error("NamedContext '$contextName' не знайдено!")
                                                                throw IllegalArgumentException("Контекст '$contextName' не знайдено у конфігурації")
                                                            }
                                                            logger.info("Знайдено NamedContext: ${targetNamedContext.name}")

                                                            // Крок 3: Створюємо специфічну конфігурацію через withCurrentContext
                                                            logger.info("Створення конфігурації через ConfigBuilder.withCurrentContext...")
                                                            val specificConfig: Config = ConfigBuilder(baseConfig)
                                                                .withCurrentContext(targetNamedContext) // <<<--- ВИКОРИСТОВУЄМО ЦЕЙ МЕТОД
                                                                .build()
                                                            logger.info("Специфічна конфігурація створена. Поточний контекст: ${specificConfig.currentContext?.name}")


                                                            // Крок 4: Створення клієнта та перевірка версії (IO)
                                                            val resultPair = kotlinx.coroutines.withContext(Dispatchers.IO) { // <--- Чи буде тут помилка?
                                                                logger.info("[IO] Створення клієнта та перевірка версії для $contextName")
                                                                val client = KubernetesClientBuilder().withConfig(specificConfig).build()
                                                                val versionInfo = client.kubernetesVersion
                                                                val ver = versionInfo?.gitVersion ?: "невідомо"
                                                                logger.info("[IO] Версія сервера: $ver для контексту $contextName")
                                                                Pair(client, ver)
                                                            }
                                                            newClient = resultPair.first
                                                            serverVersion = resultPair.second

                                                        } catch (e: Exception) {
                                                            logger.error("[Connect] Помилка під час підключення/перевірки для '$contextName': ${e.message}", e)
                                                            connectError = e
                                                        }

                                                        // Оновлюємо стан UI
                                                        isConnecting = false
                                                        if (connectError == null && newClient != null) {
                                                            activeClient = newClient
                                                            selectedContext = contextName
                                                            connectionStatus = "Підключено до: $contextName (v$serverVersion)"
                                                            errorMessage = null
                                                        } else {
                                                            connectionStatus = "Помилка підключення до '$contextName'"
                                                            errorMessage = connectError?.localizedMessage ?: "Невідома помилка"
                                                            activeClient = null
                                                            selectedContext = null
                                                        }
                                                        logger.info("Спроба підключення до '$contextName' завершена.")
                                                    } // --- Кінець coroutineScope.launch ---
                                                }
                                            }
                                            .padding(8.dp),
                                        color = if (contextName == selectedContext) MaterialTheme.colors.primary else LocalContentColor.current
                                    )
                                }
                            }
                        }
                    } // Кінець Box для списку контекстів

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Ресурси Кластера:", style = MaterialTheme.typography.h6)
                    Spacer(modifier = Modifier.height(8.dp))
                    // Дерево ресурсів
                    Box(modifier = Modifier.weight(2f).border(1.dp, Color.Gray)) {
                        ResourceTreeView(
                            rootIds = resourceTreeData[""] ?: emptyList(),
                            expandedNodes = expandedNodes,
                            onNodeClick = { nodeId, isLeaf ->
                                logger.info("Клікнуто на вузол: $nodeId, Це листок: $isLeaf")
                                if(isLeaf) {
                                    if (activeClient != null && !isConnecting) {
                                        if (selectedResourceType != nodeId) {
                                            selectedResourceType = nodeId
                                            // TODO: Запустити завантаження ресурсів типу nodeId
                                            logger.info("Запит на завантаження ресурсів типу '$nodeId'...")
                                            connectionStatus = "Завантаження '$nodeId'..."
                                        }
                                    } else if (activeClient == null) {
                                        logger.warn("Спроба вибрати ресурс '$nodeId' без активного підключення.")
                                        connectionStatus = "Спочатку підключіться до кластера!"
                                        selectedResourceType = null
                                    }
                                } else {
                                    expandedNodes[nodeId] = !(expandedNodes[nodeId] ?: false)
                                }
                            }
                        )
                    }
                } // Кінець лівої панелі

                Divider(modifier = Modifier.fillMaxHeight().width(1.dp), color = Color.LightGray)

                // --- Права панель ---
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val textToShow = when {
                        isConnecting -> "Обробка..."
                        selectedResourceType != null && activeClient != null -> "Відображення для: $selectedResourceType (завантаження...)"
                        activeClient != null -> "Підключено до $selectedContext. Виберіть тип ресурсу."
                        errorMessage != null -> errorMessage ?: "Помилка"
                        else -> "Виберіть контекст для підключення."
                    }
                    Text(textToShow, style = MaterialTheme.typography.h5)
                } // Кінець правої панелі
            } // Кінець Row для панелей

            // --- Статус-бар ---
            Divider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = connectionStatus,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.caption
                )
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                }
            }
            // ---------------

        } // Кінець Column для статус-бару
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
            !isLeaf -> Icons.Filled.Place
            else -> Icons.Filled.Info
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

// --- Головна функція та ініціалізація HTTP ---
fun main() = application {
    initializeFabric8HttpClient()
    Window(onCloseRequest = ::exitApplication, title = "Kotlin Kube Manager v0.6 - withCurrentContext") {
        App()
    }
}

fun initializeFabric8HttpClient() {
    try {
        Class.forName("io.fabric8.kubernetes.client.jdk.JDKHttpClientFactory")
        logger.info("Використовується JDK HttpClient для Fabric8.")
    } catch (e: ClassNotFoundException) {
        logger.info("JDKHttpClientFactory не знайдено. Fabric8 ймовірно використає OkHttp.")
    } catch (t: Throwable) {
        logger.warn("Помилка під час перевірки HTTP клієнта Fabric8: ${t.message}")
    }
}