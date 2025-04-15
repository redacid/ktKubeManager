// src/main/kotlin/Main.kt (Fabric8 БЕЗ KubeConfig Helper - ПОВНИЙ КОД)
import androidx.compose.desktop.ui.tooling.preview.Preview // Для @Preview
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
import io.fabric8.kubernetes.client.Config // Потрібен Config з client-api
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.api.model.Namespace // Модель Fabric8
// import io.fabric8.kubernetes.client.util.KubeConfig // НЕ ВИКОРИСТОВУЄМО
// ---------------------------
// Імпорти для Coroutines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // Використовуємо повне ім'я
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
// Імпорт для логера
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.TimeUnit // Для таймаутів у connectWithRetries

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
private val logger = LoggerFactory.getLogger("MainKtFabric8NoKubeconfigHelperFull")

// --- Константи для ретраїв ---
const val MAX_CONNECT_RETRIES = 3
const val RETRY_DELAY_MS = 1000L
// ---

// --- Функція завантаження Namespaces з Fabric8 ---
suspend fun loadNamespacesFabric8(client: KubernetesClient?): Result<List<Namespace>> {
    if (client == null) {
        logger.warn("loadNamespacesFabric8: Спроба завантажити без активного клієнта.")
        return Result.failure(IllegalStateException("Клієнт Kubernetes не ініціалізовано"))
    }
    logger.info("Завантаження списку Namespaces (Fabric8)...")
    return try {
        val namespaces = kotlinx.coroutines.withContext(Dispatchers.IO) { // Сподіваємось, компілюється
            logger.info("[IO] Виклик client.namespaces().list()...")
            client.namespaces().list().items ?: emptyList()
        }
        logger.info("Завантажено ${namespaces.size} Namespaces.")
        Result.success(namespaces.sortedBy { it.metadata?.name ?: "" })
    } catch (e: KubernetesClientException) {
        logger.error("KubernetesClientException під час завантаження Namespaces: ${e.message}", e)
        Result.failure(e)
    } catch (e: Exception) {
        logger.error("Загальна помилка завантаження Namespaces: ${e.message}", e)
        Result.failure(e)
    }
}
// ---

