import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertTriangle
import compose.icons.feathericons.Check
import compose.icons.feathericons.Copy
import compose.icons.feathericons.HelpCircle
import compose.icons.feathericons.Info
import compose.icons.feathericons.Key
import compose.icons.feathericons.Lock
import compose.icons.feathericons.Package
import compose.icons.feathericons.Unlock
import compose.icons.feathericons.X
import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadServiceAccountsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "ServiceAccounts", namespace) { cl, ns ->
        if (ns == null) cl.serviceAccounts().inAnyNamespace().list().items else cl.serviceAccounts().inNamespace(ns)
            .list().items
    }

@Composable
fun ServiceAccountDetailsView(serviceAccount: ServiceAccount) {
    Column(
        modifier = Modifier.Companion
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "ServiceAccount Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        DetailRow("Name", serviceAccount.metadata?.name)
        DetailRow("Namespace", serviceAccount.metadata?.namespace)
        DetailRow("Created", formatAge(serviceAccount.metadata?.creationTimestamp))

        // Спеціальний блок для відображення повної назви (для посилань в RBAC)
        val namespace = serviceAccount.metadata?.namespace
        val name = serviceAccount.metadata?.name
        if (namespace != null && name != null) {
            Spacer(Modifier.Companion.height(8.dp))
            Card(
                modifier = Modifier.Companion.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.Companion.padding(12.dp)) {
                    Text(
                        text = "RBAC Reference Name",
                        fontWeight = FontWeight.Companion.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.Companion.height(4.dp))

                    Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                        Text(
                            text = "system:serviceaccount:$namespace:$name",
                            fontFamily = FontFamily.Companion.Monospace,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        Spacer(Modifier.Companion.width(8.dp))

                        // Кнопка копіювання
                        val clipboardManager = LocalClipboardManager.current
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString("system:serviceaccount:$namespace:$name"))
                            },
                            modifier = Modifier.Companion.size(24.dp)
                        ) {
                            Icon(
                                imageVector = FeatherIcons.Copy,
                                contentDescription = "Copy Reference",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.Companion.size(16.dp)
                            )
                        }
                    }

                    Spacer(Modifier.Companion.height(4.dp))
                    Text(
                        text = "Use this full name when referring to this service account in RBAC rules",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Секція Secrets (токени)
        val secretsCount = serviceAccount.secrets?.size ?: 0
        Text(
            text = "Secrets ($secretsCount)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        if (secretsCount > 0) {
            LazyColumn(
                modifier = Modifier.Companion.heightIn(max = 200.dp)
            ) {
                itemsIndexed(serviceAccount.secrets ?: emptyList()) { _, secret ->
                    Card(
                        modifier = Modifier.Companion
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.Companion.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                                Icon(
                                    imageVector = FeatherIcons.Key,
                                    contentDescription = "Secret",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.Companion.size(20.dp)
                                )
                                Spacer(Modifier.Companion.width(8.dp))
                                Column {
                                    Text(
                                        text = secret.name ?: "Unnamed Secret",
                                        fontWeight = FontWeight.Companion.Bold
                                    )

                                    // Якщо є додаткова інформація про тип секрету
                                    if (secret.kind != null || secret.apiVersion != null) {
                                        Row {
                                            secret.kind?.let {
                                                Text(
                                                    text = it,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.Companion.padding(end = 8.dp)
                                                )
                                            }
                                            secret.apiVersion?.let {
                                                Text(
                                                    text = it,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Інформація про автоматичне створення
                            val isTokenSecret = secret.name?.contains("token") == true
                            if (isTokenSecret) {
                                Spacer(Modifier.Companion.height(4.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.Companion.padding(vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Companion.CenterVertically,
                                        modifier = Modifier.Companion.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = FeatherIcons.Info,
                                            contentDescription = "Info",
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.Companion.size(12.dp)
                                        )
                                        Spacer(Modifier.Companion.width(4.dp))
                                        Text(
                                            text = "Service account token for API authentication",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.Companion
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.Companion.CenterHorizontally
                ) {
                    Icon(
                        imageVector = FeatherIcons.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.Companion.size(24.dp)
                    )
                    Spacer(Modifier.Companion.height(8.dp))
                    Text(
                        "No secrets associated with this service account",
                        fontWeight = FontWeight.Companion.Medium
                    )
                    Spacer(Modifier.Companion.height(4.dp))
                    Text(
                        "In Kubernetes 1.24+, service account tokens are created on-demand",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Companion.Center
                    )
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Image Pull Secrets
        val imagePullSecretsCount = serviceAccount.imagePullSecrets?.size ?: 0
        Text(
            text = "Image Pull Secrets ($imagePullSecretsCount)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        if (imagePullSecretsCount > 0) {
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.Companion.padding(12.dp)) {
                    serviceAccount.imagePullSecrets?.forEach { pullSecret ->
                        Row(
                            verticalAlignment = Alignment.Companion.CenterVertically,
                            modifier = Modifier.Companion.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = FeatherIcons.Package,
                                contentDescription = "Pull Secret",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.Companion.size(16.dp)
                            )
                            Spacer(Modifier.Companion.width(8.dp))
                            Text(
                                text = pullSecret.name ?: "Unnamed Secret",
                                fontWeight = FontWeight.Companion.Medium
                            )
                        }
                    }

                    Spacer(Modifier.Companion.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        modifier = Modifier.Companion.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "These secrets are used for pulling container images from private registries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.Companion.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.Companion
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.Companion.CenterHorizontally
                ) {
                    Icon(
                        imageVector = FeatherIcons.Package,
                        contentDescription = "Pull Secrets",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.Companion.size(24.dp)
                    )
                    Spacer(Modifier.Companion.height(8.dp))
                    Text(
                        "No image pull secrets configured",
                        fontWeight = FontWeight.Companion.Medium
                    )
                    Spacer(Modifier.Companion.height(4.dp))
                    Text(
                        "This service account will use default settings for container image pulling",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Companion.Center
                    )
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Секція для RBAC - призначення ролей
        val rbacState = remember { mutableStateOf(false) }
        DetailSectionHeader(title = "Role Bindings", expanded = rbacState)

        if (rbacState.value) {
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.Companion
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.Companion.CenterHorizontally
                ) {
                    // Тут мав би бути список RoleBindings, які посилаються на цей ServiceAccount
                    // Але для цього потрібен додатковий запит до API

                    Icon(
                        imageVector = FeatherIcons.Unlock,
                        contentDescription = "Role Bindings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.Companion.size(32.dp)
                    )
                    Spacer(Modifier.Companion.height(8.dp))
                    Text(
                        "Role Bindings information requires additional API calls",
                        fontWeight = FontWeight.Companion.Bold
                    )
                    Spacer(Modifier.Companion.height(4.dp))
                    Text(
                        "To see which roles are assigned to this service account, check RoleBindings in this namespace or ClusterRoleBindings",
                        textAlign = TextAlign.Companion.Center
                    )

                    Spacer(Modifier.Companion.height(8.dp))
                    val serviceAccountNamespace = serviceAccount.metadata?.namespace
                    val serviceAccountName = serviceAccount.metadata?.name

                    val subjectReference = if (serviceAccountNamespace != null && serviceAccountName != null) {
                        """
                        subject:
                          kind: ServiceAccount
                          name: $serviceAccountName
                          namespace: $serviceAccountNamespace
                        """.trimIndent()
                    } else {
                        "Subject reference not available"
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        modifier = Modifier.Companion.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.Companion.padding(8.dp)) {
                            Text(
                                text = "YAML Reference for RoleBinding:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Companion.Bold
                            )
                            Spacer(Modifier.Companion.height(4.dp))

                            SelectionContainer {
                                Text(
                                    text = subjectReference,
                                    fontFamily = FontFamily.Companion.Monospace,
                                    fontSize = 12.sp
                                )
                            }

                            // Кнопка копіювання
                            Box(
                                modifier = Modifier.Companion
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                contentAlignment = Alignment.Companion.CenterEnd
                            ) {
                                val clipboardManager = LocalClipboardManager.current

                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(subjectReference))
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.Companion.height(38.dp)
                                ) {
                                    Icon(
                                        imageVector = FeatherIcons.Copy,
                                        contentDescription = "Copy YAML",
                                        modifier = Modifier.Companion.size(24.dp)
                                    )
                                    //Spacer(Modifier.Companion.width(4.dp))
                                    //Text("Copy", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Automount Service Account Token
        Card(
            modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (serviceAccount.automountServiceAccountToken == false)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.Companion.padding(12.dp)) {
                Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                    Icon(
                        imageVector = if (serviceAccount.automountServiceAccountToken == false)
                            FeatherIcons.Lock
                        else
                            FeatherIcons.Key,
                        contentDescription = "Automount Token",
                        tint = if (serviceAccount.automountServiceAccountToken == false)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary,
                        modifier = Modifier.Companion.size(20.dp)
                    )
                    Spacer(Modifier.Companion.width(8.dp))
                    Text(
                        text = "Automount Service Account Token",
                        fontWeight = FontWeight.Companion.Bold,
                        color = if (serviceAccount.automountServiceAccountToken == false)
                            MaterialTheme.colorScheme.onErrorContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.Companion.height(4.dp))

                val statusText = when (serviceAccount.automountServiceAccountToken) {
                    false -> "Disabled - Tokens will not be automatically mounted in pods"
                    true -> "Enabled - Tokens will be automatically mounted in pods"
                    null -> "Not specified - Uses namespace or cluster default setting"
                }

                val statusIcon = when (serviceAccount.automountServiceAccountToken) {
                    false -> FeatherIcons.X
                    true -> FeatherIcons.Check
                    null -> FeatherIcons.HelpCircle
                }

                val statusColor = when (serviceAccount.automountServiceAccountToken) {
                    false -> MaterialTheme.colorScheme.error
                    true -> MaterialTheme.colorScheme.primary
                    null -> MaterialTheme.colorScheme.secondary
                }

                Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = "Status",
                        tint = statusColor,
                        modifier = Modifier.Companion.size(14.dp)
                    )
                    Spacer(Modifier.Companion.width(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.Companion.height(4.dp))

                // Пояснення для кращого розуміння
                val explanationText = when (serviceAccount.automountServiceAccountToken) {
                    false -> "Pods using this service account will need to explicitly mount tokens if needed"
                    true -> "Pods using this service account will automatically receive API access tokens"
                    null -> "Default behavior depends on pod settings and Kubernetes version"
                }

                Text(
                    text = explanationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (serviceAccount.automountServiceAccountToken == false)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                // Інформація про безпеку для автоматичного монтування
                if (serviceAccount.automountServiceAccountToken != false) {
                    Spacer(Modifier.Companion.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        modifier = Modifier.Companion.padding(vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Companion.CenterVertically,
                            modifier = Modifier.Companion.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = FeatherIcons.AlertTriangle,
                                contentDescription = "Security",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.Companion.size(14.dp)
                            )
                            Spacer(Modifier.Companion.width(4.dp))
                            Text(
                                text = "Security best practice: Disable token automounting when not required",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
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
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.Companion.padding(8.dp)) {
                    // Мітки з можливістю згортання/розгортання
                    var labelsExpanded by remember { mutableStateOf(true) }
                    Row(
                        verticalAlignment = Alignment.Companion.CenterVertically,
                        modifier = Modifier.Companion.clickable { labelsExpanded = !labelsExpanded }
                    ) {
                        Icon(
                            imageVector = if (labelsExpanded) ICON_DOWN else ICON_RIGHT,
                            contentDescription = "Toggle Labels",
                            modifier = Modifier.Companion.size(16.dp)
                        )
                        Spacer(Modifier.Companion.width(4.dp))
                        Text(
                            "Labels (${serviceAccount.metadata?.labels?.size ?: 0}):",
                            fontWeight = FontWeight.Companion.Bold
                        )
                    }

                    if (labelsExpanded) {
                        if (serviceAccount.metadata?.labels.isNullOrEmpty()) {
                            Text("No labels", modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp)) {
                                serviceAccount.metadata?.labels?.forEach { (key, value) ->
                                    Row {
                                        SelectionContainer {
                                            Text(
                                                text = key,
                                                fontWeight = FontWeight.Companion.Medium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(": ")
                                        SelectionContainer {
                                            Text(value)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.Companion.height(8.dp))

                    // Анотації з можливістю згортання/розгортання
                    var annotationsExpanded by remember { mutableStateOf(true) }
                    Row(
                        verticalAlignment = Alignment.Companion.CenterVertically,
                        modifier = Modifier.Companion.clickable { annotationsExpanded = !annotationsExpanded }
                    ) {
                        Icon(
                            imageVector = if (annotationsExpanded) ICON_DOWN else ICON_RIGHT,
                            contentDescription = "Toggle Annotations",
                            modifier = Modifier.Companion.size(16.dp)
                        )
                        Spacer(Modifier.Companion.width(4.dp))
                        Text(
                            "Annotations (${serviceAccount.metadata?.annotations?.size ?: 0}):",
                            fontWeight = FontWeight.Companion.Bold
                        )
                    }

                    if (annotationsExpanded) {
                        if (serviceAccount.metadata?.annotations.isNullOrEmpty()) {
                            Text("No annotations", modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp)) {
                                serviceAccount.metadata?.annotations?.entries?.sortedBy { it.key }
                                    ?.forEach { (key, value) ->
                                        val isLongValue = value.length > 50
                                        var valueExpanded by remember { mutableStateOf(false) }

                                        Row(verticalAlignment = Alignment.Companion.Top) {
                                            SelectionContainer {
                                                Text(
                                                    text = key,
                                                    fontWeight = FontWeight.Companion.Medium,
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.Companion.width(180.dp)
                                                )
                                            }

                                            Text(": ")

                                            if (isLongValue) {
                                                Column {
                                                    SelectionContainer {
                                                        Text(
                                                            text = if (valueExpanded) value else value.take(50) + "...",
                                                            modifier = Modifier.Companion.clickable {
                                                                valueExpanded = !valueExpanded
                                                            }
                                                        )
                                                    }
                                                    if (!valueExpanded) {
                                                        Text(
                                                            text = "Click to expand",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.Companion.clickable {
                                                                valueExpanded = true
                                                            }
                                                        )
                                                    }
                                                }
                                            } else {
                                                SelectionContainer {
                                                    Text(value)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.Companion.height(4.dp))
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
}