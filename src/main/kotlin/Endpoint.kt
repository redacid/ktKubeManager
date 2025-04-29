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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.fabric8.kubernetes.api.model.Endpoints
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadEndpointsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Endpoints", namespace) { cl, ns ->
        if (ns == null) cl.endpoints().inAnyNamespace().list().items
        else cl.endpoints().inNamespace(ns).list().items
    }

@Composable
fun EndpointsDetailsView(endpoint: Endpoints) {
    val showSubsets = remember { mutableStateOf(true) }
    val showLabels = remember { mutableStateOf(false) }
    val showAnnotations = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.Companion
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "Endpoint Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        DetailRow("Name", endpoint.metadata?.name)
        DetailRow("Namespace", endpoint.metadata?.namespace)
        DetailRow("Created", formatAge(endpoint.metadata?.creationTimestamp))

        // Загальна кількість адрес
        var totalAddresses = 0
        var totalNotReadyAddresses = 0

        endpoint.subsets?.forEach { subset ->
            totalAddresses += subset.addresses?.size ?: 0
            totalNotReadyAddresses += subset.notReadyAddresses?.size ?: 0
        }

        DetailRow("Total Ready Addresses", totalAddresses.toString())
        DetailRow("Total Not Ready Addresses", totalNotReadyAddresses.toString())

        HorizontalDivider(modifier = Modifier.Companion.padding(vertical = 8.dp))

        // Секція підмножин (subsets)
        Row(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .clickable { showSubsets.value = !showSubsets.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.Companion.CenterVertically
        ) {
            Icon(
                imageVector = if (showSubsets.value) ICON_DOWN else ICON_RIGHT,
                contentDescription = "Toggle Subsets"
            )
            Text(
                text = "Subsets (${endpoint.subsets?.size ?: 0})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (showSubsets.value && !endpoint.subsets.isNullOrEmpty()) {
            endpoint.subsets?.forEachIndexed { index, subset ->
                Card(
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.Companion.padding(16.dp)) {
                        Text(
                            text = "Subset ${index + 1}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.Companion.padding(bottom = 8.dp)
                        )

                        // Порти
                        if (!subset.ports.isNullOrEmpty()) {
                            Text(
                                text = "Ports:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Companion.Bold
                            )

                            subset.ports?.forEach { port ->
                                Row(
                                    modifier = Modifier.Companion.padding(start = 8.dp, top = 4.dp),
                                    verticalAlignment = Alignment.Companion.CenterVertically
                                ) {
                                    Text(
                                        text = "• ${port.name ?: ""} ${port.port} ${port.protocol ?: "TCP"}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.Companion.height(8.dp))
                        }

                        // Готові адреси
                        if (!subset.addresses.isNullOrEmpty()) {
                            Text(
                                text = "Ready Addresses (${subset.addresses?.size}):",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Companion.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            subset.addresses?.forEach { address ->
                                Row(
                                    modifier = Modifier.Companion.padding(start = 8.dp, top = 4.dp),
                                    verticalAlignment = Alignment.Companion.CenterVertically
                                ) {
                                    Icon(
                                        ICON_SUCCESS,
                                        contentDescription = "Ready",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.Companion.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.Companion.width(4.dp))

                                    val addressText = buildString {
                                        append(address.ip ?: "unknown IP")
                                        address.targetRef?.let { targetRef ->
                                            append(" (${targetRef.kind ?: "Pod"}: ${targetRef.name})")
                                        }
                                    }

                                    Text(
                                        text = addressText,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.Companion.height(8.dp))
                        }

                        // Неготові адреси
                        if (!subset.notReadyAddresses.isNullOrEmpty()) {
                            Text(
                                text = "Not Ready Addresses (${subset.notReadyAddresses?.size}):",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Companion.Bold,
                                color = MaterialTheme.colorScheme.error
                            )

                            subset.notReadyAddresses?.forEach { address ->
                                Row(
                                    modifier = Modifier.Companion.padding(start = 8.dp, top = 4.dp),
                                    verticalAlignment = Alignment.Companion.CenterVertically
                                ) {
                                    Icon(
                                        ICON_ERROR,
                                        contentDescription = "Not Ready",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.Companion.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.Companion.width(4.dp))

                                    val addressText = buildString {
                                        append(address.ip ?: "unknown IP")
                                        address.targetRef?.let { targetRef ->
                                            append(" (${targetRef.kind ?: "Pod"}: ${targetRef.name})")
                                        }
                                    }

                                    Text(
                                        text = addressText,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.Companion.padding(vertical = 8.dp))

        // Секція міток
        Row(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .clickable { showLabels.value = !showLabels.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.Companion.CenterVertically
        ) {
            Icon(
                imageVector = if (showLabels.value) ICON_DOWN else ICON_RIGHT,
                contentDescription = "Toggle Labels"
            )
            Text(
                text = "Labels (${endpoint.metadata?.labels?.size ?: 0})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (showLabels.value) {
            Card(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.Companion.padding(16.dp)) {
                    endpoint.metadata?.labels?.entries?.forEach { (key, value) ->
                        Row(
                            modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Companion.SemiBold),
                                modifier = Modifier.Companion.weight(0.4f)
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.Companion.weight(0.6f)
                            )
                        }
                    }

                    if (endpoint.metadata?.labels.isNullOrEmpty()) {
                        Text(
                            text = "No labels",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.Companion.padding(vertical = 8.dp))

        // Секція анотацій
        Row(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .clickable { showAnnotations.value = !showAnnotations.value }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.Companion.CenterVertically
        ) {
            Icon(
                imageVector = if (showAnnotations.value) ICON_DOWN else ICON_RIGHT,
                contentDescription = "Toggle Annotations"
            )
            Text(
                text = "Annotations (${endpoint.metadata?.annotations?.size ?: 0})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (showAnnotations.value) {
            Card(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.Companion.padding(16.dp)) {
                    endpoint.metadata?.annotations?.entries?.forEach { (key, value) ->
                        Column(
                            modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Companion.SemiBold),
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

                    if (endpoint.metadata?.annotations.isNullOrEmpty()) {
                        Text(
                            text = "No annotations",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}