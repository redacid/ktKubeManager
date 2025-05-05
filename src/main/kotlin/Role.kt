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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertTriangle
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Link
import io.fabric8.kubernetes.api.model.rbac.PolicyRule
import io.fabric8.kubernetes.api.model.rbac.Role
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadRolesFabric8(client: KubernetesClient?, namespace: String?) =
    fetchK8sResource(client, "Roles", namespace) { cl, ns ->
        if (ns == null) cl.rbac().roles().inAnyNamespace().list().items else cl.rbac().roles().inNamespace(ns)
            .list().items
    }

@Composable
fun RoleDetailsView(role: Role) {
    Column(
        modifier = Modifier.Companion
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "Role Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        DetailRow("Name", role.metadata?.name)
        DetailRow("Namespace", role.metadata?.namespace)
        DetailRow("Created", formatAge(role.metadata?.creationTimestamp))

        Spacer(Modifier.Companion.height(16.dp))

        // Правила доступу
        val rulesCount = role.rules?.size ?: 0
        Text(
            text = "Rules ($rulesCount)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.Companion.padding(bottom = 8.dp)
        )

        if (rulesCount > 0) {
            LazyColumn(
                modifier = Modifier.Companion.heightIn(max = 500.dp)
            ) {
                itemsIndexed(role.rules ?: emptyList()) { index, rule ->
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
                            RuleSection(
                                title = "API Groups",
                                items = apiGroups,
                                emptyMessage = "All API Groups (*)",
                                defaultItem = "*"
                            )

                            // Resources
                            val resources = rule.resources
                            RuleSection(
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
                            RuleSection(
                                title = "Verbs (Actions)",
                                items = verbs,
                                emptyMessage = "No actions defined",
                                defaultItem = "*",
                                highlightSpecialItems = true
                            )

                            // Кнопка "копіювати як YAML"
                            Box(
                                modifier = Modifier.Companion
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                contentAlignment = Alignment.Companion.CenterEnd
                            ) {
                                val yamlText = generateRuleYaml(rule)
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
                        "No rules defined for this role",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Companion.Bold
                    )
                    Spacer(Modifier.Companion.height(4.dp))
                    Text(
                        "This role doesn't grant any permissions",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(Modifier.Companion.height(16.dp))

        // Секція для RoleBindings
        val roleBindingsState = remember { mutableStateOf(false) }
        DetailSectionHeader(
            title = "Role Bindings",
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
                    // Тут мав би бути список RoleBindings, які посилаються на цю роль
                    // Але для цього потрібен додатковий запит до API

                    Icon(
                        imageVector = FeatherIcons.Link,
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
                        "To see which subjects (users, groups, service accounts) have this role, please check RoleBindings in this namespace",
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
                        Text("Labels (${role.metadata?.labels?.size ?: 0}):", fontWeight = FontWeight.Companion.Bold)
                    }

                    if (labelsExpanded) {
                        if (role.metadata?.labels.isNullOrEmpty()) {
                            Text("No labels", modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp)) {
                                role.metadata?.labels?.forEach { (key, value) ->
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
                            "Annotations (${role.metadata?.annotations?.size ?: 0}):",
                            fontWeight = FontWeight.Companion.Bold
                        )
                    }

                    if (annotationsExpanded) {
                        if (role.metadata?.annotations.isNullOrEmpty()) {
                            Text("No annotations", modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.Companion.padding(start = 24.dp, top = 4.dp)) {
                                role.metadata?.annotations?.entries?.sortedBy { it.key }?.forEach { (key, value) ->
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
                                                        modifier = Modifier.Companion.clickable { valueExpanded = true }
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

// Допоміжна функція для відображення секції правил
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleSection(
    title: String,
    items: List<String>?,
    emptyMessage: String,
    defaultItem: String,
    highlightSpecialItems: Boolean = false
) {
    val hasItems = !items.isNullOrEmpty()
    val hasWildcard = items?.contains(defaultItem) ?: false

    Spacer(Modifier.Companion.height(8.dp))
    Text(
        text = "$title:",
        fontWeight = FontWeight.Companion.SemiBold
    )

    if (!hasItems) {
        Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        if (hasWildcard && items?.size == 1) {
            Text(
                emptyMessage,
                color = if (highlightSpecialItems) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (highlightSpecialItems) FontWeight.Companion.Bold else FontWeight.Companion.Normal
            )
        } else {
            FlowRow(
                modifier = Modifier.Companion.fillMaxWidth(),
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
                        modifier = Modifier.Companion.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSpecial) FontWeight.Companion.Bold else FontWeight.Companion.Normal,
                            color = if (isSpecial && highlightSpecialItems)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.Companion.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// Функція для генерації YAML представлення правила
fun generateRuleYaml(rule: PolicyRule): String {
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