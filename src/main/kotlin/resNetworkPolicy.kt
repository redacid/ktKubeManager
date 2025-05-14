import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadNetworkPoliciesFabric8(client: KubernetesClient?, namespace: String? = null) =
    fetchK8sResource(client, "networkpolicies", namespace) { c, ns ->
        if (ns == null) {
            c.network().v1().networkPolicies().inAnyNamespace().list().items
        } else {
            c.network().v1().networkPolicies().inNamespace(ns).list().items
        }
    }

@Composable
fun NetworkPolicyDetailsView(networkPolicy: NetworkPolicy) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        // Основна інформація
        Text(
            text = "Network Policy Information",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        DetailRow("Name", networkPolicy.metadata?.name)
        DetailRow("Namespace", networkPolicy.metadata?.namespace)
        DetailRow("Created", formatAge(networkPolicy.metadata?.creationTimestamp))

        Spacer(Modifier.height(16.dp))

        // Pod Selector
        Text(
            text = "Pod Selector",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                val matchLabels = networkPolicy.spec?.podSelector?.matchLabels
                val matchExpressions = networkPolicy.spec?.podSelector?.matchExpressions

                if (matchLabels.isNullOrEmpty() && matchExpressions.isNullOrEmpty()) {
                    Text("This policy selects all pods in the namespace")
                } else {
                    // Відображаємо matchLabels
                    if (!matchLabels.isNullOrEmpty()) {
                        Text("Match Labels:", fontWeight = FontWeight.SemiBold)
                        Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
                            matchLabels.forEach { (key, value) ->
                                Row {
                                    SelectionContainer {
                                        Text(
                                            text = key,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(120.dp)
                                        )
                                    }
                                    Text(text = "= ")
                                    SelectionContainer {
                                        Text(value)
                                    }
                                }
                            }
                        }
                    }

                    // Відображаємо matchExpressions
                    if (!matchExpressions.isNullOrEmpty()) {
                        Text(
                            "Match Expressions:",
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = if (matchLabels.isNullOrEmpty()) 0.dp else 8.dp)
                        )
                        Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
                            matchExpressions.forEach { expr ->
                                Row {
                                    SelectionContainer {
                                        Text(
                                            text = expr.key ?: "",
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.width(120.dp)
                                        )
                                    }
                                    Text(
                                        text = expr.operator ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    SelectionContainer {
                                        Text(
                                            text = expr.values?.joinToString(", ") ?: "",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Policy Types
        Text(
            text = "Policy Types",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                val policyTypes = networkPolicy.spec?.policyTypes
                if (policyTypes.isNullOrEmpty()) {
                    Text("Default: Ingress")
                } else {
                    Row {
                        policyTypes.forEach { type ->
                            Card(
                                modifier = Modifier.padding(end = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = when (type) {
                                        "Ingress" -> MaterialTheme.colorScheme.primaryContainer
                                        "Egress" -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.tertiaryContainer
                                    }
                                )
                            ) {
                                Text(
                                    text = type,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = when (type) {
                                        "Ingress" -> MaterialTheme.colorScheme.onPrimaryContainer
                                        "Egress" -> MaterialTheme.colorScheme.onSecondaryContainer
                                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Ingress Rules
        val ingressRules = networkPolicy.spec?.ingress
        if (!ingressRules.isNullOrEmpty()) {
            Text(
                text = "Ingress Rules (${ingressRules.size})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                itemsIndexed(ingressRules) { index, rule ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Rule #${index + 1}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // From section
                            val from = rule.from
                            if (from.isNullOrEmpty()) {
                                Text("Allow from: All sources (no restrictions)")
                            } else {
                                Text("Allow from:", fontWeight = FontWeight.SemiBold)
                                Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
                                    from.forEachIndexed { fromIndex, peer ->
                                        Text("Source #${fromIndex + 1}:", fontWeight = FontWeight.Medium)

                                        // Pod Selector
                                        peer.podSelector?.let { podSelector ->
                                            Text("Pod Selector:", modifier = Modifier.padding(start = 12.dp))
                                            val matchLabels = podSelector.matchLabels
                                            if (!matchLabels.isNullOrEmpty()) {
                                                Column(modifier = Modifier.padding(start = 24.dp)) {
                                                    matchLabels.forEach { (key, value) ->
                                                        Row {
                                                            Text("$key = $value")
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Namespace Selector
                                        peer.namespaceSelector?.let { nsSelector ->
                                            Text("Namespace Selector:", modifier = Modifier.padding(start = 12.dp))
                                            val matchLabels = nsSelector.matchLabels
                                            if (!matchLabels.isNullOrEmpty()) {
                                                Column(modifier = Modifier.padding(start = 24.dp)) {
                                                    matchLabels.forEach { (key, value) ->
                                                        Row {
                                                            Text("$key = $value")
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // IP Block
                                        peer.ipBlock?.let { ipBlock ->
                                            Text("IP Block:", modifier = Modifier.padding(start = 12.dp))
                                            Column(modifier = Modifier.padding(start = 24.dp)) {
                                                Text("CIDR: ${ipBlock.cidr}")
                                                if (!ipBlock.except.isNullOrEmpty()) {
                                                    Text("Except: ${ipBlock.except.joinToString(", ")}")
                                                }
                                            }
                                        }

                                        if (fromIndex < from.size - 1) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                        }
                                    }
                                }
                            }

                            // Ports section
                            val ports = rule.ports
                            if (!ports.isNullOrEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Ports:", fontWeight = FontWeight.SemiBold)
                                Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
                                    ports.forEach { port ->
                                        Row {
                                            Text(
                                                text = "${port.protocol ?: "TCP"}: ${port.port ?: "all ports"}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(Modifier.height(8.dp))
                                Text("Ports: All ports allowed")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // Egress Rules
        val egressRules = networkPolicy.spec?.egress
        if (!egressRules.isNullOrEmpty()) {
            Text(
                text = "Egress Rules (${egressRules.size})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                itemsIndexed(egressRules) { index, rule ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Rule #${index + 1}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // To section
                            val to = rule.to
                            if (to.isNullOrEmpty()) {
                                Text("Allow to: All destinations (no restrictions)")
                            } else {
                                Text("Allow to:", fontWeight = FontWeight.SemiBold)
                                Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
                                    to.forEachIndexed { toIndex, peer ->
                                        Text("Destination #${toIndex + 1}:", fontWeight = FontWeight.Medium)

                                        // Pod Selector
                                        peer.podSelector?.let { podSelector ->
                                            Text("Pod Selector:", modifier = Modifier.padding(start = 12.dp))
                                            val matchLabels = podSelector.matchLabels
                                            if (!matchLabels.isNullOrEmpty()) {
                                                Column(modifier = Modifier.padding(start = 24.dp)) {
                                                    matchLabels.forEach { (key, value) ->
                                                        Row {
                                                            Text("$key = $value")
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Namespace Selector
                                        peer.namespaceSelector?.let { nsSelector ->
                                            Text("Namespace Selector:", modifier = Modifier.padding(start = 12.dp))
                                            val matchLabels = nsSelector.matchLabels
                                            if (!matchLabels.isNullOrEmpty()) {
                                                Column(modifier = Modifier.padding(start = 24.dp)) {
                                                    matchLabels.forEach { (key, value) ->
                                                        Row {
                                                            Text("$key = $value")
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // IP Block
                                        peer.ipBlock?.let { ipBlock ->
                                            Text("IP Block:", modifier = Modifier.padding(start = 12.dp))
                                            Column(modifier = Modifier.padding(start = 24.dp)) {
                                                Text("CIDR: ${ipBlock.cidr}")
                                                if (!ipBlock.except.isNullOrEmpty()) {
                                                    Text("Except: ${ipBlock.except.joinToString(", ")}")
                                                }
                                            }
                                        }

                                        if (toIndex < to.size - 1) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                        }
                                    }
                                }
                            }

                            // Ports section
                            val ports = rule.ports
                            if (!ports.isNullOrEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Ports:", fontWeight = FontWeight.SemiBold)
                                Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
                                    ports.forEach { port ->
                                        Row {
                                            Text(
                                                text = "${port.protocol ?: "TCP"}: ${port.port ?: "all ports"}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(Modifier.height(8.dp))
                                Text("Ports: All ports allowed")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // Мітки та анотації
        val labelsState = remember { mutableStateOf(false) }
        DetailSectionHeader(title = "Labels & Annotations", expanded = labelsState)

        if (labelsState.value) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Мітки
                    var labelsExpanded by remember { mutableStateOf(true) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { labelsExpanded = !labelsExpanded }
                    ) {
                        Icon(
                            imageVector = if (labelsExpanded) ICON_DOWN else ICON_RIGHT,
                            contentDescription = "Toggle Labels",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Labels (${networkPolicy.metadata?.labels?.size ?: 0}):", fontWeight = FontWeight.Bold)
                    }

                    if (labelsExpanded) {
                        if (networkPolicy.metadata?.labels.isNullOrEmpty()) {
                            Text("No labels", modifier = Modifier.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                networkPolicy.metadata?.labels?.forEach { (key, value) ->
                                    Row {
                                        SelectionContainer {
                                            Text(
                                                text = key,
                                                fontWeight = FontWeight.Medium,
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

                    Spacer(Modifier.height(8.dp))

                    // Анотації
                    var annotationsExpanded by remember { mutableStateOf(true) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { annotationsExpanded = !annotationsExpanded }
                    ) {
                        Icon(
                            imageVector = if (annotationsExpanded) ICON_DOWN else ICON_RIGHT,
                            contentDescription = "Toggle Annotations",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Annotations (${networkPolicy.metadata?.annotations?.size ?: 0}):",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (annotationsExpanded) {
                        if (networkPolicy.metadata?.annotations.isNullOrEmpty()) {
                            Text("No annotations", modifier = Modifier.padding(start = 24.dp, top = 4.dp))
                        } else {
                            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                                networkPolicy.metadata?.annotations?.entries?.sortedBy { it.key }
                                    ?.forEach { (key, value) ->
                                        val isLongValue = value.length > 50
                                        var valueExpanded by remember { mutableStateOf(false) }

                                        Row(verticalAlignment = Alignment.Top) {
                                            SelectionContainer {
                                                Text(
                                                    text = key,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.width(180.dp)
                                                )
                                            }

                                            Text(": ")

                                            if (isLongValue) {
                                                Column {
                                                    SelectionContainer {
                                                        Text(
                                                            text = if (valueExpanded) value else value.take(50) + "...",
                                                            modifier = Modifier.clickable {
                                                                valueExpanded = !valueExpanded
                                                            }
                                                        )
                                                    }
                                                    if (!valueExpanded) {
                                                        Text(
                                                            text = "Click to expand",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.clickable { valueExpanded = true }
                                                        )
                                                    }
                                                }
                                            } else {
                                                SelectionContainer {
                                                    Text(value)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
}