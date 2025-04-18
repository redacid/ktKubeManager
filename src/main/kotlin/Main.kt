import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
//import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider as Divider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
//import androidx.compose.material3.ExposedDropdownMenuBoxScope.menuAnchor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
//import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
//import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.SimpleIcons
import compose.icons.feathericons.ArrowDown
import compose.icons.feathericons.ArrowUp
import compose.icons.feathericons.Circle
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Eye
import compose.icons.feathericons.EyeOff
import compose.icons.simpleicons.Kubernetes
// Fabric8
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.apps.*
import io.fabric8.kubernetes.api.model.batch.v1.*
import io.fabric8.kubernetes.api.model.networking.v1.*
import io.fabric8.kubernetes.api.model.rbac.*
import io.fabric8.kubernetes.api.model.storage.*
// Coroutines
import kotlinx.coroutines.*
//import io.fabric8.kubernetes.client.dsl.LogWatch
// Логер та інше
import org.slf4j.LoggerFactory
import com.fasterxml.jackson.databind.ObjectMapper //extract helm release
import com.fasterxml.jackson.module.kotlin.registerKotlinModule //extract helm release
import compose.icons.feathericons.CheckCircle
import compose.icons.feathericons.HelpCircle
import compose.icons.feathericons.XCircle
import java.util.zip.GZIPInputStream  //extract helm release
//import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Duration
import java.time.OffsetDateTime

// TODO: check NS filter for all resources (e.g. Pods)

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

// Мапа для визначення, чи є ресурс неймспейсним (спрощено)
val namespacedResources: Set<String> =
    resourceLeafNodes - setOf("Nodes", "PersistentVolumes", "StorageClasses", "ClusterRoles", "ClusterRoleBindings")

// Логер
private val logger = LoggerFactory.getLogger("MainKtNamespaceFilter")

// --- Константи ---
const val MAX_CONNECT_RETRIES = 1

//const val RETRY_DELAY_MS = 1000L
const val CONNECTION_TIMEOUT_MS = 5000
const val REQUEST_TIMEOUT_MS = 15000

//const val FABRIC8_VERSION = "6.13.5"
const val LOG_LINES_TO_TAIL = 50

// ---
const val ALL_NAMESPACES_OPTION = "<All Namespaces>"

// Calculate optimal column widths based on content
@Composable
fun calculateColumnWidths(
    headers: List<String>,
    items: List<HasMetadata>,
    resourceType: String,
    minColumnWidth: Int = 60,
    maxColumnWidth: Int = 500,
    padding: Int = 16
): List<Int> {
    // Text measurer to calculate text dimensions
    val textMeasurer = rememberTextMeasurer()
    val headerStyle = MaterialTheme.typography.titleSmall
    val cellStyle = MaterialTheme.typography.bodyMedium

    return remember(headers, items, resourceType) {
        // Initialize with minimum widths
        val widths = MutableList(headers.size) { minColumnWidth }

        // Measure header widths
        headers.forEachIndexed { index, header ->
            val textWidth = measureTextWidth(textMeasurer, header, headerStyle)
            widths[index] = maxOf(
                widths[index],
                (textWidth + padding).coerceIn(minColumnWidth, maxColumnWidth)
            )
        }

        // Measure data widths (sample up to 100 items for performance)
        val sampleItems = if (items.size > 100) items.take(100) else items
        sampleItems.forEach { item ->
            headers.forEachIndexed { colIndex, _ ->
                val cellData = getCellData(item, colIndex, resourceType)
                val textWidth = measureTextWidth(textMeasurer, cellData, cellStyle)
                widths[colIndex] = maxOf(
                    widths[colIndex],
                    (textWidth + padding).coerceIn(minColumnWidth, maxColumnWidth)
                )
            }
        }

        widths
    }
}

// Helper function to measure text width
private fun measureTextWidth(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle
): Int {
    val textLayoutResult = textMeasurer.measure(
        text = text,
        style = style
    )
    return textLayoutResult.size.width
}

// --- Допоміжні функції форматування ---
fun formatContextNameForDisplay(contextName: String): String {
    // Регулярний вираз для AWS EKS ARN
    val eksArnPattern = "arn:aws:eks:[a-z0-9-]+:([0-9]+):cluster/([a-zA-Z0-9-]+)".toRegex()

    val matchResult = eksArnPattern.find(contextName)

    return if (matchResult != null) {
        // Групи: 1 - account ID, 2 - cluster name
        val accountId = matchResult.groupValues[1]
        val clusterName = matchResult.groupValues[2]
        "$accountId:$clusterName"
    } else {
        // Повертаємо оригінальне ім'я, якщо не відповідає формату AWS EKS ARN
        contextName
    }
}

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
    } catch (e: Exception) {
        logger.warn("Failed to format timestamp '$creationTimestamp': ${e.message}"); return "Invalid"
    }
}

fun formatPodContainers(statuses: List<ContainerStatus>?): String {
    val total = statuses?.size ?: 0;
    val ready = statuses?.count { it.ready == true } ?: 0; return "$ready/$total"
}

fun formatPodRestarts(statuses: List<ContainerStatus>?): String {
    return statuses?.sumOf { it.restartCount ?: 0 }?.toString() ?: "0"
}

fun formatNodeStatus(conditions: List<NodeCondition>?): String {
    val ready = conditions?.find { it.type == "Ready" }; return when (ready?.status) {
        "True" -> "Ready"; "False" -> "NotReady${ready.reason?.let { " ($it)" } ?: ""}"; else -> "Unknown"
    }
}

fun formatNodeRoles(labels: Map<String, String>?): String {
    val r =
        labels?.filterKeys { it.startsWith("node-role.kubernetes.io/") }?.map { it.key.substringAfter('/') }?.sorted()
            ?.joinToString(","); return if (r.isNullOrEmpty()) "<none>" else r
}

fun formatTaints(taints: List<Taint>?): String {
    return taints?.size?.toString() ?: "0"
}

fun formatPorts(ports: List<ServicePort>?): String {
    if (ports.isNullOrEmpty()) return "<none>"; return ports.joinToString(", ") { p -> "${p.port}${p.nodePort?.let { ":$it" } ?: ""}/${p.protocol ?: "TCP"}${p.name?.let { "($it)" } ?: ""}" }
}

fun formatServiceExternalIP(service: Service?): String {
    if (service == null) return "<none>"
    val ips = mutableListOf<String>()
    when (service.spec?.type) {
        "LoadBalancer" -> {
            service.status?.loadBalancer?.ingress?.forEach { ingress ->
                ingress.ip?.let { ips.add(it) }; ingress.hostname?.let {
                ips.add(
                    it
                )
            }
            }
        }

        "NodePort", "ClusterIP" -> {
            service.spec?.clusterIPs?.let { ips.addAll(it.filterNotNull()) }
        }

        "ExternalName" -> {
            return service.spec?.externalName ?: "<none>"
        }
    }
    return if (ips.isEmpty() || (ips.size == 1 && ips[0].isBlank())) "<none>" else ips.joinToString(",")
}

fun formatIngressHosts(rules: List<IngressRule>?): String {
    val hosts = rules?.mapNotNull { it.host }?.distinct()
        ?: emptyList(); return if (hosts.isEmpty()) "*" else hosts.joinToString(",")
}

fun formatIngressAddress(ingresses: List<IngressLoadBalancerIngress>?): String {
    val addresses = mutableListOf<String>(); ingresses?.forEach { ingress ->
        ingress.ip?.let { addresses.add(it) }; ingress.hostname?.let {
        addresses.add(
            it
        )
    }
    }; return if (addresses.isEmpty()) "<none>" else addresses.joinToString(",")
}

fun formatIngressPorts(tls: List<IngressTLS>?): String {
    return if (tls.isNullOrEmpty()) "80" else "80, 443"
}

fun formatAccessModes(modes: List<String>?): String {
    return modes?.joinToString(",") ?: "<none>"
}

fun formatJobDuration(status: JobStatus?): String {
    val start = status?.startTime?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
    val end = status?.completionTime?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
    return when {
        start == null -> "<pending>"; end == null -> Duration.between(
            start,
            OffsetDateTime.now(start.offset)
        ).seconds.toString() + "s (running)"; else -> Duration.between(start, end).seconds.toString() + "s"
    }
}

//fun formatDataKeys(data: Map<String, String>?, stringData: Map<String, String>?): String {
//    return (data?.size ?: 0).plus(stringData?.size ?: 0).toString()
//}
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
        "PersistentVolumes" -> listOf(
            "Name",
            "Capacity",
            "Access Modes",
            "Reclaim Policy",
            "Status",
            "Claim",
            "StorageClass",
            "Age"
        )

        "PersistentVolumeClaims" -> listOf(
            "Namespace",
            "Name",
            "Status",
            "Volume",
            "Capacity",
            "Access Modes",
            "StorageClass",
            "Age"
        )

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

