import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AwsProfileAddDialog(
    onDismiss: () -> Unit,
    settingsManager: SettingsManager
) {
    var profileName by remember { mutableStateOf("") }
    var accessKeyId by remember { mutableStateOf("") }
    var secretAccessKey by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }

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
                    "Add AWS profile",
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Profile name") },
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
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isConnecting = true
                                try {
                                    // Перевіряємо підключення
                                    val credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                                    val stsClient = StsClient.builder()
                                        .region(Region.AWS_GLOBAL)
                                        .credentialsProvider(StaticCredentialsProvider.create(credentials))
                                        .build()

                                    stsClient.callerIdentity

                                    // Зберігаємо профіль
                                    val awsProfile = AwsProfile(
                                        profileName = profileName,
                                        accessKeyId = accessKeyId,
                                        secretAccessKey = secretAccessKey
                                    )

                                    settingsManager.updateSettings {
                                        copy(
                                            awsProfiles = awsProfiles + awsProfile
                                        )
                                    }
                                    onDismiss()
                                } catch (e: Exception) {
                                    showError = "Connecting to AWS:${e.message}"
                                } finally {
                                    isConnecting = false
                                }
                            }
                        },
                        enabled = !isConnecting &&
                                profileName.isNotEmpty() &&
                                accessKeyId.isNotEmpty() &&
                                secretAccessKey.isNotEmpty()
                    ) {
                        Text("Save")
                    }
                }

                if (showError != null) {
                    Snackbar(
                        modifier = Modifier.padding(8.dp),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.medium,
                        action = {}
                    ) {
                        Text(
                            text = showError!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                }
            }
        }
    }
}


