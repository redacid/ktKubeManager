import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.fabric8.kubernetes.api.model.PersistentVolume
import io.fabric8.kubernetes.client.KubernetesClient

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment


suspend fun loadPVsFabric8(client: KubernetesClient?) = fetchK8sResource(client, "PersistentVolumes", null) { cl, _ ->
    cl.persistentVolumes().list().items
} // Cluster-scoped

@Composable
fun PVDetailsView(pv: PersistentVolume) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "PV Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                DetailRow("Name", pv.metadata?.name)
                DetailRow("Created", formatAge(pv.metadata?.creationTimestamp))
                DetailRow("Storage Class", pv.spec?.storageClassName)
                DetailRow("Reclaim Policy", pv.spec?.persistentVolumeReclaimPolicy)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Статус та конфігурація
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (pv.status?.phase?.lowercase()) {
                    "bound" -> MaterialTheme.colorScheme.primaryContainer
                    "available" -> MaterialTheme.colorScheme.secondaryContainer
                    "released" -> MaterialTheme.colorScheme.tertiaryContainer
                    "failed" -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (pv.status?.phase?.lowercase()) {
                            "bound" -> ICON_LOCK
                            "available" -> ICON_CHECK
                            "released" -> ICON_UNLOCK
                            "failed" -> ICON_WARNING
                            else -> ICON_HELP
                        },
                        contentDescription = "Status Icon",
                        tint = when (pv.status?.phase?.lowercase()) {
                            "bound" -> MaterialTheme.colorScheme.primary
                            "available" -> MaterialTheme.colorScheme.secondary
                            "released" -> MaterialTheme.colorScheme.tertiary
                            "failed" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Status: ${pv.status?.phase ?: "Unknown"}",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Storage information
                Row {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Capacity",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            pv.spec?.capacity?.get("storage")?.toString() ?: "N/A",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Access Modes",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            formatAccessModes(pv.spec?.accessModes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                DetailRow("Volume Mode", pv.spec?.volumeMode)

                // Claim Reference
                pv.spec?.claimRef?.let { claim ->
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "Bound to Claim:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            SelectionContainer {
                                Text(
                                    "${claim.namespace}/${claim.name}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        // Volume Source
        Spacer(Modifier.height(16.dp))
        val volumeSourceState = remember { mutableStateOf(true) }
        DetailSectionHeader(title = "Volume Source", expanded = volumeSourceState)

        if (volumeSourceState.value) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    pv.spec?.let { spec ->
                        when {
                            spec.nfs != null -> {
                                DetailRow("Type", "NFS")
                                DetailRow("Server", spec.nfs.server)
                                DetailRow("Path", spec.nfs.path)
                                DetailRow("Read Only", spec.nfs.readOnly?.toString() ?: "false")
                            }
                            spec.hostPath != null -> {
                                DetailRow("Type", "HostPath")
                                DetailRow("Path", spec.hostPath.path)
                                DetailRow("Type", spec.hostPath.type ?: "<not specified>")
                            }
                            spec.gcePersistentDisk != null -> {
                                DetailRow("Type", "GCE Persistent Disk")
                                DetailRow("PD Name", spec.gcePersistentDisk.pdName)
                                DetailRow("FS Type", spec.gcePersistentDisk.fsType ?: "<not specified>")
                                DetailRow("Partition", spec.gcePersistentDisk.partition?.toString() ?: "0")
                                DetailRow("Read Only", spec.gcePersistentDisk.readOnly?.toString() ?: "false")
                            }
                            spec.awsElasticBlockStore != null -> {
                                DetailRow("Type", "AWS EBS")
                                DetailRow("Volume ID", spec.awsElasticBlockStore.volumeID)
                                DetailRow("FS Type", spec.awsElasticBlockStore.fsType ?: "<not specified>")
                                DetailRow("Partition", spec.awsElasticBlockStore.partition?.toString() ?: "0")
                                DetailRow("Read Only", spec.awsElasticBlockStore.readOnly?.toString() ?: "false")
                            }
                            spec.azureDisk != null -> {
                                DetailRow("Type", "Azure Disk")
                                DetailRow("Disk Name", spec.azureDisk.diskName)
                                DetailRow("Disk URI", spec.azureDisk.diskURI)
                                DetailRow("Kind", spec.azureDisk.kind ?: "<not specified>")
                                DetailRow("FS Type", spec.azureDisk.fsType ?: "<not specified>")
                                DetailRow("Caching Mode", spec.azureDisk.cachingMode ?: "<not specified>")
                                DetailRow("Read Only", spec.azureDisk.readOnly?.toString() ?: "false")
                            }
                            spec.azureFile != null -> {
                                DetailRow("Type", "Azure File")
                                DetailRow("Secret Name", spec.azureFile.secretName)
                                DetailRow("Share Name", spec.azureFile.shareName)
                                DetailRow("Read Only", spec.azureFile.readOnly?.toString() ?: "false")
                            }
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
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                    spec.csi.volumeAttributes.forEach { (key, value) ->
                                        DetailRow(key, value)
                                    }
                                }
                            }
                            // ... інші типи томів залишаються такими ж
                            else -> {
                                DetailRow("Type", "<unknown>")
                                Text(
                                    "No volume source details available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Metadata
        val metadataState = remember { mutableStateOf(false) }
        Spacer(Modifier.height(16.dp))
        DetailSectionHeader(title = "Metadata", expanded = metadataState)

        if (metadataState.value) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Labels
                    var labelsExpanded by remember { mutableStateOf(true) }
                    Row(
                        modifier = Modifier.clickable { labelsExpanded = !labelsExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (labelsExpanded) ICON_DOWN else ICON_RIGHT,
                            contentDescription = "Toggle Labels",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Labels (${pv.metadata?.labels?.size ?: 0})",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (labelsExpanded) {
                        if (pv.metadata?.labels.isNullOrEmpty()) {
                            Text(
                                "No labels defined",
                                modifier = Modifier.padding(start = 24.dp, top = 4.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                pv.metadata?.labels?.forEach { (key, value) ->
                                    Row {
                                        SelectionContainer {
                                            Text(
                                                text = key,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.width(120.dp)
                                            )
                                        }
                                        Text(": ")
                                        SelectionContainer {
                                            Text(value)
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Annotations
                    var annotationsExpanded by remember { mutableStateOf(true) }
                    Row(
                        modifier = Modifier.clickable { annotationsExpanded = !annotationsExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (annotationsExpanded) ICON_DOWN else ICON_RIGHT,
                            contentDescription = "Toggle Annotations",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Annotations (${pv.metadata?.annotations?.size ?: 0})",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (annotationsExpanded) {
                        if (pv.metadata?.annotations.isNullOrEmpty()) {
                            Text(
                                "No annotations defined",
                                modifier = Modifier.padding(start = 24.dp, top = 4.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                pv.metadata?.annotations?.forEach { (key, value) ->
                                    Column {
                                        Text(
                                            text = key,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        SelectionContainer {
                                            Text(
                                                text = value,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }

                    // Finalizers
                    if (!pv.metadata?.finalizers.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        var finalizersExpanded by remember { mutableStateOf(true) }
                        Row(
                            modifier = Modifier.clickable { finalizersExpanded = !finalizersExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (finalizersExpanded) ICON_DOWN else ICON_RIGHT,
                                contentDescription = "Toggle Finalizers",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Finalizers (${pv.metadata?.finalizers?.size})",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (finalizersExpanded) {
                            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                pv.metadata?.finalizers?.forEach { finalizer ->
                                    Text(finalizer)
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