import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check
import compose.icons.feathericons.HardDrive
import compose.icons.feathericons.Info
import compose.icons.feathericons.X
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val LOG_LINES_TO_TAIL = 50
suspend fun loadPodsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Pods", namespace) { cl, ns ->
        if (ns == null) cl.pods().inAnyNamespace().list().items else cl.pods().inNamespace(ns).list().items
    }

@Preview
@Composable
fun PodDetailsView(pod: Pod,
                   onShowLogsRequest: (containerName: String) -> Unit,
                   onOwnerClick: ((kind: String, name: String, namespace: String?) -> Unit)? = null
) {
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
                modifier = Modifier.Companion.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
            PodActions(
                pod = pod,
                onShowLogsRequest = onShowLogsRequest,
                showContainerDialog = showContainerDialog,
                portForwardService = portForwardService,
                kubernetesClient = activeClient
            )
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
        pod.metadata?.ownerReferences?.firstOrNull()?.let { owner ->
            Button(
                onClick = { onOwnerClick?.invoke(
                    owner.kind,
                    owner.name,
                    pod.metadata?.namespace
                )},
                colors = ButtonDefaults.buttonColors()

            ) {
                pod.metadata?.ownerReferences?.forEach { owner ->
                    Text("Controlled By ${owner.kind} / ${owner.name}")
                }
            }
        }

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
                                "running" -> ICON_RUN
                                "pending" -> ICON_CLOCK
                                "succeeded" -> ICON_CHECK
                                "failed" -> ICON_WARNING
                                else -> ICON_HELP
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
                                    //fontWeight = FontWeight.Companion.Bold
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
                    modifier = Modifier.Companion.heightIn(max = 3300.dp)
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
                                    style = MaterialTheme.typography.bodyMedium.copy(/*fontWeight = FontWeight.Companion.SemiBold*/),
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
                            Row(
                                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = key,
                                    fontWeight = FontWeight.Companion.Bold,
                                    style = MaterialTheme.typography.bodyMedium.copy(/*fontWeight = FontWeight.Companion.SemiBold*/),
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

// === ДІАЛОГ ВИБОРУ КОНТЕЙНЕРА ===
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
    var logJob by remember { mutableStateOf<Job?>(null) }
    var debugCounter by remember { mutableStateOf(0) }
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
                    logger.info("Polling logs - iteration ${++debugCounter}")

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
//                text = "Logs: $namespace/$podName [$containerName] (${if (debugCounter > 0) "Active" else "Inactive"})",
                text = "[$containerName] (${if (debugCounter > 0) "Active" else "Inactive"})",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = followLogs.value, onCheckedChange = { followLogs.value = it })
                Text("Follow", style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                            fontFamily = FontFamily.Companion.Monospace, color = MaterialTheme.colorScheme.onSurface
                        ), modifier = Modifier.padding(8.dp)
                    )
                }
                VerticalScrollbar(
                    modifier = Modifier.Companion.fillMaxHeight(), adapter = rememberScrollbarAdapter(scrollState)
                )
            }
        }
    }
}

@Composable
private fun PodActions(
    pod: Pod,
    onShowLogsRequest: (String) -> Unit,
    portForwardService: PortForwardService,
    showContainerDialog: MutableState<Boolean>,
    kubernetesClient: KubernetesClient?
) {
    var showPortForwardDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }

    val containers = pod.spec?.containers ?: emptyList()
    val containerPorts = containers.flatMap { it.ports ?: emptyList() }.map { it.containerPort }

    Row(
        modifier = Modifier.wrapContentSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Кнопка Port Forward
        Button(
            onClick = { showPortForwardDialog = true },
            enabled = kubernetesClient != null && containerPorts.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Icon(
                imageVector = ICON_SERVER,
                contentDescription = "Port Forward",
            )
            Spacer(Modifier.width(4.dp))
            Text("Port Forward")
        }
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

    // Діалог для налаштування Port Forward
    PortForwardDialog(
        isOpen = showPortForwardDialog,
        namespace = pod.metadata?.namespace ?: "",
        podName = pod.metadata?.name ?: "",
        availableContainerPorts = containerPorts.distinct().sorted(),
        onDismiss = { showPortForwardDialog = false },
        onConfirm = { localPort, podPort, bindAddress ->
            // Перевіряємо наявність клієнта
            kubernetesClient?.let { client ->
                // Отримуємо дані з поду
                val namespace = pod.metadata?.namespace ?: return@let
                val podName = pod.metadata?.name ?: return@let

                // Запускаємо port-forward
                try {
                    portForwardService.startPortForward(
                        client = client,
                        namespace = namespace,
                        podName = podName,
                        localPort = localPort,
                        podPort = podPort,
                        bindAddress = bindAddress
                    )
                } catch (e: Exception) {
                    // Обробка помилок - показуємо діалог
                    errorMessage = e.message ?: "Невідома помилка при створенні port-forward"
                    showErrorDialog = true
                }
            }
        }
    )

    // Показуємо діалогове вікно з помилкою
    if (showErrorDialog && errorMessage != null) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Помилка port-forward") },
            text = { Text(errorMessage!!) },
            confirmButton = {
                Button(onClick = { showErrorDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

