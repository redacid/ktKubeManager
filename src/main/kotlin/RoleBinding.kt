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
import io.fabric8.kubernetes.api.model.rbac.RoleBinding
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadRoleBindingsFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "RoleBindings", namespace) { cl, ns ->
        if (ns == null) cl.rbac().roleBindings().inAnyNamespace().list().items else cl.rbac().roleBindings()
            .inNamespace(ns).list().items
    }

@Composable
fun RoleBindingDetailsView(roleBinding: RoleBinding) {
    Column(
        modifier = Modifier.Companion
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "RoleBinding Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        DetailRow("Name", roleBinding.metadata?.name)
        DetailRow("Namespace", roleBinding.metadata?.namespace)
        DetailRow("Created", formatAge(roleBinding.metadata?.creationTimestamp))

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
                    text = "Role Reference",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.Companion.padding(bottom = 8.dp)
                )

                // Тип ролі (Role або ClusterRole)
                val roleType = roleBinding.roleRef?.kind ?: "Unknown"
                val roleName = roleBinding.roleRef?.name ?: "Unknown"
                val roleApiGroup = roleBinding.roleRef?.apiGroup ?: ""

                Row(verticalAlignment = Alignment.Companion.CenterVertically) {
                    Icon(
                        imageVector = if (roleType == "ClusterRole") FeatherIcons.Globe else FeatherIcons.Shield,
                        contentDescription = "Role Type",
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

                // Індикатор для ClusterRole
                if (roleType == "ClusterRole") {
                    Spacer(Modifier.Companion.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.Companion.padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Companion.CenterVertically,
                            modifier = Modifier.Companion.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = FeatherIcons.Info,
                                contentDescription = "Info",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.Companion.size(16.dp)
                            )
                            Spacer(Modifier.Companion.width(8.dp))
                            Text(
                                text = "This binding references a cluster-wide role, but applies only within this namespace",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Subjects - суб'єкти, яким призначається роль
        val subjectsCount = roleBinding.subjects?.size ?: 0
        Text(
            text = "Subjects ($subjectsCount)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        if (subjectsCount > 0) {
            LazyColumn(
                modifier = Modifier.Companion.heightIn(max = 400.dp)
            ) {
                itemsIndexed(roleBinding.subjects ?: emptyList()) { _, subject ->
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
                        "No subjects defined for this role binding",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Companion.Bold
                    )
                    Spacer(Modifier.Companion.height(4.dp))
                    Text(
                        "This role binding doesn't assign permissions to any subject",
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
                            "Labels (${roleBinding.metadata?.labels?.size ?: 0}):",
                            fontWeight = FontWeight.Companion.Bold
                        )
                    }

                    if (labelsExpanded) {
                        if (roleBinding.metadata?.labels.isNullOrEmpty()) {
                            Text("No labels", modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp)) {
                                roleBinding.metadata?.labels?.forEach { (key, value) ->
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
                            "Annotations (${roleBinding.metadata?.annotations?.size ?: 0}):",
                            fontWeight = FontWeight.Companion.Bold
                        )
                    }

                    if (annotationsExpanded) {
                        if (roleBinding.metadata?.annotations.isNullOrEmpty()) {
                            Text("No annotations", modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp)) {
                                roleBinding.metadata?.annotations?.entries?.sortedBy { it.key }
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
            title = "Role Rules Information",
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
                        "Rules defined in ${roleBinding.roleRef?.kind} '${roleBinding.roleRef?.name}'",
                        fontWeight = FontWeight.Companion.Bold
                    )
                    Spacer(Modifier.Companion.height(4.dp))
                    Text(
                        "To see the permissions granted by this RoleBinding, check the referenced ${
                            roleBinding.roleRef?.kind?.lowercase() ?: "role"
                        } details",
                        textAlign = TextAlign.Companion.Center
                    )

                    // Якщо це ClusterRole
                    if (roleBinding.roleRef?.kind == "ClusterRole") {
                        Spacer(Modifier.Companion.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                            modifier = Modifier.Companion.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Note: ClusterRole rules are applied only within the '${roleBinding.metadata?.namespace}' namespace for this binding",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Companion.Italic,
                                textAlign = TextAlign.Companion.Center,
                                modifier = Modifier.Companion.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}