import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadPVCsFabric8(client: KubernetesClient?, namespace: String?) = fetchK8sResource(
    client, "PersistentVolumeClaims", namespace
) { cl, ns ->
    if (ns == null) cl.persistentVolumeClaims().inAnyNamespace().list().items else cl.persistentVolumeClaims()
        .inNamespace(ns).list().items
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
            HorizontalDivider(
                modifier = Modifier.Companion.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text("Labels:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.Companion.padding(start = 8.dp)) {
                pvc.metadata?.labels?.forEach { (key, value) ->
                    DetailRow(key, value)
                }
            }
        }

        // Additional information: Annotations
        if (!pvc.metadata?.annotations.isNullOrEmpty()) {
            HorizontalDivider(
                modifier = Modifier.Companion.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text("Annotations:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.Companion.padding(start = 8.dp)) {
                pvc.metadata?.annotations?.forEach { (key, value) ->
                    DetailRow(key, value)
                }
            }
        }

        // Additional information: Finalizers
        if (!pvc.metadata?.finalizers.isNullOrEmpty()) {
            HorizontalDivider(
                modifier = Modifier.Companion.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text("Finalizers:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.Companion.padding(start = 8.dp)) {
                pvc.metadata?.finalizers?.forEach { finalizer ->
                    DetailRow("Finalizer", finalizer)
                }
            }
        }

        // Additional information: Selector
        pvc.spec?.selector?.let { selector ->
            HorizontalDivider(
                modifier = Modifier.Companion.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text("Selector:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.Companion.padding(start = 8.dp)) {
                // Match Labels
                if (!selector.matchLabels.isNullOrEmpty()) {
                    Text(
                        "Match Labels:",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.Companion.padding(top = 4.dp)
                    )
                    selector.matchLabels.forEach { (key, value) ->
                        DetailRow("  $key", value)
                    }
                }

                // Match Expressions
                if (!selector.matchExpressions.isNullOrEmpty()) {
                    Text(
                        "Match Expressions:",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.Companion.padding(top = 4.dp)
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
            HorizontalDivider(
                modifier = Modifier.Companion.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text("Data Source:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.Companion.padding(start = 8.dp)) {
                DetailRow("Kind", dataSource.kind)
                DetailRow("Name", dataSource.name)
                DetailRow("API Group", dataSource.apiGroup ?: "<core>")
            }
        }

        // Additional information: Data Source Ref (newer API)
        pvc.spec?.dataSourceRef?.let { dataSourceRef ->
            HorizontalDivider(
                modifier = Modifier.Companion.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text("Data Source Reference:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.Companion.padding(start = 8.dp)) {
                DetailRow("Kind", dataSourceRef.kind)
                DetailRow("Name", dataSourceRef.name)
                DetailRow("API Group", dataSourceRef.apiGroup ?: "<core>")
                DetailRow("Namespace", dataSourceRef.namespace ?: "<same namespace>")
            }
        }

        // Additional information: Conditions
        if (!pvc.status?.conditions.isNullOrEmpty()) {
            HorizontalDivider(
                modifier = Modifier.Companion.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text("Conditions:", style = MaterialTheme.typography.titleMedium)

            pvc.status?.conditions?.forEach { condition ->
                Column(
                    modifier = Modifier.Companion.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(4.dp)
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
            HorizontalDivider(
                modifier = Modifier.Companion.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text("Owner References:", style = MaterialTheme.typography.titleMedium)

            pvc.metadata?.ownerReferences?.forEach { owner ->
                Column(
                    modifier = Modifier.Companion.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(4.dp)
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
        HorizontalDivider(
            modifier = Modifier.Companion.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text("Additional Metadata:", style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.Companion.padding(start = 8.dp)) {
            DetailRow("UID", pvc.metadata?.uid)
            DetailRow("Resource Version", pvc.metadata?.resourceVersion)
            DetailRow("Generation", pvc.metadata?.generation?.toString())
        }
    }
}