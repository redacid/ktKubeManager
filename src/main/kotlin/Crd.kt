import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient

suspend fun loadCrdsFabric8(client: KubernetesClient?) =
    fetchK8sResource(client, "crds", null) { c, _ ->
        c.apiextensions().v1().customResourceDefinitions().list().items
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrdDetailsView(crd: CustomResourceDefinition) {
    //val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.Companion
            .padding(16.dp)
            .fillMaxSize()
        //.verticalScroll(scrollState)
    ) {
        // Базова інформація у вигляді картки
        Card(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.Companion.padding(16.dp)) {
                Text(
                    text = crd.metadata?.name ?: "Невідомий CRD",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Companion.Bold
                )

                Spacer(Modifier.Companion.height(4.dp))

                Text(
                    text = "${crd.spec?.group ?: "Невідома група"} / ${crd.spec?.names?.kind ?: "Невідомий тип"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )

                Spacer(Modifier.Companion.height(8.dp))

                Row(
                    modifier = Modifier.Companion.fillMaxWidth(),
                    verticalAlignment = Alignment.Companion.CenterVertically
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "Scope: ${crd.spec?.scope ?: "Namespaced"}",
                            modifier = Modifier.Companion.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Companion.Medium,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(Modifier.Companion.width(8.dp))

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = "Created: ${formatAge(crd.metadata?.creationTimestamp)}",
                            modifier = Modifier.Companion.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Companion.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Інформація про кастомний ресурс
        Card(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.Companion.padding(16.dp)) {
                Text(
                    text = "Інформація про ресурс",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Companion.Bold,
                    modifier = Modifier.Companion.padding(bottom = 8.dp)
                )

                HorizontalDivider(modifier = Modifier.Companion.padding(bottom = 12.dp))

                val names = crd.spec?.names

                // Базова інформація
                Row(modifier = Modifier.Companion.fillMaxWidth()) {
                    Column(modifier = Modifier.Companion.weight(1f)) {
                        Row {
                            Text(
                                text = "Kind:",
                                fontWeight = FontWeight.Companion.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.Companion.width(100.dp)
                            )
                            Text(
                                text = names?.kind ?: "Невідомий",
                                fontWeight = FontWeight.Companion.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.Companion.height(8.dp))

                        Row {
                            Text(
                                text = "List Kind:",
                                fontWeight = FontWeight.Companion.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.Companion.width(100.dp)
                            )
                            Text(
                                text = names?.listKind ?: "${names?.kind ?: ""}List",
                                fontWeight = FontWeight.Companion.Bold
                            )
                        }
                    }

                    Column(modifier = Modifier.Companion.weight(1f)) {
                        Row {
                            Text(
                                text = "Plural:",
                                fontWeight = FontWeight.Companion.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.Companion.width(100.dp)
                            )
                            Text(
                                text = names?.plural ?: "Невідомий",
                                fontWeight = FontWeight.Companion.Bold
                            )
                        }

                        Spacer(Modifier.Companion.height(8.dp))

                        Row {
                            Text(
                                text = "Singular:",
                                fontWeight = FontWeight.Companion.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.Companion.width(100.dp)
                            )
                            Text(
                                text = names?.singular ?: "-",
                                fontWeight = FontWeight.Companion.Bold
                            )
                        }
                    }
                }

                // Короткі назви
                if (!names?.shortNames.isNullOrEmpty()) {
                    Spacer(Modifier.Companion.height(12.dp))
                    Text(
                        text = "Короткі назви:",
                        fontWeight = FontWeight.Companion.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(modifier = Modifier.Companion.padding(top = 4.dp)) {
                        names?.shortNames?.forEach { shortName ->
                            Card(
                                modifier = Modifier.Companion.padding(end = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Text(
                                    text = shortName,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Companion.Medium,
                                    modifier = Modifier.Companion.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Категорії
                if (!names?.categories.isNullOrEmpty()) {
                    Spacer(Modifier.Companion.height(12.dp))
                    Text(
                        text = "Категорії:",
                        fontWeight = FontWeight.Companion.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        modifier = Modifier.Companion.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        names?.categories?.forEach { category ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = category,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.Companion.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Статус CRD
        val conditions = crd.status?.conditions
        if (!conditions.isNullOrEmpty()) {
            Spacer(Modifier.Companion.height(8.dp))

            Card(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.Companion.padding(16.dp)) {
                    Text(
                        text = "Статус CRD",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Companion.Bold,
                        modifier = Modifier.Companion.padding(bottom = 8.dp)
                    )

                    HorizontalDivider(modifier = Modifier.Companion.padding(bottom = 12.dp))

                    // Умови статусу
                    conditions.forEach { condition ->
                        val isPositive = condition.status == "True"
                        val backgroundColor = when {
                            condition.type == "Established" && isPositive -> MaterialTheme.colorScheme.primaryContainer
                            condition.type == "NamesAccepted" && isPositive -> MaterialTheme.colorScheme.secondaryContainer
                            isPositive -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        }

                        Card(
                            modifier = Modifier.Companion
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = backgroundColor)
                        ) {
                            Column(modifier = Modifier.Companion.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.Companion.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isPositive) ICON_SUCCESS else ICON_ERROR,
                                        contentDescription = condition.type,
                                        tint = if (isPositive)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error,
                                        modifier = Modifier.Companion.size(20.dp)
                                    )

                                    Spacer(Modifier.Companion.width(8.dp))

                                    Text(
                                        text = condition.type ?: "Невідомий стан",
                                        fontWeight = FontWeight.Companion.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )

                                    Spacer(Modifier.Companion.weight(1f))

                                    Text(
                                        text = condition.status ?: "Невідомо",
                                        color = if (isPositive)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Companion.Bold
                                    )
                                }

                                Spacer(Modifier.Companion.height(8.dp))

                                Text(
                                    text = "Причина: ${condition.reason ?: "Невідома"}",
                                    fontWeight = FontWeight.Companion.Medium
                                )

                                if (!condition.message.isNullOrEmpty()) {
                                    Spacer(Modifier.Companion.height(4.dp))
                                    SelectionContainer {
                                        Text(
                                            text = condition.message,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                Spacer(Modifier.Companion.height(4.dp))
                                Text(
                                    text = "Останнє оновлення: ${formatAge(condition.lastTransitionTime)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Companion.Italic
                                )
                            }
                        }
                    }
                }
            }
        }

        // Версії CRD
        val versions = crd.spec?.versions
        Spacer(Modifier.Companion.height(8.dp))

        Card(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.Companion.padding(16.dp)) {
                Text(
                    text = "Версії (${versions?.size ?: 0})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Companion.Bold,
                    modifier = Modifier.Companion.padding(bottom = 8.dp)
                )

                HorizontalDivider(modifier = Modifier.Companion.padding(bottom = 12.dp))

                if (versions.isNullOrEmpty()) {
                    Column(
                        modifier = Modifier.Companion
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.Companion.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = FeatherIcons.AlertTriangle,
                            contentDescription = "Попередження",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.Companion.size(32.dp)
                        )
                        Spacer(Modifier.Companion.height(8.dp))
                        Text(
                            "Жодної версії не визначено для цього CRD",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Companion.Bold,
                            textAlign = TextAlign.Companion.Center
                        )
                    }
                } else {
                    versions.forEachIndexed { index, version ->
                        val isServed = version.served == true

                        Card(
                            modifier = Modifier.Companion
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isServed) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.Companion.padding(16.dp)) {
                                // Заголовок версії
                                Row(
                                    modifier = Modifier.Companion.fillMaxWidth(),
                                    verticalAlignment = Alignment.Companion.CenterVertically
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isServed) {
                                                if (version.storage == true)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.secondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.errorContainer
                                            }
                                        )
                                    ) {
                                        Text(
                                            text = version.name ?: "невідома",
                                            color = if (isServed) {
                                                if (version.storage == true)
                                                    MaterialTheme.colorScheme.onPrimary
                                                else
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            },
                                            fontWeight = FontWeight.Companion.Bold,
                                            modifier = Modifier.Companion.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }

                                    Spacer(Modifier.Companion.weight(1f))

                                    // Ознаки статусу
                                    Row {
                                        if (version.storage == true) {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                                )
                                            ) {
                                                Text(
                                                    text = "Версія зберігання",
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.Companion.padding(
                                                        horizontal = 8.dp,
                                                        vertical = 4.dp
                                                    )
                                                )
                                            }
                                            Spacer(Modifier.Companion.width(8.dp))
                                        }

                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isServed)
                                                    MaterialTheme.colorScheme.tertiaryContainer
                                                else
                                                    MaterialTheme.colorScheme.errorContainer
                                            )
                                        ) {
                                            Text(
                                                text = if (isServed) "Активна" else "Неактивна",
                                                color = if (isServed)
                                                    MaterialTheme.colorScheme.onTertiaryContainer
                                                else
                                                    MaterialTheme.colorScheme.onErrorContainer,
                                                fontSize = 12.sp,
                                                modifier = Modifier.Companion.padding(
                                                    horizontal = 8.dp,
                                                    vertical = 4.dp
                                                )
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.Companion.padding(vertical = 8.dp))

                                // Деталі схеми
                                version.schema?.let { schema ->
                                    schema.openAPIV3Schema?.let { openAPISchema ->
                                        Row {
                                            Text(
                                                text = "Тип схеми:",
                                                fontWeight = FontWeight.Companion.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.Companion.width(120.dp)
                                            )
                                            Text(
                                                text = openAPISchema.type ?: "не вказано",
                                                fontWeight = FontWeight.Companion.Bold
                                            )
                                        }

                                        if (openAPISchema.required?.isNotEmpty() == true) {
                                            Spacer(Modifier.Companion.height(8.dp))
                                            Text("Обов'язкові поля:", fontWeight = FontWeight.Companion.Medium)

                                            FlowRow(
                                                modifier = Modifier.Companion.padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                openAPISchema.required.forEach { field ->
                                                    Card(
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = MaterialTheme.colorScheme.surfaceTint.copy(
                                                                alpha = 0.1f
                                                            )
                                                        )
                                                    ) {
                                                        Text(
                                                            text = field,
                                                            modifier = Modifier.Companion.padding(
                                                                horizontal = 8.dp,
                                                                vertical = 2.dp
                                                            ),
                                                            fontSize = 13.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Субресурси
                                version.subresources?.let { subresources ->
                                    if (subresources.status != null || subresources.scale != null) {
                                        Spacer(Modifier.Companion.height(12.dp))
                                        Text("Субресурси:", fontWeight = FontWeight.Companion.Medium)

                                        Row(
                                            modifier = Modifier.Companion.padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (subresources.status != null) {
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                                    )
                                                ) {
                                                    Text(
                                                        text = "status",
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.Companion.padding(
                                                            horizontal = 8.dp,
                                                            vertical = 4.dp
                                                        )
                                                    )
                                                }
                                            }

                                            if (subresources.scale != null) {
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                                    )
                                                ) {
                                                    Text(
                                                        text = "scale",
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.Companion.padding(
                                                            horizontal = 8.dp,
                                                            vertical = 4.dp
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        // Деталі scale
                                        subresources.scale?.let { scale ->
                                            Column(
                                                modifier = Modifier.Companion
                                                    .padding(start = 16.dp, top = 4.dp)
                                                    .fillMaxWidth()
                                            ) {
                                                scale.specReplicasPath?.let {
                                                    Text(
                                                        text = "Шлях до spec.replicas: $it",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }

                                                scale.statusReplicasPath?.let {
                                                    Text(
                                                        text = "Шлях до status.replicas: $it",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }

                                                scale.labelSelectorPath?.let {
                                                    Text(
                                                        text = "Шлях до селектора міток: $it",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Додаткові колонки для виводу
                                if (!version.additionalPrinterColumns.isNullOrEmpty()) {
                                    Spacer(Modifier.Companion.height(12.dp))
                                    Text(
                                        text = "Додаткові колонки виводу:",
                                        fontWeight = FontWeight.Companion.Medium
                                    )

                                    Column(
                                        modifier = Modifier.Companion
                                            .padding(top = 8.dp)
                                            .fillMaxWidth()
                                    ) {
                                        version.additionalPrinterColumns.forEach { column ->
                                            Row(
                                                modifier = Modifier.Companion.padding(bottom = 4.dp),
                                                verticalAlignment = Alignment.Companion.CenterVertically
                                            ) {
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                                    ),
                                                    modifier = Modifier.Companion.width(110.dp)
                                                ) {
                                                    Text(
                                                        text = column.name ?: "без назви",
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        fontWeight = FontWeight.Companion.Medium,
                                                        textAlign = TextAlign.Companion.Center,
                                                        modifier = Modifier.Companion
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                            .fillMaxWidth()
                                                    )
                                                }

                                                Spacer(Modifier.Companion.width(12.dp))

                                                Row {
                                                    Card(
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                        )
                                                    ) {
                                                        Text(
                                                            text = column.type ?: "невідомий",
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontSize = 13.sp,
                                                            modifier = Modifier.Companion.padding(
                                                                horizontal = 6.dp,
                                                                vertical = 2.dp
                                                            )
                                                        )
                                                    }

                                                    Spacer(Modifier.Companion.width(8.dp))

                                                    Text(
                                                        text = column.jsonPath ?: "невідомий шлях",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.Companion.align(Alignment.Companion.CenterVertically)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (index < versions.size - 1) {
                            Spacer(Modifier.Companion.height(8.dp))
                        }
                    }
                }
            }
        }

        // Мітки та анотації
        if (!crd.metadata?.labels.isNullOrEmpty() || !crd.metadata?.annotations.isNullOrEmpty()) {
            Spacer(Modifier.Companion.height(8.dp))

            Card(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.Companion.padding(16.dp)) {
                    Text(
                        text = "Мітки та анотації",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Companion.Bold,
                        modifier = Modifier.Companion.padding(bottom = 8.dp)
                    )

                    HorizontalDivider(modifier = Modifier.Companion.padding(bottom = 12.dp))

                    // Мітки
                    if (!crd.metadata?.labels.isNullOrEmpty()) {
                        Text(
                            text = "Мітки:",
                            fontWeight = FontWeight.Companion.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(
                            modifier = Modifier.Companion
                                .padding(vertical = 6.dp, horizontal = 8.dp)
                                .fillMaxWidth()
                        ) {
                            crd.metadata?.labels?.entries?.sortedBy { it.key }?.forEach { (key, value) ->
                                Row(modifier = Modifier.Companion.padding(vertical = 2.dp)) {
                                    SelectionContainer {
                                        Text(
                                            text = key,
                                            fontWeight = FontWeight.Companion.Medium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.Companion.width(180.dp)
                                        )
                                    }

                                    Text(text = "= ")

                                    SelectionContainer {
                                        Text(
                                            text = value,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Анотації
                    if (!crd.metadata?.annotations.isNullOrEmpty()) {
                        if (!crd.metadata?.labels.isNullOrEmpty()) {
                            Spacer(Modifier.Companion.height(16.dp))
                        }

                        Text(
                            text = "Анотації:",
                            fontWeight = FontWeight.Companion.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(
                            modifier = Modifier.Companion
                                .padding(vertical = 6.dp, horizontal = 8.dp)
                                .fillMaxWidth()
                        ) {
                            crd.metadata?.annotations?.entries?.sortedBy { it.key }?.forEach { (key, value) ->
                                val isLongValue = value.length > 60
                                var valueExpanded by remember { mutableStateOf(false) }

                                Column(modifier = Modifier.Companion.padding(vertical = 4.dp)) {
                                    Row {
                                        SelectionContainer {
                                            Text(
                                                text = key,
                                                fontWeight = FontWeight.Companion.Medium,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.Companion.width(180.dp)
                                            )
                                        }

                                        Text(text = "= ")
                                    }

                                    if (isLongValue) {
                                        Row(
                                            modifier = Modifier.Companion
                                                .padding(start = 12.dp, top = 4.dp)
                                                .clickable { valueExpanded = !valueExpanded }
                                        ) {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                                modifier = Modifier.Companion.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.Companion.padding(8.dp)) {
                                                    SelectionContainer {
                                                        Text(
                                                            text = if (valueExpanded) value else value.take(60) + "...",
                                                            style = MaterialTheme.typography.bodySmall
                                                        )
                                                    }

                                                    if (!valueExpanded) {
                                                        Text(
                                                            text = "Натисніть, щоб розгорнути",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.Companion.padding(top = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.Companion.padding(start = 12.dp, top = 2.dp)
                                        ) {
                                            SelectionContainer {
                                                Text(value)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Нижній відступ
        Spacer(Modifier.Companion.height(16.dp))
    }
}