// src/main/kotlin/Main.kt (Базовий код з GitHub + Ручна таблиця M2 БЕЗ stickyHeader + Панель деталей)
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState // Для прокрутки деталей
import androidx.compose.foundation.verticalScroll // Для прокрутки деталей
//import androidx.compose.material.* // ВИКОРИСТОВУЄМО Material 2
import androidx.compose.material.LocalContentColor
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.Divider
import androidx.compose.material.AlertDialog
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Іконка "Назад"
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
// --- Імпорти для Fabric8 ---
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
// Моделі Kubernetes
import io.fabric8.kubernetes.api.model.* // Використовуємо *
import io.fabric8.kubernetes.api.model.apps.*
import io.fabric8.kubernetes.api.model.batch.v1.*
import io.fabric8.kubernetes.api.model.networking.v1.*
import io.fabric8.kubernetes.api.model.rbac.*
import io.fabric8.kubernetes.api.model.storage.*
// --- НЕМАЄ імпорту KubeConfig або KubeConfigUtils ---
// --- Імпорти для Coroutines ---
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // Повне ім'я
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
// --- Імпорти для форматування та інше ---
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
// Імпорти для форматерів
import io.fabric8.kubernetes.api.model.OwnerReference
import io.fabric8.kubernetes.api.model.NodeCondition
import io.fabric8.kubernetes.api.model.NodeAddress
import io.fabric8.kubernetes.api.model.ContainerStatus
import io.fabric8.kubernetes.api.model.ServicePort
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS
import io.fabric8.kubernetes.api.model.networking.v1.IngressLoadBalancerIngress
import io.fabric8.kubernetes.api.model.PersistentVolumeSpec
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpec
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus
import io.fabric8.kubernetes.api.model.Taint

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
private val logger = LoggerFactory.getLogger("MainKtDetailsPanel")

// --- Константи ---
const val MAX_CONNECT_RETRIES = 3
const val RETRY_DELAY_MS = 1000L
const val CONNECTION_TIMEOUT_MS = 5000
const val REQUEST_TIMEOUT_MS = 15000
const val FABRIC8_VERSION = "6.13.5"
// ---