private fun getAwsRegions(): List<String> {
    println("Obtaining a list of regions ...")
    val regions = Region.regions()
        .filter { it.isGlobalRegion || it.metadata().description() != null }
        .map { it.id() }
        .sorted()
    println("Regions were obtained: $regions")
    return regions
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClusterAddDialog(
    onDismiss: () -> Unit,
    settingsManager: SettingsManager
) {
    var alias by remember { mutableStateOf("") }
    var selectedProfile by remember { mutableStateOf<AwsProfile?>(null) }
    var selectedRegion by remember { mutableStateOf("") }
    var selectedCluster by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var regions by remember { mutableStateOf(listOf<String>()) }
    var clusters by remember { mutableStateOf(listOf<String>()) }
    var showError by remember { mutableStateOf<String?>(null) }
    var isProfileExpanded by remember { mutableStateOf(false) }
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
                    "Add EKS cluster",
                    style = MaterialTheme.typography.headlineSmall
                )
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Connection name") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Вибір профілю
                ExposedDropdownMenuBox(
                    expanded = isProfileExpanded,
                    onExpandedChange = { isProfileExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedProfile?.profileName ?: "",
                        onValueChange = {},
                        label = { Text("AWS Profile") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(
                            type = MenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        ),
                        readOnly = true
                    )

                    ExposedDropdownMenu(
                        expanded = isProfileExpanded,
                        onDismissRequest = { isProfileExpanded = false }
                    ) {
                        settingsManager.settings.awsProfiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.profileName) },
                                onClick = {
                                    selectedProfile = profile
                                    isProfileExpanded = false
                                    // Отримуємо регіони при виборі профілю
                                    scope.launch {
                                        try {
                                            regions = getAwsRegions()
                                            selectedRegion = "" // Скидаємо вибраний регіон
                                            clusters = emptyList() // Скидаємо список кластерів
                                            selectedCluster = "" // Скидаємо вибраний кластер
                                        } catch (e: Exception) {
                                            showError = "Error Receiving Regions: ${e.message}"
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // Вибір регіону
                if (regions.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = isRegionExpanded,
                        onExpandedChange = { isRegionExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedRegion,
                            onValueChange = {},
                            label = { Text("Region") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(
                                type = MenuAnchorType.PrimaryNotEditable,
                                enabled = true
                            ),
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
                                        // Отримуємо список кластерів при виборі регіону
                                        scope.launch {
                                            try {
                                                val credentials = AwsBasicCredentials.create(
                                                    selectedProfile?.accessKeyId ?: "",
                                                    selectedProfile?.secretAccessKey ?: ""
                                                )
                                                val eksClient = EksClient.builder()
                                                    .region(Region.of(selectedRegion))
                                                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                                                    .build()

                                                val response = eksClient.listClusters()
                                                clusters = response.clusters() ?: emptyList()
                                                selectedCluster = "" // Скидаємо вибраний кластер
                                            } catch (e: Exception) {
                                                showError = "Error receiving clusters: ${e.message}"
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Вибір кластера
                if (clusters.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = isClusterExpanded,
                        onExpandedChange = { isClusterExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCluster,
                            onValueChange = {},
                            label = { Text("Cluster") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(
                                type = MenuAnchorType.PrimaryNotEditable,
                                enabled = true
                            ),
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
                                                val credentials = AwsBasicCredentials.create(
                                                    selectedProfile?.accessKeyId ?: "",
                                                    selectedProfile?.secretAccessKey ?: ""
                                                )
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

                                            } catch (e: Exception) {
                                                showError = "Error receiving cluster details: ${e.message}"
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Кнопки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }

                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text("Cancel")
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isConnecting = true
                                try {
                                    val clusterConfig = ClusterConfig(
                                        alias = alias,
                                        profileName = selectedProfile?.profileName ?: "",
                                        accessKeyId = selectedProfile?.accessKeyId ?: "",
                                        secretAccessKey = selectedProfile?.secretAccessKey ?: "",
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
                                alias.isNotEmpty() &&
                                selectedProfile != null &&
                                selectedCluster.isNotEmpty() &&
                                clusterEndpoint.isNotEmpty() &&
                                clusterCertificate.isNotEmpty()
                    ) {
                        Text("Add")
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AwsProfilesEditDialog(
    onDismiss: () -> Unit,
    settingsManager: SettingsManager
) {
    var editingProfile by remember { mutableStateOf<AwsProfile?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf<AwsProfile?>(null) }
    //var showError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(600.dp).height(400.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 5.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "AWS Profiles",
                    style = MaterialTheme.typography.headlineSmall
                )

                // Список профілів
                Surface(
                    modifier = Modifier.weight(1f),
                    tonalElevation = 1.dp
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(settingsManager.settings.awsProfiles) { profile ->
                            ListItem(
                                headlineContent = { Text(profile.profileName) },
                                supportingContent = { Text(profile.accessKeyId) },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { editingProfile = profile }) {
                                            Icon(ICON_EDIT, "Edit")
                                        }
                                        IconButton(onClick = { showDeleteConfirmation = profile }) {
                                            Icon(ICON_DELETE, "Remove")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Кнопки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }

    // Діалог редагування профілю
    editingProfile?.let { profile ->
        EditProfileDialog(
            profile = profile,
            onDismiss = { editingProfile = null },
            onSave = { updatedProfile ->
                settingsManager.updateSettings {
                    copy(
                        awsProfiles = awsProfiles.map {
                            if (it.profileName == profile.profileName) updatedProfile else it
                        }
                    )
                }
                editingProfile = null
            }
        )
    }

    // Діалог підтвердження видалення
    showDeleteConfirmation?.let { profile ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = null },
            title = { Text("Delete Confirmation") },
            text = { Text("You are sure you want to remove the profile '${profile.profileName}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsManager.updateSettings {
                            copy(
                                awsProfiles = awsProfiles.filter { it.profileName != profile.profileName }
                            )
                        }
                        showDeleteConfirmation = null
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = null }) {
                    Text("No")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileDialog(
    profile: AwsProfile,
    onDismiss: () -> Unit,
    onSave: (AwsProfile) -> Unit
) {
    var profileName by remember { mutableStateOf(profile.profileName) }
    var accessKeyId by remember { mutableStateOf(profile.accessKeyId) }
    var secretAccessKey by remember { mutableStateOf(profile.secretAccessKey) }
    var showError by remember { mutableStateOf<String?>(null) }

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
                    "Edit profile",
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Profile name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = accessKeyId,
                    onValueChange = { accessKeyId = it },
                    label = { Text("Access Key ID") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = secretAccessKey,
                    onValueChange = { secretAccessKey = it },
                    label = { Text("Secret Access Key") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                AwsProfile(
                                    profileName = profileName,
                                    accessKeyId = accessKeyId,
                                    secretAccessKey = secretAccessKey
                                )
                            )
                        },
                        enabled = profileName.isNotEmpty() &&
                                accessKeyId.isNotEmpty() &&
                                secretAccessKey.isNotEmpty()
                    ) {
                        Text("Save")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditClustersDialog(
    onDismiss: () -> Unit,
    settingsManager: SettingsManager
) {
    var editingCluster by remember { mutableStateOf<ClusterConfig?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf<ClusterConfig?>(null) }
    //var showError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(600.dp).height(400.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 5.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "EKS Clusters",
                    style = MaterialTheme.typography.headlineSmall
                )

                Surface(
                    modifier = Modifier.weight(1f),
                    tonalElevation = 1.dp
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(settingsManager.settings.clusters) { cluster ->
                            ListItem(
                                headlineContent = { Text(cluster.alias) },
                                supportingContent = {
                                    Column {
                                        Text(cluster.clusterName)
                                        Text(
                                            cluster.region,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { editingCluster = cluster }) {
                                            Icon(ICON_EDIT, "Edit")
                                        }
                                        IconButton(onClick = { showDeleteConfirmation = cluster }) {
                                            Icon(ICON_DELETE, "Remove")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }

    // Діалог редагування кластера
    editingCluster?.let { cluster ->
        EditClusterDialog(
            cluster = cluster,
            onDismiss = { editingCluster = null },
            onSave = { updatedCluster ->
                settingsManager.updateSettings {
                    copy(
                        clusters = clusters.map {
                            if (it.alias == cluster.alias) updatedCluster else it
                        }
                    )
                }
                editingCluster = null
            }
        )
    }

    // Діалог підтвердження видалення
    showDeleteConfirmation?.let { cluster ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = null },
            title = { Text("Deletion confirmation") },
            text = { Text("Are you sure you want to delete the context '${cluster.alias}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsManager.updateSettings {
                            copy(
                                clusters = clusters.filter { it.alias != cluster.alias }
                            )
                        }
                        showDeleteConfirmation = null
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = null }) {
                    Text("No")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditClusterDialog(
    cluster: ClusterConfig,
    onDismiss: () -> Unit,
    onSave: (ClusterConfig) -> Unit
) {
    var alias by remember { mutableStateOf(cluster.alias) }
    var showError by remember { mutableStateOf<String?>(null) }

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
                    "Edit cluster context",
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Connection Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                cluster.copy(alias = alias)
                            )
                        },
                        enabled = alias.isNotEmpty()
                    ) {
                        Text("Store")
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
