import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadServicesFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Services", namespace) { cl, ns ->
        if (ns == null) cl.services().inAnyNamespace().list().items else cl.services().inNamespace(ns).list().items
    }

@Composable
fun ServiceDetailsView(svc: Service) {
    //val scrollState = rememberScrollState()
    val showPorts = remember { mutableStateOf(false) }
    //val showEndpoints = remember { mutableStateOf(false) }
    val showLabels = remember { mutableStateOf(false) }
    val showAnnotations = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.Companion
            //.verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Basic service information section
        Text(
            text = "Service Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        DetailRow("Name", svc.metadata?.name)
        DetailRow("Namespace", svc.metadata?.namespace)
        DetailRow("Created", formatAge(svc.metadata?.creationTimestamp))
        DetailRow("Type", svc.spec?.type)
        DetailRow("ClusterIP(s)", svc.spec?.clusterIPs?.joinToString(", "))
        DetailRow("External IP(s)", formatServiceExternalIP(svc))
        DetailRow("Session Affinity", svc.spec?.sessionAffinity ?: "None")

        if (svc.spec?.type == "LoadBalancer") {
            DetailRow("Load Balancer IP", svc.spec?.loadBalancerIP)
            DetailRow("Load Balancer Class", svc.spec?.loadBalancerClass)
            DetailRow("Allocate Load Balancer NodePorts", svc.spec?.allocateLoadBalancerNodePorts?.toString() ?: "true")
        }

        if (svc.spec?.type == "ExternalName") {
            DetailRow("External Name", svc.spec?.externalName)
        }

        DetailRow("Traffic Policy", svc.spec?.externalTrafficPolicy ?: "Cluster")
        DetailRow("IP Families", svc.spec?.ipFamilies?.joinToString(", "))
        DetailRow("IP Family Policy", svc.spec?.ipFamilyPolicy)
        DetailRow("Selector", svc.spec?.selector?.map { "${it.key}=${it.value}" }?.joinToString(", ") ?: "None")

        HorizontalDivider(modifier = Modifier.Companion.padding(vertical = 8.dp))

        // Ports section (expandable)
        Row(modifier = Modifier.Companion.fillMaxWidth().clickable { showPorts.value = !showPorts.value }
            .padding(vertical = 8.dp), verticalAlignment = Alignment.Companion.CenterVertically) {
            Icon(
                imageVector = if (showPorts.value) ICON_DOWN else ICON_RIGHT, contentDescription = "Expand Ports"
            )
            Text(
                text = "Ports (${svc.spec?.ports?.size ?: 0})",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (showPorts.value && !svc.spec?.ports.isNullOrEmpty()) {
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.Companion.padding(16.dp)) {
                    // Header row
                    Row(modifier = Modifier.Companion.fillMaxWidth()) {
                        Text(
                            text = "Name",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Companion.Bold),
                            modifier = Modifier.Companion.weight(0.22f)
                        )
                        Text(
                            text = "Port",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Companion.Bold),
                            modifier = Modifier.Companion.weight(0.18f)
                        )
                        Text(
                            text = "Target Port",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Companion.Bold),
                            modifier = Modifier.Companion.weight(0.25f)
                        )
                        Text(
                            text = "Protocol",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Companion.Bold),
                            modifier = Modifier.Companion.weight(0.2f)
                        )
                        Text(
                            text = "Node Port",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Companion.Bold),
                            modifier = Modifier.Companion.weight(0.15f)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.Companion.padding(vertical = 4.dp))

                    // Port rows
                    svc.spec?.ports?.forEach { port ->
                        Row(
                            modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Companion.CenterVertically
                        ) {
                            Text(
                                text = port.name ?: "-",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.Companion.weight(0.22f)
                            )
                            Text(
                                text = port.port?.toString() ?: "-",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.Companion.weight(0.18f)
                            )
                            Text(
                                text = port.targetPort?.toString() ?: "-",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.Companion.weight(0.25f)
                            )
                            Text(
                                text = port.protocol ?: "TCP",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.Companion.weight(0.2f)
                            )
                            Text(
                                text = port.nodePort?.toString() ?: "-",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.Companion.weight(0.15f)
                            )
                        }
                    }
                }
            }
        }

        // TODO Add get endpoints
