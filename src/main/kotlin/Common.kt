import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
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

val NoNSResources: Set<String> =
    resourceLeafNodes - setOf("Nodes", "Namespaces", "PersistentVolumes", "StorageClasses", "ClusterRoles", "ClusterRoleBindings", "CRDs")
//val logger = LoggerFactory.getLogger("YF")!!

val logger = LoggerFactory.getLogger("YF")!!.apply {
    (this as ch.qos.logback.classic.Logger).level = ch.qos.logback.classic.Level.WARN
}



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

suspend fun <T> fetchK8sResource(
    client: KubernetesClient?, resourceType: String, namespace: String?,
    apiCall: (KubernetesClient, String?) -> List<T>?
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
        logger.error("Fetch resource Error $resourceType (NS: $nsLog): ${e.message}", e); Result.failure(e)
    }
}