fun getCellData(resource: Any, colIndex: Int, resourceType: String): String {
    val na = "N/A"
    try {
        return when (resourceType) {
            "Namespaces" -> if (resource is Namespace) {
                when (colIndex) {
                    0 -> resource.metadata?.name ?: na; 1 -> resource.status?.phase
                    ?: na; 2 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "Nodes" -> if (resource is Node) {
                when (colIndex) {
                    0 -> resource.metadata?.name
                        ?: na; 1 -> formatNodeStatus(resource.status?.conditions); 2 -> formatNodeRoles(resource.metadata?.labels); 3 -> resource.status?.nodeInfo?.kubeletVersion
                    ?: na; 4 -> formatTaints(resource.spec?.taints); 5 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "Pods" -> if (resource is Pod) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name
                    ?: na; 2 -> formatPodContainers(resource.status?.containerStatuses); 3 -> resource.status?.phase
                    ?: na; 4 -> formatPodRestarts(resource.status?.containerStatuses); 5 -> resource.spec?.nodeName
                    ?: "<none>"; 6 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "Deployments" -> if (resource is Deployment) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name
                    ?: na; 2 -> "${resource.status?.readyReplicas ?: 0}/${resource.spec?.replicas ?: 0}"; 3 -> resource.status?.updatedReplicas?.toString()
                    ?: "0"; 4 -> resource.status?.availableReplicas?.toString()
                    ?: "0"; 5 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "StatefulSets" -> if (resource is StatefulSet) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name
                    ?: na; 2 -> "${resource.status?.readyReplicas ?: 0}/${resource.spec?.replicas ?: 0}"; 3 -> formatAge(
                    resource.metadata?.creationTimestamp
                ); else -> ""
                }
            } else ""

            "DaemonSets" -> if (resource is DaemonSet) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name
                    ?: na; 2 -> resource.status?.desiredNumberScheduled?.toString()
                    ?: "0"; 3 -> resource.status?.currentNumberScheduled?.toString()
                    ?: "0"; 4 -> resource.status?.numberReady?.toString()
                    ?: "0"; 5 -> resource.status?.updatedNumberScheduled?.toString()
                    ?: "0"; 6 -> resource.status?.numberAvailable?.toString()
                    ?: "0"; 7 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "ReplicaSets" -> if (resource is ReplicaSet) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name
                    ?: na; 2 -> resource.spec?.replicas?.toString() ?: "0"; 3 -> resource.status?.replicas?.toString()
                    ?: "0"; 4 -> resource.status?.readyReplicas?.toString()
                    ?: "0"; 5 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "Jobs" -> if (resource is io.fabric8.kubernetes.api.model.batch.v1.Job) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name
                    ?: na; 2 -> "${resource.status?.succeeded ?: 0}/${resource.spec?.completions ?: '?'}"; 3 -> formatJobDuration(
                    resource.status
                ); 4 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "CronJobs" -> if (resource is CronJob) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name
                    ?: na; 2 -> resource.spec?.schedule ?: na; 3 -> resource.spec?.suspend?.toString()
                    ?: "false"; 4 -> resource.status?.active?.size?.toString()
                    ?: "0"; 5 -> resource.status?.lastScheduleTime?.let { formatAge(it) } ?: "<never>"; 6 -> formatAge(
                    resource.metadata?.creationTimestamp
                ); else -> ""
                }
            } else ""

            "Services" -> if (resource is Service) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name
                    ?: na; 2 -> resource.spec?.type ?: na; 3 -> resource.spec?.clusterIPs?.joinToString(",")
                    ?: na; 4 -> formatServiceExternalIP(resource); 5 -> formatPorts(resource.spec?.ports); 6 -> formatAge(
                    resource.metadata?.creationTimestamp
                ); else -> ""
                }
            } else ""

            "Ingresses" -> if (resource is Ingress) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name
                    ?: na; 2 -> resource.spec?.ingressClassName
                    ?: "<none>"; 3 -> formatIngressHosts(resource.spec?.rules); 4 -> formatIngressAddress(resource.status?.loadBalancer?.ingress); 5 -> formatIngressPorts(
                    resource.spec?.tls
                ); 6 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "PersistentVolumes" -> if (resource is PersistentVolume) {
                when (colIndex) {
                    0 -> resource.metadata?.name ?: na; 1 -> resource.spec?.capacity?.get("storage")?.toString()
                    ?: na; 2 -> formatAccessModes(resource.spec?.accessModes); 3 -> resource.spec?.persistentVolumeReclaimPolicy
                    ?: na; 4 -> resource.status?.phase
                    ?: na; 5 -> resource.spec?.claimRef?.let { "${it.namespace ?: "-"}/${it.name ?: "-"}" }
                    ?: "<none>"; 6 -> resource.spec?.storageClassName
                    ?: "<none>"; 7 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "PersistentVolumeClaims" -> if (resource is PersistentVolumeClaim) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name
                    ?: na; 2 -> resource.status?.phase ?: na; 3 -> resource.spec?.volumeName
                    ?: "<none>"; 4 -> resource.status?.capacity?.get("storage")?.toString()
                    ?: na; 5 -> formatAccessModes(resource.spec?.accessModes); 6 -> resource.spec?.storageClassName
                    ?: "<none>"; 7 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "StorageClasses" -> if (resource is StorageClass) {
                when (colIndex) {
                    0 -> resource.metadata?.name ?: na; 1 -> resource.provisioner ?: na; 2 -> resource.reclaimPolicy
                    ?: na; 3 -> resource.volumeBindingMode ?: na; 4 -> resource.allowVolumeExpansion?.toString()
                    ?: "false"; 5 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "ConfigMaps" -> if (resource is ConfigMap) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name
                    ?: na; 2 -> resource.data?.size?.toString()
                    ?: "0"; 3 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "Secrets" -> if (resource is Secret) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name ?: na; 2 -> resource.type
                    ?: na; 3 -> (resource.data?.size ?: 0).plus(resource.stringData?.size ?: 0)
                    .toString(); 4 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "ServiceAccounts" -> if (resource is ServiceAccount) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name
                    ?: na; 2 -> resource.secrets?.size?.toString()
                    ?: "0"; 3 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "Roles" -> if (resource is Role) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name
                    ?: na; 2 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "RoleBindings" -> if (resource is RoleBinding) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na; 1 -> resource.metadata?.name
                    ?: na; 2 -> resource.roleRef?.kind ?: na; 3 -> resource.roleRef.name
                    ?: na; 4 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "ClusterRoles" -> if (resource is ClusterRole) {
                when (colIndex) {
                    0 -> resource.metadata?.name ?: na; 1 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "ClusterRoleBindings" -> if (resource is ClusterRoleBinding) {
                when (colIndex) {
                    0 -> resource.metadata?.name ?: na; 1 -> resource.roleRef?.kind ?: na; 2 -> resource.roleRef.name
                    ?: na; 3 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            else -> if (resource is HasMetadata) resource.metadata?.name ?: "?" else "?"
        }
    } catch (e: Exception) {
        val resourceName = if (resource is HasMetadata) resource.metadata?.name else "unknown"
        logger.error("Error formatting cell data [$resourceType, col $colIndex] for $resourceName: ${e.message}")
        return "<error>"
    }
}

suspend fun <T> fetchK8sResource(
    client: KubernetesClient?,
    resourceType: String,
    namespace: String?, // Додано параметр неймспейсу
    apiCall: (KubernetesClient, String?) -> List<T>? // Лямбда тепер приймає клієнт і неймспейс
): Result<List<T>> {
    if (client == null) return Result.failure(IllegalStateException("Клієнт Kubernetes не ініціалізовано"))
    val targetNamespace = if (namespace == ALL_NAMESPACES_OPTION) null else namespace
    val nsLog = targetNamespace ?: "all"
    logger.info("Завантаження списку $resourceType (Namespace: $nsLog)...")
    return try {
        val items = kotlinx.coroutines.withContext(Dispatchers.IO) {
            logger.info("[IO] Виклик API для $resourceType (Namespace: $nsLog)...")
            apiCall(client, targetNamespace) ?: emptyList() // Передаємо неймспейс у лямбду
        }
        logger.info("Завантажено ${items.size} $resourceType (Namespace: $nsLog).")
        try {
            @Suppress("UNCHECKED_CAST")
            val sortedItems = items.sortedBy { (it as? HasMetadata)?.metadata?.name ?: "" }
            Result.success(sortedItems)
        } catch (e: Exception) {
            logger.warn("Не вдалося сортувати $resourceType: ${e.message}")
            Result.success(items)
        }
    } catch (e: KubernetesClientException) {
        logger.error("KubeExc $resourceType (NS: $nsLog): ${e.message}", e); Result.failure(e)
    } catch (e: Exception) {
        logger.error("Помилка $resourceType (NS: $nsLog): ${e.message}", e); Result.failure(e)
    }
}

suspend fun loadNamespacesFabric8(client: KubernetesClient?) =
    fetchK8sResource(client, "Namespaces", null) { cl, _ -> cl.namespaces().list().items } // Namespaces не фільтруються

suspend fun loadNodesFabric8(client: KubernetesClient?) =
    fetchK8sResource(client, "Nodes", null) { cl, _ -> cl.nodes().list().items } // Nodes не фільтруються

suspend fun loadPodsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Pods", namespace) { cl, ns ->
        if (ns == null) cl.pods().inAnyNamespace().list().items else cl.pods().inNamespace(ns).list().items
    }

suspend fun loadDeploymentsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Deployments", namespace) { cl, ns ->
        if (ns == null) cl.apps().deployments().inAnyNamespace().list().items else cl.apps().deployments()
            .inNamespace(ns).list().items
    }

// ... і так далі для всіх інших типів ресурсів ...
suspend fun loadStatefulSetsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "StatefulSets", namespace) { cl, ns ->
        if (ns == null) cl.apps().statefulSets().inAnyNamespace().list().items else cl.apps().statefulSets()
            .inNamespace(ns).list().items
    }

suspend fun loadDaemonSetsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "DaemonSets", namespace) { cl, ns ->
        if (ns == null) cl.apps().daemonSets().inAnyNamespace().list().items else cl.apps().daemonSets().inNamespace(ns)
            .list().items
    }

suspend fun loadReplicaSetsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "ReplicaSets", namespace) { cl, ns ->
        if (ns == null) cl.apps().replicaSets().inAnyNamespace().list().items else cl.apps().replicaSets()
            .inNamespace(ns).list().items
    }

suspend fun loadJobsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Jobs", namespace) { cl, ns ->
        if (ns == null) cl.batch().v1().jobs().inAnyNamespace().list().items else cl.batch().v1().jobs().inNamespace(ns)
            .list().items
    }

suspend fun loadCronJobsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "CronJobs", namespace) { cl, ns ->
        if (ns == null) cl.batch().v1().cronjobs().inAnyNamespace().list().items else cl.batch().v1().cronjobs()
            .inNamespace(ns).list().items
    }

suspend fun loadServicesFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Services", namespace) { cl, ns ->
        if (ns == null) cl.services().inAnyNamespace().list().items else cl.services().inNamespace(ns).list().items
    }

suspend fun loadIngressesFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Ingresses", namespace) { cl, ns ->
        if (ns == null) cl.network().v1().ingresses().inAnyNamespace().list().items else cl.network().v1().ingresses()
            .inNamespace(ns).list().items
    }

suspend fun loadPVsFabric8(client: KubernetesClient?) = fetchK8sResource(client, "PersistentVolumes", null) { cl, _ ->
    cl.persistentVolumes().list().items
} // Cluster-scoped

suspend fun loadPVCsFabric8(client: KubernetesClient?, namespace: String?) = fetchK8sResource(
    client,
    "PersistentVolumeClaims",
    namespace
) { cl, ns ->
    if (ns == null) cl.persistentVolumeClaims().inAnyNamespace().list().items else cl.persistentVolumeClaims()
        .inNamespace(ns).list().items
}

suspend fun loadStorageClassesFabric8(client: KubernetesClient?) =
    fetchK8sResource(client, "StorageClasses", null) { cl, _ ->
        cl.storage().v1().storageClasses().list().items
    } // Cluster-scoped

suspend fun loadConfigMapsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "ConfigMaps", namespace) { cl, ns ->
        if (ns == null) cl.configMaps().inAnyNamespace().list().items else cl.configMaps().inNamespace(ns).list().items
    }

suspend fun loadSecretsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Secrets", namespace) { cl, ns ->
        if (ns == null) cl.secrets().inAnyNamespace().list().items else cl.secrets().inNamespace(ns).list().items
    }

suspend fun loadServiceAccountsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "ServiceAccounts", namespace) { cl, ns ->
        if (ns == null) cl.serviceAccounts().inAnyNamespace().list().items else cl.serviceAccounts().inNamespace(ns)
            .list().items
    }

suspend fun loadRolesFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Roles", namespace) { cl, ns ->
        if (ns == null) cl.rbac().roles().inAnyNamespace().list().items else cl.rbac().roles().inNamespace(ns)
            .list().items
    }

suspend fun loadRoleBindingsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "RoleBindings", namespace) { cl, ns ->
        if (ns == null) cl.rbac().roleBindings().inAnyNamespace().list().items else cl.rbac().roleBindings()
            .inNamespace(ns).list().items
    }

suspend fun loadClusterRolesFabric8(client: KubernetesClient?) =
    fetchK8sResource(client, "ClusterRoles", null) { cl, _ -> cl.rbac().clusterRoles().list().items } // Cluster-scoped

suspend fun loadClusterRoleBindingsFabric8(client: KubernetesClient?) =
    fetchK8sResource(client, "ClusterRoleBindings", null) { cl, _ ->
        cl.rbac().clusterRoleBindings().list().items
    } // Cluster-scoped

// Function to load endpoints for a service
suspend fun loadEndpointsForService(
    client: KubernetesClient?,
    namespace: String?,
    serviceName: String?
): Result<Endpoints> {
    if (client == null || namespace.isNullOrEmpty() || serviceName.isNullOrEmpty()) {
        return Result.failure(IllegalArgumentException("Client, namespace, and service name are required"))
    }

    return try {
        val endpoints = client.endpoints()
            .inNamespace(namespace)
            .withName(serviceName)
            .get()

        Result.success(endpoints)
    } catch (e: Exception) {
        logger.error("Failed to load endpoints for service $serviceName in namespace $namespace", e)
        Result.failure(e)
    }
}

// --- Функція підключення з ретраями (використовує Config.autoConfigure(contextName)) ---
suspend fun connectWithRetries(contextName: String?): Result<Pair<KubernetesClient, String>> {
    val targetContext = if (contextName.isNullOrBlank()) null else contextName
    var lastError: Exception? = null
    val contextNameToLog = targetContext ?: "(default)"

    for (attempt in 1..MAX_CONNECT_RETRIES) {
        logger.info("Спроба підключення до '$contextNameToLog' (спроба $attempt/$MAX_CONNECT_RETRIES)...")
        try {
            val resultPair: Pair<KubernetesClient, String> = withContext(Dispatchers.IO) { // Сподіваємось, компілюється
                logger.info("[IO] Створення конфігу та клієнта для '$contextNameToLog' через Config.autoConfigure...")
                val config = Config.autoConfigure(targetContext)
                    ?: throw KubernetesClientException("Не вдалося автоматично налаштувати конфігурацію для контексту '$contextNameToLog'")
                config.connectionTimeout = CONNECTION_TIMEOUT_MS
                config.requestTimeout = REQUEST_TIMEOUT_MS
                logger.info("[IO] Config context: ${config.currentContext?.name ?: "(не вказано)"}. Namespace: ${config.namespace}")

                val client = KubernetesClientBuilder().withConfig(config).build()
                logger.info("[IO] Fabric8 client created. Checking version...")
                val ver = client.kubernetesVersion?.gitVersion ?: "невідомо" // Спрощено
                logger.info("[IO] Версія сервера: $ver для '$contextNameToLog'")
                Pair(client, ver)
            }
            logger.info("Підключення до '$contextNameToLog' успішне (спроба $attempt).")
            return Result.success(resultPair)
        } catch (e: Exception) {
            lastError = e; logger.warn("Помилка підключення '$contextNameToLog' (спроба $attempt): ${e.message}")
            //if (attempt < MAX_CONNECT_RETRIES) { kotlinx.coroutines.delay(RETRY_DELAY_MS) }
        }
    }
    logger.error("Не вдалося підключитися до '$contextNameToLog' після $MAX_CONNECT_RETRIES спроб.")
    return Result.failure(lastError ?: IOException("Невідома помилка підключення"))
}

