import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.Endpoints
import io.fabric8.kubernetes.api.model.Event
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.PersistentVolume
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apps.DaemonSet
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.kubernetes.api.model.batch.v1.CronJob
import io.fabric8.kubernetes.api.model.batch.v1.Job
import io.fabric8.kubernetes.api.model.networking.v1.Ingress
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy
import io.fabric8.kubernetes.api.model.rbac.ClusterRole
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding
import io.fabric8.kubernetes.api.model.rbac.Role
import io.fabric8.kubernetes.api.model.rbac.RoleBinding
import io.fabric8.kubernetes.api.model.storage.StorageClass
import com.sebastianneubauer.jsontree.JsonTree
import com.sebastianneubauer.jsontree.TreeColors
import com.sebastianneubauer.jsontree.TreeState
import com.sebastianneubauer.jsontree.defaultDarkColors
import com.sebastianneubauer.jsontree.defaultLightColors




@Composable
fun ResourceDetailPanel(
    resource: Any?,
    resourceType: String?,
    onClose: () -> Unit,
    onOwnerClick: ((kind: String, name: String, namespace: String?) -> Unit)? = null,
    onShowLogsRequest: (namespace: String, podName: String, containerName: String) -> Unit
) {
    if (resource == null || resourceType == null) return

    Column(modifier = Modifier.Companion.fillMaxSize().padding(8.dp)) {
        // --- Detail Header ---
        Row(
            modifier = Modifier.Companion.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.Companion.CenterVertically
        ) {
            Button(onClick = onClose) {
                Icon(ICON_LEFT, contentDescription = "Back")
                Spacer(Modifier.Companion.width(4.dp))
                Text("Back")
            }
            Spacer(Modifier.Companion.weight(1f))

            val name = if (resource is HasMetadata) resource.metadata?.name else "Details"
            Text(
                text = "$resourceType: $name",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Companion.Ellipsis
            )
            Spacer(Modifier.Companion.weight(1f))

            if (resource is HasMetadata) {
                JsonViewButton(resource)
                Spacer(Modifier.width(8.dp))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // --- End Detail Header

        // --- Уміст деталей ---
        Box(modifier = Modifier.Companion.weight(1f).verticalScroll(rememberScrollState())) {
            Column(modifier = Modifier.Companion.padding(top = 8.dp)) {
                // --- Виклик відповідного .DetailsView ---
                when (resourceType) {
                    // ВАЖЛИВО: Передаємо onShowLogsRequest в .PodDetailsView
                    "Pods" -> if (resource is Pod) PodDetailsView(
                        pod = resource,
                        onOwnerClick = onOwnerClick,
                        onShowLogsRequest = { containerName ->
                            (resource as? HasMetadata)?.metadata?.let { meta ->
                                onShowLogsRequest(
                                    meta.namespace,
                                    meta.name,
                                    containerName
                                )
                            } ?: logger.error("Metadata is null for Pod.")
                        })
                    else Text("Invalid Pod data")
                    "Namespaces" -> if (resource is Namespace) NamespaceDetailsView(ns = resource) else Text("Invalid Namespace data")
                    "Nodes" -> if (resource is Node) NodeDetailsView(node = resource) else Text("Invalid Node data")
                    "Deployments" -> if (resource is Deployment) DeploymentDetailsView(dep = resource) else Text("Invalid Deployment data")
                    "Services" -> if (resource is Service) ServiceDetailsView(svc = resource) else Text("Invalid Service data")
                    "Secrets" -> if (resource is Secret) SecretDetailsView(secret = resource) else Text("Invalid Secret data")
                    "ConfigMaps" -> if (resource is ConfigMap) ConfigMapDetailsView(cm = resource) else Text("Invalid ConfigMap data")
                    "PersistentVolumes" -> if (resource is PersistentVolume) PVDetailsView(pv = resource) else Text("Invalid PV data")
                    "PersistentVolumeClaims" -> if (resource is PersistentVolumeClaim) PVCDetailsView(pvc = resource) else Text("Invalid PVC data")
                    "Ingresses" -> if (resource is Ingress) IngressDetailsView(ing = resource) else Text("Invalid Ingress data")
                    "Endpoints" -> if (resource is Endpoints) EndpointsDetailsView(endpoint = resource) else Text("Invalid Endpoint data")
                    "StatefulSets" -> if (resource is StatefulSet) StatefulSetDetailsView(sts = resource) else Text("Invalid StatefulSet data")
                    "DaemonSets" -> if (resource is DaemonSet) DaemonSetDetailsView(ds = resource) else Text("Invalid DaemonSet data")
                    "Jobs" -> if (resource is Job) JobDetailsView(job = resource) else Text("Invalid Job data")
                    "CronJobs" -> if (resource is CronJob) CronJobDetailsView(cronJob = resource) else Text("Invalid CronJob data")
                    "ReplicaSets" -> if (resource is ReplicaSet) ReplicaSetDetailsView(replicaSet = resource,onOwnerClick = onOwnerClick) else Text("Invalid ReplicaSet data")
                    "NetworkPolicies" -> if (resource is NetworkPolicy) NetworkPolicyDetailsView(networkPolicy = resource) else Text("Invalid NetworkPolicy data")
                    "Roles" -> if (resource is Role) RoleDetailsView(role = resource) else Text("Invalid Role data")
                    "RoleBindings" -> if (resource is RoleBinding) RoleBindingDetailsView(roleBinding = resource) else Text("Invalid RoleBinding data")
                    "ClusterRoles" -> if (resource is ClusterRole) ClusterRoleDetailsView(clusterRole = resource) else Text("Invalid ClusterRole data")
                    "ClusterRoleBindings" -> if (resource is ClusterRoleBinding) ClusterRoleBindingDetailsView(clusterRoleBinding = resource) else Text("Invalid ClusterRoleBinding data")
                    "ServiceAccounts" -> if (resource is ServiceAccount) ServiceAccountDetailsView(serviceAccount = resource) else Text("Invalid ServiceAccount data")
                    "Events" -> if (resource is Event) EventDetailsView(event = resource) else Text("Invalid Event data")
                    "StorageClasses" -> if (resource is StorageClass) StorageClassDetailsView(storageClass = resource) else Text("Invalid StorageClass data")
                    "CRDs" -> if (resource is CustomResourceDefinition) CrdDetailsView(crd = resource) else Text("Invalid CRD data")

                    // TODO: Додати кейси для всіх інших типів ресурсів (NetworkPolicies, Events, CustomResourceDefinitions, etc.)
                    else -> {
                        Text("Simple detail view for '$resourceType'")
                        if (resource is HasMetadata) {
                            Spacer(Modifier.Companion.height(16.dp))
                            BasicMetadataDetails(resource)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BasicMetadataDetails(resource: HasMetadata) { // Допоміжна функція для базових метаданих
    Text("Basic Metadata:", style = MaterialTheme.typography.titleMedium)
    DetailRow("Name", resource.metadata?.name)
    DetailRow("Namespace", resource.metadata?.namespace) // Буде null для кластерних ресурсів
    DetailRow("Created", formatAge(resource.metadata?.creationTimestamp))
    DetailRow("UID", resource.metadata?.uid)
    DetailRow("Labels", resource.metadata?.labels?.entries?.joinToString("\n") { "${it.key}=${it.value}" })
    DetailRow("Annotations", resource.metadata?.annotations?.entries?.joinToString("\n") { "${it.key}=${it.value}" })
}

@Composable
fun DetailRow(label: String, value: String?) {
    Row(modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp)) {
        Spacer(Modifier.Companion.width(16.dp))
        Text(
            text = "$label:",
            style = MaterialTheme.typography.titleSmall.copy(/*fontWeight = FontWeight.Companion.Bold*/),
            modifier = Modifier.Companion.width(150.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value ?: "<none>",
            style = MaterialTheme.typography.bodyMedium, // M3 Typography
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


private val jsonMapper = ObjectMapper().apply {
    registerKotlinModule()
    writerWithDefaultPrettyPrinter()
}

@Composable
private fun ColorScheme.isLight() = this == lightColorScheme()


@Composable
fun ShowJsonDialog(
    resource: HasMetadata,
    onDismiss: () -> Unit
) {
    val windowState = remember {
        WindowState(width = 1200.dp, height = 800.dp)
    }

    var initialState: TreeState by remember { mutableStateOf(TreeState.FIRST_ITEM_EXPANDED) }

    val jsonString = remember(resource.metadata?.uid) {
        try {
            jsonMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(resource)
        } catch (e: Exception) {
            "Помилка серіалізації JSON: ${e.message}"
        }
    }

    Window(
        onCloseRequest = onDismiss,
        title = "JSON View: ${resource.metadata?.name ?: "Resource"}",
        state = windowState
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                ) {
                    SelectionContainer(
                        modifier = Modifier
                    ) {
                        JsonTree(
                            modifier = Modifier.fillMaxSize(),

                            json = jsonString,
                            colors = if (MaterialTheme.colorScheme.isLight()) {
                                TreeColors(
                                    keyColor = MaterialTheme.colorScheme.primary,
                                    stringValueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    numberValueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    booleanValueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    nullValueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indexColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    symbolColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                TreeColors(
                                    keyColor = MaterialTheme.colorScheme.primary,
                                    stringValueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    numberValueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    booleanValueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    nullValueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indexColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    symbolColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onLoading = {
                                Text(
                                    text = "Loading...",
                                    modifier = Modifier.padding(8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            initialState = initialState,
                            showIndices = true,
                            showItemCount = true,
                            expandSingleChildren = true,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        onClick = {
                            initialState = TreeState.EXPANDED
                        }
                    ) {
                        Text(text = "Expand All")
                    }
                    Button(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        onClick = {
                            initialState = TreeState.FIRST_ITEM_EXPANDED
                        }
                    ) {
                        Text(text = "Collapse All")
                    }
                    Spacer(Modifier.width(30.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Close")
                    }


                }
            }
        }
    }
}




@Composable
fun JsonViewButton(resource: HasMetadata) {
    var showJsonDialog by remember { mutableStateOf(false) }

    val resourceCopy = remember(resource.metadata?.uid) {
        try {
            jsonMapper.readValue(
                jsonMapper.writeValueAsString(resource),
                resource.javaClass
            )
        } catch (e: Exception) {
            logger.error("Error creating resource copy: ${e.message}")
            resource
        }
    }

    Button(
        onClick = { showJsonDialog = true },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Icon(ICON_CODE, contentDescription = "View JSON")
        Spacer(Modifier.width(4.dp))
        Text("View JSON")
    }

    if (showJsonDialog) {
        ShowJsonDialog(
            resource = resourceCopy,
            onDismiss = { showJsonDialog = false }
        )
    }
}


