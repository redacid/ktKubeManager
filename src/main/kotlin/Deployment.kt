import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.fabric8.kubernetes.api.model.apps.Deployment
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



suspend fun loadDeploymentsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Deployments", namespace) { cl, ns ->
        if (ns == null) cl.apps().deployments().inAnyNamespace().list().items else cl.apps().deployments()
            .inNamespace(ns).list().items
    }

@Composable
fun DeploymentDetailsView(dep: Deployment) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "Deployment Information",
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
                DetailRow("Name", dep.metadata?.name)
                DetailRow("Namespace", dep.metadata?.namespace)
                DetailRow("Created", formatAge(dep.metadata?.creationTimestamp))
                DetailRow("Strategy", dep.spec?.strategy?.type)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Статус реплік
        Text(
            text = "Replica Status",
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if ((dep.status?.readyReplicas ?: 0) >= (dep.spec?.replicas ?: 0))
                            ICON_SUCCESS else ICON_WARNING,
                        contentDescription = "Replica Status",
                        tint = if ((dep.status?.readyReplicas ?: 0) >= (dep.spec?.replicas ?: 0))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Ready: ${dep.status?.readyReplicas ?: 0} / ${dep.spec?.replicas ?: 0}",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(Modifier.height(8.dp))
                DetailRow("Updated", dep.status?.updatedReplicas?.toString() ?: "0")
                DetailRow("Available", dep.status?.availableReplicas?.toString() ?: "0")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Селектор
        val selectorState = remember { mutableStateOf(false) }
        DetailSectionHeader(title = "Selector", expanded = selectorState)

        if (selectorState.value) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    dep.spec?.selector?.matchLabels?.forEach { (key, value) ->
                        Row {
                            SelectionContainer {
                                Text(
                                    text = key,
                                    fontWeight = FontWeight.Medium,
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
                    if (dep.spec?.selector?.matchLabels.isNullOrEmpty()) {
                        Text("No selector labels defined")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Умови
        val conditionsState = remember { mutableStateOf(false) }
        DetailSectionHeader(title = "Conditions", expanded = conditionsState)

        if (conditionsState.value) {
            if (dep.status?.conditions.isNullOrEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("No conditions available")
                    }
                }
            } else {
                dep.status?.conditions?.forEach { condition ->
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
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when (condition.status) {
                                        "True" -> ICON_SUCCESS
                                        "False" -> ICON_ERROR
                                        else -> ICON_WARNING
                                    },
                                    contentDescription = "Condition Status",
                                    tint = when (condition.status) {
                                        "True" -> MaterialTheme.colorScheme.primary
                                        "False" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.outline
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = condition.type ?: "",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            DetailRow("Status", condition.status)
                            DetailRow("Last Update", formatAge(condition.lastUpdateTime))
                            DetailRow("Last Transition", formatAge(condition.lastTransitionTime))
                            condition.message?.let {
                                SelectionContainer {
                                    Text(
                                        "Message: $it",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                            condition.reason?.let { DetailRow("Reason", it) }
                        }
                    }
                }
            }
        }

        // Pod Template
        val templateState = remember { mutableStateOf(false) }
        DetailSectionHeader(title = "Pod Template", expanded = templateState)

        if (templateState.value) {
            // Labels
            var labelsExpanded by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
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
                            "Labels (${dep.spec?.template?.metadata?.labels?.size ?: 0})",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (labelsExpanded) {
                        if (dep.spec?.template?.metadata?.labels.isNullOrEmpty()) {
                            Text(
                                "No labels defined",
                                modifier = Modifier.padding(start = 24.dp, top = 4.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                dep.spec?.template?.metadata?.labels?.forEach { (key, value) ->
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
                }
            }

            // Containers
            dep.spec?.template?.spec?.containers?.forEach { container ->
                var containerExpanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.clickable { containerExpanded = !containerExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (containerExpanded) ICON_DOWN else ICON_RIGHT,
                                contentDescription = "Toggle Container",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = ICON_BOX,
                                contentDescription = "Container",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = container.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (containerExpanded) {
                            Spacer(Modifier.height(8.dp))
                            SelectionContainer {
                                Text("Image: ${container.image}")
                            }
                            DetailRow("Pull Policy", container.imagePullPolicy)

                            // Ports
                            if (!container.ports.isNullOrEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Ports:",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                container.ports.forEach { port ->
                                    Row(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                                        Text(
                                            "${port.containerPort}/${port.protocol ?: "TCP"}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        port.name?.let {
                                            Text(
                                                " ($it)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }

                            // Resources
                            container.resources?.let { resources ->
                                if (!resources.limits.isNullOrEmpty() || !resources.requests.isNullOrEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Resources:",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    resources.limits?.forEach { (key, value) ->
                                        DetailRow("Limit $key", value.toString())
                                    }
                                    resources.requests?.forEach { (key, value) ->
                                        DetailRow("Request $key", value.toString())
                                    }
                                }
                            }

                            // Environment Variables
                            if (!container.env.isNullOrEmpty()) {
                                var envExpanded by remember { mutableStateOf(false) }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.clickable { envExpanded = !envExpanded },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (envExpanded) ICON_DOWN else ICON_RIGHT,
                                        contentDescription = "Toggle Environment",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Environment Variables (${container.env.size})",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                if (envExpanded) {
                                    Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                        container.env.forEach { env ->
                                            Row {
                                                SelectionContainer {
                                                    Text(
                                                        text = env.name,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.width(120.dp)
                                                    )
                                                }
                                                Text(": ")
                                                SelectionContainer {
                                                    Text(
                                                        when {
                                                            env.value != null -> env.value
                                                            env.valueFrom?.configMapKeyRef != null ->
                                                                "ConfigMap: ${env.valueFrom?.configMapKeyRef?.name}.${env.valueFrom?.configMapKeyRef?.key}"
                                                            env.valueFrom?.secretKeyRef != null ->
                                                                "Secret: ${env.valueFrom?.secretKeyRef?.name}.${env.valueFrom?.secretKeyRef?.key}"
                                                            env.valueFrom?.fieldRef != null ->
                                                                "Field: ${env.valueFrom?.fieldRef?.fieldPath}"
                                                            else -> "<complex>"
                                                        }
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                        }
                                    }
                                }
                            }

                            // Volume Mounts
                            if (!container.volumeMounts.isNullOrEmpty()) {
                                var volumesExpanded by remember { mutableStateOf(false) }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.clickable { volumesExpanded = !volumesExpanded },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (volumesExpanded) ICON_DOWN else ICON_RIGHT,
                                        contentDescription = "Toggle Volumes",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Volume Mounts (${container.volumeMounts.size})",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                if (volumesExpanded) {
                                    Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                        container.volumeMounts.forEach { mount ->
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = ICON_HD,
                                                    contentDescription = "Volume",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    mount.name,
                                                    fontWeight = FontWeight.Medium,
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

        // Volumes Section
        if (!dep.spec?.template?.spec?.volumes.isNullOrEmpty()) {
            Spacer(Modifier.height(16.dp))
            val volumesState = remember { mutableStateOf(false) }
            DetailSectionHeader(
                title = "Volumes (${dep.spec?.template?.spec?.volumes?.size})",
                expanded = volumesState
            )

            if (volumesState.value) {
                dep.spec?.template?.spec?.volumes?.forEach { volume ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = ICON_DB,
                                    contentDescription = "Volume",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    volume.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Spacer(Modifier.height(8.dp))

                            when {
                                volume.configMap != null -> {
                                    DetailRow("Type", "ConfigMap")
                                    DetailRow("ConfigMap Name", volume.configMap.name)
                                }
                                volume.secret != null -> {
                                    DetailRow("Type", "Secret")
                                    DetailRow("Secret Name", volume.secret.secretName)
                                }
                                volume.persistentVolumeClaim != null -> {
                                    DetailRow("Type", "PVC")
                                    DetailRow("Claim Name", volume.persistentVolumeClaim.claimName)
                                    DetailRow(
                                        "Access Mode",
                                        if (volume.persistentVolumeClaim.readOnly == true) "ReadOnly" else "ReadWrite"
                                    )
                                }
                                volume.emptyDir != null -> {
                                    DetailRow("Type", "EmptyDir")
                                    volume.emptyDir.medium?.let { DetailRow("Medium", it) }
                                }
                                volume.hostPath != null -> {
                                    DetailRow("Type", "HostPath")
                                    DetailRow("Path", volume.hostPath.path)
                                    volume.hostPath.type?.let { DetailRow("Type", it) }
                                }
                                else -> DetailRow("Type", "<complex>")
                            }
                        }
                    }
                }
            }
        }
    }
}