// --- Допоміжні функції форматування ---
fun formatAge(creationTimestamp: String?): String {
    if (creationTimestamp.isNullOrBlank()) return "N/A"
    try {
        val creationTime = OffsetDateTime.parse(creationTimestamp)
        val now = OffsetDateTime.now(creationTime.offset)
        val duration = Duration.between(creationTime, now)
        return when {
            duration.toDays() > 0 -> "${duration.toDays()}d"
            duration.toHours() > 0 -> "${duration.toHours()}h"
            duration.toMinutes() > 0 -> "${duration.toMinutes()}m"
            else -> "${duration.seconds}s"
        }
    } catch (e: Exception) { logger.warn("Failed to format timestamp '$creationTimestamp': ${e.message}"); return "Invalid" }
}
fun formatPodContainers(statuses: List<ContainerStatus>?): String { val total = statuses?.size ?: 0; val ready = statuses?.count { it.ready == true } ?: 0; return "$ready/$total" }
fun formatPodRestarts(statuses: List<ContainerStatus>?): String { return statuses?.sumOf { it.restartCount ?: 0 }?.toString() ?: "0" }
fun formatNodeStatus(conditions: List<NodeCondition>?): String { val ready = conditions?.find { it.type == "Ready" }; return when (ready?.status) { "True" -> "Ready"; "False" -> "NotReady${ready.reason?.let { " ($it)" } ?: ""}"; else -> "Unknown" } }
fun formatNodeRoles(labels: Map<String, String>?): String { val r = labels?.filterKeys { it.startsWith("node-role.kubernetes.io/") }?.map { it.key.substringAfter('/') }?.sorted()?.joinToString(","); return if(r.isNullOrEmpty()) "<none>" else r }
fun formatTaints(taints: List<Taint>?): String { return taints?.size?.toString() ?: "0" }
fun formatPorts(ports: List<ServicePort>?): String { if (ports.isNullOrEmpty()) return "<none>"; return ports.joinToString(", ") { p -> "${p.port}${p.nodePort?.let { ":$it" } ?: ""}/${p.protocol ?: "TCP"}${p.name?.let { "($it)" } ?: ""}" } }
fun formatServiceExternalIP(service: Service?): String {
    if (service == null) return "<none>"
    val ips = mutableListOf<String>()
    when (service.spec?.type) {
        "LoadBalancer" -> { service.status?.loadBalancer?.ingress?.forEach { ingress -> ingress.ip?.let { ips.add(it) }; ingress.hostname?.let { ips.add(it) } } }
        "NodePort", "ClusterIP" -> { service.spec?.clusterIPs?.let { ips.addAll(it.filterNotNull()) } }
        "ExternalName" -> { return service.spec?.externalName ?: "<none>" }
    }
    return if (ips.isEmpty() || (ips.size == 1 && ips[0].isBlank())) "<none>" else ips.joinToString(",")
}
fun formatIngressHosts(rules: List<IngressRule>?): String { val hosts = rules?.mapNotNull { it.host }?.distinct() ?: emptyList(); return if (hosts.isEmpty()) "*" else hosts.joinToString(",") }
fun formatIngressAddress(ingresses: List<IngressLoadBalancerIngress>?): String { val addresses = mutableListOf<String>(); ingresses?.forEach { ingress -> ingress.ip?.let { addresses.add(it) }; ingress.hostname?.let { addresses.add(it) } }; return if (addresses.isEmpty()) "<none>" else addresses.joinToString(",") }
fun formatIngressPorts(tls: List<IngressTLS>?): String { return if (tls.isNullOrEmpty()) "80" else "80, 443" }
fun formatAccessModes(modes: List<String>?): String { return modes?.joinToString(",") ?: "<none>" }
fun formatJobDuration(status: JobStatus?): String {
    val start = status?.startTime?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
    val end = status?.completionTime?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
    return when { start == null -> "<pending>"; end == null -> Duration.between(start, OffsetDateTime.now(start.offset)).seconds.toString() + "s (running)"; else -> Duration.between(start, end).seconds.toString() + "s" }
}
// ---

// --- Функція отримання заголовків ---
fun getHeadersForType(resourceType: String): List<String> {
    return when (resourceType) {
        "Namespaces" -> listOf("Name", "Status", "Age")
        "Nodes" -> listOf("Name", "Status", "Roles", "Version", "Taints", "Age")
        "Pods" -> listOf("Namespace", "Name", "Ready", "Status", "Restarts", "Node", "Age")
        "Deployments" -> listOf("Namespace", "Name", "Ready", "Up-to-date", "Available", "Age")
        "StatefulSets" -> listOf("Namespace", "Name", "Ready", "Age")
        "DaemonSets" -> listOf("Namespace", "Name", "Desired", "Current", "Ready", "Up-to-date", "Available", "Age")
        "ReplicaSets" -> listOf("Namespace", "Name", "Desired", "Current", "Ready", "Age")
        "Jobs" -> listOf("Namespace", "Name", "Completions", "Duration", "Age")
        "CronJobs" -> listOf("Namespace", "Name", "Schedule", "Suspend", "Active", "Last Schedule", "Age")
        "Services" -> listOf("Namespace", "Name", "Type", "ClusterIP", "ExternalIP", "Ports", "Age")
        "Ingresses" -> listOf("Namespace", "Name", "Class", "Hosts", "Address", "Ports", "Age")
        "PersistentVolumes" -> listOf("Name", "Capacity", "Access Modes", "Reclaim Policy", "Status", "Claim", "StorageClass", "Age")
        "PersistentVolumeClaims" -> listOf("Namespace", "Name", "Status", "Volume", "Capacity", "Access Modes", "StorageClass", "Age")
        "StorageClasses" -> listOf("Name", "Provisioner", "Reclaim Policy", "Binding Mode", "Allow Expand", "Age")
        "ConfigMaps" -> listOf("Namespace", "Name", "Data", "Age")
        "Secrets" -> listOf("Namespace", "Name", "Type", "Data", "Age")
        "ServiceAccounts" -> listOf("Namespace", "Name", "Secrets", "Age")
        "Roles" -> listOf("Namespace", "Name", "Age")
        "RoleBindings" -> listOf("Namespace", "Name", "Role Kind", "Role Name", "Age")
        "ClusterRoles" -> listOf("Name", "Age")
        "ClusterRoleBindings" -> listOf("Name", "Role Kind", "Role Name", "Age")
        else -> listOf("Name")
    }
}
// ---

