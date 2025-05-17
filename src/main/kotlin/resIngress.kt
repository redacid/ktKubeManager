import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.fabric8.kubernetes.api.model.networking.v1.Ingress
import io.fabric8.kubernetes.client.KubernetesClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import kotlin.collections.component1
import kotlin.collections.component2

suspend fun loadIngressesFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Ingresses", namespace) { cl, ns ->
        if (ns == null) cl.network().v1().ingresses().inAnyNamespace().list().items else cl.network().v1().ingresses()
            .inNamespace(ns).list().items
    }

@Composable
fun IngressDetailsView(ing: Ingress) {
    val showRules = remember { mutableStateOf(true) }
    val showAnnotations = remember { mutableStateOf(false) }
    val showLabels = remember { mutableStateOf(false) }
    val showTLS = remember { mutableStateOf(false) }
    val showStatus = remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Заголовок
        Text(
            text = "Ingress Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurface
        )

        // Базова інформація
        DetailRow("Name", ing.metadata?.name)
        DetailRow("Namespace", ing.metadata?.namespace)
        DetailRow("Created", formatAge(ing.metadata?.creationTimestamp))
        DetailRow("Class", ing.spec?.ingressClassName ?: "<none>")
        DetailRow("Address", formatIngressAddress(ing.status?.loadBalancer?.ingress))

        Spacer(Modifier.height(16.dp))

        // Секція Rules
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { showRules.value = !showRules.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (showRules.value) ICON_DOWN else ICON_RIGHT,
                contentDescription = "Expand Rules"
            )
            Text(
                text = "Rules (${ing.spec?.rules?.size ?: 0})",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (showRules.value) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (ing.spec?.rules.isNullOrEmpty()) {
                        Text("No rules defined", modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        ing.spec?.rules?.forEachIndexed { index, rule ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                            Text(
                                "Host: ${rule.host ?: "*"}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            rule.http?.paths?.forEach { path ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        DetailRow("Path", path.path ?: "/")
                                        DetailRow("Path Type", path.pathType)
                                        DetailRow("Service", path.backend?.service?.name)
                                        DetailRow(
                                            "Port",
                                            path.backend?.service?.port?.let {
                                                it.name ?: it.number?.toString()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Секція TLS
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { showTLS.value = !showTLS.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (showTLS.value) ICON_DOWN else ICON_RIGHT,
                contentDescription = "Expand TLS"
            )
            Text(
                text = "TLS (${ing.spec?.tls?.size ?: 0})",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (showTLS.value) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (ing.spec?.tls.isNullOrEmpty()) {
                        Text("No TLS configurations", modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        ing.spec?.tls?.forEach { tls ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    DetailRow("Hosts", tls.hosts?.joinToString(", "))
                                    DetailRow("Secret", tls.secretName)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Секція Status
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { showStatus.value = !showStatus.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (showStatus.value) ICON_DOWN else ICON_RIGHT,
                contentDescription = "Expand Status"
            )
            Text(
                text = "Load Balancer Status",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (showStatus.value) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (ing.status?.loadBalancer?.ingress.isNullOrEmpty()) {
                        Text("No load balancer status available", modifier = Modifier.padding(vertical = 4.dp))
                    } else {
                        ing.status?.loadBalancer?.ingress?.forEach { ingress ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    DetailRow("IP", ingress.ip)
                                    DetailRow("Hostname", ingress.hostname)
                                    if (!ingress.ports.isNullOrEmpty()) {
                                        Text(
                                            "Ports:",
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                        ingress.ports.forEach { port ->
                                            DetailRow(
                                                "${port.port}",
                                                port.protocol ?: "TCP"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Секції анотацій та міток аналогічні PodDetailsView
        // ... (додайте секції для анотацій та міток за аналогією)
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
                text = "Labels (${ing.metadata?.labels?.size ?: 0})",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (showLabels.value) {
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.Companion.padding(12.dp)) {
                    if (ing.metadata?.labels.isNullOrEmpty()) {
                        Text("No labels found", modifier = Modifier.Companion.padding(vertical = 4.dp))
                    } else {
                        ing.metadata?.labels?.entries?.forEach { (key, value) ->
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
                text = "Annotations (${ing.metadata?.annotations?.size ?: 0})",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (showAnnotations.value) {
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.Companion.padding(12.dp)) {
                    if (ing.metadata?.annotations.isNullOrEmpty()) {
                        Text("No annotations found", modifier = Modifier.Companion.padding(vertical = 4.dp))
                    } else {
                        ing.metadata?.annotations?.entries?.sortedBy { it.key }?.forEach { (key, value) ->
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
        //----
    }
}
