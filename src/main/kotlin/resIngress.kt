import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.fabric8.kubernetes.api.model.networking.v1.Ingress
import io.fabric8.kubernetes.client.KubernetesClient
import kotlin.collections.get

suspend fun loadIngressesFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Ingresses", namespace) { cl, ns ->
        if (ns == null) cl.network().v1().ingresses().inAnyNamespace().list().items else cl.network().v1().ingresses()
            .inNamespace(ns).list().items
    }

@Composable
fun IngressDetailsView(ing: Ingress) {
    Column {
        DetailRow("Name", ing.metadata?.name)
        DetailRow("Namespace", ing.metadata?.namespace)
        DetailRow("Created", formatAge(ing.metadata?.creationTimestamp))
        DetailRow("Class", ing.spec?.ingressClassName ?: "<none>")
        DetailRow("Address", formatIngressAddress(ing.status?.loadBalancer?.ingress))

        // Additional information: Annotations
        if (!ing.metadata?.annotations.isNullOrEmpty()) {
            HorizontalDivider(
                modifier = Modifier.Companion.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text("Annotations:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.Companion.padding(start = 8.dp)) {
                ing.metadata?.annotations?.forEach { (key, value) ->
                    // Group important ingress controller annotations
                    val displayKey = when {
                        key.startsWith("nginx.ingress") -> "NGINX: ${key.substringAfter("nginx.ingress.kubernetes.io/")}"
                        key.startsWith("alb.ingress") -> "ALB: ${key.substringAfter("alb.ingress.kubernetes.io/")}"
                        key.startsWith("kubernetes.io/ingress") -> "K8s: ${key.substringAfter("kubernetes.io/ingress.")}"
                        key.startsWith("traefik.") -> "Traefik: ${key.substringAfter("traefik.")}"
                        key.startsWith("haproxy.") -> "HAProxy: ${key.substringAfter("haproxy.")}"
                        key.startsWith("cert-manager.io") -> "Cert-Manager: ${key.substringAfter("cert-manager.io/")}"
                        else -> key
                    }
                    DetailRow(displayKey, value)
                }
            }
        }

        // Labels section
        if (!ing.metadata?.labels.isNullOrEmpty()) {
            HorizontalDivider(
                modifier = Modifier.Companion.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text("Labels:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.Companion.padding(start = 8.dp)) {
                ing.metadata?.labels?.forEach { (key, value) ->
                    DetailRow(key, value)
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.Companion.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text("Rules:", style = MaterialTheme.typography.titleMedium)
        ing.spec?.rules?.forEachIndexed { index, rule ->
            Text(
                "  Rule ${index + 1}: Host: ${rule.host ?: "*"}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Companion.Bold)
            )
            rule.http?.paths?.forEach { path ->
                Column(modifier = Modifier.Companion.padding(start = 16.dp)) {
                    DetailRow("    Path", path.path ?: "/")
                    DetailRow("    Path Type", path.pathType)
                    DetailRow("    Backend Service", path.backend?.service?.name)
                    DetailRow("    Backend Port", path.backend?.service?.port?.let { it.name ?: it.number?.toString() })
                }
            }
            Spacer(Modifier.Companion.height(4.dp))
        }
        if (ing.spec?.rules.isNullOrEmpty()) {
            Text("  <No rules defined>", modifier = Modifier.Companion.padding(start = 8.dp))
        }

        // Default backend
        ing.spec?.defaultBackend?.let { backend ->
            HorizontalDivider(
                modifier = Modifier.Companion.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text("Default Backend:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.Companion.padding(start = 8.dp)) {
                backend.service?.let { service ->
                    DetailRow("Service Name", service.name)
                    DetailRow("Service Port", service.port?.let { it.name ?: it.number?.toString() })
                }
                backend.resource?.let { resource ->
                    DetailRow("API Group", resource.apiGroup ?: "<core>")
                    DetailRow("Kind", resource.kind)
                    DetailRow("Name", resource.name)
                }
                if (backend.service == null && backend.resource == null) {
                    Text("<No default backend specified>", modifier = Modifier.Companion.padding(vertical = 4.dp))
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.Companion.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text("TLS:", style = MaterialTheme.typography.titleMedium)
        ing.spec?.tls?.forEach { tls ->
            Column(
                modifier = Modifier.Companion.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(4.dp)
            ) {
                DetailRow("  Hosts", tls.hosts?.joinToString(", "))
                DetailRow("  Secret Name", tls.secretName)
            }
        }
        if (ing.spec?.tls.isNullOrEmpty()) {
            Text("  <No TLS defined>", modifier = Modifier.Companion.padding(start = 8.dp))
        }

        // Status section
        if (!ing.status?.loadBalancer?.ingress.isNullOrEmpty()) {
            HorizontalDivider(
                modifier = Modifier.Companion.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text("Load Balancer Status:", style = MaterialTheme.typography.titleMedium)
            ing.status?.loadBalancer?.ingress?.forEachIndexed { _, ingress ->
                Column(
                    modifier = Modifier.Companion.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(4.dp)
                ) {
                    DetailRow("  IP", ingress.ip)
                    DetailRow("  Hostname", ingress.hostname)

                    // Ports (in newer API versions)
                    if (!ingress.ports.isNullOrEmpty()) {
                        Text(
                            "  Ports:",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Companion.Bold),
                            modifier = Modifier.Companion.padding(top = 4.dp)
                        )
                        ingress.ports.forEach { port ->
                            DetailRow("    ${port.port}", port.protocol ?: "TCP")
                        }
                    }
                }
            }
        }

        // Conditions section (newer API versions may include this)
        val conditions = ing.status?.let {
            try {
                it::class.members.find { member -> member.name == "conditions" }?.call(it) as? List<*>
            } catch (e: Exception) {
                null
            }
        }

        if (!conditions.isNullOrEmpty()) {
            HorizontalDivider(
                modifier = Modifier.Companion.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text("Conditions:", style = MaterialTheme.typography.titleMedium)

            conditions.forEach { condition ->
                Column(
                    modifier = Modifier.Companion.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(4.dp)
                ) {
                    // Use safe access or set default values
                    val type = condition?.let { if (it is Map<*, *>) it["type"] as? String else null }
                    val status = condition?.let { if (it is Map<*, *>) it["status"] as? String else null }
                    val reason = condition?.let { if (it is Map<*, *>) it["reason"] as? String else null }
                    val message = condition?.let { if (it is Map<*, *>) it["message"] as? String else null }
                    val lastTransitionTime =
                        condition?.let { if (it is Map<*, *>) it["lastTransitionTime"] as? String else null }
                    val observedGeneration =
                        condition?.let { if (it is Map<*, *>) it["observedGeneration"]?.toString() else null }

                    DetailRow("Type", type)
                    DetailRow("Status", status)
                    DetailRow("Reason", reason)
                    DetailRow("Message", message)
                    DetailRow("Last Transition", formatAge(lastTransitionTime))
                    DetailRow("Observed Generation", observedGeneration)
                }
            }
        }

        // Owner References
        if (!ing.metadata?.ownerReferences.isNullOrEmpty()) {
            HorizontalDivider(
                modifier = Modifier.Companion.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text("Owner References:", style = MaterialTheme.typography.titleMedium)

            ing.metadata?.ownerReferences?.forEach { owner ->
                Column(
                    modifier = Modifier.Companion.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(4.dp)
                ) {
                    DetailRow("Kind", owner.kind)
                    DetailRow("Name", owner.name)
                    DetailRow("UID", owner.uid)
                    DetailRow("Controller", owner.controller?.toString() ?: "false")
                }
            }
        }

        // Additional information: Finalizers
        if (!ing.metadata?.finalizers.isNullOrEmpty()) {
            HorizontalDivider(
                modifier = Modifier.Companion.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text("Finalizers:", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.Companion.padding(start = 8.dp)) {
                ing.metadata?.finalizers?.forEach { finalizer ->
                    DetailRow("Finalizer", finalizer)
                }
            }
        }

        // Additional Metadata
        HorizontalDivider(
            modifier = Modifier.Companion.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text("Additional Metadata:", style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.Companion.padding(start = 8.dp)) {
            DetailRow("UID", ing.metadata?.uid)
            DetailRow("Resource Version", ing.metadata?.resourceVersion)
            DetailRow("Generation", ing.metadata?.generation?.toString())
        }
    }
}