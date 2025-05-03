import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.Endpoints
import io.fabric8.kubernetes.api.model.Event
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.PersistentVolume
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apps.DaemonSet
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.kubernetes.api.model.batch.v1.CronJob
import io.fabric8.kubernetes.api.model.batch.v1.Job
import io.fabric8.kubernetes.api.model.networking.v1.Ingress
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy
import io.fabric8.kubernetes.api.model.rbac.ClusterRole
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding
import io.fabric8.kubernetes.api.model.rbac.Role
import io.fabric8.kubernetes.api.model.rbac.RoleBinding
import io.fabric8.kubernetes.api.model.storage.StorageClass
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException




@Composable
@Preview
fun App() {
    // --- Стани ---
    var contexts by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) } // Для помилок завантаження/підключення
    var selectedContext by remember { mutableStateOf<String?>(null) }
    var selectedResourceType by remember { mutableStateOf<String?>(null) }
    val expandedNodes = remember { mutableStateMapOf<String, Boolean>() }
    var activeClient by remember { mutableStateOf<KubernetesClient?>(null) } // Fabric8 Client
    var connectionStatus by remember { mutableStateOf("Завантаження конфігурації...") }
    var isLoading by remember { mutableStateOf(false) } // Загальний індикатор
    var resourceLoadError by remember { mutableStateOf<String?>(null) } // Помилка завантаження ресурсів
    // Стан для всіх типів ресурсів (Моделі Fabric8)
    var namespacesList by remember { mutableStateOf<List<Namespace>>(emptyList()) }
    var nodesList by remember { mutableStateOf<List<Node>>(emptyList()) }
    var eventsList by remember { mutableStateOf<List<Event>>(emptyList()) }
    var podsList by remember { mutableStateOf<List<Pod>>(emptyList()) }
    var deploymentsList by remember { mutableStateOf<List<Deployment>>(emptyList()) }
    var statefulSetsList by remember { mutableStateOf<List<StatefulSet>>(emptyList()) }
    var daemonSetsList by remember { mutableStateOf<List<DaemonSet>>(emptyList()) }
    var replicaSetsList by remember { mutableStateOf<List<ReplicaSet>>(emptyList()) }
    var jobsList by remember { mutableStateOf<List<Job>>(emptyList()) }
    var cronJobsList by remember { mutableStateOf<List<CronJob>>(emptyList()) }
    var servicesList by remember { mutableStateOf<List<Service>>(emptyList()) }
    var ingressesList by remember { mutableStateOf<List<Ingress>>(emptyList()) }
    var endpointsList by remember { mutableStateOf<List<Endpoints>>(emptyList()) }
    var networkPoliciesList by remember { mutableStateOf<List<NetworkPolicy>>(emptyList()) }
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
    var crdsList by remember { mutableStateOf<List<CustomResourceDefinition>>(emptyList()) }

    // Стани для деталей
    var detailedResource by remember { mutableStateOf<Any?>(null) }
    var detailedResourceType by remember { mutableStateOf<String?>(null) }
    // Стани для лог вікна
    val showLogViewer = remember { mutableStateOf(false) } // Прапорець видимості
    val logViewerParams =
        remember { mutableStateOf<Triple<String, String, String>?>(null) } // Параметри: ns, pod, container
    // Діалог помилки
    val showErrorDialog = remember { mutableStateOf(false) }
    val dialogErrorMessage = remember { mutableStateOf("") }
    var allNamespaces by remember { mutableStateOf(listOf(ALL_NAMESPACES_OPTION)) }
    var selectedNamespaceFilter by remember { mutableStateOf(ALL_NAMESPACES_OPTION) }
    var isNamespaceDropdownExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun handleResourceLoad(
        nodeId: String,
        //activeClient: KubernetesClient,
        namespaceToUse: String?,
        onSuccess: suspend (Boolean, String?) -> Unit
    ) {
        var loadOk = false
        var errorMsg: String? = null

        when (nodeId) {
            "Namespaces" -> loadNamespacesFabric8(activeClient)
                .onSuccess { namespacesList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "Nodes" -> loadNodesFabric8(activeClient)
                .onSuccess {nodesList = it; loadOk = true}
                .onFailure { errorMsg = it.message }
            "Events" -> loadEventsFabric8(activeClient)
                .onSuccess {eventsList = it; loadOk = true}
                .onFailure { errorMsg = it.message }

            "Pods" -> loadPodsFabric8(activeClient, namespaceToUse)
                .onSuccess {podsList = it; loadOk = true}
                .onFailure { errorMsg = it.message }

            "Deployments" -> loadDeploymentsFabric8(activeClient, namespaceToUse)
                .onSuccess { deploymentsList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "StatefulSets" -> loadStatefulSetsFabric8(activeClient, namespaceToUse)
                .onSuccess { statefulSetsList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "DaemonSets" -> loadDaemonSetsFabric8(activeClient, namespaceToUse)
                .onSuccess { daemonSetsList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "ReplicaSets" -> loadReplicaSetsFabric8(activeClient, namespaceToUse)
                .onSuccess { replicaSetsList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "Jobs" -> loadJobsFabric8(activeClient, namespaceToUse)
                .onSuccess { jobsList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "CronJobs" -> loadCronJobsFabric8(activeClient, namespaceToUse)
                .onSuccess { cronJobsList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "Services" -> loadServicesFabric8(activeClient, namespaceToUse)
                .onSuccess { servicesList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "Ingresses" -> loadIngressesFabric8(activeClient, namespaceToUse)
                .onSuccess { ingressesList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "Endpoints" -> loadEndpointsFabric8(activeClient, namespaceToUse)
                .onSuccess { endpointsList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "NetworkPolicies" -> loadNetworkPoliciesFabric8(activeClient, namespaceToUse)
                .onSuccess { networkPoliciesList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "PersistentVolumes" -> loadPVsFabric8(activeClient)
                .onSuccess {pvsList = it; loadOk = true}
                .onFailure { errorMsg = it.message }

            "PersistentVolumeClaims" -> loadPVCsFabric8(activeClient, namespaceToUse)
                .onSuccess { pvcsList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "StorageClasses" -> loadStorageClassesFabric8(activeClient)
                .onSuccess {storageClassesList = it; loadOk = true}
                .onFailure { errorMsg = it.message }

            "ConfigMaps" -> loadConfigMapsFabric8(activeClient, namespaceToUse)
                .onSuccess { configMapsList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "Secrets" -> loadSecretsFabric8(activeClient, namespaceToUse)
                .onSuccess { secretsList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "ServiceAccounts" -> loadServiceAccountsFabric8(activeClient, namespaceToUse)
                .onSuccess { serviceAccountsList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "Roles" -> loadRolesFabric8(activeClient, namespaceToUse)
                .onSuccess { rolesList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "RoleBindings" -> loadRoleBindingsFabric8(activeClient, namespaceToUse)
                .onSuccess { roleBindingsList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "ClusterRoles" -> loadClusterRolesFabric8(activeClient)
                .onSuccess {clusterRolesList = it; loadOk = true}
                .onFailure { errorMsg = it.message }

            "ClusterRoleBindings" -> loadClusterRoleBindingsFabric8(activeClient)
                .onSuccess {clusterRoleBindingsList = it; loadOk = true}
                .onFailure { errorMsg = it.message }

            "CRDs" -> loadCrdsFabric8(activeClient)
                .onSuccess {crdsList = it; loadOk = true}
                .onFailure { errorMsg = it.message }

            else -> {
                logger.warn("The handler '$nodeId' not realized.")
                loadOk = false
                errorMsg = "Not realized"
            }
        }

        onSuccess(loadOk, errorMsg)
    }

    fun clearResourceLists() {
        namespacesList = emptyList();
        nodesList = emptyList();
        podsList = emptyList();
        deploymentsList = emptyList();
        statefulSetsList = emptyList();
        daemonSetsList = emptyList();
        replicaSetsList = emptyList();
        jobsList = emptyList();
        cronJobsList = emptyList();
        servicesList = emptyList();
        ingressesList = emptyList();
        endpointsList = emptyList();
        pvsList = emptyList();
        pvcsList = emptyList();
        storageClassesList = emptyList();
        configMapsList = emptyList();
        secretsList = emptyList();
        serviceAccountsList = emptyList();
        rolesList = emptyList();
        roleBindingsList = emptyList();
        clusterRolesList = emptyList();
        clusterRoleBindingsList = emptyList();
        eventsList = emptyList();
        networkPoliciesList = emptyList();
        crdsList = emptyList();
    }
    // --- Завантаження контекстів через Config.autoConfigure(null).contexts ---
    LaunchedEffect(Unit) {
        logger.info("LaunchedEffect: Starting context load via Config.autoConfigure(null)...")
        isLoading = true; connectionStatus = "Завантаження Kubeconfig..."
        var loadError: Exception? = null
        var loadedContextNames: List<String>
        try {
            loadedContextNames = withContext(Dispatchers.IO) {
                logger.info("[IO] Calling Config.autoConfigure(null)...")
                val config = Config.autoConfigure(null) ?: throw IOException("Не вдалося завантажити Kubeconfig")
                val names = config.contexts?.mapNotNull { it.name }?.sorted() ?: emptyList()
                logger.info("[IO] Знайдено контекстів: ${names.size}")
                names
            }
            contexts = loadedContextNames; errorMessage =
                if (loadedContextNames.isEmpty()) "Контексти не знайдено" else null; connectionStatus =
                if (loadedContextNames.isEmpty()) "Контексти не знайдено" else "Виберіть контекст"
        } catch (e: Exception) {
            loadError = e; logger.error("Помилка завантаження контекстів: ${e.message}", e)
        } finally {
            if (loadError != null) {
                errorMessage = "Помилка завантаження: ${loadError.message}"; connectionStatus = "Помилка завантаження"
            }; isLoading = false
        }
    }
    // --- Кінець LaunchedEffect ---
    // --- Завантаження неймспейсів ПІСЛЯ успішного підключення ---
    LaunchedEffect(activeClient) {
        if (activeClient != null) {
            logger.info("Client connected, fetching all namespaces for filter...")
            isLoading = true // Можна використовувати інший індикатор або оновити статус
            connectionStatus = "Завантаження просторів імен..."
            val nsResult = loadNamespacesFabric8(activeClient) // Викликаємо завантаження
            nsResult.onSuccess { loadedNs ->
                // Додаємо опцію "All" і сортуємо
                allNamespaces =
                    (listOf(ALL_NAMESPACES_OPTION) + loadedNs.mapNotNull { it.metadata?.name }).sortedWith(compareBy { it != ALL_NAMESPACES_OPTION } // "<All>" завжди зверху
                    )
                connectionStatus = "Підключено до: $selectedContext" // Повертаємо статус
                logger.info("Loaded ${allNamespaces.size - 1} namespaces for filter.")
            }.onFailure {
                logger.error("Failed to load namespaces for filter: ${it.message}")
                connectionStatus = "Помилка завантаження неймспейсів"
                // Не скидаємо allNamespaces, щоб залишилася хоча б опція "All"
            }
            isLoading = false
        } else {
            // Якщо клієнт відключився, скидаємо список неймспейсів (крім All) і фільтр
            allNamespaces = listOf(ALL_NAMESPACES_OPTION)
            selectedNamespaceFilter = ALL_NAMESPACES_OPTION
        }
    }
    // --- Діалогове вікно помилки (M3) ---
    if (showErrorDialog.value) {
        ErrorDialog(
            showDialog = showErrorDialog.value,
            errorMessage = dialogErrorMessage.value,
            onDismiss = { showErrorDialog.value = false }
        )
    }
    // ---

    MaterialTheme { // M3 Theme

        Surface(
            modifier = Modifier.Companion.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) { // M3 Surface

            }
            Column(modifier = Modifier.Companion.fillMaxSize()) {
                Column {
                    Row(modifier = Modifier.Companion.fillMaxWidth()) {
                        MainMenu()  // Додаємо меню на початку основного вікна
                    }
                Row(modifier = Modifier.Companion.weight(1f)) {
                    // --- Ліва панель ---
                    Column(modifier = Modifier.Companion.fillMaxHeight().width(300.dp).padding(16.dp)) {
                        Text(
                            "Kubernetes Contexts:", style = MaterialTheme.typography.titleMedium
                        ); Spacer(modifier = Modifier.Companion.height(8.dp)) // M3 Text + Typography
                        Box(
                            modifier = Modifier.Companion.weight(1f)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) { // M3 колір
                            if (isLoading && contexts.isEmpty()) {
                                CircularProgressIndicator(modifier = Modifier.Companion.align(Alignment.Companion.Center))
                            } // M3 Indicator
                            else if (!isLoading && contexts.isEmpty()) {
                                Text(
                                    errorMessage ?: "Контексти не знайдено",
                                    modifier = Modifier.Companion.align(Alignment.Companion.Center)
                                )
                            } // M3 Text
                            else {
                                LazyColumn(modifier = Modifier.Companion.fillMaxSize()) {
                                    items(contexts) { contextName ->
                                        Row(
                                            verticalAlignment = Alignment.Companion.CenterVertically,
                                            modifier = Modifier.Companion.fillMaxWidth()
                                                .clickable(enabled = !isLoading) {
                                                    if (selectedContext != contextName) {
                                                        logger.info("Click on context: $contextName. Launching .connectWithRetries...")
                                                        coroutineScope.launch {
                                                            isLoading = true
                                                            connectionStatus = "Connection to '$contextName'..."
                                                            activeClient?.close()
                                                            activeClient = null
                                                            selectedResourceType = null
                                                            clearResourceLists()
                                                            resourceLoadError = null
                                                            errorMessage = null
                                                            detailedResource = null
                                                            detailedResourceType = null
                                                            showLogViewer.value = false
                                                            logViewerParams.value = null // Скидаємо все

                                                            val connectionResult = connectWithRetries(contextName)
                                                            isLoading = false

                                                            connectionResult.onSuccess { (newClient, serverVersion) ->
                                                                activeClient = newClient
                                                                selectedContext = contextName
                                                                connectionStatus =
                                                                    "Connected to: $contextName (v$serverVersion)"
                                                                errorMessage = null
                                                                logger.info("UI State updated on Success for $contextName")
                                                            }.onFailure { error ->
                                                                connectionStatus =
                                                                    "Connection Error to '$contextName'"
                                                                errorMessage =
                                                                    error.localizedMessage ?: "Unknown error"
                                                                logger.info("Setting up error dialog for: $contextName. Error: ${error.message}")
                                                                dialogErrorMessage.value =
                                                                    "Failed to connect to '$contextName' after $MAX_CONNECT_RETRIES attempts:\n${error.message}"
                                                                showErrorDialog.value = true
                                                                activeClient = null
                                                                selectedContext = null
                                                            }
                                                            logger.info("Attempting to connect to '$contextName' Completed (the result is processed).")
                                                        }
                                                    }
                                                }.padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            // Додаємо іконку
                                            Icon(
                                                imageVector = ICON_CONTEXT, // Ви можете змінити цю іконку на іншу
                                                contentDescription = "Kubernetes Context",
                                                tint = if (contextName == selectedContext) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.Companion.size(24.dp).padding(end = 8.dp)
                                            )

                                            // Текст після іконки
                                            Text(
                                                text = formatContextNameForDisplay(contextName),
                                                fontSize = 14.sp,
                                                color = if (contextName == selectedContext) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        } // Кінець Box списку
                        Spacer(modifier = Modifier.Companion.height(16.dp)); Text(
                        "Cluster Resources:", style = MaterialTheme.typography.titleMedium
                    ); Spacer(modifier = Modifier.Companion.height(8.dp)) // M3 Text
                        Box(
                            modifier = Modifier.Companion.weight(2f)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) { // M3 колір
                            ResourceTreeView(
                                rootIds = resourceTreeData[""] ?: emptyList(),
                                expandedNodes = expandedNodes,
                                onNodeClick = { nodeId, isLeaf ->
                                    logger.info("Clicking on a node: $nodeId, It's a leaflet: $isLeaf")
                                    if (isLeaf) {
                                        if (activeClient != null && !isLoading) {
                                            // Скидаємо показ деталей/логів при виборі нового типу ресурсу
                                            detailedResource = null;
                                            detailedResourceType = null;
                                            showLogViewer.value = false;
                                            logViewerParams.value = null
                                            selectedResourceType = nodeId;
                                            resourceLoadError = null;
                                            clearResourceLists()
                                            connectionStatus = "Loading $nodeId..."; isLoading = true
                                            coroutineScope.launch {
                                                val currentFilter =
                                                    selectedNamespaceFilter // We take the current filter value
                                                val namespaceToUse =
                                                    if (NSResources.contains(nodeId)) currentFilter else null
                                                handleResourceLoad(nodeId, /*activeClient,*/ namespaceToUse) { loadOk, errorMsg ->
                                                    if (loadOk) {
                                                        connectionStatus = "Loaded $nodeId ${if (namespaceToUse != null && namespaceToUse != ALL_NAMESPACES_OPTION) " (ns: $namespaceToUse)" else ""}"
                                                    } else {
                                                        resourceLoadError = "Error $nodeId: $errorMsg"
                                                        connectionStatus = "Error $nodeId"
                                                    }
                                                    isLoading = false
                                                }
                                            }
                                        } else if (activeClient == null) {
                                            logger.warn("No connection."); connectionStatus =
                                                "Connect to the cluster,pls!"; selectedResourceType = null
                                        }
                                    } else {
                                        expandedNodes[nodeId] = !(expandedNodes[nodeId] ?: false)
                                    }
                                })
                        }
                    } // Кінець лівої панелі
                    HorizontalDivider(
                        modifier = Modifier.Companion.fillMaxHeight().width(1.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    ) // M3 Divider
                    // --- Права панель (АБО Таблиця АБО Деталі АБО Логи) ---
                    Column(
                        modifier = Modifier.Companion.fillMaxHeight().weight(1f)
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    ) {
                        val resourceToShowDetails = detailedResource
                        val typeForDetails = detailedResourceType
                        val paramsForLogs = logViewerParams.value
                        val showLogs = showLogViewer.value // Читаємо значення стану

                        // Визначаємо поточний режим відображення
                        val currentView = remember(showLogs, resourceToShowDetails, paramsForLogs) {
                            when {
                                showLogs && paramsForLogs != null -> "logs"
                                resourceToShowDetails != null -> "details"
                                else -> "table"
                            }
                        }

                        val currentResourceType = selectedResourceType
                        // Заголовок для таблиці та логів (для деталей він усередині .ResourceDetailPanel)
                        val headerTitle = when {
                            currentView == "logs" -> "Logs: ${paramsForLogs?.second ?: "-"} [${paramsForLogs?.third ?: "-"}]"
                            currentView == "table" && currentResourceType != null && activeClient != null && resourceLoadError == null && errorMessage == null -> "$currentResourceType у $selectedContext"
                            else -> null
                        }

                        // --- ДОДАНО ФІЛЬТР НЕЙМСПЕЙСІВ (якщо є клієнт і це не деталі/логи) ---
                        if (currentView == "table" && activeClient != null) {
                            val isFilterEnabled =
                                NSResources.contains(selectedResourceType) // Активуємо тільки для неймспейсних ресурсів
                            NamespaceFilter(
                                selectedNamespaceFilter = selectedNamespaceFilter,
                                isNamespaceDropdownExpanded = isNamespaceDropdownExpanded,
                                onExpandedChange = { isNamespaceDropdownExpanded = it },
                                isFilterEnabled = isFilterEnabled,
                                allNamespaces = allNamespaces,
                                onNamespaceSelected = { nsName ->
                                    if (selectedNamespaceFilter != nsName) {
                                        selectedNamespaceFilter = nsName
                                        if (selectedResourceType != null) {
                                            resourceLoadError = null
                                            clearResourceLists()
                                            connectionStatus = "Loading $selectedResourceType (filter)..."
                                            isLoading = true
                                            coroutineScope.launch {
                                                val namespaceToUse =
                                                if (NSResources.contains(selectedResourceType)) selectedNamespaceFilter else null
                                                handleResourceLoad(selectedResourceType!!, /*activeClient,*/ namespaceToUse) { loadOk, errorMsg ->
                                                    if (loadOk) {
                                                        connectionStatus = "Loaded $selectedResourceType (filter applied)"
                                                    } else {
                                                        resourceLoadError = "Error loading $selectedResourceType: $errorMsg"
                                                    }
                                                    isLoading = false
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.Companion.height(4.dp))
                        }
                        // --- КІНЕЦЬ ФІЛЬТРА ---

                        if (headerTitle != null && currentView != "details") {
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.Companion.padding(bottom = 8.dp)
                            ) // M3 Text
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) // M3 Divider
                        } else if (currentView == "table" || currentView == "logs") { // Додаємо відступ, якщо це не панель деталей
                            Spacer(modifier = Modifier.Companion.height(48.dp)) // Висота імітує заголовок
                        }

                        // --- Основний уміст правої панелі ---
                        Box(
                            modifier = Modifier.Companion.weight(1f)
                                .padding(top = if (headerTitle != null && currentView != "details") 8.dp else 0.dp)
                        ) {
                            when (currentView) {
                                "logs" -> {
                                    if (paramsForLogs != null) {
                                        LogViewerPanel(
                                            namespace = paramsForLogs.first,
                                            podName = paramsForLogs.second,
                                            containerName = paramsForLogs.third,
                                            client = activeClient, // Передаємо активний клієнт
                                            onClose = {
                                                showLogViewer.value = false; logViewerParams.value = null
                                            } // Закриття панелі логів
                                        )
                                    } else {
                                        // Стан коли прапорець showLogViewer ще true, але параметри вже скинуті
                                        Text(
                                            "Loading logs...",
                                            modifier = Modifier.Companion.align(Alignment.Companion.Center)
                                        )
                                        LaunchedEffect(Unit) { showLogViewer.value = false } // Скидаємо прапорець
                                    }
                                }

                                "details" -> {
                                    ResourceDetailPanel(
                                        resource = resourceToShowDetails,
                                        resourceType = typeForDetails,
                                        onClose = { detailedResource = null; detailedResourceType = null },
                                        // Передаємо лямбду для запуску лог вікна
                                        onShowLogsRequest = { ns, pod, container ->
                                            logViewerParams.value = Triple(ns, pod, container)
                                            detailedResource = null // Закриваємо деталі
                                            detailedResourceType = null
                                            showLogViewer.value = true // Показуємо лог вьювер
                                        })
                                }

                                "table" -> {
                                    // --- Таблиця або Статус/Помилка ---
                                    val currentErrorMessageForPanel = resourceLoadError ?: errorMessage
                                    val currentClientForPanel = activeClient
                                    when {
                                        isLoading -> {
                                            Column(
                                                horizontalAlignment = Alignment.Companion.CenterHorizontally,
                                                modifier = Modifier.Companion.align(Alignment.Companion.Center)
                                            ) {
                                                CircularProgressIndicator(); Spacer(
                                                modifier = Modifier.Companion.height(
                                                    8.dp
                                                )
                                            ); Text(
                                                connectionStatus
                                            )
                                            }
                                        } // M3 Indicator, M3 Text
                                        currentErrorMessageForPanel != null -> {
                                            Text(
                                                text = currentErrorMessageForPanel,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.Companion.align(Alignment.Companion.Center)
                                            )
                                        } // Явний M3 Text
                                        currentClientForPanel != null && currentResourceType != null -> {
                                            // Отримуємо список та заголовки
                                            val itemsToShow: List<HasMetadata> = remember(
                                                currentResourceType,
                                                namespacesList,
                                                nodesList,
                                                eventsList,
                                                podsList,
                                                deploymentsList,
                                                statefulSetsList,
                                                daemonSetsList,
                                                replicaSetsList,
                                                jobsList,
                                                cronJobsList,
                                                servicesList,
                                                ingressesList,
                                                endpointsList,
                                                networkPoliciesList,
                                                pvsList,
                                                pvcsList,
                                                storageClassesList,
                                                configMapsList,
                                                secretsList,
                                                serviceAccountsList,
                                                rolesList,
                                                roleBindingsList,
                                                clusterRolesList,
                                                clusterRoleBindingsList,
                                                crdsList
                                            ) {
                                                when (currentResourceType) {
                                                    "Namespaces" -> namespacesList;
                                                    "Nodes" -> nodesList;
                                                    "Events" -> eventsList;
                                                    "Pods" -> podsList;
                                                    "Deployments" -> deploymentsList;
                                                    "StatefulSets" -> statefulSetsList;
                                                    "DaemonSets" -> daemonSetsList;
                                                    "ReplicaSets" -> replicaSetsList;
                                                    "Jobs" -> jobsList;
                                                    "CronJobs" -> cronJobsList;
                                                    "Services" -> servicesList;
                                                    "Ingresses" -> ingressesList;
                                                    "Endpoints" -> endpointsList;
                                                    "NetworkPolicies" -> networkPoliciesList;
                                                    "PersistentVolumes" -> pvsList;
                                                    "PersistentVolumeClaims" -> pvcsList;
                                                    "StorageClasses" -> storageClassesList;
                                                    "ConfigMaps" -> configMapsList;
                                                    "Secrets" -> secretsList;
                                                    "ServiceAccounts" -> serviceAccountsList;
                                                    "Roles" -> rolesList;
                                                    "RoleBindings" -> roleBindingsList;
                                                    "ClusterRoles" -> clusterRolesList;
                                                    "ClusterRoleBindings" -> clusterRoleBindingsList;
                                                    "CRDs" -> crdsList;
                                                    else -> emptyList()
                                                }
                                            }
                                            val headers =
                                                remember(currentResourceType) { getHeadersForType(currentResourceType) }

                                            if (itemsToShow.isEmpty() && !isLoading) {
                                                Box(
                                                    modifier = Modifier.Companion.fillMaxSize(),
                                                    contentAlignment = Alignment.Companion.Center
                                                ) { Text("No type resources '$currentResourceType'") }
                                            } else if (headers.isNotEmpty()) {
                                                // --- Ручна таблиця з LazyColumn (M3 компоненти) ---
                                                Column(modifier = Modifier.Companion.fillMaxSize()) {
                                                    // Calculate column widths based on headers and data
                                                    val columnWidths = calculateColumnWidths(
                                                        headers = headers,
                                                        items = itemsToShow,
                                                        resourceType = currentResourceType
                                                    )

                                                    // Use the calculated widths
                                                    Box(modifier = Modifier.Companion.fillMaxWidth()) {
                                                        Row(
                                                            modifier = Modifier.Companion.horizontalScroll(
                                                                rememberScrollState()
                                                            )
                                                        ) {
                                                            Column {
                                                                KubeTableHeaderRow(
                                                                    headers = headers, columnWidths = columnWidths
                                                                )
                                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                                                LazyColumn(
                                                                    modifier = Modifier.Companion.weight(
                                                                        1f,
                                                                        fill = false
                                                                    )
                                                                ) {
                                                                    items(itemsToShow) { item ->
                                                                        KubeTableRow(
                                                                            item = item,
                                                                            headers = headers,
                                                                            resourceType = currentResourceType,
                                                                            columnWidths = columnWidths,
                                                                            onRowClick = { clickedItem ->
                                                                                detailedResource = clickedItem
                                                                                detailedResourceType =
                                                                                    currentResourceType
                                                                                showLogViewer.value = false
                                                                                logViewerParams.value = null
                                                                            })
                                                                        HorizontalDivider(
                                                                            color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                                                alpha = 0.5f
                                                                            )
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier.Companion.fillMaxSize(),
                                                    contentAlignment = Alignment.Companion.Center
                                                ) { Text("No columns for '$currentResourceType'") }
                                            } // M3 Text
                                        }
                                        // --- Стани за замовчуванням (M3 Text) ---
                                        activeClient != null -> {
                                            Text(
                                                "Connected to $selectedContext.\nChoose a resource type.",
                                                modifier = Modifier.Companion.align(Alignment.Companion.Center)
                                            )
                                        }

                                        else -> {
                                            Text(
                                                errorMessage ?: "Choose a context.",
                                                modifier = Modifier.Companion.align(Alignment.Companion.Center)
                                            )
                                        }
                                    }
                                } // Кінець table case
                            } // Кінець when(currentView)
                        } // Кінець Box вмісту
                    } // Кінець Column правої панелі
                } // Кінець Row
                // --- Статус-бар ---
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StatusBar(
                    connectionStatus = connectionStatus,
                    isLoading = isLoading
                )


            } // Кінець Column
        } // Кінець Surface M3
    } // Кінець MaterialTheme M3
}

@OptIn(ExperimentalMaterial3Api::class) // Для ExposedDropdownMenuBox
@Composable
private fun NamespaceFilter(
    selectedNamespaceFilter: String,
    isNamespaceDropdownExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isFilterEnabled: Boolean,
    allNamespaces: List<String>,
    onNamespaceSelected: (String) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = isNamespaceDropdownExpanded,
        onExpandedChange = { if (isFilterEnabled) onExpandedChange(it) },
        modifier = Modifier.Companion.fillMaxWidth().padding(bottom = 4.dp)
    ) {
        TextField(
            value = selectedNamespaceFilter,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isNamespaceDropdownExpanded) },
            modifier = Modifier.Companion
                .menuAnchor(MenuAnchorType.Companion.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
            enabled = isFilterEnabled,
            colors = ExposedDropdownMenuDefaults.textFieldColors()
        )
        ExposedDropdownMenu(
            expanded = isNamespaceDropdownExpanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            allNamespaces.forEach { nsName ->
                DropdownMenuItem(
                    text = { Text(nsName) },
                    onClick = {
                        if (selectedNamespaceFilter != nsName) {
                            onNamespaceSelected(nsName)
                            onExpandedChange(false)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusBar(
    connectionStatus: String,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier.Companion.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Companion.CenterVertically
    ) {
        Text(
            text = connectionStatus,
            modifier = Modifier.Companion.weight(1f),
            style = MaterialTheme.typography.labelSmall
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.Companion.size(16.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

const val ALL_NAMESPACES_OPTION = "<All Namespaces>"