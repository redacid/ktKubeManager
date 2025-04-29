import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertTriangle
import io.fabric8.kubernetes.api.model.Event
import io.fabric8.kubernetes.client.KubernetesClient
import java.time.Duration
import java.time.OffsetDateTime

suspend fun loadEventsFabric8(client: KubernetesClient?, namespace: String? = null) =
    fetchK8sResource(client, "events", namespace) { c, ns ->
        if (ns == null) {
            c.v1().events().inAnyNamespace().list().items
        } else {
            c.v1().events().inNamespace(ns).list().items
        }
    }

@Composable
fun EventDetailsView(event: Event) {
    Column(
        modifier = Modifier.Companion
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "Event Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        DetailRow("Name", event.metadata?.name)
        DetailRow("Namespace", event.metadata?.namespace)
        DetailRow("Type", event.type)
        DetailRow("Reason", event.reason)
        DetailRow("Last Timestamp", formatAge(event.lastTimestamp ?: event.metadata?.creationTimestamp))
        DetailRow("Count", event.count?.toString() ?: "1")

        // Спеціальний блок для типу Warning
        if (event.type == "Warning") {
            Spacer(Modifier.Companion.height(8.dp))
            Card(
                modifier = Modifier.Companion.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier.Companion.padding(8.dp),
                    verticalAlignment = Alignment.Companion.CenterVertically
                ) {
                    Icon(
                        imageVector = FeatherIcons.AlertTriangle,
                        contentDescription = "Warning Event",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.Companion.size(24.dp)
                    )
                    Spacer(Modifier.Companion.width(8.dp))
                    Column {
                        Text(
                            "Warning Event",
                            fontWeight = FontWeight.Companion.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "This event indicates a potential issue that might require attention",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Пов'язаний об'єкт
        Text(
            text = "Involved Object",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.Companion.padding(12.dp)) {
                DetailRow("Kind", event.involvedObject?.kind)
                DetailRow("Name", event.involvedObject?.name)
                DetailRow("Namespace", event.involvedObject?.namespace)
                DetailRow("UID", event.involvedObject?.uid)

                // ResourceVersion та FieldPath показуємо, якщо вони доступні
                if (!event.involvedObject?.resourceVersion.isNullOrEmpty()) {
                    DetailRow("Resource Version", event.involvedObject?.resourceVersion)
                }

                if (!event.involvedObject?.fieldPath.isNullOrEmpty()) {
                    DetailRow("Field Path", event.involvedObject?.fieldPath)
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Повідомлення події
        Text(
            text = "Message",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.Companion.padding(12.dp)) {
                SelectionContainer {
                    Text(
                        text = event.message ?: "No message",
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Джерело події
        Text(
            text = "Source",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.Companion.padding(12.dp)) {
                DetailRow("Component", event.source?.component ?: "N/A")
                if (!event.source?.host.isNullOrEmpty()) {
                    DetailRow("Host", event.source?.host)
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Часові рамки
        val timeframesState = remember { mutableStateOf(false) }
        DetailSectionHeader(
            title = "Event Timeframes",
            expanded = timeframesState
        )

        if (timeframesState.value) {
            Card(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.Companion.padding(12.dp)) {
                    DetailRow("First Occurrence", formatAge(event.firstTimestamp))
                    DetailRow("Last Occurrence", formatAge(event.lastTimestamp))
                    DetailRow("Created", formatAge(event.metadata?.creationTimestamp))

                    // Додаємо Event Time, якщо воно доступне
                    if (event.eventTime != null) {
                        DetailRow("Event Time", formatAge(event.eventTime.toString()))
                    }

                    // Додаємо тривалість, якщо можемо обчислити
                    if (event.firstTimestamp != null && event.lastTimestamp != null) {
                        //try {
                        val firstTime = OffsetDateTime.parse(event.firstTimestamp)
                        val lastTime = OffsetDateTime.parse(event.lastTimestamp)
                        val duration = Duration.between(firstTime, lastTime)
                        val durationText = when {
                            duration.seconds < 1 -> "Less than a second"
                            duration.seconds < 60 -> "${duration.seconds}s"
                            duration.toMinutes() < 60 -> "${duration.toMinutes()}m ${duration.seconds % 60}s"
                            duration.toHours() < 24 -> "${duration.toHours()}h ${duration.toMinutes() % 60}m"
                            else -> "${duration.toDays()}d ${duration.toHours() % 24}h"
                        }
                        DetailRow("Duration", durationText)
                        //} catch (e: Exception) {
                        // Ігноруємо помилки парсингу дати
                        //}
                    }
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Метадані
        val metadataState = remember { mutableStateOf(false) }
        DetailSectionHeader(title = "Metadata", expanded = metadataState)

        if (metadataState.value) {
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
                        Text("Labels (${event.metadata?.labels?.size ?: 0}):", fontWeight = FontWeight.Companion.Bold)
                    }

                    if (labelsExpanded) {
                        if (event.metadata?.labels.isNullOrEmpty()) {
                            Text("No labels", modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp)) {
                                event.metadata?.labels?.forEach { (key, value) ->
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
                            "Annotations (${event.metadata?.annotations?.size ?: 0}):",
                            fontWeight = FontWeight.Companion.Bold
                        )
                    }

                    if (annotationsExpanded) {
                        if (event.metadata?.annotations.isNullOrEmpty()) {
                            Text("No annotations", modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp)) {
                                event.metadata?.annotations?.entries?.sortedBy { it.key }
                                    ?.forEach { (key, value) ->
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
                                                            modifier = Modifier.Companion.clickable {
                                                                valueExpanded = true
                                                            }
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