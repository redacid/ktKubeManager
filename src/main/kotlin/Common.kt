import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

@Composable
fun ErrorDialog(
    showDialog: Boolean,
    errorMessage: String,
    onDismiss: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Connection error") },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text("OK", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

// Calculate optimal column widths based on content
@Composable
fun calculateColumnWidths(
    headers: List<String>,
    items: List<HasMetadata>,
    resourceType: String,
    minColumnWidth: Int = 60,
    maxColumnWidth: Int = 500,
    padding: Int = 16
): List<Int> {
    // Text measurer to calculate text dimensions
    val textMeasurer = rememberTextMeasurer()
    val headerStyle = MaterialTheme.typography.titleSmall
    val cellStyle = MaterialTheme.typography.bodyMedium

    return remember(headers, items, resourceType) {
        // Initialize with minimum widths
        val widths = MutableList(headers.size) { minColumnWidth }

        // Measure header widths
        headers.forEachIndexed { index, header ->
            val textWidth = measureTextWidth(textMeasurer, header, headerStyle)
            widths[index] = maxOf(
                widths[index], (textWidth + padding).coerceIn(minColumnWidth, maxColumnWidth)
            )
        }

        // Measure data widths (sample up to 100 items for performance)
        val sampleItems = if (items.size > 100) items.take(100) else items
        sampleItems.forEach { item ->
            headers.forEachIndexed { colIndex, _ ->
                val cellData = getCellData(item, colIndex, resourceType)
                val textWidth = measureTextWidth(textMeasurer, cellData, cellStyle)
                widths[colIndex] = maxOf(
                    widths[colIndex], (textWidth + padding).coerceIn(minColumnWidth, maxColumnWidth)
                )
            }
        }

        widths
    }
}

// Helper function to measure text width
fun measureTextWidth(
    textMeasurer: TextMeasurer, text: String, style: TextStyle
): Int {
    val textLayoutResult = textMeasurer.measure(
        text = text, style = style
    )
    return textLayoutResult.size.width
}

fun convertJsonToYaml(jsonString: String): String {
    try {
        // Parse JSON to object
        val jsonMapper = ObjectMapper().registerKotlinModule()
        val jsonObject = jsonMapper.readValue(jsonString, Any::class.java)

        // Convert object to YAML
        val yamlMapper = ObjectMapper(
            YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
        ).registerKotlinModule()

        return yamlMapper.writeValueAsString(jsonObject)
    } catch (e: Exception) {
        return "Error converting JSON to YAML: ${e.message}"
    }
}

@Composable
fun DetailRow(label: String, value: String?) {
    Row(modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp)) {
        Text( // M3 Text
            text = "$label:",
            style = MaterialTheme.typography.titleSmall.copy(/*fontWeight = FontWeight.Companion.Bold*/), // M3 Typography
            modifier = Modifier.Companion.width(150.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text( // M3 Text
            text = value ?: "<none>", style = MaterialTheme.typography.bodyMedium, // M3 Typography
            modifier = Modifier.Companion.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// TODO: use this in all detailView functions
@Composable
fun DetailSectionHeader(title: String, expanded: MutableState<Boolean>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded.value = !expanded.value }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Companion.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded.value) ICON_DOWN else ICON_RIGHT,
            contentDescription = "Toggle $title"
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            //fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.fillMaxWidth()
    )
}

suspend fun <T> fetchK8sResource(
    client: KubernetesClient?, resourceType: String, namespace: String?, // Додано параметр неймспейсу
    apiCall: (KubernetesClient, String?) -> List<T>? // Лямбда тепер приймає клієнт і неймспейс
): Result<List<T>> {
    if (client == null) return Result.failure(IllegalStateException("Kubernetes client is not initialized"))
    val targetNamespace = if (namespace == ALL_NAMESPACES_OPTION) null else namespace
    val nsLog = targetNamespace ?: "all"
    logger.info("Loading the list $resourceType (Namespace: $nsLog)...")
    return try {
        val items = withContext(Dispatchers.IO) {
            logger.info("[IO] Call API for $resourceType (Namespace: $nsLog)...")
            apiCall(client, targetNamespace) ?: emptyList() // Передаємо неймспейс у лямбду
        }
        logger.info("Loaded ${items.size} $resourceType (Namespace: $nsLog).")
        try {
            @Suppress("UNCHECKED_CAST") val sortedItems = items.sortedBy { (it as? HasMetadata)?.metadata?.name ?: "" }
            Result.success(sortedItems)
        } catch (e: Exception) {
            logger.warn("Failed sort $resourceType: ${e.message}")
            Result.success(items)
        }
    } catch (e: KubernetesClientException) {
        logger.error("KubeExc $resourceType (NS: $nsLog): ${e.message}", e); Result.failure(e)
    } catch (e: Exception) {
        logger.error("Error $resourceType (NS: $nsLog): ${e.message}", e); Result.failure(e)
    }
}

val resourceTreeData: Map<String, List<String>> = mapOf(
    "" to listOf("Cluster", "Workloads", "Network", "Storage", "Configuration", "Access Control", "CustomResources"),
    "Cluster" to listOf("Namespaces", "Nodes", "Events"),
    "Workloads" to listOf("Pods", "Deployments", "StatefulSets", "DaemonSets", "ReplicaSets", "Jobs", "CronJobs"),
    "Network" to listOf("Services", "Ingresses", "Endpoints", "NetworkPolicies"),
    "Storage" to listOf("PersistentVolumes", "PersistentVolumeClaims", "StorageClasses"),
    "Configuration" to listOf("ConfigMaps", "Secrets"),
    "Access Control" to listOf("ServiceAccounts", "Roles", "RoleBindings", "ClusterRoles", "ClusterRoleBindings"),
    "CustomResources" to listOf("CRDs")
)
val resourceLeafNodes: Set<String> = setOf(
    "Namespaces",
    "Nodes",
    "Events",
    "Pods",
    "Deployments",
    "StatefulSets",
    "DaemonSets",
    "ReplicaSets",
    "Jobs",
    "CronJobs",
    "Services",
    "Ingresses",
    "Endpoints",
    "NetworkPolicies",
    "PersistentVolumes",
    "PersistentVolumeClaims",
    "StorageClasses",
    "ConfigMaps",
    "Secrets",
    "ServiceAccounts",
    "Roles",
    "RoleBindings",
    "ClusterRoles",
    "ClusterRoleBindings",
    "CRDs"
)

val NSResources: Set<String> =
    resourceLeafNodes - setOf("Nodes", "Events", "PersistentVolumes", "StorageClasses", "ClusterRoles", "ClusterRoleBindings", "CRDs")
val logger = LoggerFactory.getLogger("KKM")!!