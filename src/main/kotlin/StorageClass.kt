import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.SimpleIcons
import compose.icons.feathericons.*
import compose.icons.simpleicons.*
import io.fabric8.kubernetes.api.model.storage.StorageClass
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadStorageClassesFabric8(client: KubernetesClient?) =
    fetchK8sResource(client, "StorageClasses", null) { cl, _ ->
        cl.storage().v1().storageClasses().list().items
    } // Cluster-scoped

@Composable
fun StorageClassDetailsView(storageClass: StorageClass) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "StorageClass Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        DetailRow("Name", storageClass.metadata?.name)
        DetailRow("Provisioner", storageClass.provisioner)
        DetailRow("Created", formatAge(storageClass.metadata?.creationTimestamp))

        Spacer(Modifier.height(8.dp))

        // Секція статусу типу сховища
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    storageClass.metadata?.annotations?.get("storageclass.kubernetes.io/is-default-class") == "true" ->
                        MaterialTheme.colorScheme.primaryContainer

                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when {
                            storageClass.metadata?.annotations?.get("storageclass.kubernetes.io/is-default-class") == "true" ->
                                FeatherIcons.Star

                            else -> FeatherIcons.Database
                        },
                        contentDescription = "Storage Class Status",
                        tint = when {
                            storageClass.metadata?.annotations?.get("storageclass.kubernetes.io/is-default-class") == "true" ->
                                MaterialTheme.colorScheme.onPrimaryContainer

                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = when {
                                storageClass.metadata?.annotations?.get("storageclass.kubernetes.io/is-default-class") == "true" ->
                                    "Default Storage Class"

                                else -> "Storage Class"
                            },
                            fontWeight = FontWeight.Bold,
                            color = when {
                                storageClass.metadata?.annotations?.get("storageclass.kubernetes.io/is-default-class") == "true" ->
                                    MaterialTheme.colorScheme.onPrimaryContainer

                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = when {
                                storageClass.metadata?.annotations?.get("storageclass.kubernetes.io/is-default-class") == "true" ->
                                    "Used when no storage class is specified in PVCs"

                                else -> "Must be explicitly selected in PVCs"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                storageClass.metadata?.annotations?.get("storageclass.kubernetes.io/is-default-class") == "true" ->
                                    MaterialTheme.colorScheme.onPrimaryContainer

                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Provisioner section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Storage Provisioner",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = getProvisionerIcon(storageClass.provisioner),
                        contentDescription = "Provisioner",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    SelectionContainer {
                        Text(
                            text = storageClass.provisioner ?: "Unknown",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                getProvisionerDescription(storageClass.provisioner)?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Reclaim Policy
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Volume Reclaim Policy",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                // Визначаємо колір індикатора для політики
                val reclaimPolicy = storageClass.reclaimPolicy ?: "Delete"
                val (reclaimIcon, reclaimColor, reclaimDescription) = when (reclaimPolicy) {
                    "Retain" -> Triple(
                        FeatherIcons.Lock,
                        MaterialTheme.colorScheme.primary,
                        "Manually reclaim the volume after the claim is deleted"
                    )

                    "Delete" -> Triple(
                        FeatherIcons.Trash2,
                        MaterialTheme.colorScheme.error,
                        "Automatically delete the volume when the claim is deleted"
                    )

                    "Recycle" -> Triple(
                        FeatherIcons.RefreshCw,
                        MaterialTheme.colorScheme.tertiary,
                        "Basic scrub and make available again (Deprecated)"
                    )

                    else -> Triple(
                        FeatherIcons.HelpCircle,
                        MaterialTheme.colorScheme.secondary,
                        "Unknown reclaim policy"
                    )
                }

                Surface(
                    color = when (reclaimPolicy) {
                        "Delete" -> MaterialTheme.colorScheme.errorContainer
                        "Retain" -> MaterialTheme.colorScheme.primaryContainer
                        "Recycle" -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = reclaimIcon,
                            contentDescription = "Reclaim Policy",
                            tint = reclaimColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = reclaimPolicy,
                                fontWeight = FontWeight.Bold,
                                color = when (reclaimPolicy) {
                                    "Delete" -> MaterialTheme.colorScheme.onErrorContainer
                                    "Retain" -> MaterialTheme.colorScheme.onPrimaryContainer
                                    "Recycle" -> MaterialTheme.colorScheme.onTertiaryContainer
                                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                            Text(
                                text = reclaimDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = when (reclaimPolicy) {
                                    "Delete" -> MaterialTheme.colorScheme.onErrorContainer
                                    "Retain" -> MaterialTheme.colorScheme.onPrimaryContainer
                                    "Recycle" -> MaterialTheme.colorScheme.onTertiaryContainer
                                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        }
                    }
                }

                // Додаткове попередження для політики Delete
                if (reclaimPolicy == "Delete") {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = FeatherIcons.AlertTriangle,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Caution: Data will be permanently deleted when PVCs are deleted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // VolumeBindingMode
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Volume Binding Mode",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                val bindingMode = storageClass.volumeBindingMode ?: "Immediate"
                val bindingModeIcon = if (bindingMode == "Immediate") FeatherIcons.Zap else FeatherIcons.Clock
                val bindingModeDescription = if (bindingMode == "Immediate") {
                    "Volume is provisioned immediately when PVC is created"
                } else {
                    "Volume is provisioned when first pod using the PVC is scheduled"
                }

                Surface(
                    color = if (bindingMode == "Immediate")
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = bindingModeIcon,
                            contentDescription = "Binding Mode",
                            tint = if (bindingMode == "Immediate")
                                MaterialTheme.colorScheme.secondary
                            else
                                MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = bindingMode,
                                fontWeight = FontWeight.Bold,
                                color = if (bindingMode == "Immediate")
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else
                                    MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = bindingModeDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (bindingMode == "Immediate")
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else
                                    MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Allowed Topologies
        if (!storageClass.allowedTopologies.isNullOrEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Allowed Topologies",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    storageClass.allowedTopologies?.forEachIndexed { index, topology ->
                        if (index > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        Text(
                            text = "Topology #${index + 1}",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(Modifier.height(4.dp))

                        topology.matchLabelExpressions?.forEach { expression ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    // Ключ виразу
                                    Text(
                                        text = expression.key ?: "",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(Modifier.height(2.dp))

                                    // Оператор і значення
                                    Row {
                                        Text(
                                            text = "In: ",
                                            fontStyle = FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )

                                        expression.values?.joinToString(", ")?.let { values ->
                                            SelectionContainer {
                                                Text(
                                                    text = values,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        } ?: Text("(none)")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Volumes will only be provisioned in the specified topology domains",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Parameters
        val parametersCount = storageClass.parameters?.size ?: 0
        if (parametersCount > 0) {
            Text(
                text = "Parameters ($parametersCount)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    storageClass.parameters?.entries?.sortedBy { it.key }?.forEach { (key, value) ->
                        // Визначаємо, чи є це потенційно секретне значення
                        val isSecret = key.contains("secret", ignoreCase = true) ||
                                key.contains("password", ignoreCase = true) ||
                                key.contains("key", ignoreCase = true)

                        val isLongValue = value.length > 50
                        var valueExpanded by remember { mutableStateOf(false) }

                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = key,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(150.dp)
                                )
                            }

                            Text(": ")

                            if (isSecret) {
                                var showValue by remember { mutableStateOf(false) }

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (showValue) {
                                            SelectionContainer {
                                                Text(value)
                                            }
                                        } else {
                                            Text("••••••••")
                                        }

                                        Spacer(Modifier.width(8.dp))

                                        IconButton(
                                            onClick = { showValue = !showValue },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (showValue) FeatherIcons.EyeOff else FeatherIcons.Eye,
                                                contentDescription = "Toggle Visibility",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            } else if (isLongValue) {
                                Column {
                                    SelectionContainer {
                                        Text(
                                            text = if (valueExpanded) value else value.take(50) + "...",
                                            modifier = Modifier.clickable { valueExpanded = !valueExpanded }
                                        )
                                    }
                                    if (!valueExpanded) {
                                        Text(
                                            text = "Click to expand",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { valueExpanded = true }
                                        )
                                    }
                                }
                            } else {
                                SelectionContainer {
                                    Text(value)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "These parameters are passed to the storage provisioner",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Allow Volume Expansion
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (storageClass.allowVolumeExpansion == true)
                            FeatherIcons.Maximize2
                        else
                            FeatherIcons.Minimize2,
                        contentDescription = "Volume Expansion",
                        tint = if (storageClass.allowVolumeExpansion == true)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Volume Expansion",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(4.dp))

                val expansionStatus = if (storageClass.allowVolumeExpansion == true)
                    "Allowed - Volumes can be expanded after creation"
                else
                    "Not Allowed - Volume size is fixed after creation"

                val expansionIcon = if (storageClass.allowVolumeExpansion == true)
                    FeatherIcons.Check
                else
                    FeatherIcons.X

                val expansionColor = if (storageClass.allowVolumeExpansion == true)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = expansionIcon,
                        contentDescription = "Status",
                        tint = expansionColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = expansionStatus,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Додаткова інформація для користувачів
                val additionalInfo = if (storageClass.allowVolumeExpansion == true) {
                    "You can increase the size of PVCs that use this storage class"
                } else {
                    "To change volume size, you will need to create a new PVC and migrate data"
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = additionalInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Mount Options
        val mountOptionsCount = storageClass.mountOptions?.size ?: 0
        if (mountOptionsCount > 0) {
            Text(
                text = "Mount Options ($mountOptionsCount)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 150.dp)
                    ) {
                        items(storageClass.mountOptions ?: emptyList()) { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = FeatherIcons.Terminal,
                                    contentDescription = "Mount Option",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                SelectionContainer {
                                    Text(
                                        text = option,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "These options will be used when mounting volumes provisioned using this storage class",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Мітки та анотації
        val labelsState = remember { mutableStateOf(false) }
        DetailSectionHeader(title = "Labels & Annotations", expanded = labelsState)

        if (labelsState.value) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Мітки з можливістю згортання/розгортання
                    var labelsExpanded by remember { mutableStateOf(true) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { labelsExpanded = !labelsExpanded }
                    ) {
                        Icon(
                            imageVector = if (labelsExpanded) ICON_DOWN else ICON_RIGHT,
                            contentDescription = "Toggle Labels",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Labels (${storageClass.metadata?.labels?.size ?: 0}):", fontWeight = FontWeight.Bold)
                    }

                    if (labelsExpanded) {
                        if (storageClass.metadata?.labels.isNullOrEmpty()) {
                            Text("No labels", modifier = Modifier.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                storageClass.metadata?.labels?.forEach { (key, value) ->
                                    Row {
                                        SelectionContainer {
                                            Text(
                                                text = key,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(": ")
                                        SelectionContainer {
                                            Text(value)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Анотації з можливістю згортання/розгортання
                    var annotationsExpanded by remember { mutableStateOf(true) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { annotationsExpanded = !annotationsExpanded }
                    ) {
                        Icon(
                            imageVector = if (annotationsExpanded) ICON_DOWN else ICON_RIGHT,
                            contentDescription = "Toggle Annotations",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Annotations (${storageClass.metadata?.annotations?.size ?: 0}):",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (annotationsExpanded) {
                        if (storageClass.metadata?.annotations.isNullOrEmpty()) {
                            Text("No annotations", modifier = Modifier.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                storageClass.metadata?.annotations?.entries?.sortedBy { it.key }
                                    ?.forEach { (key, value) ->
                                        val isLongValue = value.length > 50
                                        var valueExpanded by remember { mutableStateOf(false) }

                                        Row(verticalAlignment = Alignment.Top) {
                                            SelectionContainer {
                                                Text(
                                                    text = key,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.width(180.dp)
                                                )
                                            }

                                            Text(": ")

                                            if (isLongValue) {
                                                Column {
                                                    SelectionContainer {
                                                        Text(
                                                            text = if (valueExpanded) value else value.take(50) + "...",
                                                            modifier = Modifier.clickable {
                                                                valueExpanded = !valueExpanded
                                                            }
                                                        )
                                                    }
                                                    if (!valueExpanded) {
                                                        Text(
                                                            text = "Click to expand",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.clickable { valueExpanded = true }
                                                        )
                                                    }
                                                }
                                            } else {
                                                SelectionContainer {
                                                    Text(value)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun getProvisionerIcon(provisioner: String?): ImageVector {
    return when {
        provisioner == null -> FeatherIcons.HelpCircle
        provisioner.contains("aws") || provisioner.contains("ebs") -> SimpleIcons.Amazonaws
        provisioner.contains("azure") || provisioner.contains("microsoft") -> SimpleIcons.Microsoftazure
        provisioner.contains("gce") || provisioner.contains("gke") || provisioner.contains("google") -> SimpleIcons.Googlecloud
        provisioner.contains("csi") -> FeatherIcons.HardDrive
        provisioner.contains("ceph") -> SimpleIcons.Ceph
        provisioner.contains("rbd") -> FeatherIcons.Database
        provisioner.contains("nfs") -> FeatherIcons.Share2
        provisioner.contains("iscsi") -> FeatherIcons.Server
        provisioner.contains("hostpath") || provisioner.contains("local") -> FeatherIcons.Home
        provisioner.contains("gluster") -> SimpleIcons.Glitch
        provisioner.contains("vsphere") || provisioner.contains("vmware") -> SimpleIcons.Vmware
        provisioner.contains("openstack") || provisioner.contains("cinder") -> SimpleIcons.Openstack
        provisioner.contains("portworx") -> FeatherIcons.Box
        provisioner.contains("flex") -> FeatherIcons.Shuffle
        provisioner.contains("kubernetes.io") -> SimpleIcons.Kubernetes
        provisioner.contains("longhorn") -> SimpleIcons.Rancher
        provisioner.contains("digitalocean") -> SimpleIcons.Digitalocean
        provisioner.contains("linode") -> SimpleIcons.Linode
        provisioner.contains("scaleio") || provisioner.contains("dell") || provisioner.contains("emc") -> SimpleIcons.Dell
        provisioner.contains("netapp") -> SimpleIcons.Netapp
        provisioner.contains("openshift") || provisioner.contains("redhat") -> SimpleIcons.Redhat
        provisioner.contains("oracle") -> SimpleIcons.Oracle
        provisioner.contains("ibm") -> SimpleIcons.Ibm
        else -> FeatherIcons.Database
    }
}

// Function to get the description for the storage provisioner
@Composable
fun getProvisionerDescription(provisioner: String?): String? {
    return when {
        provisioner == null -> null
        provisioner.contains("kubernetes.io/aws-ebs") ->
            "AWS Elastic Block Store - Block storage for EC2 instances"

        provisioner.contains("ebs.csi.aws.com") ->
            "AWS EBS CSI Driver - Modern Container Storage Interface driver for AWS EBS"

        provisioner.contains("kubernetes.io/azure-disk") ->
            "Azure Disk - Managed disk for Azure Virtual Machines"

        provisioner.contains("disk.csi.azure.com") ->
            "Azure Disk CSI Driver - CSI driver for Azure Disk"

        provisioner.contains("kubernetes.io/azure-file") ->
            "Azure File - File storage on Azure, with SMB protocol support"

        provisioner.contains("file.csi.azure.com") ->
            "Azure File CSI Driver - CSI driver for Azure File"

        provisioner.contains("kubernetes.io/gce-pd") ->
            "Google Compute Engine Persistent Disk - Block storage for GCE"

        provisioner.contains("pd.csi.storage.gke.io") ->
            "GCE Persistent Disk CSI Driver - CSI driver for GCE Persistent Disk"

        provisioner.contains("kubernetes.io/cinder") ->
            "OpenStack Cinder - Block storage for OpenStack"

        provisioner.contains("cinder.csi.openstack.org") ->
            "OpenStack Cinder CSI Driver - CSI driver for OpenStack Cinder"

        provisioner.contains("kubernetes.io/vsphere-volume") ->
            "VMware vSphere Volume - Volume for VMware vSphere"

        provisioner.contains("csi.vsphere.vmware.com") ->
            "vSphere CSI Driver - CSI driver for VMware vSphere"

        provisioner.contains("kubernetes.io/glusterfs") ->
            "GlusterFS - Open-source distributed file system"

        provisioner.contains("kubernetes.io/rbd") ->
            "Ceph RBD (RADOS Block Device) - Block storage in Ceph"

        provisioner.contains("rbd.csi.ceph.com") ->
            "Ceph RBD CSI Driver - CSI driver for Ceph RBD"

        provisioner.contains("cephfs.csi.ceph.com") ->
            "CephFS CSI Driver - CSI driver for Ceph File System"

        provisioner.contains("kubernetes.io/nfs") ->
            "NFS (Network File System) - Traditional network file system"

        provisioner.contains("kubernetes.io/iscsi") ->
            "iSCSI - Standard for IP-based storage networking"

        provisioner.contains("kubernetes.io/portworx-volume") ->
            "Portworx - Distributed block storage for containers"

        provisioner.contains("kubernetes.io/no-provisioner") ->
            "No dynamic provisioner - For static volumes"

        provisioner.contains("kubernetes.io/hostpath") ->
            "HostPath - Local path on the node (for development only)"

        provisioner.contains("kubernetes.io/local-storage") ->
            "Local Storage - Local disks without dynamic provisioning"

        provisioner.contains("kubernetes.io/storageos") ->
            "StorageOS - Software-defined storage for cloud infrastructure"

        provisioner.contains("kubernetes.io/fc") ->
            "Fibre Channel - High-speed network protocol for SAN"

        provisioner.contains("longhorn.io") ->
            "Longhorn - Distributed block storage for Kubernetes"

        provisioner.contains("efs.csi.aws.com") ->
            "Amazon EFS CSI Driver - CSI driver for Amazon Elastic File System"

        provisioner.contains("fsx.csi.aws.com") ->
            "Amazon FSx CSI Driver - CSI driver for Amazon FSx"

        provisioner.contains("dobs.csi.digitalocean.com") ->
            "DigitalOcean Block Storage CSI Driver - Block storage for DigitalOcean"

        provisioner.contains("linode.com/block-storage") ->
            "Linode Block Storage - Block storage for Linode"

        provisioner.contains("csi.hetzner.cloud") ->
            "Hetzner Cloud CSI - CSI driver for Hetzner Cloud"

        provisioner.contains("io.juicedata.juicefs") ->
            "JuiceFS - Distributed file system for the cloud"

        provisioner.contains("nfs.csi.k8s.io") ->
            "NFS CSI Driver - CSI driver for NFS"

        provisioner.contains("openebs.io/local") ->
            "OpenEBS Local PV - Local storage for OpenEBS"

        provisioner.contains("openebs.io/jiva") ->
            "OpenEBS Jiva - Containerized block storage for OpenEBS"

        provisioner.contains("openebs.io/cstor") ->
            "OpenEBS cStor - High-performance storage for OpenEBS"

        provisioner.contains("flocker.io") ->
            "Flocker - Volume management for Docker"

        provisioner.contains("quobyte.com") ->
            "Quobyte - Software-defined file system for data centers"

        provisioner.contains("rancher.io/local-path") ->
            "Rancher Local Path Provisioner - Simplified driver for local paths"

        else -> "Third-party storage provider"
    }
}