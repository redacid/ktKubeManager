import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.client.KubernetesClient
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.ByteArrayInputStream
import java.util.*
import java.util.zip.GZIPInputStream

suspend fun loadSecretsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Secrets", namespace) { cl, ns ->
        if (ns == null) cl.secrets().inAnyNamespace().list().items else cl.secrets().inNamespace(ns).list().items
    }

@Composable
fun SecretDetailsView(secret: Secret) {
    // For displaying copy notification
    // Для відображення сповіщення про копіювання
    val snackbarHostState = remember { SnackbarHostState() }
    // Coroutine scope for showing snackbar
    // Корутин скоуп для показу снекбара
    val coroutineScope = rememberCoroutineScope()

    // Check if this is a Helm release secret
    val isHelmRelease = secret.type == "helm.sh/release.v1"

    // For storing Helm release data
    var helmReleaseInfo by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var helmReleaseError by remember { mutableStateOf<String?>(null) }

    // Process Helm release data if needed
    LaunchedEffect(secret) {
        if (isHelmRelease && secret.data?.isNotEmpty() == true) {
            try {
                // Most Helm releases store data in the "release" key
                val releaseData = secret.data?.get("release")
                if (releaseData != null) {
                    // Decode base64 first
                    // cat hr3.txt | base64 -d | base64 -d | gzip -d
                    val decodedBytes =
                        Base64.getDecoder().decode(Base64.getDecoder().decode(releaseData))

                    // Decompress GZIP data
                    val bais = ByteArrayInputStream(decodedBytes)
                    val gzis = GZIPInputStream(bais)
                    val decompressedData = gzis.readBytes()

                    // Parse JSON data
                    val mapper = ObjectMapper().registerKotlinModule()

                    @Suppress("UNCHECKED_CAST") val helmData =
                        mapper.readValue(decompressedData, Map::class.java) as Map<String, Any?>
                    helmReleaseInfo = helmData
                }
            } catch (e: Exception) {
                helmReleaseError = "Error decoding Helm release: ${e.message}"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth()
            //.fillMaxSize()
            //.verticalScroll(rememberScrollState())
        ) {
            DetailRow("Name", secret.metadata?.name)
            DetailRow("Namespace", secret.metadata?.namespace)
            DetailRow("Created", formatAge(secret.metadata?.creationTimestamp))
            DetailRow("Type", secret.type)

            // Special handling for Helm release secrets
            if (isHelmRelease) {
                Text(
                    text = "Helm Release Data:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (helmReleaseError != null) {
                    Text(
                        text = helmReleaseError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else if (helmReleaseInfo != null) {
                    // Display Helm release data
                    val releaseInfo = helmReleaseInfo!!

                    // Extract and display key information
                    val name = (releaseInfo["name"] as? String) ?: "Unknown"
                    val version = (releaseInfo["version"] as? Int)?.toString() ?: "Unknown"
                    val status = ((releaseInfo["info"] as? Map<*, *>)?.get("status") as? String) ?: "Unknown"
                    val chart = ((releaseInfo["chart"] as? Map<*, *>)?.get("metadata") as? Map<*, *>)?.let {
                        "${it["name"]}:${it["version"]}"
                    } ?: "Unknown"

                    // Display main info
                    DetailRow("Release Name", name)
                    DetailRow("Release Version", version)
                    DetailRow("Release Status", status)
                    DetailRow("Chart", chart)

                    // Display values data
                    val values = releaseInfo["config"] as? Map<*, *>
                    if (values != null && values.isNotEmpty()) {
                        // Convert to JSON for display
                        val mapper = ObjectMapper().registerKotlinModule()
                        val valuesJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(values)

                        val isYamlView = remember { mutableStateOf(false) }
                        // Compute formatted content
                        val formattedContent = remember(valuesJson, isYamlView.value) {
                            if (isYamlView.value) convertJsonToYaml(valuesJson) else valuesJson
                        }

                        Text(
                            text = "Values:",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                        )

                        // Format toggle button
                        OutlinedButton(
                            onClick = { isYamlView.value = !isYamlView.value },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text(
                                text = if (isYamlView.value) "View as JSON" else "View as YAML",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // Copy button for values
                        IconButton(
                            onClick = {
                                try {
                                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                    val selection = StringSelection(formattedContent)
                                    clipboard.setContents(selection, null)

                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Values copied to clipboard", duration = SnackbarDuration.Short
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
                                contentDescription = "Copy values",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                SelectionContainer(
                                    modifier = Modifier.padding(16.dp),
                                ) {
                                    Text(
                                        softWrap = true,
                                        text = formattedContent,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                            }
                        }
                    }
                    //Global Values
                    val valuesGlobal = ((releaseInfo["chart"] as? Map<*, *>)?.get("values") as? Map<*, *>)
                    if (valuesGlobal != null && valuesGlobal.isNotEmpty()) {
                        // Convert to JSON for display
                        val mapper = ObjectMapper().registerKotlinModule()
                        val valuesGlobalJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(valuesGlobal)

                        val isYamlView = remember { mutableStateOf(false) }
                        // Compute formatted content
                        val formattedGlobalContent = remember(valuesGlobalJson, isYamlView.value) {
                            if (isYamlView.value) convertJsonToYaml(valuesGlobalJson) else valuesGlobalJson
                        }

                        Text(
                            text = "GlobalValues:",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                        )
                        // Format toggle button
                        OutlinedButton(
                            onClick = { isYamlView.value = !isYamlView.value },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text(
                                text = if (isYamlView.value) "View as JSON" else "View as YAML",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        // Copy button for values
                        IconButton(

                            onClick = {
                                try {
                                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                    val selection = StringSelection(formattedGlobalContent)
                                    clipboard.setContents(selection, null)

                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Values copied to clipboard", duration = SnackbarDuration.Short
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
                                contentDescription = "Copy valuesGlobal",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                SelectionContainer(
                                    modifier = Modifier.padding(16.dp),
                                ) {
                                    Text(
                                        softWrap = true,
                                        text = formattedGlobalContent,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                            }
                        }
                    }
                } else {
                    Text(
                        text = "Processing Helm release data...",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                // Regular secret data handling (original code for non-Helm secrets)

                // Data section header
                // Заголовок секції Data
                Text(
                    text = "Secret Data:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Display keys and their values
                // Відображення ключів та їх значень
                secret.data?.forEach { (key, encodedValue) ->
                    var isDecoded by remember { mutableStateOf(false) }
                    var decodedValue by remember { mutableStateOf("") }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Key
                        Text(
                            text = "$key:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.width(150.dp)
                        )

                        // Value (encoded or decoded)
                        // Значення (закодоване або декодоване)
                        Text(
                            text = if (isDecoded) decodedValue else encodedValue,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )

                        // Copy icon
                        // Іконка для копіювання
                        IconButton(
                            onClick = {
                                val textToCopy = if (isDecoded) decodedValue else encodedValue
                                try {
                                    // Copy text to clipboard
                                    // Копіюємо текст у буфер обміну
                                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                    val selection = StringSelection(textToCopy)
                                    clipboard.setContents(selection, null)

                                    // Show successful copy notification
                                    // Показуємо сповіщення про успішне копіювання
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Value for '$key' copied to clipboard",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                } catch (e: Exception) {
                                    // Show error notification
                                    // Сповіщаємо про помилку
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

                        // Decode/encode icon
                        // Іконка для декодування/кодування
                        IconButton(
                            onClick = {
                                if (!isDecoded) {
                                    try {
                                        // Decode from Base64
                                        decodedValue = String(Base64.getDecoder().decode(encodedValue))
                                        isDecoded = true
                                    } catch (e: Exception) {
                                        decodedValue = "Error decoding: ${e.message}"
                                        isDecoded = true
                                    }
                                } else {
                                    // Return to encoded view
                                    // Повертаємося в закодований вигляд
                                    isDecoded = false
                                }
                            }) {
                            Icon(
                                imageVector = if (isDecoded) ICON_EYEOFF else ICON_EYE,
                                contentDescription = if (isDecoded) "Hide decoded value" else "Decode value",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Display stringData (unencoded values)
                secret.stringData?.let { stringData ->
                    if (stringData.isNotEmpty()) {
                        Text(
                            text = "String Data (not encoded):",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                        )

                        stringData.forEach { (key, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$key:",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.width(150.dp)
                                )

                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )

                                // Copy icon for stringData
                                IconButton(
                                    onClick = {
                                        try {
                                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                            val selection = StringSelection(value)
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
                                                    message = "Error copying: ${e.message}",
                                                    duration = SnackbarDuration.Short
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
                }

                // If no data in the secret
                // Якщо немає даних у секреті
                if ((secret.data == null || secret.data!!.isEmpty()) && (secret.stringData == null || secret.stringData!!.isEmpty())) {
                    Text(
                        text = "No data in this Secret",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        // Snackbar for displaying notifications
        // Snackbar для відображення сповіщень
        SnackbarHost(
            hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}