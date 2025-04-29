import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Box
import compose.icons.feathericons.Clock
import compose.icons.feathericons.HardDrive
import compose.icons.feathericons.Terminal
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadJobsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Jobs", namespace) { cl, ns ->
        if (ns == null) cl.batch().v1().jobs().inAnyNamespace().list().items else cl.batch().v1().jobs().inNamespace(ns)
            .list().items
    }

@Composable
fun JobDetailsView(job: io.fabric8.kubernetes.api.model.batch.v1.Job) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "Job Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        DetailRow("Name", job.metadata?.name)
        DetailRow("Namespace", job.metadata?.namespace)
        DetailRow("Created", formatAge(job.metadata?.creationTimestamp))

        // Статус завершення
        val completions = job.spec?.completions ?: 1
        val succeeded = job.status?.succeeded ?: 0
        val isCompleted = succeeded >= completions
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = "Completion Status: ",
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = if (isCompleted) ICON_SUCCESS else FeatherIcons.Clock,
                contentDescription = "Completion Status",
                tint = if (isCompleted)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (isCompleted) "Completed" else "In Progress",
                color = if (isCompleted)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }

        DetailRow("Completions", "$succeeded / $completions")
        DetailRow("Parallelism", job.spec?.parallelism?.toString() ?: "1")
        DetailRow("Active", job.status?.active?.toString() ?: "0")
        DetailRow("Failed", job.status?.failed?.toString() ?: "0")

        // Політика завершення
        job.spec?.backoffLimit?.let { backoffLimit ->
            DetailRow("Backoff Limit", backoffLimit.toString())
        }

        job.spec?.activeDeadlineSeconds?.let { deadline ->
            DetailRow("Active Deadline", "${deadline}s")
        }

        DetailRow("Completion Mode", job.spec?.completionMode ?: "NonIndexed")
        DetailRow("Restart Policy", job.spec?.template?.spec?.restartPolicy ?: "Never")

        Spacer(Modifier.height(16.dp))

        // Селектор
        val selectorState = remember { mutableStateOf(false) }
        DetailSectionHeader(title = "Selector", expanded = selectorState)

        if (selectorState.value) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    job.spec?.selector?.matchLabels?.let { matchLabels ->
                        if (matchLabels.isNotEmpty()) {
                            Text("Match Labels:", fontWeight = FontWeight.Bold)
                            matchLabels.forEach { (key, value) ->
                                Text("$key: $value")
                            }
                        }
                    }

                    job.spec?.selector?.matchExpressions?.let { expressions ->
                        if (expressions.isNotEmpty()) {
                            Text("Match Expressions:", fontWeight = FontWeight.Bold)
                            expressions.forEach { expr ->
                                Text("${expr.key} ${expr.operator} [${expr.values?.joinToString(", ") ?: ""}]")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Секція шаблону Pod - контейнери
        val containerState = remember { mutableStateOf(false) }
        val containerCount = job.spec?.template?.spec?.containers?.size ?: 0
        DetailSectionHeader(
            title = "Containers ($containerCount)",
            expanded = containerState
        )

        if (containerState.value) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(job.spec?.template?.spec?.containers ?: emptyList()) { container ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            // Заголовок контейнера
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = FeatherIcons.Box,
                                    contentDescription = "Container",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = container.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Образ
                            SelectionContainer {
                                Text("Image: ${container.image}")
                            }

                            // Команда та аргументи
                            if (!container.command.isNullOrEmpty()) {
                                var commandExpanded by remember { mutableStateOf(false) }
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clickable { commandExpanded = !commandExpanded }
                                ) {
                                    Icon(
                                        imageVector = if (commandExpanded) ICON_DOWN else ICON_RIGHT,
                                        contentDescription = "Toggle Command",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Column {
                                        Text("Command:", fontWeight = FontWeight.Medium)
                                        if (commandExpanded) {
                                            SelectionContainer {
                                                Text(
                                                    text = container.command?.joinToString(" ") ?: "",
                                                    style = TextStyle(fontFamily = FontFamily.Monospace)
                                                )
                                            }

                                            // Аргументи
                                            if (!container.args.isNullOrEmpty()) {
                                                Spacer(Modifier.height(2.dp))
                                                Text("Arguments:", fontWeight = FontWeight.Medium)
                                                SelectionContainer {
                                                    Text(
                                                        text = container.args?.joinToString(" ") ?: "",
                                                        style = TextStyle(fontFamily = FontFamily.Monospace)
                                                    )
                                                }
                                            }
                                        } else {
                                            val commandPreview = (container.command?.joinToString(" ") ?: "").let {
                                                if (it.length > 40) it.take(40) + "..." else it
                                            }
                                            Text(commandPreview)
                                        }
                                    }
                                }
                            } else if (!container.args.isNullOrEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = FeatherIcons.Terminal,
                                        contentDescription = "Arguments",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Column {
                                        Text("Arguments:", fontWeight = FontWeight.Medium)
                                        SelectionContainer {
                                            Text(
                                                text = container.args?.joinToString(" ") ?: "",
                                                style = TextStyle(fontFamily = FontFamily.Monospace)
                                            )
                                        }
                                    }
                                }
                            }

                            // Змінні середовища
                            if (!container.env.isNullOrEmpty()) {
                                var envExpanded by remember { mutableStateOf(false) }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clickable { envExpanded = !envExpanded }
                                ) {
                                    Icon(
                                        imageVector = if (envExpanded) ICON_DOWN else ICON_RIGHT,
                                        contentDescription = "Toggle Environment Variables",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Environment (${container.env?.size ?: 0})",
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (envExpanded) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 24.dp, top = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            container.env?.forEach { env ->
                                                Row {
                                                    Text(
                                                        text = "${env.name}:",
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.width(120.dp)
                                                    )
                                                    SelectionContainer {
                                                        Text(
                                                            text = env.value ?: env.valueFrom?.let { "(from source)" }
                                                            ?: "",
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.height(2.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            // Монтування томів
                            if (!container.volumeMounts.isNullOrEmpty()) {
                                var volumeExpanded by remember { mutableStateOf(false) }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clickable { volumeExpanded = !volumeExpanded }
                                ) {
                                    Icon(
                                        imageVector = if (volumeExpanded) ICON_DOWN else ICON_RIGHT,
                                        contentDescription = "Toggle Volume Mounts",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Volume Mounts (${container.volumeMounts?.size ?: 0})",
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (volumeExpanded) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 24.dp, top = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            container.volumeMounts?.forEach { mount ->
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = FeatherIcons.HardDrive,
                                                        contentDescription = "Volume",
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = mount.name,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.width(100.dp)
                                                    )
                                                    Text(" → ")
                                                    SelectionContainer {
                                                        Text(mount.mountPath)
                                                    }
                                                    if (mount.readOnly == true) {
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(
                                                            text = "(ro)",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.height(2.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            // Ресурси
                            container.resources?.let { resources ->
                                var resourcesExpanded by remember { mutableStateOf(false) }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clickable { resourcesExpanded = !resourcesExpanded }
                                ) {
                                    Icon(
                                        imageVector = if (resourcesExpanded) ICON_DOWN else ICON_RIGHT,
                                        contentDescription = "Toggle Resources",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Resources",
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (resourcesExpanded && (resources.requests?.isNotEmpty() == true || resources.limits?.isNotEmpty() == true)) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 24.dp, top = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            resources.requests?.let { requests ->
                                                if (requests.isNotEmpty()) {
                                                    Text("Requests:", fontWeight = FontWeight.Bold)
                                                    requests.forEach { (key, value) ->
                                                        Text("  $key: $value")
                                                    }
                                                }
                                            }

                                            resources.limits?.let { limits ->
                                                if (limits.isNotEmpty()) {
                                                    Spacer(Modifier.height(4.dp))
                                                    Text("Limits:", fontWeight = FontWeight.Bold)
                                                    limits.forEach { (key, value) ->
                                                        Text("  $key: $value")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Поди
        val podsState = remember { mutableStateOf(false) }
        DetailSectionHeader(
            title = "Status Timeline",
            expanded = podsState
        )

        if (podsState.value) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row {
                        // Графічне представлення статусу
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(120.dp)
                        ) {
                            Text("Status", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))

                            val status = job.status
                            val startTime = status?.startTime
                            val completionTime = status?.completionTime

                            // Status timeline
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        )
                                )

                                Box(
                                    modifier = Modifier
                                        .height(2.dp)
                                        .weight(1f)
                                        .background(MaterialTheme.colorScheme.primary)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(
                                            color = if (completionTime != null)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Start",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(60.dp)
                                )
                                Text(
                                    text = "Finish",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(60.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }

                        Spacer(Modifier.width(16.dp))

                        // Деталі часу
                        Column {
                            Text("Timing", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))

                            DetailRow("Start Time", formatAge(job.status?.startTime))

                            if (job.status?.completionTime != null) {
                                DetailRow("Completion Time", formatAge(job.status?.completionTime))

                                // Розрахунок тривалості - без try-catch навколо composable функцій
                                val durationText =
                                    calculateJobDuration(job.status?.startTime, job.status?.completionTime)
                                DetailRow("Duration", durationText)
                            } else {
                                DetailRow("Completion Time", "Не завершено")
                            }
                        }
                    }
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
                        Text("Labels (${job.metadata?.labels?.size ?: 0}):", fontWeight = FontWeight.Bold)
                    }

                    if (labelsExpanded) {
                        if (job.metadata?.labels.isNullOrEmpty()) {
                            Text("No labels", modifier = Modifier.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                job.metadata?.labels?.forEach { (key, value) ->
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
                        Text("Annotations (${job.metadata?.annotations?.size ?: 0}):", fontWeight = FontWeight.Bold)
                    }

                    if (annotationsExpanded) {
                        if (job.metadata?.annotations.isNullOrEmpty()) {
                            Text("No annotations", modifier = Modifier.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                job.metadata?.annotations?.entries?.sortedBy { it.key }?.forEach { (key, value) ->
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
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Умови (conditions)
        val conditionsState = remember { mutableStateOf(false) }
        DetailSectionHeader(title = "Conditions", expanded = conditionsState)

        if (conditionsState.value) {
            if (job.status?.conditions.isNullOrEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("No conditions available")
                    }
                }
            } else {
                job.status?.conditions?.forEach { condition ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (condition.status) {
                                "True" -> MaterialTheme.colorScheme.surfaceVariant
                                "False" -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (condition.status == "True") ICON_SUCCESS else ICON_ERROR,
                                    contentDescription = "Condition Status",
                                    tint = if (condition.status == "True")
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = condition.type ?: "",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text("Status: ${condition.status}")
                            Text("Last Transition: ${formatAge(condition.lastTransitionTime)}")
                            condition.message?.let { Text("Message: $it") }
                            condition.reason?.let { Text("Reason: $it") }
                        }
                    }
                }
            }
        }
    }
}