import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.*
import io.fabric8.kubernetes.api.model.Probe
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadReplicaSetsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "ReplicaSets", namespace) { cl, ns ->
        if (ns == null) cl.apps().replicaSets().inAnyNamespace().list().items else cl.apps().replicaSets()
            .inNamespace(ns).list().items
    }

@Composable
fun ReplicaSetDetailsView(replicaSet: ReplicaSet) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "ReplicaSet Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        DetailRow("Name", replicaSet.metadata?.name)
        DetailRow("Namespace", replicaSet.metadata?.namespace)
        DetailRow("Created", formatAge(replicaSet.metadata?.creationTimestamp))

        // Статус та кількість реплік
        val desiredReplicas = replicaSet.spec?.replicas ?: 0
        val availableReplicas = replicaSet.status?.availableReplicas ?: 0
        val readyReplicas = replicaSet.status?.readyReplicas ?: 0

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            // Індикатор прогресу
            val progress = if (desiredReplicas > 0) {
                readyReplicas.toFloat() / desiredReplicas.toFloat()
            } else {
                1f
            }

            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(48.dp)
            ) {
                CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 4.dp,
                )
                CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = if (progress >= 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                strokeWidth = 4.dp,
                )
                Text(
                    text = "$readyReplicas/$desiredReplicas",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(16.dp))

            Column {
                Text(
                    text = if (readyReplicas >= desiredReplicas && desiredReplicas > 0)
                        "Ready"
                    else
                        "Scaling",
                    fontWeight = FontWeight.Bold,
                    color = if (readyReplicas >= desiredReplicas && desiredReplicas > 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.secondary
                )

                Row {
                    Text("Desired: $desiredReplicas • ")
                    Text("Available: $availableReplicas • ")
                    Text("Ready: $readyReplicas")
                }

                if (replicaSet.status?.fullyLabeledReplicas != null) {
                    Text("Fully Labeled: ${replicaSet.status?.fullyLabeledReplicas}")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Власник
        replicaSet.metadata?.ownerReferences?.firstOrNull()?.let { owner ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = FeatherIcons.Link,
                        contentDescription = "Owner",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Controlled by: ${owner.kind} ${owner.name}",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Row {
                            Text(
                                text = "Controller: ${owner.controller ?: false}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "UID: ${owner.uid}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Секція селекторів
        val selectorState = remember { mutableStateOf(false) }
        DetailSectionHeader(title = "Pod Selector", expanded = selectorState)

        if (selectorState.value) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    replicaSet.spec?.selector?.matchLabels?.let { matchLabels ->
                        if (matchLabels.isNotEmpty()) {
                            Text("Match Labels:", fontWeight = FontWeight.Bold)
                            matchLabels.forEach { (key, value) ->
                                Row {
                                    SelectionContainer {
                                        Text(
                                            text = key,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(150.dp)
                                        )
                                    }
                                    Text("= ")
                                    SelectionContainer { Text(value) }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    replicaSet.spec?.selector?.matchExpressions?.let { expressions ->
                        if (expressions.isNotEmpty()) {
                            Text("Match Expressions:", fontWeight = FontWeight.Bold)
                            expressions.forEach { expr ->
                                Row {
                                    SelectionContainer {
                                        Text(
                                            text = expr.key,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.width(150.dp)
                                        )
                                    }
                                    Text("${expr.operator} ")
                                    SelectionContainer {
                                        Text(expr.values?.joinToString(", ") ?: "")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Секція шаблону Pod - контейнери
        val containerState = remember { mutableStateOf(false) }
        val containerCount = replicaSet.spec?.template?.spec?.containers?.size ?: 0
        DetailSectionHeader(
            title = "Pod Template Containers ($containerCount)",
            expanded = containerState
        )

        if (containerState.value) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 3400.dp)
            ) {
                items(replicaSet.spec?.template?.spec?.containers ?: emptyList()) { container ->
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

                            // Порти
                            if (!container.ports.isNullOrEmpty()) {
                                var portsExpanded by remember { mutableStateOf(false) }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clickable { portsExpanded = !portsExpanded }
                                ) {
                                    Icon(
                                        imageVector = if (portsExpanded) ICON_DOWN else ICON_RIGHT,
                                        contentDescription = "Toggle Ports",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Ports (${container.ports.size})",
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (portsExpanded) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 24.dp, top = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            container.ports.forEach { port ->
                                                Row {
                                                    Text(
                                                        text = port.name ?: "port",
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.width(100.dp)
                                                    )
                                                    Text("${port.containerPort}")
                                                    port.protocol?.let { protocol ->
                                                        Text(" ($protocol)")
                                                    }
                                                    port.hostPort?.let { hostPort ->
                                                        Text(" → $hostPort (host)")
                                                    }
                                                }
                                                Spacer(Modifier.height(2.dp))
                                            }
                                        }
                                    }
                                }
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
                                                    text = container.command.joinToString(" "),
                                                    style = TextStyle(fontFamily = FontFamily.Monospace)
                                                )
                                            }

                                            // Аргументи
                                            if (!container.args.isNullOrEmpty()) {
                                                Spacer(Modifier.height(2.dp))
                                                Text("Arguments:", fontWeight = FontWeight.Medium)
                                                SelectionContainer {
                                                    Text(
                                                        text = container.args.joinToString(" "),
                                                        style = TextStyle(fontFamily = FontFamily.Monospace)
                                                    )
                                                }
                                            }
                                        } else {
                                            val commandPreview = (container.command.joinToString(" ")).let {
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
                                                text = container.args.joinToString(" "),
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
                                        text = "Environment (${container.env.size})",
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
                                            container.env.forEach { env ->
                                                Row {
                                                    Text(
                                                        text = "${env.name}:",
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.width(120.dp)
                                                    )
                                                    SelectionContainer {
                                                        val envValue =
                                                            env.value ?: env.valueFrom?.let { "(from source)" } ?: ""
                                                        Text(
                                                            text = envValue,
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

                                if (resourcesExpanded) {
                                    // Перевірка на null та непорожність для кожного поля
                                    val hasRequests = resources.requests?.isNotEmpty() == true
                                    val hasLimits = resources.limits?.isNotEmpty() == true

                                    if (hasRequests || hasLimits) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 24.dp, top = 4.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                if (hasRequests) {
                                                    Text("Requests:", fontWeight = FontWeight.Bold)
                                                    resources.requests!!.forEach { (key, value) ->
                                                        Text("  $key: $value")
                                                    }
                                                }

                                                if (hasLimits) {
                                                    if (hasRequests) {
                                                        Spacer(Modifier.height(4.dp))
                                                    }
                                                    Text("Limits:", fontWeight = FontWeight.Bold)
                                                    resources.limits!!.forEach { (key, value) ->
                                                        Text("  $key: $value")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Проби готовності/життєздатності
                            var probesExpanded by remember { mutableStateOf(false) }
                            val hasProbes =
                                container.livenessProbe != null || container.readinessProbe != null || container.startupProbe != null

                            if (hasProbes) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clickable { probesExpanded = !probesExpanded }
                                ) {
                                    Icon(
                                        imageVector = if (probesExpanded) ICON_DOWN else ICON_RIGHT,
                                        contentDescription = "Toggle Probes",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Health Probes",
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (probesExpanded) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 24.dp, top = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            container.livenessProbe?.let { probe ->
                                                ProbeSummary("Liveness", probe)
                                            }

                                            container.readinessProbe?.let { probe ->
                                                if (container.livenessProbe != null) {
                                                    Spacer(Modifier.height(8.dp))
                                                    HorizontalDivider()
                                                    Spacer(Modifier.height(8.dp))
                                                }
                                                ProbeSummary("Readiness", probe)
                                            }

                                            container.startupProbe?.let { probe ->
                                                if (container.livenessProbe != null || container.readinessProbe != null) {
                                                    Spacer(Modifier.height(8.dp))
                                                    HorizontalDivider()
                                                    Spacer(Modifier.height(8.dp))
                                                }
                                                ProbeSummary("Startup", probe)
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

        // Умови
        val conditionsState = remember { mutableStateOf(false) }
        val conditionsCount = replicaSet.status?.conditions?.size ?: 0
        DetailSectionHeader(
            title = "Conditions ($conditionsCount)",
            expanded = conditionsState
        )

        if (conditionsState.value) {
            if (conditionsCount > 0) {
                Column {
                    replicaSet.status?.conditions?.forEach { condition ->
                        val isPositive = condition.status == "True"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPositive)
                                    MaterialTheme.colorScheme.surfaceVariant
                                else
                                    MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isPositive) ICON_SUCCESS else ICON_ERROR,
                                        contentDescription = "Condition Status",
                                        tint = if (isPositive)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = condition.type ?: "",
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text("Status: ${condition.status}")
                                Text("Last Update: ${formatAge(condition.lastTransitionTime)}")

                                condition.message?.let { message ->
                                    Text("Message: $message")
                                }

                                condition.reason?.let { reason ->
                                    Text("Reason: $reason")
                                }
                            }
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = FeatherIcons.Check,
                            contentDescription = "No Conditions",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("No conditions reported")
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
                        Text("Labels (${replicaSet.metadata?.labels?.size ?: 0}):", fontWeight = FontWeight.Bold)
                    }

                    if (labelsExpanded) {
                        if (replicaSet.metadata?.labels.isNullOrEmpty()) {
                            Text("No labels", modifier = Modifier.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                replicaSet.metadata?.labels?.forEach { (key, value) ->
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
                        Text(
                            "Annotations (${replicaSet.metadata?.annotations?.size ?: 0}):",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (annotationsExpanded) {
                        if (replicaSet.metadata?.annotations.isNullOrEmpty()) {
                            Text("No annotations", modifier = Modifier.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                replicaSet.metadata?.annotations?.entries?.sortedBy { it.key }
                                    ?.forEach { (key, value) ->
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
                                                            modifier = Modifier.clickable {
                                                                valueExpanded = !valueExpanded
                                                            }
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
    }
}

// Допоміжна функція для відображення інформації про пробу (probe)
@Composable
fun ProbeSummary(type: String, probe: Probe) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (type) {
                    "Liveness" -> FeatherIcons.Heart
                    "Readiness" -> FeatherIcons.Check
                    else -> FeatherIcons.Play
                },
                contentDescription = "$type Probe",
                modifier = Modifier.size(16.dp),
                tint = when (type) {
                    "Liveness" -> MaterialTheme.colorScheme.error
                    "Readiness" -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.secondary
                }
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "$type Probe",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(4.dp))

        // Загальні параметри
        val initialDelay = probe.initialDelaySeconds ?: 0
        val timeout = probe.timeoutSeconds ?: 1
        val period = probe.periodSeconds ?: 10
        val success = probe.successThreshold ?: 1
        val failure = probe.failureThreshold ?: 3

        Row {
            Column(modifier = Modifier.weight(1f)) {
                DetailRow("Initial Delay", "${initialDelay}s")
                DetailRow("Period", "${period}s")
            }
            Column(modifier = Modifier.weight(1f)) {
                DetailRow("Timeout", "${timeout}s")
                DetailRow("Success Threshold", "$success")
                DetailRow("Failure Threshold", "$failure")
            }
        }

        // Тип перевірки
        probe.httpGet?.let { httpGet ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = FeatherIcons.Globe,
                    contentDescription = "HTTP Get",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "HTTP Get: ${httpGet.scheme ?: "HTTP"}://${httpGet.host ?: ""}:${httpGet.port?.intVal ?: httpGet.port?.strVal ?: ""}${httpGet.path ?: "/"}",
                    fontWeight = FontWeight.Medium
                )
            }

            if (!httpGet.httpHeaders.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Headers:", fontWeight = FontWeight.Medium)
                httpGet.httpHeaders.forEach { header ->
                    Text("  ${header.name}: ${header.value}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        probe.tcpSocket?.let { tcpSocket ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = FeatherIcons.Server,
                    contentDescription = "TCP Socket",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "TCP Socket: ${tcpSocket.host ?: ""}:${tcpSocket.port?.intVal ?: tcpSocket.port?.strVal ?: ""}",
                    fontWeight = FontWeight.Medium
                )
            }
        }

        probe.exec?.let { exec ->
            if (!exec.command.isNullOrEmpty()) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = FeatherIcons.Terminal,
                        contentDescription = "Exec",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text("Exec Command:", fontWeight = FontWeight.Medium)
                        SelectionContainer {
                            Text(
                                text = exec.command.joinToString(" "),
                                style = TextStyle(fontFamily = FontFamily.Monospace),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Додаткова інформація про probeHandler
        probe.grpc?.let { grpc ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = FeatherIcons.Radio,
                    contentDescription = "gRPC",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "gRPC: port=${grpc.port}${grpc.service?.let { " service=$it" } ?: ""}",
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}