// --- Функція підключення з ретраями (використовує Config.autoConfigure(contextName)) ---
suspend fun connectWithRetries(contextName: String): Result<Pair<KubernetesClient, String>> {
    var lastError: Exception? = null
    for (attempt in 1..MAX_CONNECT_RETRIES) {
        logger.info("Спроба підключення до '$contextName' (спроба $attempt/$MAX_CONNECT_RETRIES)...")
        try {
            // Виконуємо IO операції в корутині
            val resultPair: Pair<KubernetesClient, String> = kotlinx.coroutines.withContext(Dispatchers.IO) { // Сподіваємось, компілюється
                logger.info("[IO] Створення конфігу та клієнта для '$contextName' через Config.autoConfigure...")
                // Створюємо конфіг відразу для потрібного контексту
                val config = Config.autoConfigure(contextName)
                    ?: throw KubernetesClientException("Не вдалося автоматично налаштувати конфігурацію для контексту '$contextName'")
                logger.info("[IO] Config context: ${config.currentContext?.name}. Namespace: ${config.namespace}")

                // Налаштовуємо таймаути
                config.requestTimeout = 30_000 // 30 секунд
                config.connectionTimeout = 15_000 // 15 секунд

                // Створюємо клієнт З ЦІЄЮ КОНКРЕТНОЮ КОНФІГУРАЦІЄЮ
                val client = KubernetesClientBuilder().withConfig(config).build()
                logger.info("[IO] Fabric8 client created. Checking version...")

                // Перевірка версії
                val ver = client.kubernetesVersion?.gitVersion ?: client.version?.gitVersion ?: "невідомо"
                logger.info("[IO] Версія сервера: $ver для '$contextName'")
                Pair(client, ver)
            }
            logger.info("Підключення до '$contextName' успішне (спроба $attempt).")
            return Result.success(resultPair) // Успіх - виходимо
        } catch (e: Exception) { // Ловимо всі типи помилок тут
            lastError = e
            logger.warn("Помилка підключення '$contextName' (спроба $attempt): ${e.message}")
            if (attempt < MAX_CONNECT_RETRIES) {
                logger.info("Повторна спроба через ${RETRY_DELAY_MS}ms...")
                kotlinx.coroutines.delay(RETRY_DELAY_MS) // Затримка перед наступною спробою
            }
        }
    }
    logger.error("Не вдалося підключитися до '$contextName' після $MAX_CONNECT_RETRIES спроб.")
    return Result.failure(lastError ?: IOException("Невідома помилка підключення після ретраїв"))
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
    var activeClient by remember { mutableStateOf<KubernetesClient?>(null) } // Тепер KubernetesClient
    var connectionStatus by remember { mutableStateOf("Завантаження конфігурації...") }
    var isLoading by remember { mutableStateOf(false) } // Загальний індикатор
    var resourceLoadError by remember { mutableStateOf<String?>(null) } // Помилка завантаження ресурсів
    var namespacesList by remember { mutableStateOf<List<Namespace>>(emptyList()) } // Використовуємо модель Fabric8

    // --- Стани для діалогу помилки ---
    val showErrorDialog = remember { mutableStateOf(false) }
    val dialogErrorMessage = remember { mutableStateOf("") }
    // -------------------------------------

    val coroutineScope = rememberCoroutineScope()

    // --- Завантаження контекстів через Config.autoConfigure(null).contexts ---
    LaunchedEffect(Unit) {
        logger.info("LaunchedEffect: Starting context load via Config.autoConfigure(null)...")
        isLoading = true; connectionStatus = "Завантаження Kubeconfig...";
        var loadError: Exception? = null; var loadedContextNames: List<String> = emptyList()
        try {
            loadedContextNames = kotlinx.coroutines.withContext(Dispatchers.IO) { // <--- Чи компілюється це?
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
            confirmButton = {
                Button(onClick = { showErrorDialog.value = false }) {
                    Text("OK")
                }
            }
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
                                            // --- ВИКЛИК ФУНКЦІЇ ПІДКЛЮЧЕННЯ З РЕТРАЯМИ ---
                                            coroutineScope.launch {
                                                isLoading = true; connectionStatus = "Підключення до '$contextName' (спроба 1/$MAX_CONNECT_RETRIES)..."; activeClient?.close(); activeClient = null; selectedResourceType = null; namespacesList = emptyList(); resourceLoadError = null; errorMessage = null;
                                                val connectionResult = connectWithRetries(contextName) // Викликаємо функцію
                                                isLoading = false // Завершили спроби

                                                connectionResult.onSuccess { (newClient, serverVersion) ->
                                                    activeClient = newClient; selectedContext = contextName; connectionStatus = "Підключено до: $contextName (v$serverVersion)"; errorMessage = null
                                                }.onFailure { error ->
                                                    connectionStatus = "Помилка підключення до '$contextName'"; errorMessage = error.localizedMessage ?: "Невідома помилка"; dialogErrorMessage.value = "Не вдалося підключитися до '$contextName' після $MAX_CONNECT_RETRIES спроб:\n${error.message}"; showErrorDialog.value = true; activeClient = null; selectedContext = null
                                                }
                                                logger.info("Спроба підключення до '$contextName' завершена (результат оброблено).")
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
                                        }
                                        else -> { logger.warn("Обробник '$nodeId' не реалізовано."); connectionStatus = "Вибрано '$nodeId' (не реалізовано)"; selectedResourceType = null }
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
// ---

// --- Головна функція ---
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Kotlin Kube Manager v1.8 - Fabric8 Final Attempt") { App() }
}

// --- Константа для версії Fabric8 (необов'язково, але корисно) ---
const val FABRIC8_VERSION = "6.13.5" // Версія, яку ми використовуємо