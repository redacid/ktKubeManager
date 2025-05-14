import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.kubernetes.client.KubernetesClient

// ... і так далі для всіх інших типів ресурсів ...
suspend fun loadStatefulSetsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "StatefulSets", namespace) { cl, ns ->
        if (ns == null) cl.apps().statefulSets().inAnyNamespace().list().items else cl.apps().statefulSets()
            .inNamespace(ns).list().items
    }

@Composable
fun StatefulSetDetailsView(sts: StatefulSet) {
    Column(
        modifier = Modifier.Companion
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "StatefulSet Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        DetailRow("Name", sts.metadata?.name)
        DetailRow("Namespace", sts.metadata?.namespace)
        DetailRow("Created", formatAge(sts.metadata?.creationTimestamp))
        DetailRow("Replicas", "${sts.status?.replicas ?: 0} / ${sts.spec?.replicas ?: 0}")
        DetailRow("Ready Replicas", "${sts.status?.readyReplicas ?: 0}")
        DetailRow("Service Name", sts.spec?.serviceName)
        DetailRow("Update Strategy", sts.spec?.updateStrategy?.type)
        DetailRow("Pod Management Policy", sts.spec?.podManagementPolicy)

        Spacer(Modifier.Companion.height(16.dp))

        // Секція контейнерів
        val containerState = remember { mutableStateOf(false) }
        DetailSectionHeader(
            title = "Containers (${sts.spec?.template?.spec?.containers?.size ?: 0})",
            expanded = containerState
        )

        if (containerState.value) {
            sts.spec?.template?.spec?.containers?.forEachIndexed { index, container ->
                Card(
                    modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.Companion.padding(8.dp)) {
                        Text(
                            text = "${index + 1}. ${container.name}",
                            fontWeight = FontWeight.Companion.Bold
                        )
                        Text("Image: ${container.image}")
                        container.ports?.let { ports ->
                            if (ports.isNotEmpty()) {
                                Text("Ports: ${ports.joinToString { "${it.containerPort}/${it.protocol ?: "TCP"}" }}")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Секція томів
        val volumesState = remember { mutableStateOf(false) }
        DetailSectionHeader(
            title = "Volume Claims (${sts.spec?.volumeClaimTemplates?.size ?: 0})",
            expanded = volumesState
        )

        if (volumesState.value) {
            sts.spec?.volumeClaimTemplates?.forEach { pvc ->
                Card(
                    modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.Companion.padding(8.dp)) {
                        Text(
                            text = pvc.metadata?.name ?: "",
                            fontWeight = FontWeight.Companion.Bold
                        )
                        Text("Storage Class: ${pvc.spec?.storageClassName ?: "default"}")
                        Text("Access Modes: ${pvc.spec?.accessModes?.joinToString(", ") ?: ""}")
                        pvc.spec?.resources?.requests?.get("storage")?.let { storage ->
                            Text("Storage: $storage")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Мітки та анотації
        val labelsState = remember { mutableStateOf(false) }
        DetailSectionHeader(title = "Labels & Annotations", expanded = labelsState)

        if (labelsState.value) {
            // Мітки
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.Companion.padding(8.dp)) {
                    Text("Labels:", fontWeight = FontWeight.Companion.Bold)
                    if (sts.metadata?.labels.isNullOrEmpty()) {
                        Text("No labels")
                    } else {
                        sts.metadata?.labels?.forEach { (key, value) ->
                            Text("$key: $value")
                        }
                    }

                    Spacer(Modifier.Companion.height(8.dp))

                    Text("Annotations:", fontWeight = FontWeight.Companion.Bold)
                    if (sts.metadata?.annotations.isNullOrEmpty()) {
                        Text("No annotations")
                    } else {
                        sts.metadata?.annotations?.forEach { (key, value) ->
                            Text("$key: $value")
                        }
                    }
                }
            }
        }
    }
}