import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Activity
import compose.icons.feathericons.Box
import compose.icons.feathericons.Check
import compose.icons.feathericons.Clock
import compose.icons.feathericons.Pause
import compose.icons.feathericons.Play
import compose.icons.feathericons.Terminal
import io.fabric8.kubernetes.api.model.batch.v1.CronJob
import io.fabric8.kubernetes.client.KubernetesClient
import java.time.Duration
import java.time.OffsetDateTime

suspend fun loadCronJobsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "CronJobs", namespace) { cl, ns ->
        if (ns == null) cl.batch().v1().cronjobs().inAnyNamespace().list().items else cl.batch().v1().cronjobs()
            .inNamespace(ns).list().items
    }

@Composable
fun CronJobDetailsView(cronJob: CronJob) {
    Column(
        modifier = Modifier.Companion
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "CronJob Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        DetailRow("Name", cronJob.metadata?.name)
        DetailRow("Namespace", cronJob.metadata?.namespace)
        DetailRow("Created", formatAge(cronJob.metadata?.creationTimestamp))

        // Розклад
        val schedule = cronJob.spec?.schedule ?: "* * * * *"
        Row(
            verticalAlignment = Alignment.Companion.CenterVertically,
            modifier = Modifier.Companion.padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = FeatherIcons.Clock,
                contentDescription = "Schedule",
                modifier = Modifier.Companion.size(18.dp)
            )
            Spacer(Modifier.Companion.width(8.dp))
            Text(
                text = "Schedule: ",
                fontWeight = FontWeight.Companion.SemiBold
            )
            SelectionContainer {
                Text(schedule)
            }
        }

        // Статус призупинення
        val suspended = cronJob.spec?.suspend ?: false
        Row(
            verticalAlignment = Alignment.Companion.CenterVertically,
            modifier = Modifier.Companion.padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = if (suspended) FeatherIcons.Pause else FeatherIcons.Play,
                contentDescription = "Suspension Status",
                tint = if (suspended)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary,
                modifier = Modifier.Companion.size(18.dp)
            )
            Spacer(Modifier.Companion.width(8.dp))
            Text(
                text = "Status: ",
                fontWeight = FontWeight.Companion.SemiBold
            )
            Text(
                text = if (suspended) "Suspended" else "Active",
                color = if (suspended)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        }

        // Додаткові параметри
        DetailRow("Concurrency Policy", cronJob.spec?.concurrencyPolicy ?: "Allow")

        cronJob.spec?.startingDeadlineSeconds?.let { deadline ->
            DetailRow("Starting Deadline", "${deadline}s")
        }

        cronJob.spec?.successfulJobsHistoryLimit?.let { limit ->
            DetailRow("Successful Jobs History Limit", limit.toString())
        }

        cronJob.spec?.failedJobsHistoryLimit?.let { limit ->
            DetailRow("Failed Jobs History Limit", limit.toString())
        }

        // Останній запуск
        cronJob.status?.lastScheduleTime?.let { lastScheduleTime ->
            DetailRow("Last Schedule Time", formatAge(lastScheduleTime))
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Секція історії запуску
        val historyState = remember { mutableStateOf(false) }
        DetailSectionHeader(
            title = "Status Timeline",
            expanded = historyState
        )

        if (historyState.value) {
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.Companion.padding(8.dp)) {
                    // Останні запуски
                    val lastScheduled = cronJob.status?.lastScheduleTime
                    val creationTime = cronJob.metadata?.creationTimestamp

                    Text("Timeline", fontWeight = FontWeight.Companion.Bold)
                    Spacer(Modifier.Companion.height(8.dp))

                    if (lastScheduled != null) {
                        // Часова лінія з останнім запуском
                        Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                            Box(
                                modifier = Modifier.Companion
                                    .size(16.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                            )

                            Box(
                                modifier = Modifier.Companion
                                    .height(2.dp)
                                    .width(40.dp)
                                    .background(MaterialTheme.colorScheme.primary)
                            )

                            Box(
                                modifier = Modifier.Companion
                                    .size(16.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                            )

                            Spacer(Modifier.Companion.width(16.dp))
                            Text("Last Schedule: ${formatAge(lastScheduled)}")
                        }

                        // Наступне виконання (приблизно) - обробка помилок винесена за межі композабельної функції
                        val nextRun = safeCalculateNextCronRun(schedule)
                        if (nextRun != null) {
                            Spacer(Modifier.Companion.height(16.dp))
                            Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                                Box(
                                    modifier = Modifier.Companion
                                        .size(16.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        )
                                )

                                Box(
                                    modifier = Modifier.Companion
                                        .height(2.dp)
                                        .width(40.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                )

                                Box(
                                    modifier = Modifier.Companion
                                        .size(16.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                )

                                Spacer(Modifier.Companion.width(16.dp))
                                Text(
                                    text = "Next Run: $nextRun (estimated)",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        Text("No schedule history available")
                    }

                    Spacer(Modifier.Companion.height(16.dp))

                    // Статистика активних/останніх запусків
                    Row(
                        modifier = Modifier.Companion.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            horizontalAlignment = Alignment.Companion.CenterHorizontally,
                            modifier = Modifier.Companion
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(8.dp)
                                .weight(1f)
                        ) {
                            Text(
                                text = cronJob.status?.active?.size?.toString() ?: "0",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Active Jobs",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(Modifier.Companion.width(16.dp))

                        Column(
                            horizontalAlignment = Alignment.Companion.CenterHorizontally,
                            modifier = Modifier.Companion
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(8.dp)
                                .weight(1f)
                        ) {
                            Text(
                                text = (cronJob.spec?.successfulJobsHistoryLimit ?: 3).toString(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "History Limit",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Секція шаблону Job
        val jobTemplateState = remember { mutableStateOf(false) }
        DetailSectionHeader(title = "Job Template", expanded = jobTemplateState)

        if (jobTemplateState.value) {
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.Companion.padding(8.dp)) {
                    // Деякі базові параметри шаблону Job
                    DetailRow(
                        "Parallelism",
                        cronJob.spec?.jobTemplate?.spec?.parallelism?.toString() ?: "1"
                    )

                    DetailRow(
                        "Completions",
                        cronJob.spec?.jobTemplate?.spec?.completions?.toString() ?: "1"
                    )

                    DetailRow(
                        "Backoff Limit",
                        cronJob.spec?.jobTemplate?.spec?.backoffLimit?.toString() ?: "6"
                    )

                    cronJob.spec?.jobTemplate?.spec?.activeDeadlineSeconds?.let { deadline ->
                        DetailRow("Active Deadline", "${deadline}s")
                    }

                    cronJob.spec?.jobTemplate?.spec?.ttlSecondsAfterFinished?.let { ttl ->
                        DetailRow("TTL After Finished", "${ttl}s")
                    }

                    DetailRow(
                        "Restart Policy",
                        cronJob.spec?.jobTemplate?.spec?.template?.spec?.restartPolicy ?: "Never"
                    )
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Секція шаблону Pod - контейнери
        val containerState = remember { mutableStateOf(false) }
        val containerCount = cronJob.spec?.jobTemplate?.spec?.template?.spec?.containers?.size ?: 0
        DetailSectionHeader(
            title = "Containers ($containerCount)",
            expanded = containerState
        )

        if (containerState.value) {
            LazyColumn(
                modifier = Modifier.Companion.heightIn(max = 400.dp)
            ) {
                items(cronJob.spec?.jobTemplate?.spec?.template?.spec?.containers ?: emptyList()) { container ->
                    Card(
                        modifier = Modifier.Companion
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.Companion.padding(8.dp)) {
                            // Заголовок контейнера
                            Row(
                                verticalAlignment = Alignment.Companion.CenterVertically,
                                modifier = Modifier.Companion.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = FeatherIcons.Box,
                                    contentDescription = "Container",
                                    modifier = Modifier.Companion.size(20.dp)
                                )
                                Spacer(Modifier.Companion.width(8.dp))
                                Text(
                                    text = container.name,
                                    fontWeight = FontWeight.Companion.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }

                            HorizontalDivider(modifier = Modifier.Companion.padding(vertical = 4.dp))

                            // Образ
                            SelectionContainer {
                                Text("Image: ${container.image}")
                            }

                            // Команда та аргументи
                            if (!container.command.isNullOrEmpty()) {
                                var commandExpanded by remember { mutableStateOf(false) }
                                Row(
                                    verticalAlignment = Alignment.Companion.Top,
                                    modifier = Modifier.Companion
                                        .padding(top = 4.dp)
                                        .clickable { commandExpanded = !commandExpanded }
                                ) {
                                    Icon(
                                        imageVector = if (commandExpanded) ICON_DOWN else ICON_RIGHT,
                                        contentDescription = "Toggle Command",
                                        modifier = Modifier.Companion.size(16.dp)
                                    )
                                    Spacer(Modifier.Companion.width(4.dp))
                                    Column {
                                        Text("Command:", fontWeight = FontWeight.Companion.Medium)
                                        if (commandExpanded) {
                                            SelectionContainer {
                                                Text(
                                                    text = container.command?.joinToString(" ") ?: "",
                                                    style = TextStyle(fontFamily = FontFamily.Companion.Monospace)
                                                )
                                            }

                                            // Аргументи
                                            if (!container.args.isNullOrEmpty()) {
                                                Spacer(Modifier.Companion.height(2.dp))
                                                Text("Arguments:", fontWeight = FontWeight.Companion.Medium)
                                                SelectionContainer {
                                                    Text(
                                                        text = container.args?.joinToString(" ") ?: "",
                                                        style = TextStyle(fontFamily = FontFamily.Companion.Monospace)
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
                                    verticalAlignment = Alignment.Companion.Top,
                                    modifier = Modifier.Companion.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = FeatherIcons.Terminal,
                                        contentDescription = "Arguments",
                                        modifier = Modifier.Companion.size(16.dp)
                                    )
                                    Spacer(Modifier.Companion.width(4.dp))
                                    Column {
                                        Text("Arguments:", fontWeight = FontWeight.Companion.Medium)
                                        SelectionContainer {
                                            Text(
                                                text = container.args?.joinToString(" ") ?: "",
                                                style = TextStyle(fontFamily = FontFamily.Companion.Monospace)
                                            )
                                        }
                                    }
                                }
                            }

                            // Змінні середовища
                            if (!container.env.isNullOrEmpty()) {
                                var envExpanded by remember { mutableStateOf(false) }
                                Row(
                                    verticalAlignment = Alignment.Companion.CenterVertically,
                                    modifier = Modifier.Companion
                                        .padding(top = 4.dp)
                                        .clickable { envExpanded = !envExpanded }
                                ) {
                                    Icon(
                                        imageVector = if (envExpanded) ICON_DOWN else ICON_RIGHT,
                                        contentDescription = "Toggle Environment Variables",
                                        modifier = Modifier.Companion.size(16.dp)
                                    )
                                    Spacer(Modifier.Companion.width(4.dp))
                                    Text(
                                        text = "Environment (${container.env?.size ?: 0})",
                                        fontWeight = FontWeight.Companion.Medium
                                    )
                                }

                                if (envExpanded) {
                                    Card(
                                        modifier = Modifier.Companion
                                            .fillMaxWidth()
                                            .padding(start = 24.dp, top = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Column(modifier = Modifier.Companion.padding(8.dp)) {
                                            container.env?.forEach { env ->
                                                Row {
                                                    Text(
                                                        text = "${env.name}:",
                                                        fontWeight = FontWeight.Companion.SemiBold,
                                                        modifier = Modifier.Companion.width(120.dp)
                                                    )
                                                    SelectionContainer {
                                                        Text(
                                                            text = env.value ?: env.valueFrom?.let { "(from source)" }
                                                            ?: "",
                                                            overflow = TextOverflow.Companion.Ellipsis
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.Companion.height(2.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            // Ресурси
                            container.resources?.let { resources ->
                                var resourcesExpanded by remember { mutableStateOf(false) }
                                Row(
                                    verticalAlignment = Alignment.Companion.CenterVertically,
                                    modifier = Modifier.Companion
                                        .padding(top = 4.dp)
                                        .clickable { resourcesExpanded = !resourcesExpanded }
                                ) {
                                    Icon(
                                        imageVector = if (resourcesExpanded) ICON_DOWN else ICON_RIGHT,
                                        contentDescription = "Toggle Resources",
                                        modifier = Modifier.Companion.size(16.dp)
                                    )
                                    Spacer(Modifier.Companion.width(4.dp))
                                    Text(
                                        text = "Resources",
                                        fontWeight = FontWeight.Companion.Medium
                                    )
                                }

                                if (resourcesExpanded && (resources.requests?.isNotEmpty() == true || resources.limits?.isNotEmpty() == true)) {
                                    Card(
                                        modifier = Modifier.Companion
                                            .fillMaxWidth()
                                            .padding(start = 24.dp, top = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Column(modifier = Modifier.Companion.padding(8.dp)) {
                                            resources.requests?.let { requests ->
                                                if (requests.isNotEmpty()) {
                                                    Text("Requests:", fontWeight = FontWeight.Companion.Bold)
                                                    requests.forEach { (key, value) ->
                                                        Text("  $key: $value")
                                                    }
                                                }
                                            }

                                            resources.limits?.let { limits ->
                                                if (limits.isNotEmpty()) {
                                                    Spacer(Modifier.Companion.height(4.dp))
                                                    Text("Limits:", fontWeight = FontWeight.Companion.Bold)
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

        Spacer(Modifier.Companion.height(16.dp))

        // Активні завдання
        val activeJobsState = remember { mutableStateOf(false) }
        val activeJobsCount = cronJob.status?.active?.size ?: 0
        DetailSectionHeader(
            title = "Active Jobs ($activeJobsCount)",
            expanded = activeJobsState
        )

        if (activeJobsState.value) {
            if (activeJobsCount > 0) {
                cronJob.status?.active?.forEach { activeJob ->
                    Card(
                        modifier = Modifier.Companion
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.Companion.padding(8.dp),
                            verticalAlignment = Alignment.Companion.CenterVertically
                        ) {
                            Icon(
                                imageVector = FeatherIcons.Activity,
                                contentDescription = "Active Job",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.Companion.size(24.dp)
                            )
                            Spacer(Modifier.Companion.width(8.dp))
                            Column {
                                Text(
                                    text = activeJob.name ?: "Unknown Job",
                                    fontWeight = FontWeight.Companion.Bold
                                )
                                activeJob.uid?.let { uid ->
                                    Text(
                                        text = "UID: $uid",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.Companion
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.Companion.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = FeatherIcons.Check,
                            contentDescription = "No Active Jobs",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.Companion.size(32.dp)
                        )
                        Spacer(Modifier.Companion.height(8.dp))
                        Text("No active jobs at the moment")
                    }
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Мітки та анотації
        val labelsState = remember { mutableStateOf(false) }
        DetailSectionHeader(title = "Labels & Annotations", expanded = labelsState)

        if (labelsState.value) {
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.Companion.padding(8.dp)) {
                    // Мітки з можливістю згортання/розгортання
                    var labelsExpanded by remember { mutableStateOf(true) }
                    Row(
                        verticalAlignment = Alignment.Companion.CenterVertically,
                        modifier = Modifier.Companion.clickable { labelsExpanded = !labelsExpanded }
                    ) {
                        Icon(
                            imageVector = if (labelsExpanded) ICON_DOWN else ICON_RIGHT,
                            contentDescription = "Toggle Labels",
                            modifier = Modifier.Companion.size(16.dp)
                        )
                        Spacer(Modifier.Companion.width(4.dp))
                        Text("Labels (${cronJob.metadata?.labels?.size ?: 0}):", fontWeight = FontWeight.Companion.Bold)
                    }

                    if (labelsExpanded) {
                        if (cronJob.metadata?.labels.isNullOrEmpty()) {
                            Text("No labels", modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp)) {
                                cronJob.metadata?.labels?.forEach { (key, value) ->
                                    Row {
                                        SelectionContainer {
                                            Text(
                                                text = key,
                                                fontWeight = FontWeight.Companion.Medium,
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

                    Spacer(Modifier.Companion.height(8.dp))

                    // Анотації з можливістю згортання/розгортання
                    var annotationsExpanded by remember { mutableStateOf(true) }
                    Row(
                        verticalAlignment = Alignment.Companion.CenterVertically,
                        modifier = Modifier.Companion.clickable { annotationsExpanded = !annotationsExpanded }
                    ) {
                        Icon(
                            imageVector = if (annotationsExpanded) ICON_DOWN else ICON_RIGHT,
                            contentDescription = "Toggle Annotations",
                            modifier = Modifier.Companion.size(16.dp)
                        )
                        Spacer(Modifier.Companion.width(4.dp))
                        Text(
                            "Annotations (${cronJob.metadata?.annotations?.size ?: 0}):",
                            fontWeight = FontWeight.Companion.Bold
                        )
                    }

                    if (annotationsExpanded) {
                        if (cronJob.metadata?.annotations.isNullOrEmpty()) {
                            Text("No annotations", modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp)) {
                                cronJob.metadata?.annotations?.entries?.sortedBy { it.key }?.forEach { (key, value) ->
                                    val isLongValue = value.length > 50
                                    var valueExpanded by remember { mutableStateOf(false) }

                                    Row(verticalAlignment = Alignment.Companion.Top) {
                                        SelectionContainer {
                                            Text(
                                                text = key,
                                                fontWeight = FontWeight.Companion.Medium,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.Companion.width(180.dp)
                                            )
                                        }

                                        Text(": ")

                                        if (isLongValue) {
                                            Column {
                                                SelectionContainer {
                                                    Text(
                                                        text = if (valueExpanded) value else value.take(50) + "...",
                                                        modifier = Modifier.Companion.clickable {
                                                            valueExpanded = !valueExpanded
                                                        }
                                                    )
                                                }
                                                if (!valueExpanded) {
                                                    Text(
                                                        text = "Click to expand",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.Companion.clickable { valueExpanded = true }
                                                    )
                                                }
                                            }
                                        } else {
                                            SelectionContainer {
                                                Text(value)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.Companion.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Безпечна функція для обчислення наступного запуску Cron
fun safeCalculateNextCronRun(cronExpression: String): String? {
    return try {
        calculateNextCronRun(cronExpression)
    } catch (e: Exception) {
        // Ігнорувати помилки при розрахунку наступного запуску
        null
    }
}

// Допоміжна функція для обчислення наступного запуску Cron
fun calculateNextCronRun(cronExpression: String): String? {
    // Спрощена реалізація для оцінки наступного запуску
    // У реальній системі тут потрібна була б повноцінна бібліотека для обробки Cron виразів

    // Розділяємо вираз на компоненти
    val components = cronExpression.split(" ")
    if (components.size < 5) return null

    val now = OffsetDateTime.now()

    // Оцінка - наступний запуск приблизно через годину
    // Це звичайно дуже спрощено, але для UI цього може бути достатньо
    val estimated = now.plusHours(1)

    return "through ~${formatDuration(Duration.between(now, estimated))}"
}