//        HorizontalDivider(modifier = Modifier.Companion.padding(vertical = 8.dp))
//
//        // Endpoints section
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .clickable { showEndpoints.value = !showEndpoints.value }
//                .padding(vertical = 8.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Icon(
//                imageVector = if (showEndpoints.value) .ICON_DOWN else .ICON_RIGHT,
//                contentDescription = "Expand Endpoints"
//            )
//            Text(
//                text = "Service Endpoints",
//                style = MaterialTheme.typography.titleMedium,
//            )
//        }
//
//        if (showEndpoints.value) {
//            // This would require fetching Endpoints from the API separately
//            // For demonstration, we'll show a placeholder with how to implement it
//            Card(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
//                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
//            ) {
//                Column(modifier = Modifier.padding(16.dp)) {
//                    Text(
//                        text = "Endpoints for service ${svc.metadata?.name} should be fetched separately from the Kubernetes API.",
//                        style = MaterialTheme.typography.bodyMedium,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                    )
//
//                    Spacer(modifier = Modifier.height(8.dp))
//
//                    // Placeholder for how the implementation would look
//                    Text(
//                        text = "Implementation note: You would need to call:",
//                        style = MaterialTheme.typography.bodySmall,
//                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
//                    )
//
//                    Text(
//                        text = "client.endpoints().inNamespace(${svc.metadata?.namespace}).withName(${svc.metadata?.name}).get()",
//                        style = MaterialTheme.typography.bodySmall,
//                        fontFamily = FontFamily.Monospace,
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .background(MaterialTheme.colorScheme.surface)
//                            .padding(8.dp)
//                    )
//
//                    // If you had the actual endpoints:
//                    /*
//                    endpoints.subsets?.forEach { subset ->
//                        subset.addresses?.forEach { address ->
//                            Row {
//                                Text(address.ip ?: "")
//                                // Show target references
//                                if (address.targetRef != null) {
//                                    Text("-> ${address.targetRef.kind}/${address.targetRef.name}")
//                                }
//                            }
//                        }
//                    }
//                    */
//                }
//            }
//        }

        HorizontalDivider(modifier = Modifier.Companion.padding(vertical = 8.dp))

        // Labels section
        Row(modifier = Modifier.Companion.fillMaxWidth().clickable { showLabels.value = !showLabels.value }
            .padding(vertical = 8.dp), verticalAlignment = Alignment.Companion.CenterVertically) {
            Icon(
                imageVector = if (showLabels.value) ICON_DOWN else ICON_RIGHT, contentDescription = "Expand Labels"
            )
            Text(
                text = "Labels (${svc.metadata?.labels?.size ?: 0})",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (showLabels.value) {
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.Companion.padding(16.dp)) {
                    svc.metadata?.labels?.entries?.forEach { (key, value) ->
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

                    if (svc.metadata?.labels.isNullOrEmpty()) {
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

        // Annotations section
        Row(modifier = Modifier.Companion.fillMaxWidth().clickable { showAnnotations.value = !showAnnotations.value }
            .padding(vertical = 8.dp), verticalAlignment = Alignment.Companion.CenterVertically) {
            Icon(
                imageVector = if (showAnnotations.value) ICON_DOWN else ICON_RIGHT,
                contentDescription = "Expand Annotations"
            )
            Text(
                text = "Annotations (${svc.metadata?.annotations?.size ?: 0})",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (showAnnotations.value) {
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.Companion.padding(16.dp)) {
                    svc.metadata?.annotations?.entries?.forEach { (key, value) ->
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

                    if (svc.metadata?.annotations.isNullOrEmpty()) {
                        Text(
                            text = "No annotations",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // You could add a section for events related to this service if you implement that
    }
}