@Composable
fun KubeTableHeaderRow(
    headers: List<String>,
    columnWidths: List<Int> // Add column widths parameter
) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        headers.forEachIndexed { index, header ->
            val width = if (index < columnWidths.size) columnWidths[index] else 100
            Text(
                text = header,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .width(width.dp)
                    .padding(horizontal = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun <T : HasMetadata> KubeTableRow(
    item: T,
    headers: List<String>,
    resourceType: String,
    columnWidths: List<Int>, // Add column widths parameter
    onRowClick: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRowClick(item) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        headers.forEachIndexed { colIndex, _ ->
            val width = if (colIndex < columnWidths.size) columnWidths[colIndex] else 100
            Text(
                text = getCellData(item, colIndex, resourceType),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .width(width.dp)
                    .padding(horizontal = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DetailRow(label: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text( // M3 Text
            text = "$label:",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), // M3 Typography
            modifier = Modifier.width(150.dp)
        )
        Text( // M3 Text
            text = value ?: "<none>",
            style = MaterialTheme.typography.bodyMedium, // M3 Typography
            modifier = Modifier.weight(1f)
        )
    }
}

// === ДІАЛОГ ВИБОРУ КОНТЕЙНЕРА (M3) ===
@Composable
fun ContainerSelectionDialog(
    containers: List<String>,
    onDismiss: () -> Unit,
    onContainerSelected: (String) -> Unit
) {
    var selectedOption by remember { mutableStateOf(containers.firstOrNull() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Container") },
        text = {
            Column {
                containers.forEach { containerName ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedOption = containerName }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (containerName == selectedOption),
                            onClick = { selectedOption = containerName })
                        Spacer(Modifier.width(8.dp))
                        Text(containerName)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onContainerSelected(selectedOption) },
                enabled = selectedOption.isNotEmpty()
            ) { Text("View Logs") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ===
@Composable
fun BasicMetadataDetails(resource: HasMetadata) { // Допоміжна функція для базових метаданих
    Text("Basic Metadata:", style = MaterialTheme.typography.titleMedium)
    DetailRow("Name", resource.metadata?.name)
    DetailRow("Namespace", resource.metadata?.namespace) // Буде null для кластерних ресурсів
    DetailRow("Created", formatAge(resource.metadata?.creationTimestamp))
    DetailRow("UID", resource.metadata?.uid)
    // Можна додати Labels / Annotations за бажанням
    DetailRow("Labels", resource.metadata?.labels?.entries?.joinToString("\n") { "${it.key}=${it.value}" })
    DetailRow("Annotations", resource.metadata?.annotations?.entries?.joinToString("\n") { "${it.key}=${it.value}" })
}

@Composable
fun PodDetailsView(pod: Pod, onShowLogsRequest: (containerName: String) -> Unit) { // Додано onShowLogsRequest
    val showContainerDialog = remember { mutableStateOf(false) }
    val containers = remember(pod) { pod.spec?.containers ?: emptyList() }

    Column {
        // --- Кнопка та Діалог логів ---
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = {
                when (containers.size) {
                    0 -> logger.warn("Под ${pod.metadata?.name} немає контейнерів.")
                    1 -> onShowLogsRequest(containers.first().name)
                    else -> showContainerDialog.value = true
                }
            }) {
                Icon(Icons.Filled.Build, contentDescription = "View Logs")
                Spacer(Modifier.width(4.dp))
                Text("View Logs")
            }
        }
        Spacer(Modifier.height(8.dp))

        if (showContainerDialog.value) {
            ContainerSelectionDialog(
                containers = containers.mapNotNull { it.name },
                onDismiss = { showContainerDialog.value = false },
                onContainerSelected = { containerName ->
                    showContainerDialog.value = false
                    onShowLogsRequest(containerName)
                }
            )
        }
        // --- Кінець логіки логів ---

        // --- Решта деталей пода ---
        DetailRow("Name", pod.metadata?.name)
        DetailRow("Namespace", pod.metadata?.namespace)
        DetailRow("Status", pod.status?.phase)
        DetailRow("Node", pod.spec?.nodeName)
        DetailRow("Pod IP", pod.status?.podIP)
        DetailRow("Service Account", pod.spec?.serviceAccountName ?: pod.spec?.serviceAccount)
        DetailRow("Created", formatAge(pod.metadata?.creationTimestamp))
        DetailRow("Restarts", formatPodRestarts(pod.status?.containerStatuses))
        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text("Containers:", style = MaterialTheme.typography.titleMedium)
        pod.status?.containerStatuses?.forEach { cs ->
            Column(
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(4.dp)
            ) {
                DetailRow("  Name", cs.name)
                DetailRow("  Image", cs.image)
                DetailRow("  Ready", cs.ready?.toString())
                DetailRow("  Restarts", cs.restartCount?.toString())
                DetailRow("  State", cs.state?.let {
                    when {
                        it.running != null -> "Running"; it.waiting != null -> "Waiting (${it.waiting.reason})"; it.terminated != null -> "Terminated (${it.terminated.reason}, Exit: ${it.terminated.exitCode})"; else -> "?"
                    }
                })
                DetailRow("  Image ID", cs.imageID)
            }
        }
        if (pod.status?.containerStatuses.isNullOrEmpty()) {
            Text("  (No container statuses)", modifier = Modifier.padding(start = 8.dp))
        }
        // ---
    }
}

@Composable
fun NamespaceDetailsView(ns: Namespace) {
    Column {
        DetailRow("Name", ns.metadata?.name)
        DetailRow("Status", ns.status?.phase)
        DetailRow("Created", formatAge(ns.metadata?.creationTimestamp))

        // Labels section with expandable panel
        val labelsExpanded = remember { mutableStateOf(false) }
        val labels = ns.metadata?.labels ?: emptyMap()

        Row(
            modifier = Modifier.fillMaxWidth().clickable { labelsExpanded.value = !labelsExpanded.value },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Labels (${labels.size})",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (labelsExpanded.value) FeatherIcons.ArrowUp else FeatherIcons.ArrowDown,
                contentDescription = if (labelsExpanded.value) "Collapse" else "Expand"
            )
        }

        if (labelsExpanded.value && labels.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                labels.forEach { (key, value) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(key, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(120.dp))
                        Text(value, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        } else if (labelsExpanded.value) {
            Text(
                "No labels",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Annotations section with expandable panel
        val annotationsExpanded = remember { mutableStateOf(false) }
        val annotations = ns.metadata?.annotations ?: emptyMap()

        Row(
            modifier = Modifier.fillMaxWidth().clickable { annotationsExpanded.value = !annotationsExpanded.value },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Annotations (${annotations.size})",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (annotationsExpanded.value) FeatherIcons.ArrowUp else FeatherIcons.ArrowDown,
                contentDescription = if (annotationsExpanded.value) "Collapse" else "Expand"
            )
        }

        if (annotationsExpanded.value && annotations.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                annotations.forEach { (key, value) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            key,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(160.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            value,
                            modifier = Modifier.padding(start = 8.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else if (annotationsExpanded.value) {
            Text(
                "No annotations",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun NodeDetailsView(node: Node) {
    val scrollState = rememberScrollState()
    val showCapacity = remember { mutableStateOf(false) }
    val showConditions = remember { mutableStateOf(false) }
    val showLabels = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            //.verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Basic node information section
        Text(
            text = "Node Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        DetailRow("Name", node.metadata?.name)
        DetailRow("Status", formatNodeStatus(node.status?.conditions))
        DetailRow("Roles", formatNodeRoles(node.metadata?.labels))
        DetailRow("Age", formatAge(node.metadata?.creationTimestamp))
        DetailRow("Version", node.status?.nodeInfo?.kubeletVersion)
        DetailRow("OS Image", node.status?.nodeInfo?.osImage)
        DetailRow("Kernel Version", node.status?.nodeInfo?.kernelVersion)
        DetailRow("Container Runtime", node.status?.nodeInfo?.containerRuntimeVersion)
        DetailRow("Architecture", node.status?.nodeInfo?.architecture)
        DetailRow("Internal IP", node.status?.addresses?.find { it.type == "InternalIP" }?.address)
        DetailRow("External IP", node.status?.addresses?.find { it.type == "ExternalIP" }?.address)
        DetailRow("Hostname", node.status?.addresses?.find { it.type == "Hostname" }?.address)
        DetailRow("Taints", formatTaints(node.spec?.taints))

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Capacity & Allocatable Resources section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showCapacity.value = !showCapacity.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (showCapacity.value) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = "Expand Capacity"
            )
            Text(
                text = "Capacity & Allocatable Resources",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (showCapacity.value) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Create a table-like view for capacity and allocatable
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Resource",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Capacity",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Allocatable",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    // CPUs
                    ResourceRow(
                        name = "CPU",
                        capacity = node.status?.capacity?.get("cpu"),
                        allocatable = node.status?.allocatable?.get("cpu")
                    )

                    // Memory
                    ResourceRow(
                        name = "Memory",
                        capacity = node.status?.capacity?.get("memory"),
                        allocatable = node.status?.allocatable?.get("memory")
                    )

                    // Ephemeral Storage
                    ResourceRow(
                        name = "Ephemeral Storage",
                        capacity = node.status?.capacity?.get("ephemeral-storage"),
                        allocatable = node.status?.allocatable?.get("ephemeral-storage")
                    )

                    // Pods
                    ResourceRow(
                        name = "Pods",
                        capacity = node.status?.capacity?.get("pods"),
                        allocatable = node.status?.allocatable?.get("pods")
                    )

                    // Display other resources dynamically
                    node.status?.capacity?.entries?.filter {
                        !setOf("cpu", "memory", "ephemeral-storage", "pods").contains(it.key)
                    }?.forEach { entry ->
                        ResourceRow(
                            name = entry.key,
                            capacity = entry.value,
                            allocatable = node.status?.allocatable?.get(entry.key)
                        )
                    }
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Conditions section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showConditions.value = !showConditions.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (showConditions.value) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = "Expand Conditions"
            )
            Text(
                text = "Conditions",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (showConditions.value) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    node.status?.conditions?.forEach { condition ->
                        val statusColor = when (condition.status) {
                            "True" -> MaterialTheme.colorScheme.primary
                            "False" -> if (condition.type == "Ready")
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary

                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (condition.status) {
                                            "True" -> FeatherIcons.CheckCircle
                                            "False" -> FeatherIcons.XCircle
                                            else -> FeatherIcons.HelpCircle
                                        },
                                        contentDescription = "Condition Status",
                                        tint = statusColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = condition.type ?: "Unknown",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = statusColor
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = condition.status ?: "Unknown",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = statusColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                DetailRow("Last Transition", formatAge(condition.lastTransitionTime))
                                if (!condition.message.isNullOrBlank()) {
                                    DetailRow("Message", condition.message)
                                }
                                if (!condition.reason.isNullOrBlank()) {
                                    DetailRow("Reason", condition.reason)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Labels section
        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLabels.value = !showLabels.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (showLabels.value) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = "Expand Labels"
            )
            Text(
                text = "Labels",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (showLabels.value) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    node.metadata?.labels?.entries?.forEach { (key, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.weight(0.4f)
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.6f)
                            )
                        }
                    }

                    if (node.metadata?.labels.isNullOrEmpty()) {
                        Text(
                            text = "No labels",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ResourceRow(name: String, capacity: Quantity?, allocatable: Quantity?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = capacity?.amount ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = allocatable?.amount ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun DeploymentDetailsView(dep: Deployment) {
    Column {
        DetailRow("Name", dep.metadata?.name)
        DetailRow("Namespace", dep.metadata?.namespace)
        DetailRow("Created", formatAge(dep.metadata?.creationTimestamp))
        DetailRow("Replicas", "${dep.status?.readyReplicas ?: 0} / ${dep.spec?.replicas ?: 0} (Ready)")
        DetailRow("Updated", dep.status?.updatedReplicas?.toString())
        DetailRow("Available", dep.status?.availableReplicas?.toString())
        DetailRow("Strategy", dep.spec?.strategy?.type)

        // Selector information
        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text("Selector:", style = MaterialTheme.typography.titleMedium)
        dep.spec?.selector?.matchLabels?.forEach { (key, value) ->
            DetailRow("  $key", value)
        }
        if (dep.spec?.selector?.matchLabels.isNullOrEmpty()) {
            Text("  (No selector labels)", modifier = Modifier.padding(start = 8.dp))
        }

        // Conditions
        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text("Conditions:", style = MaterialTheme.typography.titleMedium)
        dep.status?.conditions?.forEach { condition ->
            Column(
                modifier = Modifier
                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    .padding(4.dp)
            ) {
                DetailRow("  Type", condition.type)
                DetailRow("  Status", condition.status)
                DetailRow("  Reason", condition.reason)
                DetailRow("  Message", condition.message)
                DetailRow("  Last Update", formatAge(condition.lastUpdateTime))
                DetailRow("  Last Transition", formatAge(condition.lastTransitionTime))
            }
        }
        if (dep.status?.conditions.isNullOrEmpty()) {
            Text("  (No conditions)", modifier = Modifier.padding(start = 8.dp))
        }

        // Template information
        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text("Pod Template:", style = MaterialTheme.typography.titleMedium)

        // Template labels
        Text(
            "  Labels:", style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
        )
        dep.spec?.template?.metadata?.labels?.forEach { (key, value) ->
            DetailRow("    $key", value)
        }
        if (dep.spec?.template?.metadata?.labels.isNullOrEmpty()) {
            Text("    (No labels)", modifier = Modifier.padding(start = 16.dp))
        }

        // Template containers
        Text(
            "  Containers:", style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
        )
        dep.spec?.template?.spec?.containers?.forEach { container ->
            Column(
                modifier = Modifier
                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    .padding(4.dp)
            ) {
                DetailRow("Name", container.name)
                DetailRow("Image", container.image)
                DetailRow("Pull Policy", container.imagePullPolicy)

                // Container ports
                if (!container.ports.isNullOrEmpty()) {
                    Text(
                        "Ports:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    container.ports.forEach { port ->
                        DetailRow(
                            "  ${port.name ?: port.containerPort}",
                            "${port.containerPort}/${port.protocol ?: "TCP"}"
                        )
                    }
                }

                // Resource requirements
                container.resources?.let { resources ->
                    Text(
                        "Resources:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    resources.limits?.forEach { (key, value) ->
                        DetailRow("  Limit $key", value.toString())
                    }
                    resources.requests?.forEach { (key, value) ->
                        DetailRow("  Request $key", value.toString())
                    }
                }

                // Environment variables
                if (!container.env.isNullOrEmpty()) {
                    Text(
                        "Environment:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    container.env.forEach { env ->
                        val valueText = when {
                            env.value != null -> env.value
                            env.valueFrom?.configMapKeyRef != null ->
                                "ConfigMap: ${env.valueFrom?.configMapKeyRef?.name}.${env.valueFrom?.configMapKeyRef?.key}"

                            env.valueFrom?.secretKeyRef != null ->
                                "Secret: ${env.valueFrom?.secretKeyRef?.name}.${env.valueFrom?.secretKeyRef?.key}"

                            env.valueFrom?.fieldRef != null ->
                                "Field: ${env.valueFrom?.fieldRef?.fieldPath}"

                            else -> "<complex>"
                        }
                        DetailRow("  ${env.name}", valueText)
                    }
                }

                // Volume mounts
                if (!container.volumeMounts.isNullOrEmpty()) {
                    Text(
                        "Volume Mounts:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    container.volumeMounts.forEach { mount ->
                        DetailRow("  ${mount.name}", "${mount.mountPath}${if (mount.readOnly == true) " (ro)" else ""}")
                    }
                }
            }
        }
        if (dep.spec?.template?.spec?.containers.isNullOrEmpty()) {
            Text("    (No containers)", modifier = Modifier.padding(start = 16.dp))
        }

        // Volumes
        if (!dep.spec?.template?.spec?.volumes.isNullOrEmpty()) {
            Text(
                "  Volumes:", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            )
            dep.spec?.template?.spec?.volumes?.forEach { volume ->
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        .padding(4.dp)
                ) {
                    DetailRow("Name", volume.name)
                    // Determine volume type and details
                    when {
                        volume.configMap != null -> {
                            DetailRow("Type", "ConfigMap")
                            DetailRow("ConfigMap Name", volume.configMap.name)
                        }

                        volume.secret != null -> {
                            DetailRow("Type", "Secret")
                            DetailRow("Secret Name", volume.secret.secretName)
                        }

                        volume.persistentVolumeClaim != null -> {
                            DetailRow("Type", "PVC")
                            DetailRow("Claim Name", volume.persistentVolumeClaim.claimName)
                            DetailRow("Read Only", volume.persistentVolumeClaim.readOnly?.toString() ?: "false")
                        }

                        volume.emptyDir != null -> {
                            DetailRow("Type", "EmptyDir")
                            DetailRow("Medium", volume.emptyDir.medium ?: "")
                        }

                        volume.hostPath != null -> {
                            DetailRow("Type", "HostPath")
                            DetailRow("Path", volume.hostPath.path)
                            DetailRow("Type", volume.hostPath.type ?: "")
                        }

                        else -> DetailRow("Type", "<complex>")
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceDetailsView(svc: Service) {
    //val scrollState = rememberScrollState()
    val showPorts = remember { mutableStateOf(false) }
    val showEndpoints = remember { mutableStateOf(false) }
    val showLabels = remember { mutableStateOf(false) }
    val showAnnotations = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            //.verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Basic service information section
        Text(
            text = "Service Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        DetailRow("Name", svc.metadata?.name)
        DetailRow("Namespace", svc.metadata?.namespace)
        DetailRow("Created", formatAge(svc.metadata?.creationTimestamp))
        DetailRow("Type", svc.spec?.type)
        DetailRow("ClusterIP(s)", svc.spec?.clusterIPs?.joinToString(", "))
        DetailRow("External IP(s)", formatServiceExternalIP(svc))
        DetailRow("Session Affinity", svc.spec?.sessionAffinity ?: "None")

        if (svc.spec?.type == "LoadBalancer") {
            DetailRow("Load Balancer IP", svc.spec?.loadBalancerIP)
            DetailRow("Load Balancer Class", svc.spec?.loadBalancerClass)
            DetailRow("Allocate Load Balancer NodePorts", svc.spec?.allocateLoadBalancerNodePorts?.toString() ?: "true")
        }

        if (svc.spec?.type == "ExternalName") {
            DetailRow("External Name", svc.spec?.externalName)
        }

        DetailRow("Traffic Policy", svc.spec?.externalTrafficPolicy ?: "Cluster")
        DetailRow("IP Families", svc.spec?.ipFamilies?.joinToString(", "))
        DetailRow("IP Family Policy", svc.spec?.ipFamilyPolicy)
        DetailRow("Selector", svc.spec?.selector?.map { "${it.key}=${it.value}" }?.joinToString(", ") ?: "None")

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Ports section (expandable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPorts.value = !showPorts.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (showPorts.value) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = "Expand Ports"
            )
            Text(
                text = "Ports (${svc.spec?.ports?.size ?: 0})",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (showPorts.value && !svc.spec?.ports.isNullOrEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header row
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Name",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(0.22f)
                        )
                        Text(
                            text = "Port",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(0.18f)
                        )
                        Text(
                            text = "Target Port",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(0.25f)
                        )
                        Text(
                            text = "Protocol",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(0.2f)
                        )
                        Text(
                            text = "Node Port",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(0.15f)
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    // Port rows
                    svc.spec?.ports?.forEach { port ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = port.name ?: "-",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.22f)
                            )
                            Text(
                                text = port.port?.toString() ?: "-",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.18f)
                            )
                            Text(
                                text = port.targetPort?.toString() ?: "-",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.25f)
                            )
                            Text(
                                text = port.protocol ?: "TCP",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.2f)
                            )
                            Text(
                                text = port.nodePort?.toString() ?: "-",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.15f)
                            )
                        }
                    }
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

//        // Endpoints section
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .clickable { showEndpoints.value = !showEndpoints.value }
//                .padding(vertical = 8.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Icon(
//                imageVector = if (showEndpoints.value) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
//                contentDescription = "Expand Endpoints"
//            )
//            Text(
//                text = "Service Endpoints",
//                style = MaterialTheme.typography.titleMedium,
//            )
//        }
//
//        if (showEndpoints.value) {
//            // This would require fetching Endpoints from the API separately
//            // For demonstration, we'll show a placeholder with how to implement it
//            Card(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
//                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
//            ) {
//                Column(modifier = Modifier.padding(16.dp)) {
//                    Text(
//                        text = "Endpoints for service ${svc.metadata?.name} should be fetched separately from the Kubernetes API.",
//                        style = MaterialTheme.typography.bodyMedium,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                    )
//
//                    Spacer(modifier = Modifier.height(8.dp))
//
//                    // Placeholder for how the implementation would look
//                    Text(
//                        text = "Implementation note: You would need to call:",
//                        style = MaterialTheme.typography.bodySmall,
//                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
//                    )
//
//                    Text(
//                        text = "client.endpoints().inNamespace(${svc.metadata?.namespace}).withName(${svc.metadata?.name}).get()",
//                        style = MaterialTheme.typography.bodySmall,
//                        fontFamily = FontFamily.Monospace,
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .background(MaterialTheme.colorScheme.surface)
//                            .padding(8.dp)
//                    )
//
//                    // If you had the actual endpoints:
//                    /*
//                    endpoints.subsets?.forEach { subset ->
//                        subset.addresses?.forEach { address ->
//                            Row {
//                                Text(address.ip ?: "")
//                                // Show target references
//                                if (address.targetRef != null) {
//                                    Text("-> ${address.targetRef.kind}/${address.targetRef.name}")
//                                }
//                            }
//                        }
//                    }
//                    */
//                }
//            }
//        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Labels section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLabels.value = !showLabels.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (showLabels.value) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = "Expand Labels"
            )
            Text(
                text = "Labels (${svc.metadata?.labels?.size ?: 0})",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (showLabels.value) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    svc.metadata?.labels?.entries?.forEach { (key, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.weight(0.4f)
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.6f)
                            )
                        }
                    }

                    if (svc.metadata?.labels.isNullOrEmpty()) {
                        Text(
                            text = "No labels",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Annotations section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAnnotations.value = !showAnnotations.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (showAnnotations.value) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = "Expand Annotations"
            )
            Text(
                text = "Annotations (${svc.metadata?.annotations?.size ?: 0})",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (showAnnotations.value) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    svc.metadata?.annotations?.entries?.forEach { (key, value) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (svc.metadata?.annotations.isNullOrEmpty()) {
                        Text(
                            text = "No annotations",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // You could add a section for events related to this service if you implement that
    }
}

@Composable
fun SecretDetailsView(secret: Secret) {
    // For displaying copy notification
    // Для відображення сповіщення про копіювання
    val snackbarHostState = remember { SnackbarHostState() }
    // Coroutine scope for showing snackbar
    // Корутин скоуп для показу снекбара
    val coroutineScope = rememberCoroutineScope()

    // Check if this is a Helm release secret
    val isHelmRelease = secret.type == "helm.sh/release.v1"

    // For storing Helm release data
    var helmReleaseInfo by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var helmReleaseError by remember { mutableStateOf<String?>(null) }

    // Process Helm release data if needed
    LaunchedEffect(secret) {
        if (isHelmRelease && secret.data?.isNotEmpty() == true) {
            try {
                // Most Helm releases store data in the "release" key
                val releaseData = secret.data?.get("release")
                if (releaseData != null) {
                    // Decode base64 first
                    // cat hr3.txt | base64 -d | base64 -d | gzip -d
                    val decodedBytes =
                        java.util.Base64.getDecoder().decode(java.util.Base64.getDecoder().decode(releaseData))

                    // Decompress GZIP data
                    val bais = ByteArrayInputStream(decodedBytes)
                    val gzis = GZIPInputStream(bais)
                    val decompressedData = gzis.readBytes()

                    // Parse JSON data
                    val mapper = ObjectMapper().registerKotlinModule()
                    val helmData = mapper.readValue(decompressedData, Map::class.java) as Map<String, Any?>
                    helmReleaseInfo = helmData
                }
            } catch (e: Exception) {
                helmReleaseError = "Error decoding Helm release: ${e.message}"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
            //.fillMaxSize()
            //.verticalScroll(rememberScrollState())
        ) {
            DetailRow("Name", secret.metadata?.name)
            DetailRow("Namespace", secret.metadata?.namespace)
            DetailRow("Created", formatAge(secret.metadata?.creationTimestamp))
            DetailRow("Type", secret.type)

            // Special handling for Helm release secrets
            if (isHelmRelease) {
                Text(
                    text = "Helm Release Data:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (helmReleaseError != null) {
                    Text(
                        text = helmReleaseError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else if (helmReleaseInfo != null) {
                    // Display Helm release data
                    val releaseInfo = helmReleaseInfo!!

                    // Extract and display key information
                    val name = (releaseInfo["name"] as? String) ?: "Unknown"
                    val version = (releaseInfo["version"] as? Int)?.toString() ?: "Unknown"
                    val status = ((releaseInfo["info"] as? Map<*, *>)?.get("status") as? String) ?: "Unknown"
                    val chart = ((releaseInfo["chart"] as? Map<*, *>)?.get("metadata") as? Map<*, *>)?.let {
                        "${it["name"]}:${it["version"]}"
                    } ?: "Unknown"

                    // Display main info
                    DetailRow("Release Name", name)
                    DetailRow("Release Version", version)
                    DetailRow("Release Status", status)
                    DetailRow("Chart", chart)

                    // Display values data
                    val values = releaseInfo["config"] as? Map<*, *>
                    if (values != null && values.isNotEmpty()) {
                        // Convert to JSON for display
                        val mapper = ObjectMapper().registerKotlinModule()
                        val valuesJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(values)

                        Text(
                            text = "Values:",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                        )
                        // Copy button for values
                        IconButton(
                            onClick = {
                                try {
                                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                    val selection = java.awt.datatransfer.StringSelection(valuesJson)
                                    clipboard.setContents(selection, null)

                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Values copied to clipboard",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                } catch (e: Exception) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Error copying: ${e.message}",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = FeatherIcons.Copy,
                                contentDescription = "Copy values",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                SelectionContainer(
                                    modifier = Modifier
                                        .padding(16.dp),
                                ) {
                                    Text(
                                        softWrap = true,
                                        text = valuesJson,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                            }
                        }
                    }
                    //Global Values
                    val valuesGlobal = ((releaseInfo["chart"] as? Map<*, *>)?.get("values") as? Map<*, *>)
                    if (valuesGlobal != null && valuesGlobal.isNotEmpty()) {
                        // Convert to JSON for display
                        val mapper = ObjectMapper().registerKotlinModule()
                        val valuesGlobalJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(valuesGlobal)

                        Text(
                            text = "GlobalValues:",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                        )
                        // Copy button for values
                        IconButton(

                            onClick = {
                                try {
                                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                    val selection = java.awt.datatransfer.StringSelection(valuesGlobalJson)
                                    clipboard.setContents(selection, null)

                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Values copied to clipboard",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                } catch (e: Exception) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Error copying: ${e.message}",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = FeatherIcons.Copy,
                                contentDescription = "Copy valuesGlobal",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                SelectionContainer(
                                    modifier = Modifier
                                        .padding(16.dp),
                                ) {
                                    Text(
                                        softWrap = true,
                                        text = valuesGlobalJson,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                            }
                        }
                    }
                } else {
                    Text(
                        text = "Processing Helm release data...",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                // Regular secret data handling (original code for non-Helm secrets)

                // Data section header
                // Заголовок секції Data
                Text(
                    text = "Secret Data:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Display keys and their values
                // Відображення ключів та їх значень
                secret.data?.forEach { (key, encodedValue) ->
                    var isDecoded by remember { mutableStateOf(false) }
                    var decodedValue by remember { mutableStateOf("") }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Key
                        Text(
                            text = "$key:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.width(150.dp)
                        )

                        // Value (encoded or decoded)
                        // Значення (закодоване або декодоване)
                        Text(
                            text = if (isDecoded) decodedValue else encodedValue,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )

                        // Copy icon
                        // Іконка для копіювання
                        IconButton(
                            onClick = {
                                val textToCopy = if (isDecoded) decodedValue else encodedValue
                                try {
                                    // Copy text to clipboard
                                    // Копіюємо текст у буфер обміну
                                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                    val selection = java.awt.datatransfer.StringSelection(textToCopy)
                                    clipboard.setContents(selection, null)

                                    // Show successful copy notification
                                    // Показуємо сповіщення про успішне копіювання
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Value for '$key' copied to clipboard",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                } catch (e: Exception) {
                                    // Show error notification
                                    // Сповіщаємо про помилку
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Error copying: ${e.message}",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = FeatherIcons.Copy,
                                contentDescription = "Copy value",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Decode/encode icon
                        // Іконка для декодування/кодування
                        IconButton(
                            onClick = {
                                if (!isDecoded) {
                                    try {
                                        // Decode from Base64
                                        decodedValue = String(java.util.Base64.getDecoder().decode(encodedValue))
                                        isDecoded = true
                                    } catch (e: Exception) {
                                        decodedValue = "Error decoding: ${e.message}"
                                        isDecoded = true
                                    }
                                } else {
                                    // Return to encoded view
                                    // Повертаємося в закодований вигляд
                                    isDecoded = false
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isDecoded) FeatherIcons.EyeOff else FeatherIcons.Eye,
                                contentDescription = if (isDecoded) "Hide decoded value" else "Decode value",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Display stringData (unencoded values)
                secret.stringData?.let { stringData ->
                    if (stringData.isNotEmpty()) {
                        Text(
                            text = "String Data (not encoded):",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                        )

                        stringData.forEach { (key, value) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$key:",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.width(150.dp)
                                )

                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )

                                // Copy icon for stringData
                                IconButton(
                                    onClick = {
                                        try {
                                            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                            val selection = java.awt.datatransfer.StringSelection(value)
                                            clipboard.setContents(selection, null)

                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = "Value for '$key' copied to clipboard",
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                        } catch (e: Exception) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = "Error copying: ${e.message}",
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = FeatherIcons.Copy,
                                        contentDescription = "Copy value",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // If no data in the secret
                // Якщо немає даних у секреті
                if ((secret.data == null || secret.data!!.isEmpty()) &&
                    (secret.stringData == null || secret.stringData!!.isEmpty())
                ) {
                    Text(
                        text = "No data in this Secret",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        // Snackbar for displaying notifications
        // Snackbar для відображення сповіщень
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
fun ConfigMapDetailsView(cm: ConfigMap) {
    // TODO change vertical align cmname,copybutton
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DetailRow("Name", cm.metadata?.name)
            DetailRow("Namespace", cm.metadata?.namespace)
            DetailRow("Created", formatAge(cm.metadata?.creationTimestamp))

            // Заголовок секції Data
            Text(
                text = "ConfigMap Data:",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Відображення ключів та їх значень
            cm.data?.forEach { (key, cmValue) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$key:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.width(150.dp)
                    )
                    Text(
                        text = cmValue,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val textToCopy = cmValue
                            try {
                                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                val selection = java.awt.datatransfer.StringSelection(textToCopy)
                                clipboard.setContents(selection, null)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "Value for '$key' copied to clipboard",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            } catch (e: Exception) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "Error copying: ${e.message}",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = FeatherIcons.Copy,
                            contentDescription = "Copy value",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

        }
        // Snackbar для відображення сповіщень
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
fun PVDetailsView(pv: PersistentVolume) {
    Column {
        DetailRow("Name", pv.metadata?.name)
        DetailRow("Created", formatAge(pv.metadata?.creationTimestamp))
        DetailRow("Status", pv.status?.phase)
        DetailRow("Claim", pv.spec?.claimRef?.let { "${it.namespace ?: "-"}/${it.name ?: "-"}" })
        DetailRow("Reclaim Policy", pv.spec?.persistentVolumeReclaimPolicy)
        DetailRow("Access Modes", formatAccessModes(pv.spec?.accessModes))
        DetailRow("Storage Class", pv.spec?.storageClassName)
        DetailRow("Capacity", pv.spec?.capacity?.get("storage")?.toString())
        DetailRow("Volume Mode", pv.spec?.volumeMode)

        // Source details implementation
        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text("Volume Source:", style = MaterialTheme.typography.titleMedium)

        Column(
            modifier = Modifier
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                .padding(4.dp)
        ) {
            pv.spec?.let { spec ->
                when {
                    // NFS volume source
                    spec.nfs != null -> {
                        DetailRow("Type", "NFS")
                        DetailRow("Server", spec.nfs.server)
                        DetailRow("Path", spec.nfs.path)
                        DetailRow("Read Only", spec.nfs.readOnly?.toString() ?: "false")
                    }

                    // HostPath volume source
                    spec.hostPath != null -> {
                        DetailRow("Type", "HostPath")
                        DetailRow("Path", spec.hostPath.path)
                        DetailRow("Type", spec.hostPath.type ?: "<not specified>")
                    }

                    // GCE Persistent Disk
                    spec.gcePersistentDisk != null -> {
                        DetailRow("Type", "GCE Persistent Disk")
                        DetailRow("PD Name", spec.gcePersistentDisk.pdName)
                        DetailRow("FS Type", spec.gcePersistentDisk.fsType ?: "<not specified>")
                        DetailRow("Partition", spec.gcePersistentDisk.partition?.toString() ?: "0")
                        DetailRow("Read Only", spec.gcePersistentDisk.readOnly?.toString() ?: "false")
                    }

                    // AWS Elastic Block Store
                    spec.awsElasticBlockStore != null -> {
                        DetailRow("Type", "AWS EBS")
                        DetailRow("Volume ID", spec.awsElasticBlockStore.volumeID)
                        DetailRow("FS Type", spec.awsElasticBlockStore.fsType ?: "<not specified>")
                        DetailRow("Partition", spec.awsElasticBlockStore.partition?.toString() ?: "0")
                        DetailRow("Read Only", spec.awsElasticBlockStore.readOnly?.toString() ?: "false")
                    }

                    // Azure Disk
                    spec.azureDisk != null -> {
                        DetailRow("Type", "Azure Disk")
                        DetailRow("Disk Name", spec.azureDisk.diskName)
                        DetailRow("Disk URI", spec.azureDisk.diskURI)
                        DetailRow("Kind", spec.azureDisk.kind ?: "<not specified>")
                        DetailRow("FS Type", spec.azureDisk.fsType ?: "<not specified>")
                        DetailRow("Caching Mode", spec.azureDisk.cachingMode ?: "<not specified>")
                        DetailRow("Read Only", spec.azureDisk.readOnly?.toString() ?: "false")
                    }

                    // Azure File
                    spec.azureFile != null -> {
                        DetailRow("Type", "Azure File")
                        DetailRow("Secret Name", spec.azureFile.secretName)
                        DetailRow("Share Name", spec.azureFile.shareName)
                        DetailRow("Read Only", spec.azureFile.readOnly?.toString() ?: "false")
                    }

                    // Ceph RBD
                    spec.rbd != null -> {
                        DetailRow("Type", "Ceph RBD")
                        DetailRow("Monitors", spec.rbd.monitors.joinToString(", "))
                        DetailRow("Image", spec.rbd.image)
                        DetailRow("Pool", spec.rbd.pool ?: "rbd")
                        DetailRow("User", spec.rbd.user ?: "admin")
                        DetailRow("FS Type", spec.rbd.fsType ?: "<not specified>")
                        DetailRow("Read Only", spec.rbd.readOnly?.toString() ?: "false")
                    }

                    // CephFS
                    spec.cephfs != null -> {
                        DetailRow("Type", "CephFS")
                        DetailRow("Monitors", spec.cephfs.monitors.joinToString(", "))
                        DetailRow("Path", spec.cephfs.path ?: "/")
                        DetailRow("User", spec.cephfs.user ?: "admin")
                        DetailRow("Read Only", spec.cephfs.readOnly?.toString() ?: "false")
                    }

                    // iSCSI
                    spec.iscsi != null -> {
                        DetailRow("Type", "iSCSI")
                        DetailRow("Target Portal", spec.iscsi.targetPortal)
                        DetailRow("IQN", spec.iscsi.iqn)
                        DetailRow("Lun", spec.iscsi.lun.toString())
                        DetailRow("FS Type", spec.iscsi.fsType ?: "<not specified>")
                        DetailRow("Read Only", spec.iscsi.readOnly?.toString() ?: "false")
                    }

                    // FC (Fibre Channel)
                    spec.fc != null -> {
                        DetailRow("Type", "Fibre Channel")
                        DetailRow("WWNs", spec.fc.wwids?.joinToString(", ") ?: "<not specified>")
                        DetailRow("Lun", spec.fc.lun?.toString() ?: "<not specified>")
                        DetailRow("FS Type", spec.fc.fsType ?: "<not specified>")
                        DetailRow("Read Only", spec.fc.readOnly?.toString() ?: "false")
                    }

                    // Local
                    spec.local != null -> {
                        DetailRow("Type", "Local")
                        DetailRow("Path", spec.local.path)
                    }

                    // CSI (Container Storage Interface)
                    spec.csi != null -> {
                        DetailRow("Type", "CSI Volume")
                        DetailRow("Driver", spec.csi.driver)
                        DetailRow("Volume Handle", spec.csi.volumeHandle)
                        DetailRow("Read Only", spec.csi.readOnly?.toString() ?: "false")
                        DetailRow("FS Type", spec.csi.fsType ?: "<not specified>")

                        if (!spec.csi.volumeAttributes.isNullOrEmpty()) {
                            Text(
                                "Volume Attributes:",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            spec.csi.volumeAttributes.forEach { (key, value) ->
                                DetailRow("  $key", value)
                            }
                        }
                    }

                    // Glusterfs
                    spec.glusterfs != null -> {
                        DetailRow("Type", "GlusterFS")
                        DetailRow("Endpoints", spec.glusterfs.endpoints)
                        DetailRow("Path", spec.glusterfs.path)
                        DetailRow("Read Only", spec.glusterfs.readOnly?.toString() ?: "false")
                    }

                    // Empty - no source provided
                    else -> {
                        DetailRow("Type", "<unknown>")
                        Text(
                            "No volume source details available", style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PVCDetailsView(pvc: PersistentVolumeClaim) {
    Column {
        DetailRow("Name", pvc.metadata?.name)
        DetailRow("Namespace", pvc.metadata?.namespace)
        DetailRow("Created", formatAge(pvc.metadata?.creationTimestamp))
        DetailRow("Status", pvc.status?.phase)
        DetailRow("Volume", pvc.spec?.volumeName)
        DetailRow("Access Modes", formatAccessModes(pvc.spec?.accessModes))
        DetailRow("Storage Class", pvc.spec?.storageClassName)
        DetailRow("Capacity Request", pvc.spec?.resources?.requests?.get("storage")?.toString())
        DetailRow("Capacity Actual", pvc.status?.capacity?.get("storage")?.toString())
        DetailRow("Volume Mode", pvc.spec?.volumeMode)

        // Additional information: Labels
        if (!pvc.metadata?.labels.isNullOrEmpty()) {
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Labels:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                pvc.metadata?.labels?.forEach { (key, value) ->
                    DetailRow(key, value)
                }
            }
        }

        // Additional information: Annotations
        if (!pvc.metadata?.annotations.isNullOrEmpty()) {
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Annotations:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                pvc.metadata?.annotations?.forEach { (key, value) ->
                    DetailRow(key, value)
                }
            }
        }

        // Additional information: Finalizers
        if (!pvc.metadata?.finalizers.isNullOrEmpty()) {
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Finalizers:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                pvc.metadata?.finalizers?.forEach { finalizer ->
                    DetailRow("Finalizer", finalizer)
                }
            }
        }

        // Additional information: Selector
        pvc.spec?.selector?.let { selector ->
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Selector:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                // Match Labels
                if (!selector.matchLabels.isNullOrEmpty()) {
                    Text(
                        "Match Labels:", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    selector.matchLabels.forEach { (key, value) ->
                        DetailRow("  $key", value)
                    }
                }

                // Match Expressions
                if (!selector.matchExpressions.isNullOrEmpty()) {
                    Text(
                        "Match Expressions:", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    selector.matchExpressions.forEach { expr ->
                        val values = if (expr.values.isNullOrEmpty()) "<none>" else expr.values.joinToString(", ")
                        DetailRow("  ${expr.key} ${expr.operator}", values)
                    }
                }
            }
        }

        // Additional information: Data Source
        pvc.spec?.dataSource?.let { dataSource ->
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Data Source:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                DetailRow("Kind", dataSource.kind)
                DetailRow("Name", dataSource.name)
                DetailRow("API Group", dataSource.apiGroup ?: "<core>")
            }
        }

        // Additional information: Data Source Ref (newer API)
        pvc.spec?.dataSourceRef?.let { dataSourceRef ->
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Data Source Reference:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                DetailRow("Kind", dataSourceRef.kind)
                DetailRow("Name", dataSourceRef.name)
                DetailRow("API Group", dataSourceRef.apiGroup ?: "<core>")
                DetailRow("Namespace", dataSourceRef.namespace ?: "<same namespace>")
            }
        }

        // Additional information: Conditions
        if (!pvc.status?.conditions.isNullOrEmpty()) {
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Conditions:", style = MaterialTheme.typography.titleMedium)

            pvc.status?.conditions?.forEach { condition ->
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        .padding(4.dp)
                ) {
                    DetailRow("Type", condition.type)
                    DetailRow("Status", condition.status)
                    DetailRow("Last Probe Time", formatAge(condition.lastProbeTime))
                    DetailRow("Last Transition Time", formatAge(condition.lastTransitionTime))
                    DetailRow("Reason", condition.reason)
                    DetailRow("Message", condition.message)
                }
            }
        }

        // Additional information: Owner References
        if (!pvc.metadata?.ownerReferences.isNullOrEmpty()) {
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Owner References:", style = MaterialTheme.typography.titleMedium)

            pvc.metadata?.ownerReferences?.forEach { owner ->
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        .padding(4.dp)
                ) {
                    DetailRow("Kind", owner.kind)
                    DetailRow("Name", owner.name)
                    DetailRow("UID", owner.uid)
                    DetailRow("Controller", owner.controller?.toString() ?: "false")
                    DetailRow("Block Owner Deletion", owner.blockOwnerDeletion?.toString() ?: "false")
                }
            }
        }

        // Additional information: Resource Version and UID
        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text("Additional Metadata:", style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            DetailRow("UID", pvc.metadata?.uid)
            DetailRow("Resource Version", pvc.metadata?.resourceVersion)
            DetailRow("Generation", pvc.metadata?.generation?.toString())
        }
    }
}

@Composable
fun IngressDetailsView(ing: Ingress) {
    Column {
        DetailRow("Name", ing.metadata?.name)
        DetailRow("Namespace", ing.metadata?.namespace)
        DetailRow("Created", formatAge(ing.metadata?.creationTimestamp))
        DetailRow("Class", ing.spec?.ingressClassName ?: "<none>")
        DetailRow("Address", formatIngressAddress(ing.status?.loadBalancer?.ingress))

        // Additional information: Annotations
        if (!ing.metadata?.annotations.isNullOrEmpty()) {
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Annotations:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                ing.metadata?.annotations?.forEach { (key, value) ->
                    // Group important ingress controller annotations
                    val displayKey = when {
                        key.startsWith("nginx.ingress") -> "NGINX: ${key.substringAfter("nginx.ingress.kubernetes.io/")}"
                        key.startsWith("alb.ingress") -> "ALB: ${key.substringAfter("alb.ingress.kubernetes.io/")}"
                        key.startsWith("kubernetes.io/ingress") -> "K8s: ${key.substringAfter("kubernetes.io/ingress.")}"
                        key.startsWith("traefik.") -> "Traefik: ${key.substringAfter("traefik.")}"
                        key.startsWith("haproxy.") -> "HAProxy: ${key.substringAfter("haproxy.")}"
                        key.startsWith("cert-manager.io") -> "Cert-Manager: ${key.substringAfter("cert-manager.io/")}"
                        else -> key
                    }
                    DetailRow(displayKey, value)
                }
            }
        }

        // Labels section
        if (!ing.metadata?.labels.isNullOrEmpty()) {
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Labels:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                ing.metadata?.labels?.forEach { (key, value) ->
                    DetailRow(key, value)
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text("Rules:", style = MaterialTheme.typography.titleMedium)
        ing.spec?.rules?.forEachIndexed { index, rule ->
            Text(
                "  Rule ${index + 1}: Host: ${rule.host ?: "*"}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            rule.http?.paths?.forEach { path ->
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    DetailRow("    Path", path.path ?: "/")
                    DetailRow("    Path Type", path.pathType)
                    DetailRow("    Backend Service", path.backend?.service?.name)
                    DetailRow("    Backend Port", path.backend?.service?.port?.let { it.name ?: it.number?.toString() })
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        if (ing.spec?.rules.isNullOrEmpty()) {
            Text("  <No rules defined>", modifier = Modifier.padding(start = 8.dp))
        }

        // Default backend
        ing.spec?.defaultBackend?.let { backend ->
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Default Backend:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                backend.service?.let { service ->
                    DetailRow("Service Name", service.name)
                    DetailRow("Service Port", service.port?.let { it.name ?: it.number?.toString() })
                }
                backend.resource?.let { resource ->
                    DetailRow("API Group", resource.apiGroup ?: "<core>")
                    DetailRow("Kind", resource.kind)
                    DetailRow("Name", resource.name)
                }
                if (backend.service == null && backend.resource == null) {
                    Text("<No default backend specified>", modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text("TLS:", style = MaterialTheme.typography.titleMedium)
        ing.spec?.tls?.forEach { tls ->
            Column(
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(4.dp)
            ) {
                DetailRow("  Hosts", tls.hosts?.joinToString(", "))
                DetailRow("  Secret Name", tls.secretName)
            }
        }
        if (ing.spec?.tls.isNullOrEmpty()) {
            Text("  <No TLS defined>", modifier = Modifier.padding(start = 8.dp))
        }

        // Status section
        if (!ing.status?.loadBalancer?.ingress.isNullOrEmpty()) {
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Load Balancer Status:", style = MaterialTheme.typography.titleMedium)
            ing.status?.loadBalancer?.ingress?.forEachIndexed { index, ingress ->
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        .padding(4.dp)
                ) {
                    DetailRow("  IP", ingress.ip)
                    DetailRow("  Hostname", ingress.hostname)

                    // Ports (in newer API versions)
                    if (!ingress.ports.isNullOrEmpty()) {
                        Text(
                            "  Ports:", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        ingress.ports.forEach { port ->
                            DetailRow("    ${port.port}", port.protocol ?: "TCP")
                        }
                    }
                }
            }
        }

        // Conditions section (newer API versions may include this)
        val conditions = ing.status?.let {
            try {
                it::class.members.find { member -> member.name == "conditions" }?.call(it) as? List<*>
            } catch (e: Exception) {
                null
            }
        }

        if (!conditions.isNullOrEmpty()) {
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Conditions:", style = MaterialTheme.typography.titleMedium)

            conditions.forEach { condition ->
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        .padding(4.dp)
                ) {
                    // Use safe access or set default values
                    val type = condition?.let { if (it is Map<*, *>) it["type"] as? String else null }
                    val status = condition?.let { if (it is Map<*, *>) it["status"] as? String else null }
                    val reason = condition?.let { if (it is Map<*, *>) it["reason"] as? String else null }
                    val message = condition?.let { if (it is Map<*, *>) it["message"] as? String else null }
                    val lastTransitionTime =
                        condition?.let { if (it is Map<*, *>) it["lastTransitionTime"] as? String else null }
                    val observedGeneration =
                        condition?.let { if (it is Map<*, *>) it["observedGeneration"]?.toString() else null }

                    DetailRow("Type", type)
                    DetailRow("Status", status)
                    DetailRow("Reason", reason)
                    DetailRow("Message", message)
                    DetailRow("Last Transition", formatAge(lastTransitionTime))
                    DetailRow("Observed Generation", observedGeneration)
                }
            }
        }

        // Owner References
        if (!ing.metadata?.ownerReferences.isNullOrEmpty()) {
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Owner References:", style = MaterialTheme.typography.titleMedium)

            ing.metadata?.ownerReferences?.forEach { owner ->
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        .padding(4.dp)
                ) {
                    DetailRow("Kind", owner.kind)
                    DetailRow("Name", owner.name)
                    DetailRow("UID", owner.uid)
                    DetailRow("Controller", owner.controller?.toString() ?: "false")
                }
            }
        }

        // Additional information: Finalizers
        if (!ing.metadata?.finalizers.isNullOrEmpty()) {
            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Finalizers:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                ing.metadata?.finalizers?.forEach { finalizer ->
                    DetailRow("Finalizer", finalizer)
                }
            }
        }

        // Additional Metadata
        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text("Additional Metadata:", style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            DetailRow("UID", ing.metadata?.uid)
            DetailRow("Resource Version", ing.metadata?.resourceVersion)
            DetailRow("Generation", ing.metadata?.generation?.toString())
        }
    }
}

@Composable
fun ResourceDetailPanel(
    resource: Any?,
    resourceType: String?,
    onClose: () -> Unit,
    onShowLogsRequest: (namespace: String, podName: String, containerName: String) -> Unit // Додано callback
) {
    if (resource == null || resourceType == null) return

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // --- Верхня панель ---
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back"); Spacer(
                Modifier.width(4.dp)
            ); Text("Back")
            }
            Spacer(Modifier.weight(1f))
            val name = if (resource is HasMetadata) resource.metadata?.name else "Details"
            Text(
                text = "$resourceType: $name",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
        }
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        // ---

        // --- Уміст деталей ---
        Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                // --- Виклик відповідного DetailsView ---
                when (resourceType) {
                    // ВАЖЛИВО: Передаємо onShowLogsRequest в PodDetailsView
                    "Pods" -> if (resource is Pod) PodDetailsView(
                        pod = resource,
                        onShowLogsRequest = { containerName ->
                            (resource as? HasMetadata)?.metadata?.let { meta ->
                                onShowLogsRequest(
                                    meta.namespace,
                                    meta.name,
                                    containerName
                                )
                            } ?: logger.error("Metadata is null for Pod.")
                        }) else Text("Invalid Pod data")

                    "Namespaces" -> if (resource is Namespace) NamespaceDetailsView(ns = resource) else Text("Invalid Namespace data")
                    "Nodes" -> if (resource is Node) NodeDetailsView(node = resource) else Text("Invalid Node data")
                    "Deployments" -> if (resource is Deployment) DeploymentDetailsView(dep = resource) else Text("Invalid Deployment data")
                    "Services" -> if (resource is Service) ServiceDetailsView(svc = resource) else Text("Invalid Service data")
                    "Secrets" -> if (resource is Secret) SecretDetailsView(secret = resource) else Text("Invalid Secret data")
                    "ConfigMaps" -> if (resource is ConfigMap) ConfigMapDetailsView(cm = resource) else Text("Invalid ConfigMap data")
                    "PersistentVolumes" -> if (resource is PersistentVolume) PVDetailsView(pv = resource) else Text("Invalid PV data")
                    "PersistentVolumeClaims" -> if (resource is PersistentVolumeClaim) PVCDetailsView(pvc = resource) else Text(
                        "Invalid PVC data"
                    )

                    "Ingresses" -> if (resource is Ingress) IngressDetailsView(ing = resource) else Text("Invalid Ingress data")
                    // TODO: Додати кейси для всіх інших типів ресурсів (StatefulSet, DaemonSet, Role, etc.)
                    else -> {
                        Text("Simple detail view for '$resourceType'")
                        if (resource is HasMetadata) {
                            Spacer(Modifier.height(16.dp))
                            BasicMetadataDetails(resource)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogViewerPanel(
    namespace: String,
    podName: String,
    containerName: String,
    client: KubernetesClient?,
    onClose: () -> Unit
) {
    val logState = remember { mutableStateOf("Завантаження логів...") }
    val scrollState = rememberScrollState()
    val followLogs = remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    var isLogLoading by remember { mutableStateOf(false) }
    // Fix the type to be kotlinx.coroutines.Job
    var logJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Add debug state to see if logs are actually being received
    var debugCounter by remember { mutableStateOf(0) }

    // Define the extension function at the top level within the composable
    fun startLogPolling(
        scope: CoroutineScope,
        namespace: String,
        podName: String,
        containerName: String,
        client: KubernetesClient?,
        logState: MutableState<String>,
        scrollState: ScrollState,
        followLogs: MutableState<Boolean>
    ) {
        logJob?.cancel()
        var lastTimestamp = System.currentTimeMillis()

        logJob = scope.launch {
            logger.info("Starting log polling for $namespace/$podName/$containerName")
            while (isActive && followLogs.value) {
                try {
                    delay(1000) // Poll every second

                    // Отримуємо тільки нові логи з моменту останнього запиту
                    // Convert the Long to Int for sinceSeconds
                    val sinceSeconds = ((System.currentTimeMillis() - lastTimestamp) / 1000 + 1).toInt()
                    lastTimestamp = System.currentTimeMillis()

                    // Debug log to verify that the coroutine is running
                    logger.debug("Polling logs - iteration ${++debugCounter}")

                    // Створюємо watchLog без sinceTime - просто слідкуємо за новими логами
                    // Робимо окремий запит для отримання тільки нових логів
                    val newLogs = withContext(Dispatchers.IO) {
                        val logs = client?.pods()?.inNamespace(namespace)
                            ?.withName(podName)
                            ?.inContainer(containerName)
                            ?.sinceSeconds(sinceSeconds)
                            ?.tailingLines(100)
                            ?.withPrettyOutput()
                            ?.log

                        // Debug log to verify if we're getting logs from Kubernetes
                        if (logs != null && logs.isNotEmpty()) {
                            logger.info("Retrieved new logs - length: ${logs.length} chars")
                        }

                        logs
                    } ?: ""

                    // Пропускаємо початкові логи, що дублюються з тими, які ми вже отримали
                    if (newLogs.isNotEmpty()) {
                        // Use withContext(Dispatchers.Main.immediate) to ensure UI updates immediately
                        withContext(Dispatchers.Main.immediate) {
                            if (isActive) {
                                // Explicitly show that we're appending logs
                                val currentText = logState.value
                                // Додаємо нові логи до існуючих
                                val textToAppend =
                                    if (currentText.endsWith("\n") || currentText.isEmpty()) newLogs else "\n$newLogs"
                                val separator =
                                    "--------------------------------------------------------------------------------------------\n"
                                val newText = currentText + separator + textToAppend

                                // Debug log to track text appending
                                logger.info("Appending logs - current: ${currentText.length} chars, new: ${newText.length} chars")

                                // Update the state with new text
                                logState.value = newText

                                // Force UI refresh by incrementing debug counter
                                debugCounter++

                                // Auto-scroll with a slight delay
                                // Автоматична прокрутка вниз
                                if (followLogs.value) {
                                    launch {
                                        delay(50)
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        logger.warn("Error polling logs: ${e.message}", e)
                        // Не зупиняємо опитування при помилці, продовжуємо спроби
                    }
                }
            }
            logger.info("Log polling stopped for $namespace/$podName/$containerName")
        }
    }

    DisposableEffect(namespace, podName, containerName) {
        logger.info("LogViewerPanel DisposableEffect: Starting for $namespace/$podName [$containerName]")
        isLogLoading = true
        // Clear log state and start with a clear indicator
        logState.value = "Loading last $LOG_LINES_TO_TAIL lines...\n"

        // Завантаження початкових логів
        val initialLogJob = coroutineScope.launch {
            try {
                logger.info("Fetching initial logs...")
                // Спочатку отримуємо тільки останні N рядків
                // 1. Отримання початкових логів
                val initialLogs = withContext(Dispatchers.IO) {
                    client?.pods()?.inNamespace(namespace)
                        ?.withName(podName)
                        ?.inContainer(containerName)
                        ?.tailingLines(LOG_LINES_TO_TAIL)
                        ?.withPrettyOutput()
                        ?.log
                } ?: "Failed to load logs."

                logger.info("Initial logs fetched: ${initialLogs.length} characters")

                withContext(Dispatchers.Main.immediate) {
                    // Explicitly clear and set instead of just replacing
                    logState.value = "=== Log start ===\n$initialLogs"
                    delay(100)
                    scrollState.animateScrollTo(scrollState.maxValue)
                    isLogLoading = false

                    // Force a UI refresh
                    debugCounter++
                }

                // 2. Слідкування за появою нових рядків логів
                // Запускаємо окремий процес для отримання нових логів
                if (followLogs.value) {
                    // Fix: Call the regular function instead of an extension function
                    startLogPolling(
                        coroutineScope,
                        namespace,
                        podName,
                        containerName,
                        client,
                        logState,
                        scrollState,
                        followLogs
                    )
                }

            } catch (e: Exception) {
                logger.error("Error loading initial logs: ${e.message}", e)
                withContext(Dispatchers.Main.immediate) {
                    logState.value = "Error loading logs: ${e.message}"
                    isLogLoading = false
                }
            }
        }

        onDispose {
            logger.info("LogViewerPanel DisposableEffect: Stopping for $namespace/$podName [$containerName]")
            initialLogJob.cancel()
            logJob?.cancel()
            logJob = null
        }
    }

    // Обробник зміни стану "Слідкувати"
    LaunchedEffect(followLogs.value) {
        if (followLogs.value && logJob == null) {
            // Fix: Call the regular function with the scope as a parameter
            startLogPolling(
                coroutineScope, namespace, podName, containerName,
                client, logState, scrollState, followLogs
            )
        } else if (!followLogs.value) {
            logJob?.cancel()
            logJob = null
        }
    }

    // UI code
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                Spacer(Modifier.width(4.dp))
                Text("Назад")
            }
            Text(
                text = "Logs: $namespace/$podName [$containerName] (${if (debugCounter > 0) "Active" else "Inactive"})",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = followLogs.value, onCheckedChange = { followLogs.value = it })
                Text("Слідкувати", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(
            modifier = Modifier
                .weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {

            if (isLogLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // Add a debug message to indicate if no logs are being displayed
            if (logState.value.isEmpty() || logState.value == "Завантаження логів..." || logState.value == "Loading last $LOG_LINES_TO_TAIL lines...\n") {
                Text(
                    "No logs to display yet (poll count: $debugCounter)",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(modifier = Modifier.fillMaxSize()) {
                // Use a more explicit text container to ensure visibility
                Box(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
                    Text(
                        text = logState.value,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(8.dp)
                    )
                }
                VerticalScrollbar(
                    modifier = Modifier.fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) // Для ExposedDropdownMenuBox
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
    var podsList by remember { mutableStateOf<List<Pod>>(emptyList()) }
    var deploymentsList by remember { mutableStateOf<List<Deployment>>(emptyList()) }
    var statefulSetsList by remember { mutableStateOf<List<StatefulSet>>(emptyList()) }
    var daemonSetsList by remember { mutableStateOf<List<DaemonSet>>(emptyList()) }
    var replicaSetsList by remember { mutableStateOf<List<ReplicaSet>>(emptyList()) }
    var jobsList by remember { mutableStateOf<List<io.fabric8.kubernetes.api.model.batch.v1.Job>>(emptyList()) }
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

    // --- Функція для очищення всіх списків ресурсів ---
    fun clearResourceLists() {
        namespacesList = emptyList(); nodesList = emptyList(); podsList = emptyList(); deploymentsList =
            emptyList(); statefulSetsList = emptyList(); daemonSetsList = emptyList(); replicaSetsList =
            emptyList(); jobsList = emptyList(); cronJobsList = emptyList(); servicesList = emptyList(); ingressesList =
            emptyList(); pvsList = emptyList(); pvcsList = emptyList(); storageClassesList =
            emptyList(); configMapsList = emptyList(); secretsList = emptyList(); serviceAccountsList =
            emptyList(); rolesList = emptyList(); roleBindingsList = emptyList(); clusterRolesList =
            emptyList(); clusterRoleBindingsList = emptyList();
    }
    // ---

    // --- Завантаження контекстів через Config.autoConfigure(null).contexts ---
    LaunchedEffect(Unit) {
        logger.info("LaunchedEffect: Starting context load via Config.autoConfigure(null)...")
        isLoading = true; connectionStatus = "Завантаження Kubeconfig...";
        var loadError: Exception? = null
        var loadedContextNames: List<String>
        try {
            loadedContextNames = kotlinx.coroutines.withContext(Dispatchers.IO) {
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
                allNamespaces = (listOf(ALL_NAMESPACES_OPTION) + loadedNs.mapNotNull { it.metadata?.name }).sortedWith(
                    compareBy { it != ALL_NAMESPACES_OPTION } // "<All>" завжди зверху
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
        AlertDialog( // M3 AlertDialog
            onDismissRequest = { showErrorDialog.value = false },
            title = { Text("Помилка Підключення") }, // M3 Text
            text = { Text(dialogErrorMessage.value) }, // M3 Text
            confirmButton = { Button(onClick = { showErrorDialog.value = false }) { Text("OK") } } // M3 Button, M3 Text
        )
    }
    // ---

    MaterialTheme { // M3 Theme
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { // M3 Surface
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    // --- Ліва панель ---
                    Column(modifier = Modifier.fillMaxHeight().width(300.dp).padding(16.dp)) {
                        Text(
                            "Контексти Kubernetes:",
                            style = MaterialTheme.typography.titleMedium
                        ); Spacer(modifier = Modifier.height(8.dp)) // M3 Text + Typography
                        Box(
                            modifier = Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) { // M3 колір
                            if (isLoading && contexts.isEmpty()) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            } // M3 Indicator
                            else if (!isLoading && contexts.isEmpty()) {
                                Text(
                                    errorMessage ?: "Контексти не знайдено",
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } // M3 Text
                            else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(contexts) { contextName ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !isLoading) {
                                                    if (selectedContext != contextName) {
                                                        logger.info("Клікнуто на контекст: $contextName. Запуск connectWithRetries...")
                                                        coroutineScope.launch {
                                                            isLoading = true
                                                            connectionStatus = "Підключення до '$contextName'..."
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
                                                                    "Підключено до: $contextName (v$serverVersion)"
                                                                errorMessage = null
                                                                logger.info("UI State updated on Success for $contextName")
                                                            }
                                                                .onFailure { error ->
                                                                    connectionStatus =
                                                                        "Помилка підключення до '$contextName'"
                                                                    errorMessage =
                                                                        error.localizedMessage ?: "Невід. помилка"
                                                                    logger.info("Setting up error dialog for: $contextName. Error: ${error.message}")
                                                                    dialogErrorMessage.value =
                                                                        "Не вдалося підключитися до '$contextName' після $MAX_CONNECT_RETRIES спроб:\n${error.message}"
                                                                    showErrorDialog.value = true
                                                                    activeClient = null
                                                                    selectedContext = null
                                                                }
                                                            logger.info("Спроба підключення до '$contextName' завершена (результат оброблено).")
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            // Додаємо іконку
                                            Icon(
                                                imageVector = SimpleIcons.Kubernetes, // Ви можете змінити цю іконку на іншу
                                                contentDescription = "Kubernetes Context",
                                                tint = if (contextName == selectedContext)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(24.dp).padding(end = 8.dp)
                                            )

                                            // Текст після іконки
                                            Text(
                                                text = formatContextNameForDisplay(contextName),
                                                fontSize = 14.sp,
                                                color = if (contextName == selectedContext)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        } // Кінець Box списку
                        Spacer(modifier = Modifier.height(16.dp)); Text(
                        "Ресурси Кластера:",
                        style = MaterialTheme.typography.titleMedium
                    ); Spacer(modifier = Modifier.height(8.dp)) // M3 Text
                        Box(
                            modifier = Modifier.weight(2f).border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) { // M3 колір
                            ResourceTreeView(
                                rootIds = resourceTreeData[""] ?: emptyList(),
                                expandedNodes = expandedNodes,
                                onNodeClick = { nodeId, isLeaf ->
                                    logger.info("Клікнуто на вузол: $nodeId, Це листок: $isLeaf")
                                    if (isLeaf) {
                                        if (activeClient != null && !isLoading) {
                                            // Скидаємо показ деталей/логів при виборі нового типу ресурсу
                                            detailedResource = null; detailedResourceType = null; showLogViewer.value =
                                                false; logViewerParams.value = null;
                                            selectedResourceType = nodeId; resourceLoadError =
                                                null; clearResourceLists()
                                            connectionStatus = "Завантаження $nodeId..."; isLoading = true
                                            coroutineScope.launch {
                                                var loadOk = false;
                                                var errorMsg: String? = null
                                                val currentFilter =
                                                    selectedNamespaceFilter // Беремо поточне значення фільтра

                                                // Визначаємо, чи ресурс неймспейсний, щоб знати, чи передавати фільтр
                                                val namespaceToUse =
                                                    if (namespacedResources.contains(nodeId)) currentFilter else null
                                                // --- ВИКЛИК ВІДПОВІДНОЇ ФУНКЦІЇ ЗАВАНТАЖЕННЯ ---
                                                when (nodeId) {
                                                    "Namespaces" -> loadNamespacesFabric8(activeClient).onSuccess {
                                                        namespacesList = it; loadOk = true
                                                    }.onFailure { errorMsg = it.message }

                                                    "Nodes" -> loadNodesFabric8(activeClient).onSuccess {
                                                        nodesList = it; loadOk = true
                                                    }.onFailure { errorMsg = it.message }

                                                    "Pods" -> loadPodsFabric8(
                                                        activeClient,
                                                        namespaceToUse
                                                    ).onSuccess { podsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "Deployments" -> loadDeploymentsFabric8(
                                                        activeClient,
                                                        namespaceToUse
                                                    ).onSuccess { deploymentsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "StatefulSets" -> loadStatefulSetsFabric8(
                                                        activeClient,
                                                        namespaceToUse
                                                    ).onSuccess { statefulSetsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "DaemonSets" -> loadDaemonSetsFabric8(
                                                        activeClient,
                                                        namespaceToUse
                                                    ).onSuccess { daemonSetsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "ReplicaSets" -> loadReplicaSetsFabric8(
                                                        activeClient,
                                                        namespaceToUse
                                                    ).onSuccess { replicaSetsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "Jobs" -> loadJobsFabric8(
                                                        activeClient,
                                                        namespaceToUse
                                                    ).onSuccess { jobsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "CronJobs" -> loadCronJobsFabric8(
                                                        activeClient,
                                                        namespaceToUse
                                                    ).onSuccess { cronJobsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "Services" -> loadServicesFabric8(
                                                        activeClient,
                                                        namespaceToUse
                                                    ).onSuccess { servicesList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "Ingresses" -> loadIngressesFabric8(
                                                        activeClient,
                                                        namespaceToUse
                                                    ).onSuccess { ingressesList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "PersistentVolumes" -> loadPVsFabric8(activeClient).onSuccess {
                                                        pvsList = it; loadOk = true
                                                    }.onFailure { errorMsg = it.message }

                                                    "PersistentVolumeClaims" -> loadPVCsFabric8(
                                                        activeClient,
                                                        namespaceToUse
                                                    ).onSuccess { pvcsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "StorageClasses" -> loadStorageClassesFabric8(activeClient).onSuccess {
                                                        storageClassesList = it; loadOk = true
                                                    }.onFailure { errorMsg = it.message }

                                                    "ConfigMaps" -> loadConfigMapsFabric8(
                                                        activeClient,
                                                        namespaceToUse
                                                    ).onSuccess { configMapsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "Secrets" -> loadSecretsFabric8(
                                                        activeClient,
                                                        namespaceToUse
                                                    ).onSuccess { secretsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "ServiceAccounts" -> loadServiceAccountsFabric8(
                                                        activeClient,
                                                        namespaceToUse
                                                    ).onSuccess { serviceAccountsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "Roles" -> loadRolesFabric8(
                                                        activeClient,
                                                        namespaceToUse
                                                    ).onSuccess { rolesList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "RoleBindings" -> loadRoleBindingsFabric8(
                                                        activeClient,
                                                        namespaceToUse
                                                    ).onSuccess { roleBindingsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "ClusterRoles" -> loadClusterRolesFabric8(activeClient).onSuccess {
                                                        clusterRolesList = it; loadOk = true
                                                    }.onFailure { errorMsg = it.message }

                                                    "ClusterRoleBindings" -> loadClusterRoleBindingsFabric8(activeClient).onSuccess {
                                                        clusterRoleBindingsList = it; loadOk = true
                                                    }.onFailure { errorMsg = it.message }

                                                    else -> {
                                                        logger.warn("Обробник '$nodeId' не реалізовано."); loadOk =
                                                            false; errorMsg = "Не реалізовано"
                                                    }
                                                }
                                                // Оновлюємо статус після завершення
                                                if (loadOk) {
                                                    connectionStatus =
                                                        "Завантажено $nodeId ${if (namespaceToUse != null && namespaceToUse != ALL_NAMESPACES_OPTION) " (ns: $namespaceToUse)" else ""}"
                                                } else {
                                                    resourceLoadError = "Помилка $nodeId: $errorMsg"; connectionStatus =
                                                        "Помилка $nodeId"
                                                }
                                                isLoading = false
                                            }
                                        } else if (activeClient == null) {
                                            logger.warn("Немає підключення."); connectionStatus =
                                                "Підключіться до кластера!"; selectedResourceType = null
                                        }
                                    } else {
                                        expandedNodes[nodeId] = !(expandedNodes[nodeId] ?: false)
                                    }
                                })
                        }
                    } // Кінець лівої панелі

                    Divider(
                        modifier = Modifier.fillMaxHeight().width(1.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    ) // M3 Divider

                    // --- Права панель (АБО Таблиця АБО Деталі АБО Логи) ---
                    Column(
                        modifier = Modifier.fillMaxHeight().weight(1f).padding(start = 16.dp, end = 16.dp, top = 16.dp)
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
                        // Заголовок для таблиці та логів (для деталей він усередині ResourceDetailPanel)
                        val headerTitle = when {
                            currentView == "logs" -> "Logs: ${paramsForLogs?.second ?: "-"} [${paramsForLogs?.third ?: "-"}]"
                            currentView == "table" && currentResourceType != null && activeClient != null && resourceLoadError == null && errorMessage == null -> "$currentResourceType у $selectedContext"
                            else -> null
                        }

                        // --- ДОДАНО ФІЛЬТР НЕЙМСПЕЙСІВ (якщо є клієнт і це не деталі/логи) ---
                        if (currentView == "table" && activeClient != null) {
                            val isFilterEnabled =
                                namespacedResources.contains(selectedResourceType) // Активуємо тільки для неймспейсних ресурсів
                            ExposedDropdownMenuBox(
                                expanded = isNamespaceDropdownExpanded,
                                onExpandedChange = { if (isFilterEnabled) isNamespaceDropdownExpanded = it },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                            ) {
                                TextField( // M3 TextField
                                    value = selectedNamespaceFilter,
                                    onValueChange = {}, // ReadOnly
                                    readOnly = true,
                                    //label = { Text("Namespace Filter") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isNamespaceDropdownExpanded) },
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                        .fillMaxWidth(), // menuAnchor для M3
                                    enabled = isFilterEnabled, // Вимикаємо для кластерних ресурсів
                                    colors = ExposedDropdownMenuDefaults.textFieldColors() // M3 кольори
                                )
                                ExposedDropdownMenu(
                                    expanded = isNamespaceDropdownExpanded,
                                    onDismissRequest = { isNamespaceDropdownExpanded = false }
                                ) {
                                    allNamespaces.forEach { nsName ->
                                        DropdownMenuItem(
                                            text = { Text(nsName) },
                                            onClick = {
                                                if (selectedNamespaceFilter != nsName) {
                                                    selectedNamespaceFilter = nsName
                                                    isNamespaceDropdownExpanded = false
                                                    // Перезавантаження даних спрацює через LaunchedEffect в onNodeClick,
                                                    // але нам треба його "тригернути", якщо тип ресурсу вже вибрано.
                                                    // Найпростіше - знову викликати логіку завантаження поточного ресурсу
                                                    if (selectedResourceType != null) {
                                                        // Повторно викликаємо ту саму логіку, що й в onNodeClick
                                                        resourceLoadError = null; clearResourceLists()
                                                        connectionStatus =
                                                            "Завантаження $selectedResourceType (фільтр)..."; isLoading =
                                                            true
                                                        coroutineScope.launch {
                                                            var loadOk = false;
                                                            var errorMsg: String? = null
                                                            val namespaceToUse =
                                                                if (namespacedResources.contains(selectedResourceType)) selectedNamespaceFilter else null
                                                            when (selectedResourceType) { // Повторний виклик з новим фільтром
                                                                "Pods" -> loadPodsFabric8(
                                                                    activeClient,
                                                                    namespaceToUse
                                                                ).onSuccess { podsList = it; loadOk = true }
                                                                    .onFailure { errorMsg = it.message }

                                                                "Deployments" -> loadDeploymentsFabric8(
                                                                    activeClient,
                                                                    namespaceToUse
                                                                ).onSuccess { deploymentsList = it; loadOk = true }
                                                                    .onFailure { errorMsg = it.message }
                                                                // ... додати ВСІ неймспейсні ресурси ...
                                                                "Namespaces" -> {
                                                                    loadNamespacesFabric8(activeClient).onSuccess {
                                                                        namespacesList = it; loadOk = true
                                                                    }.onFailure { errorMsg = it.message }
                                                                } // Namespaces не фільтруємо
                                                                // ... решта ...
                                                                else -> {
                                                                    loadOk = false; errorMsg =
                                                                        "Фільтр не застосовується"
                                                                }
                                                            }
                                                            if (loadOk) {
                                                                connectionStatus =
                                                                    "Завантажено $selectedResourceType ${if (namespaceToUse != null && namespaceToUse != ALL_NAMESPACES_OPTION) " (ns: $namespaceToUse)" else ""}"
                                                            } else {
                                                                resourceLoadError =
                                                                    "Помилка $selectedResourceType: $errorMsg"; connectionStatus =
                                                                    "Помилка $selectedResourceType"
                                                            }
                                                            isLoading = false
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(4.dp))
                        }
                        // --- КІНЕЦЬ ФІЛЬТРА ---

                        if (headerTitle != null && currentView != "details") {
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) // M3 Text
                            Divider(color = MaterialTheme.colorScheme.outlineVariant) // M3 Divider
                        } else if (currentView == "table" || currentView == "logs") { // Додаємо відступ, якщо це не панель деталей
                            Spacer(modifier = Modifier.height(48.dp)) // Висота імітує заголовок
                        }

                        // --- Основний уміст правої панелі ---
                        Box(
                            modifier = Modifier.weight(1f)
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
                                            "Завантаження параметрів логів...",
                                            modifier = Modifier.align(Alignment.Center)
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
                                        }
                                    )
                                }

                                "table" -> {
                                    // --- Таблиця або Статус/Помилка ---
                                    val currentErrorMessageForPanel = resourceLoadError ?: errorMessage
                                    val currentClientForPanel = activeClient
                                    when {
                                        isLoading -> {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.align(Alignment.Center)
                                            ) {
                                                CircularProgressIndicator(); Spacer(modifier = Modifier.height(8.dp)); Text(
                                                connectionStatus
                                            )
                                            }
                                        } // M3 Indicator, M3 Text
                                        currentErrorMessageForPanel != null -> {
                                            androidx.compose.material3.Text(
                                                text = currentErrorMessageForPanel,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.align(Alignment.Center)
                                            )
                                        } // Явний M3 Text
                                        currentClientForPanel != null && currentResourceType != null -> {
                                            // Отримуємо список та заголовки
                                            val itemsToShow: List<HasMetadata> = remember(
                                                currentResourceType,
                                                namespacesList,
                                                nodesList,
                                                podsList,
                                                deploymentsList,
                                                statefulSetsList,
                                                daemonSetsList,
                                                replicaSetsList,
                                                jobsList,
                                                cronJobsList,
                                                servicesList,
                                                ingressesList,
                                                pvsList,
                                                pvcsList,
                                                storageClassesList,
                                                configMapsList,
                                                secretsList,
                                                serviceAccountsList,
                                                rolesList,
                                                roleBindingsList,
                                                clusterRolesList,
                                                clusterRoleBindingsList
                                            ) {
                                                when (currentResourceType) {
                                                    "Namespaces" -> namespacesList; "Nodes" -> nodesList; "Pods" -> podsList; "Deployments" -> deploymentsList; "StatefulSets" -> statefulSetsList; "DaemonSets" -> daemonSetsList; "ReplicaSets" -> replicaSetsList; "Jobs" -> jobsList; "CronJobs" -> cronJobsList; "Services" -> servicesList; "Ingresses" -> ingressesList; "PersistentVolumes" -> pvsList; "PersistentVolumeClaims" -> pvcsList; "StorageClasses" -> storageClassesList; "ConfigMaps" -> configMapsList; "Secrets" -> secretsList; "ServiceAccounts" -> serviceAccountsList; "Roles" -> rolesList; "RoleBindings" -> roleBindingsList; "ClusterRoles" -> clusterRolesList; "ClusterRoleBindings" -> clusterRoleBindingsList
                                                    else -> emptyList()
                                                }
                                            }
                                            val headers =
                                                remember(currentResourceType) { getHeadersForType(currentResourceType) }

                                            if (itemsToShow.isEmpty() && !isLoading) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) { Text("Немає ресурсів типу '$currentResourceType'") }
                                            } // M3 Text
                                            else if (headers.isNotEmpty()) {
                                                // --- Ручна таблиця з LazyColumn (M3 компоненти) ---
                                                Column(modifier = Modifier.fillMaxSize()) {
                                                    // Calculate column widths based on headers and data
                                                    val columnWidths = calculateColumnWidths(
                                                        headers = headers,
                                                        items = itemsToShow,
                                                        resourceType = currentResourceType
                                                    )

                                                    // Use the calculated widths
                                                    Box(modifier = Modifier.fillMaxWidth()) {
                                                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                                            Column {
                                                                KubeTableHeaderRow(
                                                                    headers = headers,
                                                                    columnWidths = columnWidths
                                                                )
                                                                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                                                                LazyColumn(
                                                                    modifier = Modifier.weight(1f, fill = false)
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
                                                                            }
                                                                        )
                                                                        Divider(
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
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) { Text("Немає колонок для '$currentResourceType'") }
                                            } // M3 Text
                                        }
                                        // --- Стани за замовчуванням (M3 Text) ---
                                        activeClient != null -> {
                                            androidx.compose.material3.Text(
                                                "Підключено до $selectedContext.\nВиберіть тип ресурсу.",
                                                modifier = Modifier.align(Alignment.Center)
                                            )
                                        }

                                        else -> {
                                            androidx.compose.material3.Text(
                                                errorMessage ?: "Виберіть контекст.",
                                                modifier = Modifier.align(Alignment.Center)
                                            )
                                        }
                                    }
                                } // Кінець table case
                            } // Кінець when(currentView)
                        } // Кінець Box вмісту
                    } // Кінець Column правої панелі
                } // Кінець Row
                // --- Статус-бар ---
                Divider(color = MaterialTheme.colorScheme.outlineVariant) // M3 Divider
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Text(
                        text = connectionStatus,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall
                    ); if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } // Явний M3 Text, M3 Indicator
                }
                // ---------------
            } // Кінець Column
        } // Кінець Surface M3
    } // Кінець MaterialTheme M3
}

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
            !isLeaf -> Icons.Filled.Place
            else -> FeatherIcons.Circle
        }
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp)) // M3 Icon
        Spacer(modifier = Modifier.width(4.dp))
        Text(nodeId, style = MaterialTheme.typography.bodyMedium) // M3 Text
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

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Kotlin Kube Manager") { App() }
}
