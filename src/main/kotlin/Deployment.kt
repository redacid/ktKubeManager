import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadDeploymentsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Deployments", namespace) { cl, ns ->
        if (ns == null) cl.apps().deployments().inAnyNamespace().list().items else cl.apps().deployments()
            .inNamespace(ns).list().items
    }

@Composable
fun DeploymentDetailsView(dep: Deployment) {
    Column {
        DetailRow("Name", dep.metadata?.name)
        DetailRow("Namespace", dep.metadata?.namespace)
        DetailRow("Created", formatAge(dep.metadata?.creationTimestamp))
        DetailRow("Replicas", "${dep.status?.readyReplicas ?: 0} / ${dep.spec?.replicas ?: 0} (Ready)")
        DetailRow("Updated", dep.status?.updatedReplicas?.toString())
        DetailRow("Available", dep.status?.availableReplicas?.toString())
        DetailRow("Strategy", dep.spec?.strategy?.type)

        // Selector information
        HorizontalDivider(
            modifier = Modifier.Companion.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text("Selector:", style = MaterialTheme.typography.titleMedium)
        dep.spec?.selector?.matchLabels?.forEach { (key, value) ->
            DetailRow("  $key", value)
        }
        if (dep.spec?.selector?.matchLabels.isNullOrEmpty()) {
            Text("  (No selector labels)", modifier = Modifier.Companion.padding(start = 8.dp))
        }

        // Conditions
        HorizontalDivider(
            modifier = Modifier.Companion.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text("Conditions:", style = MaterialTheme.typography.titleMedium)
        dep.status?.conditions?.forEach { condition ->
            Column(
                modifier = Modifier.Companion.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(4.dp)
            ) {
                DetailRow("  Type", condition.type)
                DetailRow("  Status", condition.status)
                DetailRow("  Reason", condition.reason)
                DetailRow("  Message", condition.message)
                DetailRow("  Last Update", formatAge(condition.lastUpdateTime))
                DetailRow("  Last Transition", formatAge(condition.lastTransitionTime))
            }
        }
        if (dep.status?.conditions.isNullOrEmpty()) {
            Text("  (No conditions)", modifier = Modifier.Companion.padding(start = 8.dp))
        }

        // Template information
        HorizontalDivider(
            modifier = Modifier.Companion.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text("Pod Template:", style = MaterialTheme.typography.titleMedium)

        // Template labels
        Text(
            "  Labels:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.Companion.padding(start = 8.dp, top = 4.dp)
        )
        dep.spec?.template?.metadata?.labels?.forEach { (key, value) ->
            DetailRow("    $key", value)
        }
        if (dep.spec?.template?.metadata?.labels.isNullOrEmpty()) {
            Text("    (No labels)", modifier = Modifier.Companion.padding(start = 16.dp))
        }

        // Template containers
        Text(
            "  Containers:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.Companion.padding(start = 8.dp, top = 4.dp)
        )
        dep.spec?.template?.spec?.containers?.forEach { container ->
            Column(
                modifier = Modifier.Companion.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(4.dp)
            ) {
                DetailRow("Name", container.name)
                DetailRow("Image", container.image)
                DetailRow("Pull Policy", container.imagePullPolicy)

                // Container ports
                if (!container.ports.isNullOrEmpty()) {
                    Text(
                        "Ports:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Companion.Bold),
                        modifier = Modifier.Companion.padding(top = 2.dp)
                    )
                    container.ports.forEach { port ->
                        DetailRow(
                            "  ${port.name ?: port.containerPort}", "${port.containerPort}/${port.protocol ?: "TCP"}"
                        )
                    }
                }

                // Resource requirements
                container.resources?.let { resources ->
                    Text(
                        "Resources:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Companion.Bold),
                        modifier = Modifier.Companion.padding(top = 2.dp)
                    )
                    resources.limits?.forEach { (key, value) ->
                        DetailRow("  Limit $key", value.toString())
                    }
                    resources.requests?.forEach { (key, value) ->
                        DetailRow("  Request $key", value.toString())
                    }
                }

                // Environment variables
                if (!container.env.isNullOrEmpty()) {
                    Text(
                        "Environment:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Companion.Bold),
                        modifier = Modifier.Companion.padding(top = 2.dp)
                    )
                    container.env.forEach { env ->
                        val valueText = when {
                            env.value != null -> env.value
                            env.valueFrom?.configMapKeyRef != null -> "ConfigMap: ${env.valueFrom?.configMapKeyRef?.name}.${env.valueFrom?.configMapKeyRef?.key}"

                            env.valueFrom?.secretKeyRef != null -> "Secret: ${env.valueFrom?.secretKeyRef?.name}.${env.valueFrom?.secretKeyRef?.key}"

                            env.valueFrom?.fieldRef != null -> "Field: ${env.valueFrom?.fieldRef?.fieldPath}"

                            else -> "<complex>"
                        }
                        DetailRow("  ${env.name}", valueText)
                    }
                }

                // Volume mounts
                if (!container.volumeMounts.isNullOrEmpty()) {
                    Text(
                        "Volume Mounts:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Companion.Bold),
                        modifier = Modifier.Companion.padding(top = 2.dp)
                    )
                    container.volumeMounts.forEach { mount ->
                        DetailRow("  ${mount.name}", "${mount.mountPath}${if (mount.readOnly == true) " (ro)" else ""}")
                    }
                }
            }
        }
        if (dep.spec?.template?.spec?.containers.isNullOrEmpty()) {
            Text("    (No containers)", modifier = Modifier.Companion.padding(start = 16.dp))
        }

        // Volumes
        if (!dep.spec?.template?.spec?.volumes.isNullOrEmpty()) {
            Text(
                "  Volumes:",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.Companion.padding(start = 8.dp, top = 4.dp)
            )
            dep.spec?.template?.spec?.volumes?.forEach { volume ->
                Column(
                    modifier = Modifier.Companion.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(4.dp)
                ) {
                    DetailRow("Name", volume.name)
                    // Determine volume type and details
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
                            DetailRow("Read Only", volume.persistentVolumeClaim.readOnly?.toString() ?: "false")
                        }

                        volume.emptyDir != null -> {
                            DetailRow("Type", "EmptyDir")
                            DetailRow("Medium", volume.emptyDir.medium ?: "")
                        }

                        volume.hostPath != null -> {
                            DetailRow("Type", "HostPath")
                            DetailRow("Path", volume.hostPath.path)
                            DetailRow("Type", volume.hostPath.type ?: "")
                        }

                        else -> DetailRow("Type", "<complex>")
                    }
                }
            }
        }
    }
}