import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.KubernetesClient

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*


suspend fun loadNamespacesFabric8(client: KubernetesClient?) =
    fetchK8sResource(client, "Namespaces", null) { cl, _ -> cl.namespaces().list().items } // Namespaces не фільтруються

@Composable
fun NamespaceDetailsView(ns: Namespace) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "Namespace Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                DetailRow("Name", ns.metadata?.name)
                DetailRow("Status", ns.status?.phase)
                DetailRow("Created", formatAge(ns.metadata?.creationTimestamp))
                DetailRow("UID", ns.metadata?.uid)
                DetailRow("Resource Version", ns.metadata?.resourceVersion)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Секція стану
        Text(
            text = "Status Details",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                val conditions = ns.status?.conditions ?: emptyList()
                if (conditions.isNotEmpty()) {
                    conditions.forEach { condition ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                condition.type ?: "Unknown",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            DetailRow("Status", condition.status)
                            condition.message?.let { message ->
                                SelectionContainer {
                                    Text(
                                        message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        if (condition != conditions.last()) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                } else {
                    Text(
                        "No conditions available",
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Метадані
        val metadataState = remember { mutableStateOf(false) }
        DetailSectionHeader(title = "Metadata", expanded = metadataState)

        if (metadataState.value) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Labels
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
                        Text(
                            "Labels (${ns.metadata?.labels?.size ?: 0}):",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (labelsExpanded) {
                        Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                            if (ns.metadata?.labels.isNullOrEmpty()) {
                                Text("No labels")
                            } else {
                                ns.metadata?.labels?.forEach { (key, value) ->
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

                    // Annotations
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
                            "Annotations (${ns.metadata?.annotations?.size ?: 0}):",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (annotationsExpanded) {
                        Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                            if (ns.metadata?.annotations.isNullOrEmpty()) {
                                Text("No annotations")
                            } else {
                                ns.metadata?.annotations?.forEach { (key, value) ->
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
                                        SelectionContainer {
                                            Text(value)
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }

                    // Finalizers
                    if (!ns.metadata?.finalizers.isNullOrEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Finalizers:",
                            fontWeight = FontWeight.Bold
                        )
                        Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                            ns.metadata?.finalizers?.forEach { finalizer ->
                                Text(finalizer)
                            }
                        }
                    }
                }
            }
        }
    }
}