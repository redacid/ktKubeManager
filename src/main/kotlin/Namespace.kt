import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadNamespacesFabric8(client: KubernetesClient?) =
    fetchK8sResource(client, "Namespaces", null) { cl, _ -> cl.namespaces().list().items } // Namespaces не фільтруються

@Composable
fun NamespaceDetailsView(ns: Namespace) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        // Базова інформація
        DetailRow("Name", ns.metadata?.name)
        DetailRow("Status", ns.status?.phase)
        DetailRow("Created", formatAge(ns.metadata?.creationTimestamp))
        DetailRow("UID", ns.metadata?.uid)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Секція стану та налаштувань
        val configExpanded = remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth().clickable { configExpanded.value = !configExpanded.value },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Configuration",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (configExpanded.value) ICON_UP else ICON_DOWN,
                contentDescription = if (configExpanded.value) "Collapse" else "Expand"
            )
        }

        if (configExpanded.value) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                DetailRow("Finalizers", ns.metadata?.finalizers?.joinToString(", ") ?: "None")
                DetailRow("Generation", ns.metadata?.generation?.toString() ?: "0")
                DetailRow("Resource Version", ns.metadata?.resourceVersion)
                DetailRow("Self Link", ns.metadata?.selfLink ?: "None")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Labels section
        val labelsExpanded = remember { mutableStateOf(false) }
        val labels = ns.metadata?.labels ?: emptyMap()

        Row(
            modifier = Modifier.fillMaxWidth().clickable { labelsExpanded.value = !labelsExpanded.value },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Labels (${labels.size})",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (labelsExpanded.value) ICON_UP else ICON_DOWN,
                contentDescription = if (labelsExpanded.value) "Collapse" else "Expand"
            )
        }

        if (labelsExpanded.value && labels.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                labels.forEach { (key, value) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            key,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(120.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            value,
                            modifier = Modifier.padding(start = 8.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else if (labelsExpanded.value) {
            Text(
                "No labels",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Annotations section
        val annotationsExpanded = remember { mutableStateOf(false) }
        val annotations = ns.metadata?.annotations ?: emptyMap()

        Row(
            modifier = Modifier.fillMaxWidth().clickable { annotationsExpanded.value = !annotationsExpanded.value },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Annotations (${annotations.size})",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (annotationsExpanded.value) ICON_UP else ICON_DOWN,
                contentDescription = if (annotationsExpanded.value) "Collapse" else "Expand"
            )
        }

        if (annotationsExpanded.value && annotations.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                annotations.forEach { (key, value) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            key,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(160.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            value,
                            modifier = Modifier.padding(start = 8.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else if (annotationsExpanded.value) {
            Text(
                "No annotations",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Conditions section
        val conditionsExpanded = remember { mutableStateOf(false) }
        val conditions = ns.status?.conditions ?: emptyList()

        Row(
            modifier = Modifier.fillMaxWidth().clickable { conditionsExpanded.value = !conditionsExpanded.value },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Conditions (${conditions.size})",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (conditionsExpanded.value) ICON_UP else ICON_DOWN,
                contentDescription = if (conditionsExpanded.value) "Collapse" else "Expand"
            )
        }

        if (conditionsExpanded.value && conditions.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                conditions.forEach { condition ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            condition.type ?: "Unknown",
                            fontWeight = FontWeight.SemiBold
                        )
                        Row {
                            Text(
                                "Status: ",
                                modifier = Modifier.width(80.dp)
                            )
                            Text(
                                condition.status ?: "Unknown",
                                color = if (condition.status == "True")
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }
                        condition.message?.let { message ->
                            Text(
                                message,
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else if (conditionsExpanded.value) {
            Text(
                "No conditions",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}