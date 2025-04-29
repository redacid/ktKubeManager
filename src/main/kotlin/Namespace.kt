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
    Column {
        DetailRow("Name", ns.metadata?.name)
        DetailRow("Status", ns.status?.phase)
        DetailRow("Created", formatAge(ns.metadata?.creationTimestamp))

        // Labels section with expandable panel
        val labelsExpanded = remember { mutableStateOf(false) }
        val labels = ns.metadata?.labels ?: emptyMap()

        Row(
            modifier = Modifier.fillMaxWidth().clickable { labelsExpanded.value = !labelsExpanded.value },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Labels (${labels.size})", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)
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
                        Text(key, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(120.dp))
                        Text(value, modifier = Modifier.padding(start = 8.dp))
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

        // Annotations section with expandable panel
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
    }
}