import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertCircle
import compose.icons.feathericons.Check
import compose.icons.feathericons.CheckCircle
import compose.icons.feathericons.Clock
import compose.icons.feathericons.HardDrive
import compose.icons.feathericons.HelpCircle
import compose.icons.feathericons.Info
import compose.icons.feathericons.X
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadPodsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Pods", namespace) { cl, ns ->
        if (ns == null) cl.pods().inAnyNamespace().list().items else cl.pods().inNamespace(ns).list().items
    }

@Composable
fun PodDetailsView(pod: Pod, onShowLogsRequest: (containerName: String) -> Unit) {
    val showContainerDialog = remember { mutableStateOf(false) }
    val containers = remember(pod) { pod.spec?.containers ?: emptyList() }
    val showContainerStatuses = remember { mutableStateOf(true) }
    val showLabels = remember { mutableStateOf(false) }
    val showAnnotations = remember { mutableStateOf(false) }
    val showVolumes = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.Companion
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Header with logs button
        Row(
            modifier = Modifier.Companion.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Companion.CenterVertically
        ) {
            Text(
                text = "Pod Information",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.Companion.padding(bottom = 8.dp)
            )

            Button(
                onClick = {
                    when (containers.size) {
                        0 -> logger.warn("Pod ${pod.metadata?.name} has no containers.")
                        1 -> onShowLogsRequest(containers.first().name)
                        else -> showContainerDialog.value = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(ICON_LOGS, contentDescription = "View Logs")
                Spacer(Modifier.Companion.width(4.dp))
                Text("View Logs")
            }
        }

        // Container logs dialog
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

        // Basic pod information
        DetailRow("Name", pod.metadata?.name)
        DetailRow("Namespace", pod.metadata?.namespace)
        DetailRow("Status", pod.status?.phase)
        DetailRow("Node", pod.spec?.nodeName)
        DetailRow("Pod IP", pod.status?.podIP)
        DetailRow("Host IP", pod.status?.hostIP)
        DetailRow("Service Account", pod.spec?.serviceAccountName ?: pod.spec?.serviceAccount)
        DetailRow("Created", formatAge(pod.metadata?.creationTimestamp))
        DetailRow("Restarts", formatPodRestarts(pod.status?.containerStatuses))
        DetailRow("QoS Class", pod.status?.qosClass)

        // Special card for readiness/status information
        val phase = pod.status?.phase
        if (phase != null) {
            Spacer(Modifier.Companion.height(8.dp))
            Card(
                modifier = Modifier.Companion.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (phase.lowercase()) {
                        "running" -> MaterialTheme.colorScheme.tertiaryContainer
                        "pending" -> MaterialTheme.colorScheme.secondaryContainer
                        "succeeded" -> MaterialTheme.colorScheme.primaryContainer
                        "failed" -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.Companion.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                        Icon(
                            imageVector = when (phase.lowercase()) {
                                "running" -> FeatherIcons.Check
                                "pending" -> FeatherIcons.Clock
                                "succeeded" -> FeatherIcons.CheckCircle
                                "failed" -> FeatherIcons.AlertCircle
                                else -> FeatherIcons.HelpCircle
                            },
                            contentDescription = "Status",
                            tint = when (phase.lowercase()) {
                                "running" -> MaterialTheme.colorScheme.tertiary
                                "pending" -> MaterialTheme.colorScheme.secondary
                                "succeeded" -> MaterialTheme.colorScheme.primary
                                "failed" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.Companion.size(24.dp)
                        )
                        Spacer(Modifier.Companion.width(8.dp))
                        Text(
                            text = "Status: $phase",
                            fontWeight = FontWeight.Companion.Bold,
                            color = when (phase.lowercase()) {
                                "running" -> MaterialTheme.colorScheme.onTertiaryContainer
                                "pending" -> MaterialTheme.colorScheme.onSecondaryContainer
                                "succeeded" -> MaterialTheme.colorScheme.onPrimaryContainer
                                "failed" -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    // Additional status information
                    Spacer(Modifier.Companion.height(8.dp))

                    // Conditions
                    pod.status?.conditions?.forEach { condition ->
                        Row(
                            verticalAlignment = Alignment.Companion.CenterVertically,
                            modifier = Modifier.Companion.padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (condition.status?.equals("True", ignoreCase = true) == true)
                                    FeatherIcons.Check else FeatherIcons.X,
                                contentDescription = "Condition Status",
                                tint = if (condition.status?.equals("True", ignoreCase = true) == true)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.Companion.size(16.dp)
                            )
                            Spacer(Modifier.Companion.width(4.dp))
                            Text(
                                text = "${condition.type}: ${condition.status}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // If reason or message exists, show them
                    pod.status?.reason?.let {
                        Text(
                            text = "Reason: $it",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.Companion.padding(top = 4.dp)
                        )
                    }

                    pod.status?.message?.let {
                        Text(
                            text = "Message: $it",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.Companion.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Container statuses section
        Row(
            modifier = Modifier.Companion.fillMaxWidth()
                .clickable { showContainerStatuses.value = !showContainerStatuses.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.Companion.CenterVertically
        ) {
            Icon(
                imageVector = if (showContainerStatuses.value) ICON_DOWN else ICON_RIGHT,
                contentDescription = "Expand Container Statuses"
            )
            Text(
                text = "Container Statuses (${pod.status?.containerStatuses?.size ?: 0})",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (showContainerStatuses.value) {
            if (pod.status?.containerStatuses.isNullOrEmpty()) {
                Card(
                    modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.Companion.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.Companion.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = FeatherIcons.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.Companion.size(24.dp)
                        )
                        Spacer(Modifier.Companion.height(8.dp))
                        Text(
                            "No container statuses available for this pod",
                            fontWeight = FontWeight.Companion.Medium
                        )
                    }
                }
            } else {
                pod.status?.containerStatuses?.forEach { cs ->
                    Card(
                        modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (cs.ready == true)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        )
                    ) {
                        Column(modifier = Modifier.Companion.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                                Icon(
                                    imageVector = if (cs.ready == true) FeatherIcons.Check else FeatherIcons.X,
                                    contentDescription = "Container Status",
                                    tint = if (cs.ready == true)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.error,
                                    modifier = Modifier.Companion.size(16.dp)
                                )
                                Spacer(Modifier.Companion.width(8.dp))
                                Text(
                                    text = cs.name,
                                    fontWeight = FontWeight.Companion.Bold
                                )
                            }

                            Spacer(Modifier.Companion.height(4.dp))

                            DetailRow("Image", cs.image)
                            DetailRow("Ready", cs.ready?.toString())
                            DetailRow("Restarts", cs.restartCount?.toString())
                            DetailRow("State", cs.state?.let {
                                when {
                                    it.running != null -> "Running since ${formatAge(it.running.startedAt)}"
                                    it.waiting != null -> "Waiting (${it.waiting.reason})"
                                    it.terminated != null -> "Terminated (${it.terminated.reason}, Exit: ${it.terminated.exitCode})"
                                    else -> "Unknown"
                                }
                            })
                            DetailRow("Image ID", cs.imageID)

                            // Add state-specific details
                            cs.state?.waiting?.message?.let {
                                Spacer(Modifier.Companion.height(4.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.Companion.padding(4.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }

                            cs.state?.terminated?.let { terminated ->
                                Spacer(Modifier.Companion.height(4.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                ) {
                                    Column(modifier = Modifier.Companion.padding(4.dp)) {
                                        Text(
                                            text = "Exit Code: ${terminated.exitCode}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        terminated.signal?.let {
                                            Text(
                                                text = "Signal: $it",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Text(
                                            text = "Started: ${formatAge(terminated.startedAt)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "Finished: ${formatAge(terminated.finishedAt)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Volumes section
        Row(
            modifier = Modifier.Companion.fillMaxWidth()
                .clickable { showVolumes.value = !showVolumes.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.Companion.CenterVertically
        ) {
            Icon(
                imageVector = if (showVolumes.value) ICON_DOWN else ICON_RIGHT,
                contentDescription = "Expand Volumes"
            )
            Text(
                text = "Volumes (${pod.spec?.volumes?.size ?: 0})",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (showVolumes.value) {
            if (pod.spec?.volumes.isNullOrEmpty()) {
                Card(
                    modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.Companion.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.Companion.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = FeatherIcons.HardDrive,
                            contentDescription = "Volumes",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.Companion.size(24.dp)
                        )
                        Spacer(Modifier.Companion.height(8.dp))
                        Text(
                            "No volumes defined for this pod",
                            fontWeight = FontWeight.Companion.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.Companion.heightIn(max = 300.dp)
                ) {
                    items(pod.spec?.volumes ?: emptyList()) { volume ->
                        Card(
                            modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.Companion.padding(12.dp)) {
                                Text(
                                    text = volume.name ?: "Unnamed Volume",
                                    fontWeight = FontWeight.Companion.Bold
                                )

                                Spacer(Modifier.Companion.height(4.dp))

                                // Determine volume type and show specific details
                                when {
                                    volume.configMap != null -> {
                                        Text("Type: ConfigMap")
                                        Text("Name: ${volume.configMap.name ?: "Not specified"}")
                                    }

                                    volume.secret != null -> {
                                        Text("Type: Secret")
                                        Text("Secret Name: ${volume.secret.secretName ?: "Not specified"}")
                                    }

                                    volume.persistentVolumeClaim != null -> {
                                        Text("Type: Persistent Volume Claim")
                                        Text("Claim Name: ${volume.persistentVolumeClaim.claimName ?: "Not specified"}")
                                    }

                                    volume.emptyDir != null -> {
                                        Text("Type: EmptyDir")
                                        volume.emptyDir.medium?.let { Text("Medium: $it") }
                                    }

                                    volume.hostPath != null -> {
                                        Text("Type: HostPath")
                                        Text("Path: ${volume.hostPath.path ?: "Not specified"}")
                                    }

                                    else -> {
                                        Text("Type: Other volume type")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Labels section
        Row(
            modifier = Modifier.Companion.fillMaxWidth()
                .clickable { showLabels.value = !showLabels.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.Companion.CenterVertically
        ) {
            Icon(
                imageVector = if (showLabels.value) ICON_DOWN else ICON_RIGHT,
                contentDescription = "Expand Labels"
            )
            Text(
                text = "Labels (${pod.metadata?.labels?.size ?: 0})",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (showLabels.value) {
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.Companion.padding(12.dp)) {
                    if (pod.metadata?.labels.isNullOrEmpty()) {
                        Text("No labels found", modifier = Modifier.Companion.padding(vertical = 4.dp))
                    } else {
                        pod.metadata?.labels?.entries?.forEach { (key, value) ->
                            Row(
                                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Companion.SemiBold),
                                    modifier = Modifier.Companion.weight(0.4f)
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.Companion.weight(0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Annotations section
        Row(
            modifier = Modifier.Companion.fillMaxWidth()
                .clickable { showAnnotations.value = !showAnnotations.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.Companion.CenterVertically
        ) {
            Icon(
                imageVector = if (showAnnotations.value) ICON_DOWN else ICON_RIGHT,
                contentDescription = "Expand Annotations"
            )
            Text(
                text = "Annotations (${pod.metadata?.annotations?.size ?: 0})",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (showAnnotations.value) {
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.Companion.padding(12.dp)) {
                    if (pod.metadata?.annotations.isNullOrEmpty()) {
                        Text("No annotations found", modifier = Modifier.Companion.padding(vertical = 4.dp))
                    } else {
                        pod.metadata?.annotations?.entries?.sortedBy { it.key }?.forEach { (key, value) ->
                            Column(
                                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Companion.SemiBold),
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.Companion.padding(start = 8.dp, top = 2.dp),
                                    maxLines = 3,
                                    overflow = TextOverflow.Companion.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// === ДІАЛОГ ВИБОРУ КОНТЕЙНЕРА (M3) ===
@Composable
fun ContainerSelectionDialog(
    containers: List<String>, onDismiss: () -> Unit, onContainerSelected: (String) -> Unit
) {
    var selectedOption by remember { mutableStateOf(containers.firstOrNull() ?: "") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Select Container") }, text = {
        Column {
            containers.forEach { containerName ->
                Row(
                    Modifier.fillMaxWidth().clickable { selectedOption = containerName }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (containerName == selectedOption), onClick = { selectedOption = containerName })
                    Spacer(Modifier.width(8.dp))
                    Text(containerName)
                }
            }
        }
    }, confirmButton = {
        Button(
            onClick = { onContainerSelected(selectedOption) }, enabled = selectedOption.isNotEmpty()
        ) { Text("View Logs") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}