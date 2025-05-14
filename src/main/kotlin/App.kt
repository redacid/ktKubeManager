import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import ua.`in`.ios.theme1.*

const val ALL_NAMESPACES_OPTION = "<All Namespaces>"
var recomposeScope: RecomposeScope? = null
data class ClusterContext(
    val name: String,
    val source: String, // "kubeconfig" або "saved"
    val config: ClusterConfig? = null
)

@Composable
private fun AppTextStyle(
    style: TextStyle = MaterialTheme.typography.labelMedium,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        LocalTextStyle provides style
    )
    {
        content()
    }
}

private fun createResourceListsMap(
    namespacesList: List<Namespace>,
    nodesList: List<Node>,
    eventsList: List<Event>,
    podsList: List<Pod>,
    deploymentsList: List<Deployment>,
    statefulSetsList: List<StatefulSet>,
    daemonSetsList: List<DaemonSet>,
    replicaSetsList: List<ReplicaSet>,
    jobsList: List<Job>,
    cronJobsList: List<CronJob>,
    servicesList: List<Service>,
    ingressesList: List<Ingress>,
    endpointsList: List<Endpoints>,
    networkPoliciesList: List<NetworkPolicy>,
    pvsList: List<PersistentVolume>,
    pvcsList: List<PersistentVolumeClaim>,
    storageClassesList: List<StorageClass>,
    configMapsList: List<ConfigMap>,
    secretsList: List<Secret>,
    serviceAccountsList: List<ServiceAccount>,
    rolesList: List<Role>,
    roleBindingsList: List<RoleBinding>,
    clusterRolesList: List<ClusterRole>,
    clusterRoleBindingsList: List<ClusterRoleBinding>,
    crdsList: List<CustomResourceDefinition>
): Map<String, List<HasMetadata>> {
    return mapOf(
        "Namespaces" to namespacesList,
        "Nodes" to nodesList,
        "Events" to eventsList,
        "Pods" to podsList,
        "Deployments" to deploymentsList,
        "StatefulSets" to statefulSetsList,
        "DaemonSets" to daemonSetsList,
        "ReplicaSets" to replicaSetsList,
        "Jobs" to jobsList,
        "CronJobs" to cronJobsList,
        "Services" to servicesList,
        "Ingresses" to ingressesList,
        "Endpoints" to endpointsList,
        "NetworkPolicies" to networkPoliciesList,
        "PersistentVolumes" to pvsList,
        "PersistentVolumeClaims" to pvcsList,
        "StorageClasses" to storageClassesList,
        "ConfigMaps" to configMapsList,
        "Secrets" to secretsList,
        "ServiceAccounts" to serviceAccountsList,
        "Roles" to rolesList,
        "RoleBindings" to roleBindingsList,
        "ClusterRoles" to clusterRolesList,
        "ClusterRoleBindings" to clusterRoleBindingsList,
        "CRDs" to crdsList
    )
}
var activeClient by mutableStateOf<KubernetesClient?>(null)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(windowState: WindowState, settingsManager: SettingsManager) {
    recomposeScope = currentRecomposeScope
    var contexts by remember { mutableStateOf<List<ClusterContext>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) } // Для помилок завантаження/підключення
    var selectedContext by remember { mutableStateOf<String?>(null) }
    var selectedResourceType by remember { mutableStateOf<String?>(null) }
    val expandedNodes = remember { mutableStateMapOf<String, Boolean>() }
    var connectionStatus by remember { mutableStateOf("Configuration Loading...") }
    var isLoading by remember { mutableStateOf(false) } // Загальний індикатор
    var resourceLoadError by remember { mutableStateOf<String?>(null) } // Помилка завантаження ресурсів
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
    var detailedResource by remember { mutableStateOf<Any?>(null) }
    var detailedResourceType by remember { mutableStateOf<String?>(null) }
    val showLogViewer = remember { mutableStateOf(false) }
    val logViewerParams = remember { mutableStateOf<Triple<String, String, String>?>(null) }
    val showErrorDialog = remember { mutableStateOf(false) }
    val dialogErrorMessage = remember { mutableStateOf("") }
    var allNamespaces by remember { mutableStateOf(listOf(ALL_NAMESPACES_OPTION)) }
    var selectedNamespaceFilter by remember { mutableStateOf(ALL_NAMESPACES_OPTION) }
    var isNamespaceDropdownExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val isDarkTheme = useTheme()

    suspend fun handleResourceLoad(
        nodeId: String,
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
                .onSuccess { nodesList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "Events" -> loadEventsFabric8(activeClient, namespaceToUse)
                .onSuccess { eventsList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "Pods" -> loadPodsFabric8(activeClient, namespaceToUse)
                .onSuccess { podsList = it; loadOk = true }
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
                .onSuccess { pvsList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "PersistentVolumeClaims" -> loadPVCsFabric8(activeClient, namespaceToUse)
                .onSuccess { pvcsList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "StorageClasses" -> loadStorageClassesFabric8(activeClient)
                .onSuccess { storageClassesList = it; loadOk = true }
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
                .onSuccess { clusterRolesList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "ClusterRoleBindings" -> loadClusterRoleBindingsFabric8(activeClient)
                .onSuccess { clusterRoleBindingsList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            "CRDs" -> loadCrdsFabric8(activeClient)
                .onSuccess { crdsList = it; loadOk = true }
                .onFailure { errorMsg = it.message }

            else -> {
                logger.warn("The handler '$nodeId' not present.")
                loadOk = false
                errorMsg = "Not present"
            }
        }

        onSuccess(loadOk, errorMsg)
    }

    fun clearResourceLists() {
        namespacesList = emptyList()
        nodesList = emptyList()
        podsList = emptyList()
        deploymentsList = emptyList()
        statefulSetsList = emptyList()
        daemonSetsList = emptyList()
        replicaSetsList = emptyList()
        jobsList = emptyList()
        cronJobsList = emptyList()
        servicesList = emptyList()
        ingressesList = emptyList()
        endpointsList = emptyList()
        pvsList = emptyList()
        pvcsList = emptyList()
        storageClassesList = emptyList()
        configMapsList = emptyList()
        secretsList = emptyList()
        serviceAccountsList = emptyList()
        rolesList = emptyList()
        roleBindingsList = emptyList()
        clusterRolesList = emptyList()
        clusterRoleBindingsList = emptyList()
        eventsList = emptyList()
        networkPoliciesList = emptyList()
        crdsList = emptyList()
    }

    LaunchedEffect(Unit) {
        logger.info("LaunchedEffect: Loading contexts...")
        isLoading = true
        connectionStatus = "Configuration Loading..."

        try {
            val kubeConfigContexts = withContext(Dispatchers.IO) {
                val config = Config.autoConfigure(null)
                    ?: throw IOException("Failed to load kubeconfig")

                config.contexts?.mapNotNull { context ->
                    context.name?.let { name ->
                        ClusterContext(name = name, source = "kubeconfig")
                    }
                } ?: emptyList()
            }

            val savedContexts = settingsManager.settings.clusters.map { cluster ->
                ClusterContext(
                    name = cluster.alias,
                    source = "saved",
                    config = cluster
                )
            }

            contexts = (kubeConfigContexts + savedContexts)
            errorMessage = if (contexts.isEmpty()) "Contexts not found" else null
            connectionStatus = if (contexts.isEmpty()) "Contexts not found" else "Choose a context"

        } catch (e: Exception) {
            logger.error("Error loading contexts: ${e.message}", e)
            errorMessage = "Loading error: ${e.message}"
            connectionStatus = "Loading error"
        }

        isLoading = false
    }
    LaunchedEffect(activeClient) {
        if (activeClient != null) {
            logger.info("Client connected, fetching all namespaces for filter...")
            isLoading = true // Можна використовувати інший індикатор або оновити статус
            connectionStatus = "Loading namespaces..."
            val nsResult = loadNamespacesFabric8(activeClient) // Викликаємо завантаження
            nsResult.onSuccess { loadedNs ->
                // Додаємо опцію "All" і сортуємо
                allNamespaces =
                    (listOf(ALL_NAMESPACES_OPTION) + loadedNs.mapNotNull { it.metadata?.name }).sortedWith(compareBy { it != ALL_NAMESPACES_OPTION })// "<All>" завжди зверху
                connectionStatus = "Connected to: $selectedContext" // Повертаємо статус
                logger.info("Loaded ${allNamespaces.size - 1} namespaces for filter.")
            }.onFailure {
                logger.error("Failed to load namespaces for filter: ${it.message}")
                connectionStatus = "Error loading namespaces"
                // Не скидаємо allNamespaces, щоб залишилася хоча б опція "All"
            }
            isLoading = false
        } else {
            // Якщо клієнт відключився, скидаємо список неймспейсів (крім All) і фільтр
            allNamespaces = listOf(ALL_NAMESPACES_OPTION)
            selectedNamespaceFilter = ALL_NAMESPACES_OPTION
        }
    }
    if (showErrorDialog.value) {
        ErrorDialog(
            showDialog = showErrorDialog.value,
            errorMessage = dialogErrorMessage.value,
            onDismiss = { showErrorDialog.value = false }
        )
    }
    //Спроба оновлювати дані на екрані, але оновлюється тільки formatAge
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000) // затримка 5 секунд
            recomposeScope?.invalidate()
        }
    }

    AppTheme(darkTheme = isDarkTheme.value)
    {
        AppTextStyle {

            Surface(
                modifier = Modifier.Companion.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
            }
            Column(modifier = Modifier.Companion.fillMaxSize()) {
                Column {
                    //Main Menu
                    Row(modifier = Modifier.Companion.fillMaxWidth()) {
                        MainMenu(
                            windowState = windowState, settingsManager = settingsManager
                        )
                    }
                    // End Main Menu
                    Row(modifier = Modifier.Companion.weight(1f)) {
                        // --- Left panel ---
                        Column(modifier = Modifier.Companion.fillMaxHeight().width(300.dp).padding(16.dp)) {
                            Text(
                                "Kubernetes Contexts:", style = MaterialTheme.typography.titleMedium
                            ); Spacer(modifier = Modifier.Companion.height(8.dp))
                            Box(
                                modifier = Modifier.Companion
                                    .weight(1f)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                if (isLoading && contexts.isEmpty()) {
                                    CircularProgressIndicator(modifier = Modifier.Companion.align(Alignment.Companion.Center))
                                } else if (!isLoading && contexts.isEmpty()) {
                                    Text(
                                        errorMessage ?: "Contexts not found",
                                        modifier = Modifier.Companion.align(Alignment.Companion.Center)
                                    )
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(contexts) { context ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                                    .clickable(enabled = !isLoading) {
                                                        if (selectedContext != context.name) {
                                                            logger.info("Click on context: ${context.name}. Launching .connectWithRetries...")
                                                            coroutineScope.launch {
                                                                isLoading = true
                                                                connectionStatus = "Connect to '${context.name}'..."
                                                                activeClient?.close()
                                                                activeClient = null
                                                                selectedResourceType = null
                                                                clearResourceLists()
                                                                resourceLoadError = null
                                                                errorMessage = null
                                                                detailedResource = null
                                                                detailedResourceType = null
                                                                showLogViewer.value = false
                                                                logViewerParams.value = null
                                                                val connectionResult = when {
                                                                    // Використовуємо різні методи підключення в залежності від джерела
                                                                    context.source == "saved" && context.config != null -> {
                                                                        logger.info("Connecting to saved cluster: ${context.name}")
                                                                        connectToSavedCluster(context.config)
                                                                    }

                                                                    else -> {
                                                                        logger.info("Connecting to kubeconfig context: ${context.name}")
                                                                        connectWithRetries(context.name)
                                                                    }
                                                                }
                                                                isLoading = false

                                                                connectionResult.onSuccess { (newClient, serverVersion) ->
                                                                    activeClient = newClient
                                                                    selectedContext = context.name
                                                                    connectionStatus =
                                                                        "Connected to: ${context.name} (v$serverVersion)"
                                                                    errorMessage = null
                                                                    logger.info("UI State updated on Success for ${context.name}")
                                                                }.onFailure { error ->
                                                                    connectionStatus =
                                                                        "Connection Error to '${context.name}'"
                                                                    errorMessage =
                                                                        error.localizedMessage ?: "Unknown error"
                                                                    logger.info("Setting up error dialog for: ${context.name}. Error: ${error.message}")
                                                                    dialogErrorMessage.value =
                                                                        "Failed to connect to '${context.name}' after $MAX_CONNECT_RETRIES attempts:\n${error.message}"
                                                                    showErrorDialog.value = true
                                                                    activeClient = null
                                                                    selectedContext = null
                                                                }
                                                                logger.info("Attempting to connect to '${context.name}' Completed (the result is processed).")
                                                            }
                                                        }
                                                    }.padding(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (context.source == "saved") ICON_CLOUD else ICON_CONTEXT,
                                                    contentDescription = if (context.source == "saved") "Saved EKS Cluster" else "Kubernetes Context",
                                                    tint = if (context.name == selectedContext) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(24.dp).padding(end = 8.dp)
                                                )
                                                Column(
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = formatContextNameForDisplay(context),
                                                        fontSize = 14.sp,
                                                        color = if (context.name == selectedContext)
                                                            MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (context.source == "saved") {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            TooltipBox(
                                                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                                                tooltip = {
                                                                    Surface(
                                                                        modifier = Modifier
                                                                            .padding(8.dp)
                                                                            .widthIn(max = 600.dp)
                                                                            .heightIn(max = 300.dp)
                                                                            .border(
                                                                                width = 1.dp,
                                                                                color = MaterialTheme.colorScheme.outline,
                                                                                shape = RoundedCornerShape(4.dp)
                                                                            ),
                                                                        shape = RoundedCornerShape(4.dp),
                                                                        color = MaterialTheme.colorScheme.surfaceContainerHighest

                                                                    ) {
                                                                        Column(modifier = Modifier.padding(10.dp)) {
                                                                            // Тип джерела
                                                                            Text(
                                                                                text = "Source: ${context.source.uppercase()}",
                                                                                style = MaterialTheme.typography.bodySmall,
                                                                                color = MaterialTheme.colorScheme.secondary
                                                                            )
                                                                            // AWS деталі
                                                                            context.config?.let { config ->
                                                                                HorizontalDivider(
                                                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                                                )
                                                                                // AWS Profile
                                                                                Row {
                                                                                    Text(
                                                                                        text = "Profile: ",
                                                                                        style = MaterialTheme.typography.bodySmall,
                                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                                    )
                                                                                    Text(
                                                                                        text = config.profileName,
                                                                                        style = MaterialTheme.typography.bodySmall,
                                                                                        fontWeight = FontWeight.Medium
                                                                                    )
                                                                                }

                                                                                // AWS Region
                                                                                Row {
                                                                                    Text(
                                                                                        text = "Region: ",
                                                                                        style = MaterialTheme.typography.bodySmall,
                                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                                    )
                                                                                    Text(
                                                                                        text = config.region,
                                                                                        style = MaterialTheme.typography.bodySmall,
                                                                                        fontWeight = FontWeight.Medium
                                                                                    )
                                                                                }

                                                                                // Cluster Name
                                                                                Row {
                                                                                    Text(
                                                                                        text = "Cluster: ",
                                                                                        style = MaterialTheme.typography.bodySmall,
                                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                                    )
                                                                                    Text(
                                                                                        text = config.clusterName,
                                                                                        style = MaterialTheme.typography.bodySmall,
                                                                                        fontWeight = FontWeight.Medium
                                                                                    )
                                                                                }

                                                                                // Endpoint
                                                                                if (config.endpoint.isNotBlank()) {
                                                                                    Row {
                                                                                        Text(
                                                                                            text = "Endpoint: ",
                                                                                            style = MaterialTheme.typography.bodySmall,
                                                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                                        )
                                                                                        Text(
                                                                                            text = config.endpoint,
                                                                                            style = MaterialTheme.typography.bodySmall,
                                                                                            fontWeight = FontWeight.Medium
                                                                                        )
                                                                                    }
                                                                                }

                                                                                // Role ARN (якщо є)
                                                                                config.roleArn?.let { role ->
                                                                                    Row {
                                                                                        Text(
                                                                                            text = "Role ARN: ",
                                                                                            style = MaterialTheme.typography.bodySmall,
                                                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                                        )
                                                                                        Text(
                                                                                            text = role,
                                                                                            style = MaterialTheme.typography.bodySmall,
                                                                                            fontWeight = FontWeight.Medium
                                                                                        )
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                },
                                                                state = rememberTooltipState(),

                                                                )
                                                            {
                                                                Text(
                                                                    text = context.config?.profileName ?: "default",
                                                                    fontSize = 12.sp,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = context.config?.region ?: "unknown region",
                                                                fontSize = 12.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } // End contexts list
                            Spacer(modifier = Modifier.Companion.height(16.dp)); Text(
                            "Cluster Resources:", style = MaterialTheme.typography.titleMedium
                        ); Spacer(modifier = Modifier.Companion.height(8.dp)) // M3 Text
                            Box(
                                modifier = Modifier.Companion.weight(2f)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                ResourceTreeView(
                                    rootIds = resourceTreeData[""] ?: emptyList(),
                                    expandedNodes = expandedNodes,
                                    onNodeClick = { nodeId, isLeaf ->
                                        logger.info("Click on a TreeNode: $nodeId, It's a leaflet: $isLeaf")
                                        if (isLeaf) {
                                            if (activeClient != null && !isLoading) {
                                                // Скидаємо показ деталей/логів при виборі нового типу ресурсу
                                                detailedResource = null
                                                detailedResourceType = null
                                                showLogViewer.value = false
                                                logViewerParams.value = null
                                                selectedResourceType = nodeId
                                                resourceLoadError = null
                                                clearResourceLists()
                                                connectionStatus = "Loading $nodeId..."
                                                isLoading = true
                                                coroutineScope.launch {
                                                    val currentFilter = selectedNamespaceFilter // We take the current filter value
                                                    val namespaceToUse = if (NSResources.contains(nodeId)) currentFilter else null
                                                    handleResourceLoad(nodeId, namespaceToUse)
                                                    {
                                                       loadOk, errorMsg ->
                                                        if (loadOk) {
                                                            connectionStatus =
                                                                "Loaded $nodeId ${if (namespaceToUse != null && namespaceToUse != ALL_NAMESPACES_OPTION) " (ns: $namespaceToUse)" else ""}"
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
                                    }
                                )
                            }
                        } // End of left panel
                        HorizontalDivider(
                            modifier = Modifier.Companion.fillMaxHeight().width(1.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        ) // Divider
                        // --- Right panel (Table OR Detail OR Logs) ---
                        Column(
                            modifier = Modifier.Companion.fillMaxHeight().weight(1f)
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        ) {
                            val resourceToShowDetails = detailedResource
                            val typeForDetails = detailedResourceType
                            val paramsForLogs = logViewerParams.value
                            val showLogs = showLogViewer.value
                            val currentView = remember(showLogs, resourceToShowDetails, paramsForLogs) {
                                when {
                                    showLogs && paramsForLogs != null -> "logs"
                                    resourceToShowDetails != null -> "details"
                                    else -> "table"
                                }
                            }
                            val currentResourceType = selectedResourceType
                            val headerTitle = when {
                                currentView == "logs" -> "Logs: ${paramsForLogs?.second ?: "-"} [${paramsForLogs?.third ?: "-"}]"
                                currentView == "table" &&
                                        currentResourceType != null &&
                                        activeClient != null &&
                                        resourceLoadError == null &&
                                        errorMessage == null -> "$currentResourceType in $selectedContext"

                                else -> null
                            }
                            // --- NS filter ---
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
                                                    handleResourceLoad(
                                                        selectedResourceType!!,
                                                        namespaceToUse
                                                    ) { loadOk, errorMsg ->
                                                        if (loadOk) {
                                                            connectionStatus =
                                                                "Loaded $selectedResourceType (filter applied)"
                                                        } else {
                                                            resourceLoadError =
                                                                "Error loading $selectedResourceType: $errorMsg"
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
                            // --- end of NS filter ---
                            if (headerTitle != null && currentView != "details") {
                                Text(
                                    text = headerTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.Companion.padding(bottom = 8.dp)
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            } else if (currentView == "table" || currentView == "logs") { // Додаємо відступ, якщо це не панель деталей
                                Spacer(modifier = Modifier.Companion.height(48.dp)) // Висота імітує заголовок
                            }
                            // --- Right panel ---
                            Box(
                                modifier = Modifier.Companion.weight(1f)
                                    .padding(top = if (headerTitle != null && currentView != "details") 8.dp else 0.dp)
                            ) {
                                when (currentView) {
                                    "logs" -> LogsView(
                                        paramsForLogs = paramsForLogs,
                                        activeClient = activeClient,
                                        onClose = { showLogViewer.value = false; logViewerParams.value = null }
                                    )
                                    "details" -> DetailsView(
                                        resource = resourceToShowDetails as HasMetadata?,
                                        resourceType = typeForDetails,
                                        onClose = { detailedResource = null; detailedResourceType = null },
                                        onShowLogsRequest = { ns, pod, container ->
                                            logViewerParams.value = Triple(ns, pod, container)
                                            detailedResource = null
                                            detailedResourceType = null
                                            showLogViewer.value = true
                                        }
                                    )
                                    "table" -> TableView(
                                        isLoading = isLoading,
                                        connectionStatus = connectionStatus,
                                        errorMessage = errorMessage,
                                        resourceLoadError = resourceLoadError,
                                        activeClient = activeClient,
                                        currentResourceType = currentResourceType,
                                        selectedContext = selectedContext,
                                        resourceLists = createResourceListsMap(
                                            namespacesList = namespacesList,
                                            nodesList = nodesList,
                                            eventsList = eventsList,
                                            podsList = podsList,
                                            deploymentsList = deploymentsList,
                                            statefulSetsList = statefulSetsList,
                                            daemonSetsList = daemonSetsList,
                                            replicaSetsList = replicaSetsList,
                                            jobsList = jobsList,
                                            cronJobsList = cronJobsList,
                                            servicesList = servicesList,
                                            ingressesList = ingressesList,
                                            endpointsList = endpointsList,
                                            networkPoliciesList = networkPoliciesList,
                                            pvsList = pvsList,
                                            pvcsList = pvcsList,
                                            storageClassesList = storageClassesList,
                                            configMapsList = configMapsList,
                                            secretsList = secretsList,
                                            serviceAccountsList = serviceAccountsList,
                                            rolesList = rolesList,
                                            roleBindingsList = roleBindingsList,
                                            clusterRolesList = clusterRolesList,
                                            clusterRoleBindingsList = clusterRoleBindingsList,
                                            crdsList = crdsList
                                        ),
                                        onResourceClick = { clickedItem, resourceType ->
                                            detailedResource = clickedItem
                                            detailedResourceType = resourceType
                                            showLogViewer.value = false
                                            logViewerParams.value = null
                                        }
                                    )
                                }
                            }
                        } // End right panel
                    }
                    // --- Status bar ---
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    StatusBar(
                        connectionStatus = connectionStatus,
                        isLoading = isLoading
                    )
                }
            }
        }
    }
}









