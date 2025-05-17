import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import io.fabric8.kubernetes.client.KubernetesClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
@OptIn(ExperimentalMaterial3Api::class) // Для ExposedDropdownMenuBox
@Composable
fun NamespaceFilter(
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
fun StatusBar(
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

fun getHeadersForType(resourceType: String): List<String> {
    return when (resourceType) {
        "Namespaces" ->             listOf("Name", "Status", "Age")
        "Nodes" ->                  listOf("Name", "Status", "Roles", "Version", "Taints", "Age")
        "Events" ->                 listOf("Namespace", "Name", "Type", "Reason", "Kind", "Object Name", "Age")
        "Pods" ->                   listOf("Namespace", "Name", "Ready", "Status", "Restarts", "Node", "Age")
        "Deployments" ->            listOf("Namespace", "Name", "Ready", "Up-to-date", "Available", "Age")
        "StatefulSets" ->           listOf("Namespace", "Name", "Ready", "Age")
        "DaemonSets" ->             listOf("Namespace", "Name", "Desired", "Current", "Ready", "Up-to-date", "Available", "Age")
        "ReplicaSets" ->            listOf("Namespace", "Name", "Desired", "Current", "Ready", "Age")
        "Jobs" ->                   listOf("Namespace", "Name", "Completions", "Duration", "Age")
        "CronJobs" ->               listOf("Namespace", "Name", "Schedule", "Suspend", "Active", "Last Schedule", "Age")
        "Services" ->               listOf("Namespace", "Name", "Type", "ClusterIP", "ExternalIP", "Ports", "Age")
        "Ingresses" ->              listOf("Namespace", "Name", "Class", "Hosts", "Address", "Ports", "Age")
        "Endpoints" ->              listOf("Namespace", "Name", "Endpoints", "Age")
        "NetworkPolicies" ->        listOf("Namespace", "Name", "Selector", "Type","Age")
        "PersistentVolumes" ->      listOf("Name", "Capacity", "Access Modes", "Reclaim Policy", "Status", "Claim", "StorageClass", "Age")
        "PersistentVolumeClaims" -> listOf("Namespace", "Name", "Status", "Volume", "Capacity", "Access Modes", "StorageClass", "Age")
        "StorageClasses" ->         listOf("Name", "Provisioner", "Reclaim Policy", "Binding Mode", "Allow Expand", "Age")
        "ConfigMaps" ->             listOf("Namespace", "Name", "Data", "Age")
        "Secrets" ->                listOf("Namespace", "Name", "Type", "Data", "Age")
        "ServiceAccounts" ->        listOf("Namespace", "Name", "Secrets", "Age")
        "Roles" ->                  listOf("Namespace", "Name", "Age")
        "RoleBindings" ->           listOf("Namespace", "Name", "Role Kind", "Role Name", "Age")
        "ClusterRoles" ->           listOf("Name", "Age")
        "ClusterRoleBindings" ->    listOf("Name", "Role Kind", "Role Name", "Age")
        "CRDs" ->                   listOf("Name", "Group", "Version", "Kind", "Age")
        else ->                     listOf("Name")
    }
}

fun getCellData(resource: Any, colIndex: Int, resourceType: String): String {
    val na = "N/A"
    try {
        return when (resourceType) {
            "Namespaces" -> if (resource is Namespace) {
                when (colIndex) {
                    0 -> resource.metadata?.name ?: na
                    1 -> resource.status?.phase ?: na
                    2 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "Nodes" -> if (resource is Node) {
                when (colIndex) {
                    0 -> resource.metadata?.name ?: na
                    1 -> formatNodeStatus(resource.status?.conditions)
                    2 -> formatNodeRoles(resource.metadata?.labels)
                    3 -> resource.status?.nodeInfo?.kubeletVersion ?: na
                    4 -> formatTaints(resource.spec?.taints)
                    5 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
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
                    0 -> resource.metadata?.namespace ?: na
                    1 -> resource.metadata?.name ?: na
                    2 -> formatPodContainers(resource.status?.containerStatuses)
                    3 -> resource.status?.phase ?: na
                    4 -> formatPodRestarts(resource.status?.containerStatuses)
                    5 -> resource.spec?.nodeName ?: "<none>"
                    6 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "Deployments" -> if (resource is Deployment) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na
                    1 -> resource.metadata?.name ?: na
                    2 -> "${resource.status?.readyReplicas ?: 0}/${resource.spec?.replicas ?: 0}"
                    3 -> resource.status?.updatedReplicas?.toString() ?: "0"
                    4 -> resource.status?.availableReplicas?.toString() ?: "0"
                    5 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "StatefulSets" -> if (resource is StatefulSet) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na
                    1 -> resource.metadata?.name ?: na
                    2 -> "${resource.status?.readyReplicas ?: 0}/${resource.spec?.replicas ?: 0}"
                    3 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
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
                    ?: "0"; 7 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "ReplicaSets" -> if (resource is ReplicaSet) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na
                    1 -> resource.metadata?.name ?: na
                    2 -> resource.spec?.replicas?.toString() ?: "0"
                    3 -> resource.status?.replicas?.toString() ?: "0"
                    4 -> resource.status?.readyReplicas?.toString() ?: "0"
                    5 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "Jobs" -> if (resource is Job) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na
                    1 -> resource.metadata?.name ?: na
                    2 -> "${resource.status?.succeeded ?: 0}/${resource.spec?.completions ?: '?'}"
                    3 -> formatJobDuration(resource.status)
                    4 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "CronJobs" -> if (resource is CronJob) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na
                    1 -> resource.metadata?.name ?: na
                    2 -> resource.spec?.schedule ?: na
                    3 -> resource.spec?.suspend?.toString() ?: "false"
                    4 -> resource.status?.active?.size?.toString() ?: "0"
                    5 -> resource.status?.lastScheduleTime?.let { formatAge(it) } ?: "<never>"
                    6 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "Services" -> if (resource is Service) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na
                    1 -> resource.metadata?.name ?: na
                    2 -> resource.spec?.type ?: na
                    3 -> resource.spec?.clusterIPs?.joinToString(",") ?: na
                    4 -> formatServiceExternalIP(resource)
                    5 -> formatPorts(resource.spec?.ports)
                    6 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "Ingresses" -> if (resource is Ingress) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na
                    1 -> resource.metadata?.name ?: na
                    2 -> resource.spec?.ingressClassName ?: "<none>"
                    3 -> formatIngressHosts(resource.spec?.rules)
                    4 -> formatIngressAddress(resource.status?.loadBalancer?.ingress)
                    5 -> formatIngressPorts(resource.spec?.tls)
                    6 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "Endpoints" -> if (resource is Endpoints) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na
                    1 -> resource.metadata?.name ?: na
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
                    2 -> resource.spec?.podSelector?.matchLabels?.entries?.joinToString(", ") { "${it.key}=${it.value}" } ?: "<all pods>"
                    3 -> formatPolicyTypes(resource.spec?.policyTypes)
                    4 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "PersistentVolumes" -> if (resource is PersistentVolume) {
                when (colIndex) {
                    0 -> resource.metadata?.name ?: na
                    1 -> resource.spec?.capacity?.get("storage")?.toString() ?: na
                    2 -> formatAccessModes(resource.spec?.accessModes)
                    3 -> resource.spec?.persistentVolumeReclaimPolicy ?: na
                    4 -> resource.status?.phase ?: na
                    5 -> resource.spec?.claimRef?.let { "${it.namespace ?: "-"}/${it.name ?: "-"}" } ?: "<none>"
                    6 -> resource.spec?.storageClassName ?: "<none>"
                    7 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "PersistentVolumeClaims" -> if (resource is PersistentVolumeClaim) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na
                    1 -> resource.metadata?.name ?: na
                    2 -> resource.status?.phase ?: na
                    3 -> resource.spec?.volumeName ?: "<none>"
                    4 -> resource.status?.capacity?.get("storage")?.toString() ?: na
                    5 -> formatAccessModes(resource.spec?.accessModes)
                    6 -> resource.spec?.storageClassName ?: "<none>"
                    7 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "StorageClasses" -> if (resource is StorageClass) {
                when (colIndex) {
                    0 -> resource.metadata?.name ?: na
                    1 -> resource.provisioner ?: na
                    2 -> resource.reclaimPolicy ?: na
                    3 -> resource.volumeBindingMode ?: na
                    4 -> resource.allowVolumeExpansion?.toString() ?: "false"
                    5 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "ConfigMaps" -> if (resource is ConfigMap) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na
                    1 -> resource.metadata?.name ?: na
                    2 -> resource.data?.size?.toString() ?: "0"
                    3 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "Secrets" -> if (resource is Secret) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na
                    1 -> resource.metadata?.name ?: na
                    2 -> resource.type ?: na
                    3 -> (resource.data?.size ?: 0).plus(resource.stringData?.size ?: 0).toString()
                    4 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "ServiceAccounts" -> if (resource is ServiceAccount) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na
                    1 -> resource.metadata?.name ?: na
                    2 -> resource.secrets?.size?.toString() ?: "0"
                    3 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "Roles" -> if (resource is Role) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na
                    1 -> resource.metadata?.name ?: na
                    2 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "RoleBindings" -> if (resource is RoleBinding) {
                when (colIndex) {
                    0 -> resource.metadata?.namespace ?: na
                    1 -> resource.metadata?.name ?: na
                    2 -> resource.roleRef?.kind ?: na
                    3 -> resource.roleRef.name ?: na
                    4 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "ClusterRoles" -> if (resource is ClusterRole) {
                when (colIndex) {
                    0 -> resource.metadata?.name ?: na
                    1 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
                }
            } else ""

            "ClusterRoleBindings" -> if (resource is ClusterRoleBinding) {
                when (colIndex) {
                    0 -> resource.metadata?.name ?: na
                    1 -> resource.roleRef?.kind ?: na
                    2 -> resource.roleRef.name ?: na
                    3 -> formatAge(resource.metadata?.creationTimestamp)
                    else -> ""
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

@Composable
fun KubeTableHeaderRow(
    headers: List<String>,
    columnWidths: List<Int> // Add column widths parameter
) {
    Row(
        modifier = Modifier.Companion.background(MaterialTheme.colorScheme.surfaceVariant).fillMaxWidth()
            .padding(vertical = 8.dp), verticalAlignment = Alignment.Companion.CenterVertically
    ) {
        headers.forEachIndexed { index, header ->
            val width = if (index < columnWidths.size) columnWidths[index] else 100
            Text(
                text = header,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Companion.Bold,
                modifier = Modifier.Companion.width(width.dp).padding(horizontal = 8.dp),
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
                overflow = TextOverflow.Companion.Ellipsis
            )
        }
    }
}

@Composable
fun <T : HasMetadata> KubeTableRow(
    item: T,
    headers: List<String>,
    resourceType: String,
    columnWidths: List<Int>,
    onRowClick: (T) -> Unit
) {
    Row(
        modifier = Modifier.Companion.fillMaxWidth().clickable { onRowClick(item) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Companion.CenterVertically
    ) {
        headers.forEachIndexed { colIndex, _ ->
            val width = if (colIndex < columnWidths.size) columnWidths[colIndex] else 100
            Text(
                text = getCellData(item, colIndex, resourceType),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .width(width.dp)
                    .background(
                        when (getCellData(item, colIndex, resourceType)) {
                            "Running" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f,red = 0/255.0f ,green = 255/255.0f ,blue = 0/255.0f)
                            "Ready" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f,red = 0/255.0f ,green = 255/255.0f ,blue = 0/255.0f)
                            "Normal" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f,red = 0/255.0f ,green = 255/255.0f ,blue = 0/255.0f)
                            "Succeeded" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f,red = 0/255.0f ,green = 255/255.0f ,blue = 0/255.0f)
                            "Bound" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f,red = 0/255.0f ,green = 255/255.0f ,blue = 0/255.0f)
                            "Pending" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f,red = 125/255.0f ,green = 125/255.0f ,blue = 0/255.0f)
                            "Warning" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f,red = 255/255.0f ,green = 0/255.0f ,blue = 0/255.0f)
                            "Error" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f,red = 255/255.0f ,green = 0/255.0f ,blue = 0/255.0f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    )
                    .padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

        }
    }
}

@Composable
fun LogsView(
    paramsForLogs: Triple<String, String, String>?,
    activeClient: KubernetesClient?,
    onClose: () -> Unit
) {
    if (paramsForLogs != null) {
        LogViewerPanel(
            namespace = paramsForLogs.first,
            podName = paramsForLogs.second,
            containerName = paramsForLogs.third,
            client = activeClient,
            onClose = onClose
        )
    } else {
        Text(
            "Loading logs...",
            //modifier = Modifier.align(Alignment.Center)
        )
        LaunchedEffect(Unit) { onClose() }
    }
}

@Composable
fun DetailsView(
    resource: HasMetadata?,
    resourceType: String?,
    onClose: () -> Unit,
    onShowLogsRequest: (String, String, String) -> Unit,
    onResourceClick: ((HasMetadata, String) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()

    when (resource) {
        is Pod -> ResourceDetailPanel(
            resource = resource,
            resourceType = resourceType,
            onClose = onClose,
            onOwnerClick = { kind, name, namespace ->
                val metadata = io.fabric8.kubernetes.api.model.ObjectMeta()
                metadata.name = name
                metadata.namespace = namespace
                val parentResourceType = when (kind) {
                    "ReplicaSet" -> "ReplicaSets"
                    "Deployment" -> "Deployments"
                    "StatefulSet" -> "StatefulSets"
                    "DaemonSet" -> "DaemonSets"
                    "Job" -> "Jobs"
                    else -> null
                }
                if (parentResourceType != null) {
                    // Запускаємо корутину для отримання деталей
                    coroutineScope.launch {
                        fetchResourceDetails(activeClient, parentResourceType, metadata)
                            .onSuccess { parentResource ->
                                onResourceClick?.invoke(parentResource, parentResourceType)
                            }
                    }
                }
            },
            onShowLogsRequest = onShowLogsRequest
        )
        is ReplicaSet -> ResourceDetailPanel(
            resource = resource,
            resourceType = resourceType,
            onClose = onClose,
            onOwnerClick = { kind, name, namespace ->
                val metadata = io.fabric8.kubernetes.api.model.ObjectMeta()
                metadata.name = name
                metadata.namespace = namespace
                val parentResourceType = when (kind) {
                    "ReplicaSet" -> "ReplicaSets"
                    "Deployment" -> "Deployments"
                    "StatefulSet" -> "StatefulSets"
                    "DaemonSet" -> "DaemonSets"
                    "Job" -> "Jobs"
                    else -> null
                }
                if (parentResourceType != null) {
                    // Запускаємо корутину для отримання деталей
                    coroutineScope.launch {
                        fetchResourceDetails(activeClient, parentResourceType, metadata)
                            .onSuccess { parentResource ->
                                onResourceClick?.invoke(parentResource, parentResourceType)
                            }
                    }
                }
            },
            onShowLogsRequest = onShowLogsRequest
        )
//        is Deployment -> DeploymentDetailsView(dep = resource)
//        is StatefulSet -> StatefulSetDetailsView(sts = resource)
//        is DaemonSet -> DaemonSetDetailsView(ds = resource)
         // TODO Controled by for Job
//        is Job -> JobDetailsView(job = resource)
        else ->
            ResourceDetailPanel(
            resource = resource,
            resourceType = resourceType,
            onClose = onClose,
            onShowLogsRequest = onShowLogsRequest
        )
    }
}


@Composable
fun TableView(
    isLoading: Boolean,
    connectionStatus: String,
    errorMessage: String?,
    resourceLoadError: String?,
    activeClient: KubernetesClient?,
    currentResourceType: String?,
    selectedContext: String?,
    resourceLists: Map<String, List<HasMetadata>>,
    onResourceClick: (HasMetadata, String) -> Unit
) {
    val currentErrorMessageForPanel = resourceLoadError ?: errorMessage

    when {
        isLoading -> LoadingView(connectionStatus)
        currentErrorMessageForPanel != null -> ErrorView(currentErrorMessageForPanel)
        activeClient != null && currentResourceType != null -> {
            ResourceTableView(
                currentResourceType = currentResourceType,
                resourceLists = resourceLists,
                onResourceClick = onResourceClick
            )
        }
        activeClient != null -> DefaultConnectedView(selectedContext)
        else -> DefaultDisconnectedView()
    }
}

@Composable
private fun ResourceTable(
    headers: List<String>,
    items: List<HasMetadata>,
    resourceType: String,
    onRowClick: (HasMetadata) -> Unit
) {
    Column(modifier = Modifier.Companion.fillMaxSize()) {
        val columnWidths = calculateColumnWidths(
            headers = headers,
            items = items,
            resourceType = resourceType
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.Companion.horizontalScroll(rememberScrollState())) {
                Column {
                    KubeTableHeaderRow(headers = headers, columnWidths = columnWidths)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(items) { item ->
                            KubeTableRow(
                                item = item,
                                headers = headers,
                                resourceType = resourceType,
                                columnWidths = columnWidths,
                                onRowClick = onRowClick
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingView(connectionStatus: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        //modifier = Modifier.align(Alignment.Center)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.Companion.height(20.dp))
            Text(text = connectionStatus)
        }
    }
}

@Composable
private fun ErrorView(errorMessage: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun DefaultConnectedView(selectedContext: String?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Connected to $selectedContext.\nChoose a resource type.",
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun DefaultDisconnectedView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Choose a context.",
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun ResourceTableView(
    currentResourceType: String,
    resourceLists: Map<String, List<HasMetadata>>,
    onResourceClick: (HasMetadata, String) -> Unit
) {
    val itemsToShow = remember(currentResourceType, resourceLists) {
        resourceLists[currentResourceType] ?: emptyList()
    }

    val headers = remember(currentResourceType) {
        getHeadersForType(currentResourceType)
    }

    if (itemsToShow.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No type resources '$currentResourceType'")
        }
    } else if (headers.isNotEmpty()) {
        ResourceTable(
            headers = headers,
            items = itemsToShow,
            resourceType = currentResourceType,
            onRowClick = { clickedItem -> onResourceClick(clickedItem, currentResourceType) }
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No columns for '$currentResourceType'")
        }
    }
}