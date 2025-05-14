import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.client.KubernetesClient
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

suspend fun loadConfigMapsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "ConfigMaps", namespace) { cl, ns ->
        if (ns == null) cl.configMaps().inAnyNamespace().list().items else cl.configMaps().inNamespace(ns).list().items
    }

@Composable
fun ConfigMapDetailsView(cm: ConfigMap) {
    // TODO change vertical align cmname,copybutton
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    Box(modifier = Modifier.Companion.fillMaxSize()) {
        Column(modifier = Modifier.Companion.fillMaxWidth()) {
            DetailRow("Name", cm.metadata?.name)
            DetailRow("Namespace", cm.metadata?.namespace)
            DetailRow("Created", formatAge(cm.metadata?.creationTimestamp))

            // Заголовок секції Data
            Text(
                text = "ConfigMap Data:",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Companion.Bold),
                modifier = Modifier.Companion.padding(vertical = 8.dp)
            )

            // Відображення ключів та їх значень
            cm.data?.forEach { (key, cmValue) ->
                Row(
                    modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Companion.Top
                ) {
                    Text(
                        text = "$key:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Companion.Bold),
                        modifier = Modifier.Companion.width(150.dp)
                    )
                    Text(
                        text = cmValue,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.Companion.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val textToCopy = cmValue
                            try {
                                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                val selection = StringSelection(textToCopy)
                                clipboard.setContents(selection, null)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "Value for '$key' copied to clipboard",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            } catch (e: Exception) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "Error copying: ${e.message}", duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        }) {
                        Icon(
                            imageVector = ICON_COPY,
                            contentDescription = "Copy value",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

        }
        // Snackbar для відображення сповіщень
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.Companion.align(Alignment.Companion.BottomCenter).padding(16.dp)
        )
    }
}