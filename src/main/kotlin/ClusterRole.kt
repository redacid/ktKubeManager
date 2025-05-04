import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertTriangle
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Link
import compose.icons.feathericons.Shield
import io.fabric8.kubernetes.api.model.rbac.ClusterRole
import io.fabric8.kubernetes.api.model.rbac.PolicyRule
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadClusterRolesFabric8(client: KubernetesClient?) =
    fetchK8sResource(client, "ClusterRoles", null) { cl, _ -> cl.rbac().clusterRoles().list().items } // Cluster-scoped

@Composable
fun ClusterRoleDetailsView(clusterRole: ClusterRole) {
    Column(
        modifier = Modifier.Companion
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "ClusterRole Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        DetailRow("Name", clusterRole.metadata?.name)
        DetailRow("Created", formatAge(clusterRole.metadata?.creationTimestamp))

        // Спеціальний блок для системних ролей
        if (clusterRole.metadata?.name?.startsWith("system:") == true) {
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
                        contentDescription = "System Role",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.Companion.size(24.dp)
                    )
                    Spacer(Modifier.Companion.width(8.dp))
                    Column {
                        Text(
                            "Kubernetes System Role",
                            fontWeight = FontWeight.Companion.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            "This is a pre-defined system role used by Kubernetes components",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Aggregation Rules, якщо є
        clusterRole.aggregationRule?.let { aggregationRule ->
            val clusterRoleSelectors = aggregationRule.clusterRoleSelectors
            if (!clusterRoleSelectors.isNullOrEmpty()) {
                // Заголовок секції
                Text(
                    text = "Aggregation Rule",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.Companion.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.Companion.padding(12.dp)) {
                        Text(
                            text = "This ClusterRole aggregates permissions from other ClusterRoles",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Companion.SemiBold
                        )

                        Spacer(Modifier.Companion.height(8.dp))

                        Text(
                            text = "Cluster Role Selectors:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Companion.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        clusterRoleSelectors.forEachIndexed { index, selector ->
                            val matchLabels = selector.matchLabels
                            val matchExpressions = selector.matchExpressions

                            Card(
                                modifier = Modifier.Companion
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.Companion.padding(8.dp)) {
                                    Text(
                                        text = "Selector #${index + 1}",
                                        fontWeight = FontWeight.Companion.Bold
                                    )

                                    // Match Labels
                                    if (!matchLabels.isNullOrEmpty()) {
                                        Text(
                                            text = "Match Labels:",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Companion.Medium
                                        )

                                        matchLabels.forEach { (key, value) ->
                                            Row {
                                                SelectionContainer {
                                                    Text(
                                                        text = key,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Companion.Medium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.Companion.width(120.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "= ",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                SelectionContainer {
                                                    Text(
                                                        text = value,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Match Expressions
                                    if (!matchExpressions.isNullOrEmpty()) {
                                        if (!matchLabels.isNullOrEmpty()) {
                                            Spacer(Modifier.Companion.height(4.dp))
                                        }

                                        Text(
                                            text = "Match Expressions:",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Companion.Medium
                                        )

                                        matchExpressions.forEach { expr ->
                                            Row {
                                                SelectionContainer {
                                                    Text(
                                                        text = expr.key ?: "",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Companion.Medium,
                                                        color = MaterialTheme.colorScheme.tertiary,
                                                        modifier = Modifier.Companion.width(120.dp)
                                                    )
                                                }
                                                Text(
                                                    text = expr.operator ?: "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Companion.Medium
                                                )
                                                Spacer(Modifier.Companion.width(4.dp))
                                                SelectionContainer {
                                                    Text(
                                                        text = expr.values?.joinToString(", ") ?: "",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.Companion.height(16.dp))
            }
        }

        // Правила доступу
        val rulesCount = clusterRole.rules?.size ?: 0
        Text(
            text = "Rules ($rulesCount)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        if (rulesCount > 0) {
            LazyColumn(
                modifier = Modifier.Companion.heightIn(max = 500.dp)
            ) {
                itemsIndexed(clusterRole.rules ?: emptyList()) { index, rule ->
                    Card(
                        modifier = Modifier.Companion
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.Companion.padding(12.dp)) {
                            // Заголовок правила
                            Text(
                                text = "Rule #${index + 1}",
                                fontWeight = FontWeight.Companion.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.Companion.padding(bottom = 4.dp)
                            )

                            HorizontalDivider(modifier = Modifier.Companion.padding(vertical = 4.dp))

                            // API Groups
                            val apiGroups = rule.apiGroups
                            ClusterRuleSection(
                                title = "API Groups",
                                items = apiGroups,
                                emptyMessage = "All API Groups (*)",
                                defaultItem = "*"
                            )

                            // Resources
                            val resources = rule.resources
                            ClusterRuleSection(
                                title = "Resources",
                                items = resources,
                                emptyMessage = "All Resources (*)",
                                defaultItem = "*"
                            )

                            // Resource Names (специфічні імена ресурсів)
                            val resourceNames = rule.resourceNames
                            if (!resourceNames.isNullOrEmpty()) {
                                Spacer(Modifier.Companion.height(8.dp))
                                Text(
                                    text = "Resource Names:",
                                    fontWeight = FontWeight.Companion.SemiBold
                                )
                                resourceNames.forEach { name ->
                                    Text("• $name")
                                }
                            }

                            // Verbs (дії)
                            val verbs = rule.verbs
                            ClusterRuleSection(
                                title = "Verbs (Actions)",
                                items = verbs,
                                emptyMessage = "No actions defined",
                                defaultItem = "*",
                                highlightSpecialItems = true
                            )

                            // Не-Ресурсні URL (NonResourceURLs) - специфічні для ClusterRole
                            val nonResourceURLs = rule.nonResourceURLs
                            if (!nonResourceURLs.isNullOrEmpty()) {
                                Spacer(Modifier.Companion.height(8.dp))
                                Text(
                                    text = "Non-Resource URLs:",
                                    fontWeight = FontWeight.Companion.SemiBold
                                )

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.Companion
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.Companion.padding(8.dp)) {
                                        nonResourceURLs.forEach { url ->
                                            SelectionContainer {
                                                Text(
                                                    text = url,
                                                    fontFamily = FontFamily.Companion.Monospace,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }

                                        // Інформаційне повідомлення про nonResourceURLs
                                        Spacer(Modifier.Companion.height(4.dp))
                                        Text(
                                            text = "Non-resource URLs are API endpoints that don't correspond to Kubernetes objects",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontStyle = FontStyle.Companion.Italic,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Кнопка "копіювати як YAML"
                            Box(
                                modifier = Modifier.Companion
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                contentAlignment = Alignment.Companion.CenterEnd
                            ) {
                                val yamlText = generateClusterRuleYaml(rule)
                                val clipboardManager = LocalClipboardManager.current

                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(yamlText))
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.Companion.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = FeatherIcons.Copy,
                                        contentDescription = "Copy YAML",
                                        modifier = Modifier.Companion.size(14.dp)
                                    )
                                    Spacer(Modifier.Companion.width(4.dp))
                                    Text("Copy as YAML", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Порожній набір правил
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
                        "No rules defined for this cluster role",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Companion.Bold
                    )
                    Spacer(Modifier.Companion.height(4.dp))
                    Text(
                        "This cluster role doesn't grant any permissions",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Секція для ClusterRoleBindings
        val roleBindingsState = remember { mutableStateOf(false) }
        DetailSectionHeader(
            title = "Cluster Role Bindings",
            expanded = roleBindingsState
        )

        if (roleBindingsState.value) {
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
                    // Тут мав би бути список ClusterRoleBindings, які посилаються на цю роль
                    // Але для цього потрібен додатковий запит до API

                    Icon(
                        imageVector = FeatherIcons.Link,
                        contentDescription = "Role Bindings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.Companion.size(32.dp)
                    )
                    Spacer(Modifier.Companion.height(8.dp))
                    Text(
                        "Cluster Role Bindings information requires additional API calls",
                        fontWeight = FontWeight.Companion.Bold
                    )
                    Spacer(Modifier.Companion.height(4.dp))
                    Text(
                        "To see which subjects (users, groups, service accounts) have this cluster role, please check ClusterRoleBindings",
                        textAlign = TextAlign.Companion.Center
                    )
                    Spacer(Modifier.Companion.height(8.dp))
                    Text(
                        "Note: This cluster role might also be referenced by namespace-scoped RoleBindings",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Companion.Italic,
                        textAlign = TextAlign.Companion.Center
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
                            "Labels (${clusterRole.metadata?.labels?.size ?: 0}):",
                            fontWeight = FontWeight.Companion.Bold
                        )
                    }

                    if (labelsExpanded) {
                        if (clusterRole.metadata?.labels.isNullOrEmpty()) {
                            Text("No labels", modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp)) {
                                clusterRole.metadata?.labels?.forEach { (key, value) ->
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
                            "Annotations (${clusterRole.metadata?.annotations?.size ?: 0}):",
                            fontWeight = FontWeight.Companion.Bold
                        )
                    }

                    if (annotationsExpanded) {
                        if (clusterRole.metadata?.annotations.isNullOrEmpty()) {
                            Text("No annotations", modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp)) {
                                clusterRole.metadata?.annotations?.entries?.sortedBy { it.key }
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

// Допоміжна функція для відображення секції правил в ClusterRole
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClusterRuleSection(
    title: String,
    items: List<String>?,
    emptyMessage: String,
    defaultItem: String,
    highlightSpecialItems: Boolean = false
) {
    val hasItems = !items.isNullOrEmpty()
    val hasWildcard = items?.contains(defaultItem) ?: false

    Spacer(Modifier.height(8.dp))
    Text(
        text = "$title:",
        fontWeight = FontWeight.SemiBold
    )

    if (!hasItems) {
        Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        if (hasWildcard && items?.size == 1) {
            Text(
                emptyMessage,
                color = if (highlightSpecialItems) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (highlightSpecialItems) FontWeight.Bold else FontWeight.Normal
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items?.forEach { item ->
                    val isSpecial = item == defaultItem

                    Surface(
                        color = if (isSpecial && highlightSpecialItems)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSpecial) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSpecial && highlightSpecialItems)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// Функція для генерації YAML представлення правила ClusterRole
fun generateClusterRuleYaml(rule: PolicyRule): String {
    val builder = StringBuilder()

    builder.append("- apiGroups: ")
    if (rule.apiGroups.isNullOrEmpty()) {
        builder.append("['*']\n")
    } else {
        builder.append("[")
        builder.append(rule.apiGroups.joinToString(", ") { "'$it'" })
        builder.append("]\n")
    }

    builder.append("  resources: ")
    if (rule.resources.isNullOrEmpty()) {
        builder.append("['*']\n")
    } else {
        builder.append("[")
        builder.append(rule.resources.joinToString(", ") { "'$it'" })
        builder.append("]\n")
    }

    if (!rule.resourceNames.isNullOrEmpty()) {
        builder.append("  resourceNames: [")
        builder.append(rule.resourceNames.joinToString(", ") { "'$it'" })
        builder.append("]\n")
    }

    // NonResourceURLs (специфічні для ClusterRole)
    if (!rule.nonResourceURLs.isNullOrEmpty()) {
        builder.append("  nonResourceURLs: [")
        builder.append(rule.nonResourceURLs.joinToString(", ") { "'$it'" })
        builder.append("]\n")
    }

    builder.append("  verbs: ")
    if (rule.verbs.isNullOrEmpty()) {
        builder.append("[]\n")
    } else {
        builder.append("[")
        builder.append(rule.verbs.joinToString(", ") { "'$it'" })
        builder.append("]\n")
    }

    return builder.toString()
}