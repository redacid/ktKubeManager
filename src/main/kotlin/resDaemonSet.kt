import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.unit.max
import compose.icons.FeatherIcons
import compose.icons.feathericons.Box
import compose.icons.feathericons.Database
import compose.icons.feathericons.HardDrive
import compose.icons.feathericons.Server
import compose.icons.feathericons.Terminal
import io.fabric8.kubernetes.api.model.apps.DaemonSet
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadDaemonSetsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "DaemonSets", namespace) { cl, ns ->
        if (ns == null) cl.apps().daemonSets().inAnyNamespace().list().items else cl.apps().daemonSets().inNamespace(ns)
            .list().items
    }

@Composable
fun DaemonSetDetailsView(ds: DaemonSet) {
    Column(
        modifier = Modifier.Companion
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "DaemonSet Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        DetailRow("Name", ds.metadata?.name)
        DetailRow("Namespace", ds.metadata?.namespace)
        DetailRow("Created", formatAge(ds.metadata?.creationTimestamp))
        DetailRow("Desired Nodes", ds.status?.desiredNumberScheduled?.toString())
        DetailRow("Current Nodes", ds.status?.currentNumberScheduled?.toString())
        DetailRow("Ready Nodes", ds.status?.numberReady?.toString())
        DetailRow("Available Nodes", ds.status?.numberAvailable?.toString())
        DetailRow("Update Strategy", ds.spec?.updateStrategy?.type)

        Spacer(Modifier.Companion.height(16.dp))

        // Секція селектора
        val selectorState = remember { mutableStateOf(false) }
        DetailSectionHeader(title = "Selector", expanded = selectorState)

        if (selectorState.value) {
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.Companion.padding(8.dp)) {
                    ds.spec?.selector?.matchLabels?.let { matchLabels ->
                        if (matchLabels.isNotEmpty()) {
                            Text("Match Labels:", fontWeight = FontWeight.Companion.Bold)
                            matchLabels.forEach { (key, value) ->
                                Text("$key: $value")
                            }
                        }
                    }

                    ds.spec?.selector?.matchExpressions?.let { expressions ->
                        if (expressions.isNotEmpty()) {
                            Text("Match Expressions:", fontWeight = FontWeight.Companion.Bold)
                            expressions.forEach { expr ->
                                Text("${expr.key} ${expr.operator} [${expr.values?.joinToString(", ") ?: ""}]")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Секція контейнерів - покращена
        val containerState = remember { mutableStateOf(false) }
        DetailSectionHeader(
            title = "Containers (${ds.spec?.template?.spec?.containers?.size ?: 0})",
            expanded = containerState
        )

        if (containerState.value) {
            LazyColumn(
                modifier = Modifier.Companion.heightIn(max = 3300.dp)
            ) {
                items(ds.spec?.template?.spec?.containers ?: emptyList()) { container ->
                    Card(
                        modifier = Modifier.Companion
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.Companion.padding(8.dp)) {
                            // Заголовок контейнера з іконкою
                            Row(
                                verticalAlignment = Alignment.Companion.CenterVertically,
                                modifier = Modifier.Companion.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = ICON_BOX,
                                    contentDescription = "Container",
                                    tint = MaterialTheme.colorScheme.tertiary,
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

                            // Базова інформація
                            SelectionContainer {
                                Text("Image: ${container.image}")
                            }

                            // Порти - виведення у компактній формі
                            container.ports?.let { ports ->
                                if (ports.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.Companion.CenterVertically,
                                        modifier = Modifier.Companion.padding(top = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = ICON_SERVER,
                                            contentDescription = "Ports",
                                            modifier = Modifier.Companion.size(16.dp)
                                        )
                                        Spacer(Modifier.Companion.width(4.dp))
                                        Text(
                                            text = "Ports: ${ports.joinToString { "${it.containerPort}/${it.protocol ?: "TCP"}" }}"
                                        )
                                    }
                                }
                            }

                            // Команда з можливістю копіювання
                            container.command?.let { command ->
                                if (command.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.Companion.Top,
                                        modifier = Modifier.Companion.padding(top = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = ICON_TERMINAL,
                                            contentDescription = "Command",
                                            modifier = Modifier.Companion.size(16.dp)
                                        )
                                        Spacer(Modifier.Companion.width(4.dp))
                                        Column {
                                            Text("Command:", fontWeight = FontWeight.Companion.Medium)
                                            SelectionContainer {
                                                Text(
                                                    text = command.joinToString(" "),
                                                    style = TextStyle(fontFamily = FontFamily.Companion.Monospace)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Змінні середовища з оформленням
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
                                                        modifier = Modifier.Companion.width(IntrinsicSize.Max)
                                                    )
                                                    Spacer(Modifier.Companion.width(4.dp))
                                                    SelectionContainer {
                                                        Text(
                                                            text = env.value ?: "(from source)",
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

                            // Монтування томів з іконками
                            if (!container.volumeMounts.isNullOrEmpty()) {
                                var volumeExpanded by remember { mutableStateOf(false) }
                                Row(
                                    verticalAlignment = Alignment.Companion.CenterVertically,
                                    modifier = Modifier.Companion
                                        .padding(top = 4.dp)
                                        .clickable { volumeExpanded = !volumeExpanded }
                                ) {
                                    Icon(
                                        imageVector = if (volumeExpanded) ICON_DOWN else ICON_RIGHT,
                                        contentDescription = "Toggle Volume Mounts",
                                        modifier = Modifier.Companion.size(16.dp)
                                    )
                                    Spacer(Modifier.Companion.width(4.dp))
                                    Text(
                                        text = "Volume Mounts (${container.volumeMounts?.size ?: 0})",
                                        fontWeight = FontWeight.Companion.Medium
                                    )
                                }

                                if (volumeExpanded) {
                                    Card(
                                        modifier = Modifier.Companion
                                            .fillMaxWidth()
                                            .padding(start = 24.dp, top = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Column(modifier = Modifier.Companion.padding(8.dp)) {
                                            container.volumeMounts?.forEach { mount ->
                                                Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                                                    Icon(
                                                        imageVector = FeatherIcons.HardDrive,
                                                        contentDescription = "Volume",
                                                        modifier = Modifier.Companion.size(14.dp)
                                                    )
                                                    Spacer(Modifier.Companion.width(4.dp))
                                                    Text(
                                                        text = mount.name,
                                                        fontWeight = FontWeight.Companion.SemiBold,
                                                        modifier = Modifier.Companion.width(IntrinsicSize.Max)
                                                    )
                                                    Text(" → ")
                                                    SelectionContainer {
                                                        Text(mount.mountPath)
                                                    }
                                                    if (mount.readOnly == true) {
                                                        Spacer(Modifier.Companion.width(4.dp))
                                                        Text(
                                                            text = "(ro)",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.Companion.height(2.dp))
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

        // Секція томів
        val volumesState = remember { mutableStateOf(false) }
        DetailSectionHeader(title = "Volumes (${ds.spec?.template?.spec?.volumes?.size ?: 0})", expanded = volumesState)

        if (volumesState.value) {
            LazyColumn(
                modifier = Modifier.Companion.heightIn(max = 3300.dp)
            ) {
                items(ds.spec?.template?.spec?.volumes ?: emptyList()) { volume ->
                    Card(
                        modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.Companion.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                                Icon(
                                    imageVector = FeatherIcons.Database,
                                    contentDescription = "Volume",
                                    modifier = Modifier.Companion.size(16.dp)
                                )
                                Spacer(Modifier.Companion.width(8.dp))
                                Text(
                                    text = volume.name,
                                    fontWeight = FontWeight.Companion.Bold
                                )
                            }

                            Spacer(Modifier.Companion.height(4.dp))

                            // Визначаємо тип тому і його деталі
                            when {
                                volume.configMap != null -> {
                                    Text("Type: ConfigMap")
                                    Text("Name: ${volume.configMap?.name ?: ""}")
                                    if (volume.configMap?.optional == true) {
                                        Text("Optional: true")
                                    }
                                }

                                volume.secret != null -> {
                                    Text("Type: Secret")
                                    Text("Name: ${volume.secret?.secretName ?: ""}")
                                    if (volume.secret?.optional == true) {
                                        Text("Optional: true")
                                    }
                                }

                                volume.persistentVolumeClaim != null -> {
                                    Text("Type: PersistentVolumeClaim")
                                    Text("Claim Name: ${volume.persistentVolumeClaim?.claimName ?: ""}")
                                    if (volume.persistentVolumeClaim?.readOnly == true) {
                                        Text("Read Only: true")
                                    }
                                }

                                volume.hostPath != null -> {
                                    Text("Type: HostPath")
                                    Text("Path: ${volume.hostPath?.path ?: ""}")
                                    Text("Type: ${volume.hostPath?.type ?: "Directory"}")
                                }

                                volume.emptyDir != null -> {
                                    Text("Type: EmptyDir")
                                    volume.emptyDir?.medium?.let { Text("Medium: $it") }
                                    volume.emptyDir?.sizeLimit?.let { Text("Size Limit: $it") }
                                }

                                else -> Text("Type: Other volume type")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Мітки та анотації - покращений вивід
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
                        Text("Labels (${ds.metadata?.labels?.size ?: 0}):", fontWeight = FontWeight.Companion.Bold)
                    }

                    if (labelsExpanded) {
                        if (ds.metadata?.labels.isNullOrEmpty()) {
                            Text("No labels", modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp)) {
                                ds.metadata?.labels?.forEach { (key, value) ->
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
                            "Annotations (${ds.metadata?.annotations?.size ?: 0}):",
                            fontWeight = FontWeight.Companion.Bold
                        )
                    }

                    if (annotationsExpanded) {
                        if (ds.metadata?.annotations.isNullOrEmpty()) {
                            Text("No annotations", modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp)) {
                                ds.metadata?.annotations?.entries?.sortedBy { it.key }?.forEach { (key, value) ->
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

        // Умови (conditions)
        val conditionsState = remember { mutableStateOf(false) }
        DetailSectionHeader(title = "Conditions", expanded = conditionsState)

        if (conditionsState.value) {
            if (ds.status?.conditions.isNullOrEmpty()) {
                Card(
                    modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.Companion.padding(8.dp)) {
                        Text("No conditions available")
                    }
                }
            } else {
                ds.status?.conditions?.forEach { condition ->
                    Card(
                        modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (condition.status) {
                                "True" -> MaterialTheme.colorScheme.surfaceVariant
                                "False" -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Column(modifier = Modifier.Companion.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                                Icon(
                                    imageVector = if (condition.status == "True") ICON_SUCCESS else ICON_ERROR,
                                    contentDescription = "Condition Status",
                                    tint = if (condition.status == "True")
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.Companion.width(8.dp))
                                Text(
                                    text = condition.type ?: "",
                                    fontWeight = FontWeight.Companion.Bold
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