// Coroutines
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
//import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import compose.icons.FeatherIcons
import compose.icons.SimpleIcons
import compose.icons.feathericons.*
import compose.icons.simpleicons.*
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apps.DaemonSet
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.kubernetes.api.model.batch.v1.CronJob
import io.fabric8.kubernetes.api.model.networking.v1.*
import io.fabric8.kubernetes.api.model.rbac.ClusterRole
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding
import io.fabric8.kubernetes.api.model.rbac.Role
import io.fabric8.kubernetes.api.model.rbac.RoleBinding
import io.fabric8.kubernetes.api.model.storage.StorageClass
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.IOException
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableMap
import kotlin.collections.Set
import kotlin.collections.contains
import kotlin.collections.emptyList
import kotlin.collections.firstOrNull
import kotlin.collections.forEach
import kotlin.collections.forEachIndexed
import kotlin.collections.isNotEmpty
import kotlin.collections.joinToString
import kotlin.collections.listOf
import kotlin.collections.mapNotNull
import kotlin.collections.mapOf
import kotlin.collections.minus
import kotlin.collections.mutableListOf
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.collections.setOf
import kotlin.collections.sorted
import kotlin.collections.sortedBy
import kotlin.collections.sortedWith
import androidx.compose.material3.HorizontalDivider as Divider

// TODO: check NS filter for all resources (e.g. Pods)

// --- Дані для дерева ресурсів ---
val resourceTreeData: Map<String, List<String>> = mapOf(
    "" to listOf("Cluster", "Workloads", "Network", "Storage", "Configuration", "Access Control", "CustomResources"),
    "Cluster" to listOf("Namespaces", "Nodes", "Events"),
    "Workloads" to listOf("Pods", "Deployments", "StatefulSets", "DaemonSets", "ReplicaSets", "Jobs", "CronJobs"),
    "Network" to listOf("Services", "Ingresses", "Endpoints", "NetworkPolicies"),
    "Storage" to listOf("PersistentVolumes", "PersistentVolumeClaims", "StorageClasses"),
    "Configuration" to listOf("ConfigMaps", "Secrets"),
    "Access Control" to listOf("ServiceAccounts", "Roles", "RoleBindings", "ClusterRoles", "ClusterRoleBindings"),
    "CustomResources" to listOf("CRDs")
)
val resourceLeafNodes: Set<String> = setOf(
    "Namespaces",
    "Nodes",
    "Events",
    "Pods",
    "Deployments",
    "StatefulSets",
    "DaemonSets",
    "ReplicaSets",
    "Jobs",
    "CronJobs",
    "Services",
    "Ingresses",
    "Endpoints",
    "NetworkPolicies",
    "PersistentVolumes",
    "PersistentVolumeClaims",
    "StorageClasses",
    "ConfigMaps",
    "Secrets",
    "ServiceAccounts",
    "Roles",
    "RoleBindings",
    "ClusterRoles",
    "ClusterRoleBindings",
    "CRDs"

)

// Мапа для визначення, чи є ресурс неймспейсним (спрощено)
val nonNSResources: Set<String> =
    resourceLeafNodes - setOf("Nodes", "PersistentVolumes", "StorageClasses", "ClusterRoles", "ClusterRoleBindings", "CRDs")

// Логер
val logger = LoggerFactory.getLogger("KKM")

const val MAX_CONNECT_RETRIES = 1
//const val RETRY_DELAY_MS = 1000L
const val CONNECTION_TIMEOUT_MS = 5000
const val REQUEST_TIMEOUT_MS = 15000
const val LOG_LINES_TO_TAIL = 50
const val ALL_NAMESPACES_OPTION = "<All Namespaces>"

