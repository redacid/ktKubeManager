import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.*
import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.NodeCondition
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.Taint
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadNodesFabric8(client: KubernetesClient?) =
    fetchK8sResource(client, "Nodes", null) { cl, _ -> cl.nodes().list().items } // Nodes не фільтруються

@Composable
fun NodeDetailsView(node: Node) {
    val showCapacity = remember { mutableStateOf(false) }
    val showConditions = remember { mutableStateOf(false) }
    val showLabels = remember { mutableStateOf(false) }
    val showAnnotations = remember { mutableStateOf(false) }
    val showTaints = remember { mutableStateOf(false) }
    val showImages = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основний блок інформації про ноду
        Text(
            text = "Node Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Спеціальна картка для статусу вузла
        val nodeStatus = formatNodeStatus(node.status?.conditions)
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    nodeStatus.contains("Ready") -> MaterialTheme.colorScheme.primaryContainer
                    nodeStatus.contains("NotReady") -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when {
                            nodeStatus.contains("Ready") -> FeatherIcons.Check
                            nodeStatus.contains("NotReady") -> FeatherIcons.AlertTriangle
                            else -> FeatherIcons.HelpCircle
                        },
                        contentDescription = "Node Status",
                        tint = when {
                            nodeStatus.contains("Ready") -> MaterialTheme.colorScheme.primary
                            nodeStatus.contains("NotReady") -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Status: $nodeStatus",
                        fontWeight = FontWeight.Bold,
                        color = when {
                            nodeStatus.contains("Ready") -> MaterialTheme.colorScheme.onPrimaryContainer
                            nodeStatus.contains("NotReady") -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Основна інформація
        DetailRow("Name", node.metadata?.name)
        DetailRow("Roles", formatNodeRoles(node.metadata?.labels))
        DetailRow("Created", formatAge(node.metadata?.creationTimestamp))
        DetailRow("Kubernetes Version", node.status?.nodeInfo?.kubeletVersion)
        DetailRow("OS", node.status?.nodeInfo?.osImage)
        DetailRow("Kernel Version", node.status?.nodeInfo?.kernelVersion)
        DetailRow("Container Runtime", node.status?.nodeInfo?.containerRuntimeVersion)
        DetailRow("Architecture", node.status?.nodeInfo?.architecture)
        DetailRow("Machine ID", node.status?.nodeInfo?.machineID)
        DetailRow("System UUID", node.status?.nodeInfo?.systemUUID)
        DetailRow("Boot ID", node.status?.nodeInfo?.bootID)

        // IP адреси та hostname
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Addressing Information",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.height(4.dp))

                node.status?.addresses?.forEach { address ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when (address.type) {
                            "InternalIP" -> FeatherIcons.Server
                            "ExternalIP" -> FeatherIcons.Globe
                            "Hostname" -> FeatherIcons.Home
                            "InternalDNS" -> FeatherIcons.Eye
                            else -> FeatherIcons.Terminal
                        }

                        Icon(
                            imageVector = icon,
                            contentDescription = address.type,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${address.type}: ${address.address}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Taints section
        Row(
            modifier = Modifier.fillMaxWidth()
                //.clickable { showTaints.value = !showTaints.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
//            Icon(
//                imageVector = if (showTaints.value) ICON_DOWN else ICON_RIGHT,
//                contentDescription = "Expand Taints"
//            )
            Text(
                text = "Taints (${node.spec?.taints?.size ?: 0})",
                style = MaterialTheme.typography.titleSmall
            )
        }

       // if (showTaints.value) {
            if (node.spec?.taints.isNullOrEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = FeatherIcons.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No taints set on this node",
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Pods can be scheduled normally without tolerations",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(node.spec?.taints ?: emptyList()) { taint ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when (taint.effect) {
                                    "NoSchedule" -> MaterialTheme.colorScheme.errorContainer
                                    "PreferNoSchedule" -> MaterialTheme.colorScheme.secondaryContainer
                                    "NoExecute" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (taint.effect) {
                                            "NoSchedule" -> FeatherIcons.Lock
                                            "PreferNoSchedule" -> FeatherIcons.AlertTriangle
                                            "NoExecute" -> FeatherIcons.XCircle
                                            else -> FeatherIcons.HelpCircle
                                        },
                                        contentDescription = "Taint Effect",
                                        tint = when (taint.effect) {
                                            "NoSchedule" -> MaterialTheme.colorScheme.error
                                            "PreferNoSchedule" -> MaterialTheme.colorScheme.secondary
                                            "NoExecute" -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "${taint.key}=${taint.value}:${taint.effect}",
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(Modifier.height(4.dp))

                                // Пояснювальний текст
                                val explanationText = when (taint.effect) {
                                    "NoSchedule" -> "Pods without matching toleration won't be scheduled on this node"
                                    "PreferNoSchedule" -> "Scheduler will try to avoid placing pods without matching toleration on this node"
                                    "NoExecute" -> "Pods without matching toleration will be evicted from the node if already running"
                                    else -> "Custom taint effect"
                                }

                                Text(
                                    text = explanationText,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
      //  }

        Spacer(Modifier.height(16.dp))

        // Capacity & Allocatable Resources section
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { showCapacity.value = !showCapacity.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (showCapacity.value) ICON_DOWN else ICON_RIGHT,
                contentDescription = "Expand Capacity"
            )
            Text(
                text = "Capacity & Allocatable Resources",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (showCapacity.value) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Заголовок таблиці
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Основні ресурси з гарним відображенням
                    ResourceRowWithVisualization(
                        name = "CPU",
                        capacity = node.status?.capacity?.get("cpu").toString(),
                        allocatable = node.status?.allocatable?.get("cpu").toString(),
                        icon = FeatherIcons.Cpu
                    )

                    ResourceRowWithVisualization(
                        name = "Memory",
                        capacity = node.status?.capacity?.get("memory").toString(),
                        allocatable = node.status?.allocatable?.get("memory").toString(),
                        icon = FeatherIcons.Database
                    )

                    ResourceRowWithVisualization(
                        name = "Ephemeral Storage",
                        capacity = node.status?.capacity?.get("ephemeral-storage").toString(),
                        allocatable = node.status?.allocatable?.get("ephemeral-storage").toString(),
                        icon = FeatherIcons.HardDrive
                    )

                    ResourceRowWithVisualization(
                        name = "Pods",
                        capacity = node.status?.capacity?.get("pods").toString(),
                        allocatable = node.status?.allocatable?.get("pods").toString(),
                        icon = FeatherIcons.Box
                    )

                    // Інші ресурси
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Extended Resources",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))

                    // Відобразити інші ресурси динамічно
                    node.status?.capacity?.entries?.filter {
                        !setOf("cpu", "memory", "ephemeral-storage", "pods").contains(it.key)
                    }?.forEach { entry ->
                        ResourceRow(
                            name = entry.key,
                            capacity = entry.value,
                            allocatable = node.status?.allocatable?.get(entry.key)
                        )
                    }

                    if (node.status?.capacity?.entries?.none {
                            !setOf(
                                "cpu",
                                "memory",
                                "ephemeral-storage",
                                "pods"
                            ).contains(it.key)
                        } == true) {
                        Text(
                            text = "No extended resources available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Conditions section
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { showConditions.value = !showConditions.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (showConditions.value) ICON_DOWN else ICON_RIGHT,
                contentDescription = "Expand Conditions"
            )
            Text(
                text = "Conditions (${node.status?.conditions?.size ?: 0})",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (showConditions.value) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(node.status?.conditions ?: emptyList()) { condition ->
                    val statusColor = when (condition.status) {
                        "True" -> MaterialTheme.colorScheme.primary
                        "False" -> if (condition.type == "Ready") MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary

                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                condition.type == "Ready" && condition.status == "True" ->
                                    MaterialTheme.colorScheme.primaryContainer

                                condition.type == "Ready" && condition.status != "True" ->
                                    MaterialTheme.colorScheme.errorContainer

                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when (condition.status) {
                                        "True" -> ICON_SUCCESS
                                        "False" -> ICON_ERROR
                                        else -> ICON_HELP
                                    },
                                    contentDescription = "Condition Status",
                                    tint = statusColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
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
                            if (!condition.reason.isNullOrBlank()) {
                                DetailRow("Reason", condition.reason)
                            }
                            if (!condition.message.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = condition.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Container Images section
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { showImages.value = !showImages.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (showImages.value) ICON_DOWN else ICON_RIGHT,
                contentDescription = "Expand Images"
            )
            Text(
                text = "Container Images (${node.status?.images?.size ?: 0})",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (showImages.value) {
            if (node.status?.images.isNullOrEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = FeatherIcons.Image,
                            contentDescription = "No Images",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No container images on this node",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.heightIn(max = 400.dp).padding(12.dp)) {
                        Text(
                            text = "Images cached on this node:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        LazyColumn {
                            items(node.status?.images?.sortedByDescending { it.sizeBytes } ?: emptyList()) { image ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = FeatherIcons.Package,
                                        contentDescription = "Container Image",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        // Відображення тегів зображення
                                        image.names?.firstOrNull()?.let { mainName ->
                                            Text(
                                                text = mainName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        // Розмір зображення
                                        image.sizeBytes?.let { size ->
                                            Text(
                                                text = "Size: ${formatBytes(size)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Інші теги, якщо є більше одного
                                        if ((image.names?.size ?: 0) > 1) {
                                            var expanded by remember { mutableStateOf(false) }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.clickable { expanded = !expanded }
                                            ) {
                                                Icon(
                                                    imageVector = if (expanded) ICON_DOWN else ICON_RIGHT,
                                                    contentDescription = "Show more tags",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${image.names?.size?.minus(1)} more tags",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            if (expanded) {
                                                Column(modifier = Modifier.padding(start = 16.dp)) {
                                                    image.names?.drop(1)?.forEach { tag ->
                                                        Text(
                                                            text = tag,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Labels section
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { showLabels.value = !showLabels.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (showLabels.value) ICON_DOWN else ICON_RIGHT,
                contentDescription = "Expand Labels"
            )
            Text(
                text = "Labels (${node.metadata?.labels?.size ?: 0})",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (showLabels.value) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (node.metadata?.labels.isNullOrEmpty()) {
                        Text("No labels found", modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        // Розділяємо мітки на категорії
                        val kubernetesLabels =
                            node.metadata?.labels?.filterKeys { it.startsWith("kubernetes.io/") || it.startsWith("node-role.kubernetes.io/") }
                        val otherLabels =
                            node.metadata?.labels?.filterKeys { !it.startsWith("kubernetes.io/") && !it.startsWith("node-role.kubernetes.io/") }

                        // Kubernetes мітки
                        if (!kubernetesLabels.isNullOrEmpty()) {
                            Text(
                                text = "Kubernetes Labels:",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            kubernetesLabels.entries.forEach { (key, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier.weight(0.5f)
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(0.5f)
                                    )
                                }
                            }
                        }

                        // Інші мітки
                        if (!otherLabels.isNullOrEmpty()) {
                            if (!kubernetesLabels.isNullOrEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                            }

                            Text(
                                text = "Custom Labels:",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            otherLabels.entries.forEach { (key, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier.weight(0.5f)
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Annotations section
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { showAnnotations.value = !showAnnotations.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (showAnnotations.value) ICON_DOWN else ICON_RIGHT,
                contentDescription = "Expand Annotations"
            )
            Text(
                text = "Annotations (${node.metadata?.annotations?.size ?: 0})",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (showAnnotations.value) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (node.metadata?.annotations.isNullOrEmpty()) {
                        Text("No annotations found", modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        node.metadata?.annotations?.entries?.sortedBy { it.key }?.forEach { (key, value) ->
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
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
                    }
                }
            }
        }
    }
}

/**
 * Відображає рядок із ресурсом та його візуалізацією
 */
@Composable
private fun ResourceRowWithVisualization(
    name: String,
    capacity: String?,
    allocatable: String?,
    icon: ImageVector
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Capacity",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = capacity ?: "N/A",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Allocatable",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = allocatable ?: "N/A",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Прогрес-бар для візуалізації (якщо можливо)
        if (capacity != null && allocatable != null) {
            // Винести логіку парсингу за межі композиції
            val resourceInfo = remember(capacity, allocatable) {
                calculateResourceUsage(capacity, allocatable)
            }

            if (resourceInfo.isValid) {
                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { resourceInfo.usedRatio },
                        modifier = Modifier
                            .height(8.dp)
                            .fillMaxWidth(0.8f),
                        color = when {
                            resourceInfo.freeRatio < 0.2f -> MaterialTheme.colorScheme.error
                            resourceInfo.freeRatio < 0.5f -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = "${resourceInfo.freePercentFormatted}% free",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun calculateResourceUsage(capacity: String, allocatable: String): ResourceUsageInfo {
    return try {
        val capValue = parseResourceValue(capacity)
        val allocValue = parseResourceValue(allocatable)

        if (capValue > 0) {
            val freeRatio = allocValue / capValue
            val freePercent = (freeRatio * 100).toInt()

            ResourceUsageInfo(
                isValid = true,
                usedRatio = freeRatio.toFloat(),
                freeRatio = freeRatio.toFloat(),
                freePercentFormatted = freePercent
            )
        } else {
            ResourceUsageInfo()
        }
    } catch (e: Exception) {
        ResourceUsageInfo()
    }
}

/**
 * Клас для зберігання інформації про використання ресурсу
 */
private data class ResourceUsageInfo(
    val isValid: Boolean = false,
    val usedRatio: Float = 0f,
    val freeRatio: Float = 0f,
    val freePercentFormatted: Int = 0
)

/**
 * Функція для парсингу значень ресурсів Kubernetes
 */
private fun parseResourceValue(resource: String): Double {
    return try {
        when {
            resource.endsWith("Ki") -> resource.substring(0, resource.length - 2).toDouble() * 1024
            resource.endsWith("Mi") -> resource.substring(0, resource.length - 2).toDouble() * 1024 * 1024
            resource.endsWith("Gi") -> resource.substring(0, resource.length - 2).toDouble() * 1024 * 1024 * 1024
            resource.endsWith("Ti") -> resource.substring(0, resource.length - 2).toDouble() * 1024 * 1024 * 1024 * 1024
            resource.endsWith("m") -> resource.substring(0, resource.length - 1).toDouble() / 1000
            else -> resource.toDouble()
        }
    } catch (e: Exception) {
        0.0
    }
}

fun formatNodeStatus(conditions: List<NodeCondition>?): String {
    val ready = conditions?.find { it.type == "Ready" }; return when (ready?.status) {
        "True" -> "Ready"; "False" -> "NotReady${ready.reason?.let { " ($it)" } ?: ""}"; else -> "Unknown"
    }
}

fun formatNodeRoles(labels: Map<String, String>?): String {
    if (labels.isNullOrEmpty()) return "none"

    val roles = labels.keys
        .filter { it.startsWith("node-role.kubernetes.io/") }
        .map { it.removePrefix("node-role.kubernetes.io/") }

    return if (roles.isEmpty()) "worker" else roles.sorted().joinToString(", ")
}

fun formatTaints(taints: List<Taint>?): String {
    if (taints.isNullOrEmpty()) return "None"

    return taints.take(2).joinToString(", ") {
        "${it.key}=${it.value}:${it.effect}"
    } + if (taints.size > 2) "..." else ""
}

@Composable
fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L * 1024L -> String.format("%.2f TiB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L * 1024L -> String.format("%.2f GiB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format("%.2f MiB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format("%.2f KiB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

@Composable
fun ResourceRow(name: String, capacity: Quantity?, allocatable: Quantity?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)
        )
        Text(
            text = capacity?.amount ?: "-", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)
        )
        Text(
            text = allocatable?.amount ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}