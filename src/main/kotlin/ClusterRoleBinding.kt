import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertTriangle
import compose.icons.feathericons.Flag
import compose.icons.feathericons.Globe
import compose.icons.feathericons.HelpCircle
import compose.icons.feathericons.Info
import compose.icons.feathericons.Package
import compose.icons.feathericons.Server
import compose.icons.feathericons.Shield
import compose.icons.feathericons.User
import compose.icons.feathericons.Users
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadClusterRoleBindingsFabric8(client: KubernetesClient?) =
    fetchK8sResource(client, "ClusterRoleBindings", null) { cl, _ ->
        cl.rbac().clusterRoleBindings().list().items
    } // Cluster-scoped

@Composable
fun ClusterRoleBindingDetailsView(clusterRoleBinding: ClusterRoleBinding) {
    Column(
        modifier = Modifier.Companion
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "ClusterRoleBinding Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        DetailRow("Name", clusterRoleBinding.metadata?.name)
        DetailRow("Created", formatAge(clusterRoleBinding.metadata?.creationTimestamp))

        // Спеціальний блок для системних ролей
        if (clusterRoleBinding.metadata?.name?.startsWith("system:") == true) {
            Spacer(Modifier.Companion.height(8.dp))
            Card(
                modifier = Modifier.Companion.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Row(
                    modifier = Modifier.Companion.padding(8.dp),
                    verticalAlignment = Alignment.Companion.CenterVertically
                ) {
                    Icon(
                        imageVector = FeatherIcons.Shield,
                        contentDescription = "System Role Binding",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.Companion.size(24.dp)
                    )
                    Spacer(Modifier.Companion.width(8.dp))
                    Column {
                        Text(
                            "Kubernetes System Role Binding",
                            fontWeight = FontWeight.Companion.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            "This is a pre-defined system role binding used by Kubernetes components",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // RoleRef - посилання на роль, яка призначається
        Card(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.Companion.padding(12.dp)) {
                Text(
                    text = "Cluster Role Reference",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.Companion.padding(bottom = 8.dp)
                )

                // Тип ролі (повинен бути ClusterRole)
                val roleType = clusterRoleBinding.roleRef?.kind ?: "Unknown"
                val roleName = clusterRoleBinding.roleRef?.name ?: "Unknown"
                val roleApiGroup = clusterRoleBinding.roleRef?.apiGroup ?: ""

                Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                    Icon(
                        imageVector = FeatherIcons.Globe,
                        contentDescription = "Cluster Role",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.Companion.size(20.dp)
                    )
                    Spacer(Modifier.Companion.width(8.dp))
                    Column {
                        Text(
                            text = roleName,
                            fontWeight = FontWeight.Companion.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Row {
                            Text(
                                text = "Kind: $roleType",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (roleApiGroup.isNotEmpty()) {
                                Spacer(Modifier.Companion.width(8.dp))
                                Text(
                                    text = "API Group: $roleApiGroup",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                // Попередження, якщо роль не є ClusterRole
                if (roleType != "ClusterRole") {
                    Spacer(Modifier.Companion.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.Companion.padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Companion.CenterVertically,
                            modifier = Modifier.Companion.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = FeatherIcons.AlertTriangle,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.Companion.size(16.dp)
                            )
                            Spacer(Modifier.Companion.width(8.dp))
                            Text(
                                text = "Invalid configuration: ClusterRoleBinding should reference a ClusterRole, but found $roleType",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Інформація про глобальну область видимості
                Spacer(Modifier.Companion.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                    modifier = Modifier.Companion.padding(vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Companion.CenterVertically,
                        modifier = Modifier.Companion.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = FeatherIcons.Globe,
                            contentDescription = "Cluster Wide",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.Companion.size(16.dp)
                        )
                        Spacer(Modifier.Companion.width(8.dp))
                        Text(
                            text = "This binding grants permissions across the entire cluster",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Subjects - суб'єкти, яким призначається роль
        val subjectsCount = clusterRoleBinding.subjects?.size ?: 0
        Text(
            text = "Subjects ($subjectsCount)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        if (subjectsCount > 0) {
            LazyColumn(
                modifier = Modifier.Companion.heightIn(max = 400.dp)
            ) {
                itemsIndexed(clusterRoleBinding.subjects ?: emptyList()) { _, subject ->
                    Card(
                        modifier = Modifier.Companion
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.Companion.padding(12.dp)) {
                            // Тип суб'єкта та іконка
                            Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                                Icon(
                                    imageVector = when (subject.kind) {
                                        "User" -> FeatherIcons.User
                                        "Group" -> FeatherIcons.Users
                                        "ServiceAccount" -> FeatherIcons.Server
                                        else -> FeatherIcons.HelpCircle
                                    },
                                    contentDescription = "Subject Type",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.Companion.size(20.dp)
                                )
                                Spacer(Modifier.Companion.width(8.dp))
                                Text(
                                    text = "${subject.kind}: ${subject.name}",
                                    fontWeight = FontWeight.Companion.Bold
                                )
                            }

                            Spacer(Modifier.Companion.height(4.dp))

                            // Namespace (якщо застосовно)
                            subject.namespace?.let { namespace ->
                                Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                                    Icon(
                                        imageVector = FeatherIcons.Flag,
                                        contentDescription = "Namespace",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.Companion.size(16.dp)
                                    )
                                    Spacer(Modifier.Companion.width(8.dp))
                                    Text("Namespace: $namespace")
                                }

                                // Додаткова підказка для namespace в ClusterRoleBinding
                                Spacer(Modifier.Companion.height(4.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                    modifier = Modifier.Companion.padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Note: Even with namespace specified, this subject has cluster-wide permissions",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = FontStyle.Companion.Italic,
                                        modifier = Modifier.Companion.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // API Group (якщо є)
                            subject.apiGroup?.let { apiGroup ->
                                if (apiGroup.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                                        Icon(
                                            imageVector = FeatherIcons.Package,
                                            contentDescription = "API Group",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.Companion.size(16.dp)
                                        )
                                        Spacer(Modifier.Companion.width(8.dp))
                                        Text("API Group: $apiGroup")
                                    }
                                }
                            }

                            // Додаткова інформація для ServiceAccount
                            if (subject.kind == "ServiceAccount" && subject.namespace != null) {
                                Spacer(Modifier.Companion.height(8.dp))
                                Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                                    Icon(
                                        imageVector = FeatherIcons.Info,
                                        contentDescription = "Info",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.Companion.size(16.dp)
                                    )
                                    Spacer(Modifier.Companion.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Full reference: system:serviceaccount:${subject.namespace}:${subject.name}",
                                            fontStyle = FontStyle.Companion.Italic,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            // Додаткова інформація для Group
                            if (subject.kind == "Group" && subject.name?.startsWith("system:") == true) {
                                Spacer(Modifier.Companion.height(8.dp))
                                Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                                    Icon(
                                        imageVector = FeatherIcons.Info,
                                        contentDescription = "System Group",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.Companion.size(16.dp)
                                    )
                                    Spacer(Modifier.Companion.width(8.dp))
                                    Text(
                                        text = "This is a Kubernetes system group",
                                        fontStyle = FontStyle.Companion.Italic,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // Додаткова інформація для User, якщо це системний користувач
                            if (subject.kind == "User" && subject.name?.startsWith("system:") == true) {
                                Spacer(Modifier.Companion.height(8.dp))
                                Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                                    Icon(
                                        imageVector = FeatherIcons.Info,
                                        contentDescription = "System User",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.Companion.size(16.dp)
                                    )
                                    Spacer(Modifier.Companion.width(8.dp))
                                    Text(
                                        text = "This is a Kubernetes system user",
                                        fontStyle = FontStyle.Companion.Italic,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // Спеціальне повідомлення для суб'єктів, які стосуються всіх
                            if ((subject.kind == "Group" && subject.name == "system:authenticated") ||
                                (subject.kind == "Group" && subject.name == "system:unauthenticated")
                            ) {
                                Spacer(Modifier.Companion.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                    modifier = Modifier.Companion.padding(vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Companion.CenterVertically,
                                        modifier = Modifier.Companion.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = FeatherIcons.AlertTriangle,
                                            contentDescription = "Warning",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.Companion.size(16.dp)
                                        )
                                        Spacer(Modifier.Companion.width(8.dp))
                                        Text(
                                            text = if (subject.name == "system:authenticated")
                                                "This grants permissions to ALL authenticated users!"
                                            else
                                                "This grants permissions to ALL unauthenticated users!",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Companion.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Порожній список суб'єктів
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(
                    modifier = Modifier.Companion
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.Companion.CenterHorizontally
                ) {
                    Icon(
                        imageVector = FeatherIcons.AlertTriangle,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.Companion.size(32.dp)
                    )
                    Spacer(Modifier.Companion.height(8.dp))
                    Text(
                        "No subjects defined for this cluster role binding",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Companion.Bold
                    )
                    Spacer(Modifier.Companion.height(4.dp))
                    Text(
                        "This cluster role binding doesn't assign permissions to any subject",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
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
                            "Labels (${clusterRoleBinding.metadata?.labels?.size ?: 0}):",
                            fontWeight = FontWeight.Companion.Bold
                        )
                    }

                    if (labelsExpanded) {
                        if (clusterRoleBinding.metadata?.labels.isNullOrEmpty()) {
                            Text("No labels", modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp)) {
                                clusterRoleBinding.metadata?.labels?.forEach { (key, value) ->
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
                            "Annotations (${clusterRoleBinding.metadata?.annotations?.size ?: 0}):",
                            fontWeight = FontWeight.Companion.Bold
                        )
                    }

                    if (annotationsExpanded) {
                        if (clusterRoleBinding.metadata?.annotations.isNullOrEmpty()) {
                            Text("No annotations", modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp)) {
                                clusterRoleBinding.metadata?.annotations?.entries?.sortedBy { it.key }
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

        // Додатково - посилання на правила, які надає ця роль
        Spacer(Modifier.Companion.height(16.dp))

        val rulesInfoState = remember { mutableStateOf(false) }
        DetailSectionHeader(
            title = "Cluster Role Rules Information",
            expanded = rulesInfoState
        )

        if (rulesInfoState.value) {
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
                        contentDescription = "Role Rules Info",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.Companion.size(32.dp)
                    )
                    Spacer(Modifier.Companion.height(8.dp))
                    Text(
                        "Rules defined in ${clusterRoleBinding.roleRef?.kind} '${clusterRoleBinding.roleRef?.name}'",
                        fontWeight = FontWeight.Companion.Bold
                    )
                    Spacer(Modifier.Companion.height(4.dp))
                    Text(
                        "To see the permissions granted by this ClusterRoleBinding, check the referenced ${
                            clusterRoleBinding.roleRef?.kind?.lowercase() ?: "role"
                        } details",
                        textAlign = TextAlign.Companion.Center
                    )

                    // Спеціальне повідомлення про область дії кластера
                    Spacer(Modifier.Companion.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        modifier = Modifier.Companion.padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Companion.CenterVertically,
                            modifier = Modifier.Companion.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = FeatherIcons.Globe,
                                contentDescription = "Cluster Wide",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.Companion.size(16.dp)
                            )
                            Spacer(Modifier.Companion.width(8.dp))
                            Column {
                                Text(
                                    text = "These permissions are granted cluster-wide",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Companion.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Subjects listed in this binding have these permissions across all namespaces",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // Інформація про безпеку
        Spacer(Modifier.Companion.height(16.dp))

        val securityInfoState = remember { mutableStateOf(false) }
        DetailSectionHeader(
            title = "Security Considerations",
            expanded = securityInfoState
        )

        if (securityInfoState.value) {
            Card(
                modifier = Modifier.Companion.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(
                    modifier = Modifier.Companion
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                        Icon(
                            imageVector = FeatherIcons.Shield,
                            contentDescription = "Security",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.Companion.size(24.dp)
                        )
                        Spacer(Modifier.Companion.width(8.dp))
                        Text(
                            "Cluster-Wide Permission Binding",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Companion.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Spacer(Modifier.Companion.height(8.dp))

                    Text(
                        "ClusterRoleBindings grant permissions across all namespaces in your cluster. " +
                                "This can have significant security implications:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Spacer(Modifier.Companion.height(8.dp))

                    Column {
                        Row(verticalAlignment = Alignment.Companion.Top) {
                            Text(
                                "• ",
                                fontWeight = FontWeight.Companion.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "They can provide broad access to sensitive resources across your entire cluster",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        Spacer(Modifier.Companion.height(4.dp))

                        Row(verticalAlignment = Alignment.Companion.Top) {
                            Text(
                                "• ",
                                fontWeight = FontWeight.Companion.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Consider using namespace-scoped RoleBindings whenever possible for better isolation",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        Spacer(Modifier.Companion.height(4.dp))

                        Row(verticalAlignment = Alignment.Companion.Top) {
                            Text(
                                "• ",
                                fontWeight = FontWeight.Companion.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Review these bindings regularly, especially for production clusters",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}