// --- Функція отримання даних комірки (для Fabric8 моделей) ---
fun getCellData(resource: Any, colIndex: Int, resourceType: String): String {
    val na = "N/A"
    try {
        return when (resourceType) {
            "Namespaces" -> if (resource is Namespace) { when (colIndex) { 0 -> resource.metadata?.name ?: na; 1 -> resource.status?.phase ?: na; 2 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "Nodes" -> if (resource is Node) { when (colIndex) { 0 -> resource.metadata?.name ?: na; 1 -> formatNodeStatus(resource.status?.conditions); 2 -> formatNodeRoles(resource.metadata?.labels); 3 -> resource.status?.nodeInfo?.kubeletVersion ?: na; 4 -> formatTaints(resource.spec?.taints); 5 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "Pods" -> if (resource is Pod) { when (colIndex) { 0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name ?: na; 2 -> formatPodContainers(resource.status?.containerStatuses); 3 -> resource.status?.phase ?: na; 4 -> formatPodRestarts(resource.status?.containerStatuses); 5 -> resource.spec?.nodeName ?: "<none>"; 6 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "Deployments" -> if (resource is Deployment) { when (colIndex) { 0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name ?: na; 2 -> "${resource.status?.readyReplicas ?: 0}/${resource.spec?.replicas ?: 0}"; 3 -> resource.status?.updatedReplicas?.toString() ?: "0"; 4 -> resource.status?.availableReplicas?.toString() ?: "0"; 5 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "StatefulSets" -> if (resource is StatefulSet) { when (colIndex) { 0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name ?: na; 2 -> "${resource.status?.readyReplicas ?: 0}/${resource.spec?.replicas ?: 0}"; 3 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "DaemonSets" -> if (resource is DaemonSet) { when (colIndex) { 0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name ?: na; 2 -> resource.status?.desiredNumberScheduled?.toString() ?: "0"; 3 -> resource.status?.currentNumberScheduled?.toString() ?: "0"; 4 -> resource.status?.numberReady?.toString() ?: "0"; 5 -> resource.status?.updatedNumberScheduled?.toString() ?: "0"; 6 -> resource.status?.numberAvailable?.toString() ?: "0"; 7 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "ReplicaSets" -> if (resource is ReplicaSet) { when (colIndex) { 0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name ?: na; 2 -> resource.spec?.replicas?.toString() ?: "0"; 3 -> resource.status?.replicas?.toString() ?: "0"; 4 -> resource.status?.readyReplicas?.toString() ?: "0"; 5 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "Jobs" -> if (resource is Job) { when (colIndex) { 0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name ?: na; 2 -> "${resource.status?.succeeded ?: 0}/${resource.spec?.completions ?: '?'}" ; 3 -> formatJobDuration(resource.status); 4 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "CronJobs" -> if (resource is CronJob) { when (colIndex) { 0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name ?: na; 2 -> resource.spec?.schedule ?: na; 3 -> resource.spec?.suspend?.toString() ?: "false"; 4 -> resource.status?.active?.size?.toString() ?: "0"; 5 -> resource.status?.lastScheduleTime?.let { formatAge(it) } ?: "<never>"; 6 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "Services" -> if (resource is Service) { when (colIndex) { 0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name ?: na; 2 -> resource.spec?.type ?: na; 3 -> resource.spec?.clusterIPs?.joinToString(",") ?: na; 4 -> formatServiceExternalIP(resource); 5 -> formatPorts(resource.spec?.ports); 6 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "Ingresses" -> if (resource is Ingress) { when (colIndex) { 0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name ?: na; 2 -> resource.spec?.ingressClassName ?: "<none>"; 3 -> formatIngressHosts(resource.spec?.rules); 4 -> formatIngressAddress(resource.status?.loadBalancer?.ingress); 5 -> formatIngressPorts(resource.spec?.tls); 6 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "PersistentVolumes" -> if (resource is PersistentVolume) { when (colIndex) { 0 -> resource.metadata?.name ?: na; 1 -> resource.spec?.capacity?.get("storage")?.toString() ?: na; 2 -> formatAccessModes(resource.spec?.accessModes); 3 -> resource.spec?.persistentVolumeReclaimPolicy ?: na; 4 -> resource.status?.phase ?: na; 5 -> resource.spec?.claimRef?.let { "${it.namespace ?: "-"}/${it.name ?: "-"}" } ?: "<none>"; 6 -> resource.spec?.storageClassName ?: "<none>"; 7 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "PersistentVolumeClaims" -> if (resource is PersistentVolumeClaim) { when (colIndex) { 0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name ?: na; 2 -> resource.status?.phase ?: na; 3 -> resource.spec?.volumeName ?: "<none>"; 4 -> resource.status?.capacity?.get("storage")?.toString() ?: na; 5 -> formatAccessModes(resource.spec?.accessModes); 6 -> resource.spec?.storageClassName ?: "<none>"; 7 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "StorageClasses" -> if (resource is StorageClass) { when (colIndex) { 0 -> resource.metadata?.name ?: na; 1 -> resource.provisioner ?: na; 2 -> resource.reclaimPolicy ?: na; 3 -> resource.volumeBindingMode ?: na; 4 -> resource.allowVolumeExpansion?.toString() ?: "false"; 5 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "ConfigMaps" -> if (resource is ConfigMap) { when (colIndex) { 0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name ?: na; 2 -> resource.data?.size?.toString() ?: "0"; 3 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "Secrets" -> if (resource is Secret) { when (colIndex) { 0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name ?: na; 2 -> resource.type ?: na; 3 -> (resource.data?.size ?: 0).plus(resource.stringData?.size ?: 0).toString(); 4 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "ServiceAccounts" -> if (resource is ServiceAccount) { when (colIndex) { 0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name ?: na; 2 -> resource.secrets?.size?.toString() ?: "0"; 3 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "Roles" -> if (resource is Role) { when (colIndex) { 0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name ?: na; 2 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "RoleBindings" -> if (resource is RoleBinding) { when (colIndex) { 0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name ?: na; 2 -> resource.roleRef?.kind ?: na; 3 -> resource.roleRef.name ?: na; 4 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "ClusterRoles" -> if (resource is ClusterRole) { when (colIndex) { 0 -> resource.metadata?.name ?: na; 1 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            "ClusterRoleBindings" -> if (resource is ClusterRoleBinding) { when (colIndex) { 0 -> resource.metadata?.name ?: na; 1 -> resource.roleRef?.kind ?: na; 2 -> resource.roleRef.name ?: na; 3 -> formatAge(resource.metadata?.creationTimestamp); else -> "" } } else ""
            else -> if (resource is HasMetadata) resource.metadata?.name ?: "?" else "?"
        }
    } catch (e: Exception) {
        val resourceName = if (resource is HasMetadata) resource.metadata?.name else "unknown"
        logger.error("Error formatting cell data [$resourceType, col $colIndex] for $resourceName: ${e.message}")
        return "<error>"
    }
}
// ---

// --- Функції завантаження ресурсів (fetchK8sResource та load...Fabric8) ---
suspend fun <T> fetchK8sResource(
    client: KubernetesClient?,
    resourceType: String,
    apiCall: suspend (KubernetesClient) -> List<T>?
): Result<List<T>> {
    if (client == null) return Result.failure(IllegalStateException("Клієнт Kubernetes не ініціалізовано"))
    logger.info("Завантаження списку $resourceType (Fabric8)...")
    return try {
        val items = kotlinx.coroutines.withContext(Dispatchers.IO) {
            logger.info("[IO] Виклик API для $resourceType...")
            apiCall(client) ?: emptyList()
        }
        logger.info("Завантажено ${items.size} $resourceType.")
        try {
            @Suppress("UNCHECKED_CAST")
            val sortedItems = items.sortedBy { (it as? HasMetadata)?.metadata?.name ?: "" }
            Result.success(sortedItems)
        } catch (e: Exception) {
            logger.warn("Не вдалося сортувати $resourceType: ${e.message}")
            Result.success(items)
        }
    } catch (e: KubernetesClientException) { logger.error("KubernetesClientException $resourceType: ${e.message}", e); Result.failure(e) }
    catch (e: Exception) { logger.error("Загальна помилка $resourceType: ${e.message}", e); Result.failure(e) }
}

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
            val resultPair: Pair<KubernetesClient, String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
                logger.info("[IO] Створення конфігу та клієнта для '$contextNameToLog' через Config.autoConfigure...")
                val config = Config.autoConfigure(targetContext) ?: throw KubernetesClientException("Не вдалося налаштувати конфігурацію для '$contextNameToLog'")
                config.connectionTimeout = CONNECTION_TIMEOUT_MS
                config.requestTimeout = REQUEST_TIMEOUT_MS
                logger.info("[IO] Config context: ${config.currentContext?.name ?: "(не вказано)"}. Namespace: ${config.namespace}")
                val client = KubernetesClientBuilder().withConfig(config).build()
                logger.info("[IO] Fabric8 client created. Checking version...")
                val ver = client.kubernetesVersion?.gitVersion ?: "невідомо"
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

// --- Composable для рядка заголовка таблиці (Адаптовано під M2) ---
@Composable
fun KubeTableHeaderRow(headers: List<String>) {
    Surface(color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled), modifier = Modifier.height(40.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            headers.forEachIndexed { index, header ->
                Box(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = header,
                        style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (index < headers.size - 1) {
                    Divider(modifier = Modifier.fillMaxHeight().width(1.dp), color = MaterialTheme.colors.background)
                }
            }
        }
    }
}
// ---

// --- Composable для рядка даних таблиці (Адаптовано під M2) ---
@Composable
fun <T: HasMetadata> KubeTableRow( // Додано дженерік T : HasMetadata
    item: T, // Приймаємо сам об'єкт
    headers: List<String>, // Приймаємо заголовки для визначення кількості колонок
    resourceType: String, // Приймаємо тип для getCellData
    onRowClick: (T) -> Unit // Обробник кліку тепер приймає об'єкт T
) {
    val cellValues = remember(item, resourceType) { // Генеруємо значення комірок тут
        headers.indices.map { colIndex ->
            getCellData(item, colIndex, resourceType)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min) // Дозволяє визначити висоту за вмістом
            .clickable(onClick = { onRowClick(item) }) // Передаємо item в обробник
            .padding(horizontal = 4.dp)
            .padding(vertical = 4.dp), // Додано вертикальний паддінг
        verticalAlignment = Alignment.CenterVertically
    ) {
        cellValues.forEachIndexed { index, value ->
            Box(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.body2, // Стиль Material 2
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Роздільник між комірками даних
            if (index < headers.size - 1) {
                Divider(modifier = Modifier.fillMaxHeight().width(1.dp), color = Color.LightGray.copy(alpha = 0.3f))
            }
        }
    }
}
// ---

// --- Composable для панелі деталей (з попередньої відповіді) ---
@Composable
fun DetailRow(label: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = "$label:", style = MaterialTheme.typography.subtitle2, fontWeight = FontWeight.Bold, modifier = Modifier.width(150.dp))
        Text(text = value ?: "<none>", style = MaterialTheme.typography.body2, modifier = Modifier.weight(1f))
    }
}

@Composable
fun PodDetailsView(pod: Pod) { // Використовує Fabric8 Pod model
    Column {
        DetailRow("Name", pod.metadata?.name)
        DetailRow("Namespace", pod.metadata?.namespace)
        DetailRow("Status", pod.status?.phase)
        DetailRow("Node", pod.spec?.nodeName)
        DetailRow("Pod IP", pod.status?.podIP)
        // DetailRow("Controlled By", formatOwnerRefs(pod.metadata?.ownerReferences)) // TODO: Need OwnerReference import
        DetailRow("Created", formatAge(pod.metadata?.creationTimestamp))
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Containers:", style = MaterialTheme.typography.subtitle1)
        pod.spec?.containers?.forEach { container ->
            Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                DetailRow("  Name", container.name)
                DetailRow("  Image", container.image)
                DetailRow("  Ready", pod.status?.containerStatuses?.find { it.name == container.name }?.ready?.toString() ?: "false")
                DetailRow("  Restarts", pod.status?.containerStatuses?.find { it.name == container.name }?.restartCount?.toString() ?: "0")
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

// TODO: Додати Composable функції для інших типів ресурсів

@Composable
fun ResourceDetailPanel(
    resource: Any?,
    resourceType: String?,
    onClose: () -> Unit
) {
    if (resource == null || resourceType == null) return

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back"); Spacer(Modifier.width(4.dp)); Text("Back to List")
            }
            Spacer(Modifier.weight(1f))
            val name = if (resource is HasMetadata) resource.metadata?.name else "Details"
            Text(text = "$resourceType: $name", style = MaterialTheme.typography.h6, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
        }
        Divider()
        Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                when(resourceType) {
                    "Pods" -> if (resource is Pod) PodDetailsView(pod = resource) else Text("Invalid Pod data")
                    // Додайте інші кейси тут
                    else -> {
                        Text("Detail view for '$resourceType' is not implemented yet.")
                        if (resource is HasMetadata) {
                            Spacer(Modifier.height(16.dp))
                            Text("Metadata:", style = MaterialTheme.typography.subtitle1)
                            DetailRow("Name", resource.metadata?.name)
                            DetailRow("Namespace", resource.metadata?.namespace)
                            DetailRow("Created", formatAge(resource.metadata?.creationTimestamp))
                            DetailRow("UID", resource.metadata?.uid)
                        }
                    }
                }
            }
        }
    }
}
// === КІНЕЦЬ COMPOSABLES ДЛЯ ПАНЕЛІ ДЕТАЛЕЙ ===


@Composable
@Preview
fun App() {
    // --- Стани (всі списки + деталі) ---
    var contexts by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedContext by remember { mutableStateOf<String?>(null) }
    var selectedResourceType by remember { mutableStateOf<String?>(null) }
    val expandedNodes = remember { mutableStateMapOf<String, Boolean>() }
    var activeClient by remember { mutableStateOf<KubernetesClient?>(null) }
    var connectionStatus by remember { mutableStateOf("Завантаження конфігурації...") }
    var isLoading by remember { mutableStateOf(false) }
    var resourceLoadError by remember { mutableStateOf<String?>(null) }
    // Списки ресурсів
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
    // Діалог помилки
    val showErrorDialog = remember { mutableStateOf(false) }
    val dialogErrorMessage = remember { mutableStateOf("") }
    // Стани для деталей
    var detailedResource by remember { mutableStateOf<Any?>(null) }
    var detailedResourceType by remember { mutableStateOf<String?>(null) }
    // ------------------------------

    val coroutineScope = rememberCoroutineScope()

    // --- Функція для очищення всіх списків ресурсів ---
    fun clearResourceLists() {
        namespacesList = emptyList(); nodesList = emptyList(); podsList = emptyList(); deploymentsList = emptyList(); statefulSetsList = emptyList(); daemonSetsList = emptyList(); replicaSetsList = emptyList(); jobsList = emptyList(); cronJobsList = emptyList(); servicesList = emptyList(); ingressesList = emptyList(); pvsList = emptyList(); pvcsList = emptyList(); storageClassesList = emptyList(); configMapsList = emptyList(); secretsList = emptyList(); serviceAccountsList = emptyList(); rolesList = emptyList(); roleBindingsList = emptyList(); clusterRolesList = emptyList(); clusterRoleBindingsList = emptyList();
    }
    // ---

    // --- Завантаження контекстів через Config.autoConfigure(null).contexts ---
    // (Використовує kotlinx.coroutines.withContext)
    LaunchedEffect(Unit) {
        logger.info("LaunchedEffect: Starting context load via Config.autoConfigure(null)...")
        isLoading = true; connectionStatus = "Завантаження Kubeconfig...";
        var loadError: Exception? = null
        var loadedContextNames: List<String> = emptyList()
        try {
            loadedContextNames = kotlinx.coroutines.withContext(Dispatchers.IO) {
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
    // ---

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

    MaterialTheme { // ВИКОРИСТОВУЄМО MATERIAL 2
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
                                                isLoading = true; connectionStatus = "Підключення до '$contextName'..."; activeClient?.close(); activeClient = null; selectedResourceType = null; clearResourceLists(); resourceLoadError = null; errorMessage = null; detailedResource = null; detailedResourceType = null // Скидаємо деталі
                                                val connectionResult = connectWithRetries(contextName)
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
                        ResourceTreeView(rootIds = resourceTreeData[""] ?: emptyList(), expandedNodes = expandedNodes, onNodeClick = { nodeId, isLeaf ->
                            logger.info("Клікнуто на вузол: $nodeId, Це листок: $isLeaf")
                            if (isLeaf) {
                                if (activeClient != null && !isLoading) {
                                    // Скидаємо показ деталей при виборі нового типу ресурсу
                                    detailedResource = null
                                    detailedResourceType = null
                                    // --- Запускаємо завантаження ---
                                    selectedResourceType = nodeId; resourceLoadError = null; clearResourceLists()
                                    connectionStatus = "Завантаження $nodeId..."; isLoading = true
                                    coroutineScope.launch {
                                        var loadOk = false; var errorMsg: String? = null
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
                                        if (loadOk) { connectionStatus = "Завантажено $nodeId" } else { resourceLoadError = "Помилка завантаження $nodeId: ${errorMsg ?: "Невід. помилка"}"; connectionStatus = "Помилка завантаження $nodeId" }
                                        isLoading = false
                                    }
                                } else if (activeClient == null) { logger.warn("Немає підключення."); connectionStatus = "Підключіться до кластера!"; selectedResourceType = null }
                            } else { expandedNodes[nodeId] = !(expandedNodes[nodeId] ?: false) }
                        })
                    }
                } // Кінець лівої панелі

                Divider(modifier = Modifier.fillMaxHeight().width(1.dp), color = Color.LightGray)

                // --- Права панель (АБО Таблиця АБО Деталі) ---
                Box(modifier = Modifier.fillMaxHeight().weight(1f).padding(start = 8.dp)) {
                    // --- Визначаємо, що показувати ---
                    val resourceToShowDetails = detailedResource
                    val typeForDetails = detailedResourceType
                    val showDetails = resourceToShowDetails != null && typeForDetails != null

                    if (showDetails) {
                        // --- Панель Деталей ---
                        ResourceDetailPanel(
                            resource = resourceToShowDetails,
                            resourceType = typeForDetails!!,
                            onClose = {
                                detailedResource = null
                                detailedResourceType = null
                            }
                        )
                    } else {
                        // --- Таблиця або Статус/Помилка ---
                        val currentResourceType = selectedResourceType
                        val currentErrorMessage = resourceLoadError ?: errorMessage
                        val currentClient = activeClient

                        // Заголовок правої панелі для таблиці
                        if (currentResourceType != null && currentClient != null && currentErrorMessage == null) {
                            // Обертаємо таблицю та її заголовок в Column
                            Column(modifier = Modifier.fillMaxSize()){
                                Text(text = "$currentResourceType у $selectedContext", style = MaterialTheme.typography.h6, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
                                Divider()
                                // Box для вмісту таблиці або повідомлення "Немає ресурсів"
                                Box(modifier = Modifier.weight(1f)) { // Важливо для LazyColumn
                                    when {
                                        isLoading -> {Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) { CircularProgressIndicator(); Spacer(modifier = Modifier.height(8.dp)); Text(connectionStatus) }}
                                        // TODO: треба виправити
                                        //currentErrorMessage != null -> { Text(currentErrorMessage, color = MaterialTheme.colors.error, modifier = Modifier.align(Alignment.Center)) }
                                        currentClient != null && currentResourceType != null -> {
                                            // Отримуємо список
                                            val itemsToShow: List<HasMetadata> = remember(currentResourceType, namespacesList, nodesList, podsList, deploymentsList, statefulSetsList, daemonSetsList, replicaSetsList, jobsList, cronJobsList, servicesList, ingressesList, pvsList, pvcsList, storageClassesList, configMapsList, secretsList, serviceAccountsList, rolesList, roleBindingsList, clusterRolesList, clusterRoleBindingsList ) {
                                                when (currentResourceType) {
                                                    "Namespaces" -> namespacesList
                                                    "Nodes" -> nodesList
                                                    "Pods" -> podsList
                                                    "Deployments" -> deploymentsList
                                                    "StatefulSets" -> statefulSetsList
                                                    "DaemonSets" -> daemonSetsList
                                                    "ReplicaSets" -> replicaSetsList
                                                    "Jobs" -> jobsList
                                                    "CronJobs" -> cronJobsList
                                                    "Services" -> servicesList
                                                    "Ingresses" -> ingressesList
                                                    "PersistentVolumes" -> pvsList
                                                    "PersistentVolumeClaims" -> pvcsList
                                                    "StorageClasses" -> storageClassesList
                                                    "ConfigMaps" -> configMapsList
                                                    "Secrets" -> secretsList
                                                    "ServiceAccounts" -> serviceAccountsList
                                                    "Roles" -> rolesList
                                                    "RoleBindings" -> roleBindingsList
                                                    "ClusterRoles" -> clusterRolesList
                                                    "ClusterRoleBindings" -> clusterRoleBindingsList
                                                    else -> emptyList()
                                                }
                                            }
                                            val headers = remember(currentResourceType) { getHeadersForType(currentResourceType) }

                                            if (itemsToShow.isEmpty() && !isLoading) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Немає ресурсів типу '$currentResourceType'") }
                                            } else if (headers.isNotEmpty()) {
                                                // --- Ручна таблиця з LazyColumn ---
                                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                    item { // НЕ stickyHeader
                                                        KubeTableHeaderRow(headers = headers)
                                                        Divider()
                                                    }
                                                    items(itemsToShow) { item ->
                                                        KubeTableRow(
                                                            item = item, // Передаємо сам об'єкт
                                                            headers = headers,
                                                            resourceType = currentResourceType,
                                                            onRowClick = { clickedItem -> // Лямбда для обробки кліку
                                                                logger.info("Запит деталей для: ${clickedItem.metadata?.namespace ?: ""}/${clickedItem.metadata?.name ?: ""}")
                                                                detailedResource = clickedItem // Зберігаємо об'єкт
                                                                detailedResourceType = currentResourceType // Зберігаємо тип
                                                            }
                                                        )
                                                        Divider(color = Color.Gray, thickness = 0.5.dp)
                                                    }
                                                }
                                                // --- Кінець ручної таблиці ---
                                            } else { /*...*/ }
                                        }
                                        // --- Стани за замовчуванням, якщо не таблиця ---
                                        activeClient != null -> { Text("Підключено до $selectedContext.\nВиберіть тип ресурсу.", modifier = Modifier.align(Alignment.Center)) }
                                        else -> { Text(errorMessage ?: "Виберіть контекст.", modifier = Modifier.align(Alignment.Center)) }
                                    }
                                } // Кінець Box для вмісту таблиці
                            } // Кінець Column, що містить таблицю
                        } else {
                            // Показуємо повідомлення, якщо не вибрано ресурс або не підключено
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                when {
                                    activeClient != null -> Text("Підключено до $selectedContext.\nВиберіть тип ресурсу.")
                                    isLoading && contexts.isEmpty() -> Text("Завантаження конфігурації...")
                                    else -> Text(errorMessage ?: "Виберіть контекст.")
                                }
                            }
                        }
                    } // Кінець else для showDetails
                } // Кінець правої панелі Box
            } // Кінець Row
            // --- Статус-бар ---
            Divider()
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = connectionStatus, modifier = Modifier.weight(1f), style = MaterialTheme.typography.caption); if (isLoading) { CircularProgressIndicator(modifier = Modifier.size(16.dp)) }
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
    Window(onCloseRequest = ::exitApplication, title = "Kotlin Kube Manager - Baseline + Manual Table M2 + Details") { App() }
}
// --- END OF FULL Main.kt ---