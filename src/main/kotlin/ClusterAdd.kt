import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import software.amazon.awssdk.services.eks.EksClient
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region
import kotlinx.coroutines.launch
import software.amazon.awssdk.services.sts.StsClient

// Додайте цю функцію для отримання списку регіонів
private fun getAwsRegions(): List<String> {
    println("Отримання списку регіонів...")
    val regions = Region.regions()
        .filter { it.isGlobalRegion || it.metadata().description() != null }
        .map { it.id() }
        .sorted()
    println("Отримано регіони: $regions")
    return regions
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClusterAddDialog(
    onDismiss: () -> Unit,
    settingsManager: SettingsManager
) {
    var profileName by remember { mutableStateOf("") }
    var accessKeyId by remember { mutableStateOf("") }
    var secretAccessKey by remember { mutableStateOf("") }
    var selectedRegion by remember { mutableStateOf("") }
    var selectedCluster by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var regions by remember { mutableStateOf(listOf<String>()) }
    var clusters by remember { mutableStateOf(listOf<String>()) }
    var showError by remember { mutableStateOf<String?>(null) }
    var isRegionExpanded by remember { mutableStateOf(false) }
    var isClusterExpanded by remember { mutableStateOf(false) }
    var clusterEndpoint by remember { mutableStateOf("") }
    var clusterCertificate by remember { mutableStateOf("") }


    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(400.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 5.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Додати EKS кластер",
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Назва профілю") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = accessKeyId,
                    onValueChange = { accessKeyId = it },
                    label = { Text("AWS Access Key ID") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = secretAccessKey,
                    onValueChange = { secretAccessKey = it },
                    label = { Text("AWS Secret Access Key") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        scope.launch {
                            isConnecting = true
                            try {
                                val credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                                println("Перевірка підключення до AWS...")
                                val stsClient = StsClient.builder()
                                    .region(Region.AWS_GLOBAL)
                                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                                    .build()

                                val identity = stsClient.getCallerIdentity()
                                println("Успішне підключення до AWS. Account ID: ${identity.account()}")

                                regions = getAwsRegions()
                                println("Отримано ${regions.size} регіонів")
                                showError = null
                            } catch (e: Exception) {
                                println("Помилка підключення до AWS: ${e.message}")
                                println("Stacktrace: ${e.stackTraceToString()}")
                                showError = "Помилка підключення до AWS: ${e.message}"
                            } finally {
                                isConnecting = false
                            }
                        }
                    },
                    enabled = accessKeyId.isNotEmpty() && secretAccessKey.isNotEmpty() && !isConnecting
                ) {
                    Text("Connect")
                }


                if (regions.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = isRegionExpanded,
                        onExpandedChange = { isRegionExpanded = it }

                    ) {
                        OutlinedTextField(
                            value = selectedRegion,
                            onValueChange = {},
                            label = { Text("Регіон") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            readOnly = true
                        )

                        ExposedDropdownMenu(
                            expanded = isRegionExpanded,
                            onDismissRequest = { isRegionExpanded = false }

                        ) {
                            regions.forEach { region ->
                                DropdownMenuItem(
                                    text = { Text(region) },
                                    onClick = {
                                        selectedRegion = region
                                        isRegionExpanded = false
                                        scope.launch {
                                            try {
                                                val credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                                                val eksClient = EksClient.builder()
                                                    .region(Region.of(selectedRegion))
                                                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                                                    .build()

                                                val response = eksClient.listClusters()
                                                clusters = response.clusters() ?: emptyList()
                                            } catch (e: Exception) {
                                                showError = "Помилка отримання кластерів: ${e.message}"
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                if (clusters.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = isClusterExpanded,
                        onExpandedChange = { isClusterExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCluster,
                            onValueChange = {},
                            label = { Text("Кластер") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            readOnly = true
                        )

                        ExposedDropdownMenu(
                            expanded = isClusterExpanded,
                            onDismissRequest = { isClusterExpanded = false }
                        ) {
                            clusters.forEach { cluster ->
                                DropdownMenuItem(
                                    text = { Text(cluster) },
                                    onClick = {
                                        selectedCluster = cluster
                                        isClusterExpanded = false
                                        scope.launch {
                                            try {
                                                val credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                                                val eksClient = EksClient.builder()
                                                    .region(Region.of(selectedRegion))
                                                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                                                    .build()

                                                // Отримуємо детальну інформацію про кластер
                                                val clusterDetails = eksClient.describeCluster { builder ->
                                                    builder.name(cluster)
                                                }

                                                // Зберігаємо endpoint та сертифікат
                                                clusterEndpoint = clusterDetails.cluster().endpoint() ?: ""
                                                clusterCertificate = clusterDetails.cluster().certificateAuthority().data() ?: ""

                                                println("Отримано дані кластера:")
                                                println("Endpoint: $clusterEndpoint")
                                                println("Certificate length: ${clusterCertificate.length}")

                                            } catch (e: Exception) {
                                                showError = "Помилка отримання деталей кластера: ${e.message}"
                                            }
                                        }

                                    }
                                )
                            }
                        }
                    }
                }


                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text("Скасувати")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isConnecting = true
                                try {
                                    val clusterConfig = ClusterConfig(
                                        profileName = profileName,
                                        accessKeyId = accessKeyId,
                                        secretAccessKey = secretAccessKey,
                                        region = selectedRegion,
                                        clusterName = selectedCluster,
                                        endpoint = clusterEndpoint,
                                        certificateAuthority = clusterCertificate
                                    )

                                    settingsManager.updateSettings {
                                        copy(
                                            clusters = this.clusters + clusterConfig,
                                            lastCluster = selectedCluster
                                        )
                                    }
                                    onDismiss()
                                } catch (e: Exception) {
                                    showError = e.message
                                } finally {
                                    isConnecting = false
                                }
                            }
                        },
                        enabled = !isConnecting &&
                                selectedCluster.isNotEmpty() &&
                                clusterEndpoint.isNotEmpty() &&
                                clusterCertificate.isNotEmpty()
                    ) {
                        Text("Додати")
                    }


                }

                if (showError != null) {
                    Text(
                        text = showError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}