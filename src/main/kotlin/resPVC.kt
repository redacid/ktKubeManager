import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
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
import androidx.compose.ui.text.font.FontWeight

suspend fun loadPVCsFabric8(client: KubernetesClient?, namespace: String?) = fetchK8sResource(
    client, "PersistentVolumeClaims", namespace
) { cl, ns ->
    if (ns == null) cl.persistentVolumeClaims().inAnyNamespace().list().items else cl.persistentVolumeClaims()
        .inNamespace(ns).list().items
}

@Composable
fun PVCDetailsView(pvc: PersistentVolumeClaim) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "PVC Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                DetailRow("Name", pvc.metadata?.name)
                DetailRow("Namespace", pvc.metadata?.namespace)
                DetailRow("Created", formatAge(pvc.metadata?.creationTimestamp))
                DetailRow("Volume", pvc.spec?.volumeName)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Статус та конфігурація
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (pvc.status?.phase?.lowercase()) {
                    "bound" -> MaterialTheme.colorScheme.primaryContainer
                    "pending" -> MaterialTheme.colorScheme.secondaryContainer
                    "lost" -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (pvc.status?.phase?.lowercase()) {
                            "bound" -> ICON_SUCCESS
                            "pending" -> ICON_CLOCK
                            "lost" -> ICON_WARNING
                            else -> ICON_HELP
                        },
                        contentDescription = "Status Icon",
                        tint = when (pvc.status?.phase?.lowercase()) {
                            "bound" -> MaterialTheme.colorScheme.primary
                            "pending" -> MaterialTheme.colorScheme.secondary
                            "lost" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Status: ${pvc.status?.phase ?: "Unknown"}",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Storage information
                Row {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Requested",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            pvc.spec?.resources?.requests?.get("storage")?.toString() ?: "N/A",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Allocated",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            pvc.status?.capacity?.get("storage")?.toString() ?: "Pending",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                DetailRow("Storage Class", pvc.spec?.storageClassName)
                DetailRow("Volume Mode", pvc.spec?.volumeMode)
                DetailRow("Access Modes", formatAccessModes(pvc.spec?.accessModes))
            }
        }

        // Conditions
        if (!pvc.status?.conditions.isNullOrEmpty()) {
            Spacer(Modifier.height(16.dp))
            val conditionsState = remember { mutableStateOf(false) }
            DetailSectionHeader(
                title = "Conditions (${pvc.status?.conditions?.size})",
                expanded = conditionsState
            )

            if (conditionsState.value) {
                pvc.status?.conditions?.forEach { condition ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (condition.status?.lowercase()) {
                                "true" -> MaterialTheme.colorScheme.primaryContainer
                                "false" -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when (condition.status?.lowercase()) {
                                        "true" -> ICON_SUCCESS
                                        "false" -> ICON_CLOSE
                                        else -> ICON_HELP
                                    },
                                    contentDescription = "Condition Status",
                                    tint = when (condition.status?.lowercase()) {
                                        "true" -> MaterialTheme.colorScheme.primary
                                        "false" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = condition.type,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(Modifier.height(4.dp))
                            DetailRow("Status", condition.status)
                            DetailRow("Last Transition", formatAge(condition.lastTransitionTime))
                            condition.message?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            condition.reason?.let { DetailRow("Reason", it) }
                        }
                    }
                }
            }
        }

        // Selector
        pvc.spec?.selector?.let { selector ->
            Spacer(Modifier.height(16.dp))
            val selectorState = remember { mutableStateOf(false) }
            DetailSectionHeader(title = "Volume Selector", expanded = selectorState)

            if (selectorState.value) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Match Labels
                        if (!selector.matchLabels.isNullOrEmpty()) {
                            Text(
                                "Match Labels",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            selector.matchLabels.forEach { (key, value) ->
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
                            }
                        }

                        // Match Expressions
                        if (!selector.matchExpressions.isNullOrEmpty()) {
                            if (!selector.matchLabels.isNullOrEmpty()) {
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(
                                "Match Expressions",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            selector.matchExpressions.forEach { expr ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            "${expr.key} ${expr.operator}",
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (!expr.values.isNullOrEmpty()) {
                                            Text(
                                                expr.values.joinToString(", "),
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Labels та анотації
        val metadataState = remember { mutableStateOf(false) }
        Spacer(Modifier.height(16.dp))
        DetailSectionHeader(
            title = "Metadata",
            expanded = metadataState
        )

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
                            "Labels (${pvc.metadata?.labels?.size ?: 0})",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (labelsExpanded) {
                        if (pvc.metadata?.labels.isNullOrEmpty()) {
                            Text(
                                "No labels defined",
                                modifier = Modifier.padding(start = 24.dp, top = 4.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                pvc.metadata?.labels?.forEach { (key, value) ->
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
                            "Annotations (${pvc.metadata?.annotations?.size ?: 0})",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (annotationsExpanded) {
                        if (pvc.metadata?.annotations.isNullOrEmpty()) {
                            Text(
                                "No annotations defined",
                                modifier = Modifier.padding(start = 24.dp, top = 4.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                pvc.metadata?.annotations?.forEach { (key, value) ->
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
                    if (!pvc.metadata?.finalizers.isNullOrEmpty()) {
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
                                "Finalizers (${pvc.metadata?.finalizers?.size})",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (finalizersExpanded) {
                            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                pvc.metadata?.finalizers?.forEach { finalizer ->
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