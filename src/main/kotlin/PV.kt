import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.fabric8.kubernetes.api.model.PersistentVolume
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadPVsFabric8(client: KubernetesClient?) = fetchK8sResource(client, "PersistentVolumes", null) { cl, _ ->
    cl.persistentVolumes().list().items
} // Cluster-scoped

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
        HorizontalDivider(
            modifier = Modifier.Companion.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text("Volume Source:", style = MaterialTheme.typography.titleMedium)

        Column(
            modifier = Modifier.Companion.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(4.dp)
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
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Companion.Bold),
                                modifier = Modifier.Companion.padding(top = 2.dp)
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
                            "No volume source details available",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.Companion.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}