var ICON_UP = FeatherIcons.ArrowUp
var ICON_DOWN = FeatherIcons.ArrowDown
var ICON_RIGHT = FeatherIcons.ArrowRight
var ICON_LEFT = FeatherIcons.ArrowLeft
var ICON_LOGS = FeatherIcons.List
var ICON_HELP = FeatherIcons.HelpCircle
var ICON_SUCCESS = FeatherIcons.CheckCircle
var ICON_ERROR = FeatherIcons.XCircle
var ICON_COPY = FeatherIcons.Copy
var ICON_EYE = FeatherIcons.Eye
var ICON_EYEOFF = FeatherIcons.EyeOff
var ICON_CONTEXT = SimpleIcons.Kubernetes
var ICON_RESOURCE = FeatherIcons.Aperture
var ICON_NF = Icons.Filled.Place


fun getHeadersForType(resourceType: String): List<String> {
    return when (resourceType) {
        "Namespaces" -> listOf("Name", "Status", "Age")
        "Nodes" -> listOf("Name", "Status", "Roles", "Version", "Taints", "Age")
        "Events" -> listOf("Namespace", "Name", "Type", "Reason", "Object Type", "Object Name", "Message", "Age")
        "Pods" -> listOf("Namespace", "Name", "Ready", "Status", "Restarts", "Node", "Age")
        "Deployments" -> listOf("Namespace", "Name", "Ready", "Up-to-date", "Available", "Age")
        "StatefulSets" -> listOf("Namespace", "Name", "Ready", "Age")
        "DaemonSets" -> listOf("Namespace", "Name", "Desired", "Current", "Ready", "Up-to-date", "Available", "Age")
        "ReplicaSets" -> listOf("Namespace", "Name", "Desired", "Current", "Ready", "Age")
        "Jobs" -> listOf("Namespace", "Name", "Completions", "Duration", "Age")
        "CronJobs" -> listOf("Namespace", "Name", "Schedule", "Suspend", "Active", "Last Schedule", "Age")
        "Services" -> listOf("Namespace", "Name", "Type", "ClusterIP", "ExternalIP", "Ports", "Age")
        "Ingresses" -> listOf("Namespace", "Name", "Class", "Hosts", "Address", "Ports", "Age")
        "Endpoints" -> listOf("Namespace", "Name", "Endpoints", "Age")
        "NetworkPolicies" -> listOf("Namespace", "Name", "Selector", "Type","Age")
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
        "CRDs" -> listOf("Name", "Group", "Version", "Kind", "Age")
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
                    0 -> resource.metadata?.name ?: na;
                    1 -> formatNodeStatus(resource.status?.conditions);
                    2 -> formatNodeRoles(resource.metadata?.labels);
                    3 -> resource.status?.nodeInfo?.kubeletVersion ?: na;
                    4 -> formatTaints(resource.spec?.taints);
                    5 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "Events" -> if (resource is Event) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na
                    1 -> resource.message ?: na
                    //1 -> resource.metadata?.name ?: na
                    2 -> resource.type ?: na
                    3 -> resource.reason ?: na
                    4 -> resource.involvedObject?.kind ?: na
                    5 -> resource.source?.component ?: na
                    //5 -> resource.involvedObject?.name ?: na
                    //6 -> resource.message ?: na
                    6 -> formatAge(resource.lastTimestamp ?: resource.metadata?.creationTimestamp)
                    else -> ""
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

            "Endpoints" -> if (resource is Endpoints) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na;
                    1 -> resource.metadata?.name ?: na;
                    2 -> {
                        // Збираємо всі адреси (готові та неготові) з усіх підмножин
                        val allAddresses = mutableListOf<String>()

                        resource.subsets?.forEach { subset ->
                            // Додаємо готові адреси
                            subset.addresses?.forEach { address ->
                                val addressText = address.ip ?: "unknown"

                                // Додаємо інформацію про порти, якщо вони доступні
                                val portsInfo = subset.ports?.joinToString(", ") { port ->
                                    "${port.port} ${port.protocol ?: "TCP"}"
                                } ?: ""

                                if (portsInfo.isNotEmpty()) {
                                    allAddresses.add("$addressText:$portsInfo")
                                } else {
                                    allAddresses.add(addressText)
                                }
                            }

                            // Додаємо неготові адреси
                            subset.notReadyAddresses?.forEach { address ->
                                val addressText = "${address.ip ?: "unknown"} (NotReady)"

                                // Додаємо інформацію про порти, якщо вони доступні
                                val portsInfo = subset.ports?.joinToString(", ") { port ->
                                    "${port.port} ${port.protocol ?: "TCP"}"
                                } ?: ""

                                if (portsInfo.isNotEmpty()) {
                                    allAddresses.add("$addressText:$portsInfo")
                                } else {
                                    allAddresses.add(addressText)
                                }
                            }
                        }

                        // Повертаємо список адрес, розділених комами
                        allAddresses.joinToString("; ")
                    }

                    3 -> formatAge(resource.metadata?.creationTimestamp); else -> ""
                }
            } else ""

            "NetworkPolicies" -> if (resource is NetworkPolicy) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na
                    1 -> resource.metadata?.name ?: na
                    2 -> resource.spec?.podSelector?.matchLabels?.entries?.joinToString(", ") { "${it.key}=${it.value}" }
                        ?: "<all pods>"
                    3 -> formatPolicyTypes(resource.spec?.policyTypes)
                    4 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
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

            "CRDs" -> if (resource is CustomResourceDefinition) {
                when (colIndex) {
                    0 -> resource.metadata?.name ?: na
                    1 -> resource.spec?.group ?: na
                    2 -> resource.spec?.versions?.firstOrNull()?.name ?: na
                    3 -> resource.spec?.names?.kind ?: na
                    4 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
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
    client: KubernetesClient?, resourceType: String, namespace: String?, // Додано параметр неймспейсу
    apiCall: (KubernetesClient, String?) -> List<T>? // Лямбда тепер приймає клієнт і неймспейс
): Result<List<T>> {
    if (client == null) return Result.failure(IllegalStateException("Kubernetes client is not initialized"))
    val targetNamespace = if (namespace == ALL_NAMESPACES_OPTION) null else namespace
    val nsLog = targetNamespace ?: "all"
    logger.info("Loading the list $resourceType (Namespace: $nsLog)...")
    return try {
        val items = withContext(Dispatchers.IO) {
            logger.info("[IO] Call API for $resourceType (Namespace: $nsLog)...")
            apiCall(client, targetNamespace) ?: emptyList() // Передаємо неймспейс у лямбду
        }
        logger.info("Loaded ${items.size} $resourceType (Namespace: $nsLog).")
        try {
            @Suppress("UNCHECKED_CAST") val sortedItems = items.sortedBy { (it as? HasMetadata)?.metadata?.name ?: "" }
            Result.success(sortedItems)
        } catch (e: Exception) {
            logger.warn("Failed sort $resourceType: ${e.message}")
            Result.success(items)
        }
    } catch (e: KubernetesClientException) {
        logger.error("KubeExc $resourceType (NS: $nsLog): ${e.message}", e); Result.failure(e)
    } catch (e: Exception) {
        logger.error("Error $resourceType (NS: $nsLog): ${e.message}", e); Result.failure(e)
    }
}


@Composable
fun KubeTableHeaderRow(
    headers: List<String>, columnWidths: List<Int> // Add column widths parameter
) {
    Row(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).fillMaxWidth()
            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        headers.forEachIndexed { index, header ->
            val width = if (index < columnWidths.size) columnWidths[index] else 100
            Text(
                text = header,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(width.dp).padding(horizontal = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun <T : HasMetadata> KubeTableRow(
    item: T, headers: List<String>, resourceType: String, columnWidths: List<Int>, // Add column widths parameter
    onRowClick: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onRowClick(item) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        headers.forEachIndexed { colIndex, _ ->
            val width = if (colIndex < columnWidths.size) columnWidths[colIndex] else 100
            Text(
                text = getCellData(item, colIndex, resourceType),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(width.dp).padding(horizontal = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogViewerPanel(
    namespace: String, podName: String, containerName: String, client: KubernetesClient?, onClose: () -> Unit
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
                        val logs = client?.pods()?.inNamespace(namespace)?.withName(podName)?.inContainer(containerName)
                            ?.sinceSeconds(sinceSeconds)?.tailingLines(100)?.withPrettyOutput()?.log

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
                    client?.pods()?.inNamespace(namespace)?.withName(podName)?.inContainer(containerName)
                        ?.tailingLines(LOG_LINES_TO_TAIL)?.withPrettyOutput()?.log
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
                        coroutineScope, namespace, podName, containerName, client, logState, scrollState, followLogs
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
                coroutineScope, namespace, podName, containerName, client, logState, scrollState, followLogs
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
                Icon(ICON_LEFT, contentDescription = "Back")
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
            Text(
                text = "Logs: $namespace/$podName [$containerName] (${if (debugCounter > 0) "Active" else "Inactive"})",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = followLogs.value, onCheckedChange = { followLogs.value = it })
                Text("Follow", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(
            modifier = Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {

            if (isLogLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // Add a debug message to indicate if no logs are being displayed
            if (logState.value.isEmpty() || logState.value == "Download logs..." || logState.value == "Loading last $LOG_LINES_TO_TAIL lines...\n") {
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
                        text = logState.value, style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface
                        ), modifier = Modifier.padding(8.dp)
                    )
                }
                VerticalScrollbar(
                    modifier = Modifier.fillMaxHeight(), adapter = rememberScrollbarAdapter(scrollState)
                )
            }
        }
    }
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
                    nodeId = nodeId, level = 0, expandedNodes = expandedNodes, onNodeClick = onNodeClick
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

    Row(modifier = Modifier.fillMaxWidth().padding(start = (level * 16).dp).clickable { onNodeClick(nodeId, isLeaf) }
        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val icon = when {
            !isLeaf && children?.isNotEmpty() == true -> if (isExpanded) ICON_DOWN else ICON_RIGHT
            !isLeaf -> ICON_NF
            else -> ICON_RESOURCE
        }
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp)) // M3 Icon
        Spacer(modifier = Modifier.width(4.dp))
        Text(nodeId, style = MaterialTheme.typography.bodyMedium) // M3 Text
    }

    if (!isLeaf && isExpanded && children != null) {
        Column {
            children.sorted().forEach { childId ->
                ResourceTreeNode(
                    nodeId = childId, level = level + 1, expandedNodes = expandedNodes, onNodeClick = onNodeClick
                )
            }
        }
    }
}


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
    var jobsList by remember { mutableStateOf<List<io.fabric8.kubernetes.api.model.batch.v1.Job>>(emptyList()) }
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

    // --- Функція для очищення всіх списків ресурсів ---
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
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { // M3 Surface
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    // --- Ліва панель ---
                    Column(modifier = Modifier.fillMaxHeight().width(300.dp).padding(16.dp)) {
                        Text(
                            "Kubernetes Contexts:", style = MaterialTheme.typography.titleMedium
                        ); Spacer(modifier = Modifier.height(8.dp)) // M3 Text + Typography
                        Box(
                            modifier = Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) { // M3 колір
                            if (isLoading && contexts.isEmpty()) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            } // M3 Indicator
                            else if (!isLoading && contexts.isEmpty()) {
                                Text(
                                    errorMessage ?: "Контексти не знайдено", modifier = Modifier.align(Alignment.Center)
                                )
                            } // M3 Text
                            else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(contexts) { contextName ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth().clickable(enabled = !isLoading) {
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
                                                modifier = Modifier.size(24.dp).padding(end = 8.dp)
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
                        Spacer(modifier = Modifier.height(16.dp)); Text(
                        "Cluster Resources:", style = MaterialTheme.typography.titleMedium
                    ); Spacer(modifier = Modifier.height(8.dp)) // M3 Text
                        Box(
                            modifier = Modifier.weight(2f).border(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                                                var loadOk = false
                                                var errorMsg: String? = null
                                                val currentFilter =
                                                    selectedNamespaceFilter // We take the current filter value

                                                // Визначаємо, чи ресурс неймспейсний, щоб знати, чи передавати фільтр
                                                val namespaceToUse =
                                                    if (nonNSResources.contains(nodeId)) currentFilter else null
                                                // --- ВИКЛИК ВІДПОВІДНОЇ ФУНКЦІЇ ЗАВАНТАЖЕННЯ ---
                                                when (nodeId) {
                                                    "Namespaces" -> loadNamespacesFabric8(activeClient).onSuccess {
                                                        namespacesList = it; loadOk = true
                                                    }.onFailure { errorMsg = it.message }

                                                    "Nodes" -> loadNodesFabric8(activeClient).onSuccess {
                                                        nodesList = it; loadOk = true
                                                    }.onFailure { errorMsg = it.message }

                                                    "Events" -> loadEventsFabric8(activeClient).onSuccess {
                                                        eventsList = it; loadOk = true
                                                    }.onFailure { errorMsg = it.message }

                                                    "Pods" -> loadPodsFabric8(activeClient, namespaceToUse).onSuccess {
                                                        podsList = it; loadOk = true
                                                    }
                                                        .onFailure { errorMsg = it.message }

                                                    "Deployments" -> loadDeploymentsFabric8(
                                                        activeClient, namespaceToUse
                                                    ).onSuccess { deploymentsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "StatefulSets" -> loadStatefulSetsFabric8(
                                                        activeClient, namespaceToUse
                                                    ).onSuccess { statefulSetsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "DaemonSets" -> loadDaemonSetsFabric8(
                                                        activeClient, namespaceToUse
                                                    ).onSuccess { daemonSetsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "ReplicaSets" -> loadReplicaSetsFabric8(
                                                        activeClient, namespaceToUse
                                                    ).onSuccess { replicaSetsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "Jobs" -> loadJobsFabric8(
                                                        activeClient, namespaceToUse
                                                    ).onSuccess { jobsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "CronJobs" -> loadCronJobsFabric8(
                                                        activeClient, namespaceToUse
                                                    ).onSuccess { cronJobsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "Services" -> loadServicesFabric8(
                                                        activeClient, namespaceToUse
                                                    ).onSuccess { servicesList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "Ingresses" -> loadIngressesFabric8(
                                                        activeClient, namespaceToUse
                                                    ).onSuccess { ingressesList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "Endpoints" -> loadEndpointsFabric8(
                                                        activeClient, namespaceToUse
                                                    ).onSuccess { endpointsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "NetworkPolicies" -> loadNetworkPoliciesFabric8(
                                                        activeClient, namespaceToUse
                                                    ).onSuccess { networkPoliciesList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "PersistentVolumes" -> loadPVsFabric8(activeClient).onSuccess {
                                                        pvsList = it; loadOk = true
                                                    }.onFailure { errorMsg = it.message }

                                                    "PersistentVolumeClaims" -> loadPVCsFabric8(
                                                        activeClient, namespaceToUse
                                                    ).onSuccess { pvcsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "StorageClasses" -> loadStorageClassesFabric8(activeClient).onSuccess {
                                                        storageClassesList = it; loadOk = true
                                                    }.onFailure { errorMsg = it.message }

                                                    "ConfigMaps" -> loadConfigMapsFabric8(
                                                        activeClient, namespaceToUse
                                                    ).onSuccess { configMapsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "Secrets" -> loadSecretsFabric8(
                                                        activeClient, namespaceToUse
                                                    ).onSuccess { secretsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "ServiceAccounts" -> loadServiceAccountsFabric8(
                                                        activeClient, namespaceToUse
                                                    ).onSuccess { serviceAccountsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "Roles" -> loadRolesFabric8(
                                                        activeClient, namespaceToUse
                                                    ).onSuccess { rolesList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "RoleBindings" -> loadRoleBindingsFabric8(
                                                        activeClient, namespaceToUse
                                                    ).onSuccess { roleBindingsList = it; loadOk = true }
                                                        .onFailure { errorMsg = it.message }

                                                    "ClusterRoles" -> loadClusterRolesFabric8(activeClient).onSuccess {
                                                        clusterRolesList = it; loadOk = true
                                                    }.onFailure { errorMsg = it.message }

                                                    "ClusterRoleBindings" -> loadClusterRoleBindingsFabric8(activeClient).onSuccess {
                                                        clusterRoleBindingsList = it; loadOk = true
                                                    }.onFailure { errorMsg = it.message }

                                                    "CRDs" -> loadCrdsFabric8(activeClient).onSuccess {
                                                        crdsList = it; loadOk = true
                                                    }.onFailure { errorMsg = it.message }


                                                    else -> {
                                                        logger.warn("The handler '$nodeId' not realized."); loadOk =
                                                            false; errorMsg = "Not realized"
                                                    }
                                                }
                                                // Оновлюємо статус після завершення
                                                if (loadOk) {
                                                    connectionStatus =
                                                        "Loaded $nodeId ${if (namespaceToUse != null && namespaceToUse != ALL_NAMESPACES_OPTION) " (ns: $namespaceToUse)" else ""}"
                                                } else {
                                                    resourceLoadError = "Error $nodeId: $errorMsg"; connectionStatus =
                                                        "Error $nodeId"
                                                }
                                                isLoading = false
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
                        // Заголовок для таблиці та логів (для деталей він усередині .ResourceDetailPanel)
                        val headerTitle = when {
                            currentView == "logs" -> "Logs: ${paramsForLogs?.second ?: "-"} [${paramsForLogs?.third ?: "-"}]"
                            currentView == "table" && currentResourceType != null && activeClient != null && resourceLoadError == null && errorMessage == null -> "$currentResourceType у $selectedContext"
                            else -> null
                        }

                        // --- ДОДАНО ФІЛЬТР НЕЙМСПЕЙСІВ (якщо є клієнт і це не деталі/логи) ---
                        if (currentView == "table" && activeClient != null) {
                            val isFilterEnabled =
                                nonNSResources.contains(selectedResourceType) // Активуємо тільки для неймспейсних ресурсів
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
                                                        var loadOk = false
                                                        var errorMsg: String? = null
                                                        val namespaceToUse =
                                                            if (nonNSResources.contains(selectedResourceType)) selectedNamespaceFilter else null
                                                        when (selectedResourceType) { // Повторний виклик з новим фільтром
                                                            "Pods" -> loadPodsFabric8(
                                                                activeClient, namespaceToUse
                                                            ).onSuccess { podsList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "Deployments" -> loadDeploymentsFabric8(
                                                                activeClient, namespaceToUse
                                                            ).onSuccess { deploymentsList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "StatefulSets" -> loadStatefulSetsFabric8(
                                                                activeClient, namespaceToUse
                                                            ).onSuccess { statefulSetsList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "DaemonSets" -> loadDaemonSetsFabric8(
                                                                activeClient, namespaceToUse
                                                            ).onSuccess { daemonSetsList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "ReplicaSets" -> loadReplicaSetsFabric8(
                                                                activeClient, namespaceToUse
                                                            ).onSuccess { replicaSetsList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "Jobs" -> loadJobsFabric8(
                                                                activeClient, namespaceToUse
                                                            ).onSuccess { jobsList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "CronJobs" -> loadCronJobsFabric8(
                                                                activeClient, namespaceToUse
                                                            ).onSuccess { cronJobsList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "Services" -> loadServicesFabric8(
                                                                activeClient, namespaceToUse
                                                            ).onSuccess { servicesList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "Ingresses" -> loadIngressesFabric8(
                                                                activeClient, namespaceToUse
                                                            ).onSuccess { ingressesList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "Endpoints" -> loadEndpointsFabric8(
                                                                activeClient, namespaceToUse
                                                            ).onSuccess { endpointsList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "NetworkPolicies" -> loadNetworkPoliciesFabric8(
                                                                activeClient, namespaceToUse
                                                            ).onSuccess { networkPoliciesList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "PersistentVolumeClaims" -> loadPVCsFabric8(
                                                                activeClient, namespaceToUse
                                                            ).onSuccess { pvcsList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "ConfigMaps" -> loadConfigMapsFabric8(
                                                                activeClient, namespaceToUse
                                                            ).onSuccess { configMapsList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "Secrets" -> loadSecretsFabric8(
                                                                activeClient, namespaceToUse
                                                            ).onSuccess { secretsList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "ServiceAccounts" -> loadServiceAccountsFabric8(
                                                                activeClient, namespaceToUse
                                                            ).onSuccess { serviceAccountsList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "Roles" -> loadRolesFabric8(
                                                                activeClient, namespaceToUse
                                                            ).onSuccess { rolesList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "RoleBindings" -> loadRoleBindingsFabric8(
                                                                activeClient, namespaceToUse
                                                            ).onSuccess { roleBindingsList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            // Ресурси без неймспейсу
                                                            "Namespaces" -> {
                                                                loadNamespacesFabric8(activeClient).onSuccess {
                                                                    namespacesList = it; loadOk = true
                                                                }.onFailure { errorMsg = it.message }
                                                            } // Namespaces не фільтруємо

                                                            "Nodes" -> loadNodesFabric8(
                                                                activeClient
                                                            ).onSuccess { nodesList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "Events" -> loadEventsFabric8(
                                                                activeClient
                                                            ).onSuccess { eventsList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "PersistentVolumes" -> loadPVsFabric8(
                                                                activeClient
                                                            ).onSuccess { pvsList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "StorageClasses" -> loadStorageClassesFabric8(
                                                                activeClient
                                                            ).onSuccess { storageClassesList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "ClusterRoles" -> loadClusterRolesFabric8(
                                                                activeClient
                                                            ).onSuccess { clusterRolesList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            "ClusterRoleBindings" -> loadClusterRoleBindingsFabric8(
                                                                activeClient
                                                            ).onSuccess { clusterRoleBindingsList = it; loadOk = true }
                                                                .onFailure { errorMsg = it.message }

                                                            else -> {
                                                                loadOk = false; errorMsg = "The filter is not used"
                                                            }
                                                        }

                                                        if (loadOk) {
                                                            connectionStatus =
                                                                "Loaded $selectedResourceType ${if (namespaceToUse != null && namespaceToUse != ALL_NAMESPACES_OPTION) " (ns: $namespaceToUse)" else ""}"
                                                        } else {
                                                            resourceLoadError =
                                                                "Error $selectedResourceType: $errorMsg"; connectionStatus =
                                                                "Error $selectedResourceType"
                                                        }
                                                        isLoading = false
                                                    }
                                        }
                                    }
                                }
                            )
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
                                            "Loading logs...",
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
                                        })
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
                                            Text(
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
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) { Text("No type resources '$currentResourceType'") }
                                            }
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
                                                                    headers = headers, columnWidths = columnWidths
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
                                                                                detailedResourceType = currentResourceType
                                                                                showLogViewer.value = false
                                                                                logViewerParams.value = null
                                                                            })
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
                                                ) { Text("No columns for '$currentResourceType'") }
                                            } // M3 Text
                                        }
                                        // --- Стани за замовчуванням (M3 Text) ---
                                        activeClient != null -> {
                                            Text(
                                                "Connected to $selectedContext.\nChoose a resource type.",
                                                modifier = Modifier.align(Alignment.Center)
                                            )
                                        }

                                        else -> {
                                            Text(
                                                errorMessage ?: "Choose a context.",
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
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
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
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
    ) {
        TextField(
            value = selectedNamespaceFilter,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isNamespaceDropdownExpanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = connectionStatus,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
        }
    }
}


fun main() = application {
    // Створюємо іконку з Base64-даних для вікна
    val iconPainter = IconsBase64.getIcon(32)?.let {
        BitmapPainter(it.toComposeImageBitmap())
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Kotlin Kube Manager",
        icon = iconPainter // Встановлюємо іконку
    ) {
        LaunchedEffect(Unit) {
            val awtWindow = java.awt.Window.getWindows().firstOrNull()
            awtWindow?.let { IconsBase64.setWindowIcon(it) }
        }

        App()
    }
}
