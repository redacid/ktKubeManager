import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.fabric8.kubernetes.api.model.HasMetadata

@Composable
fun ErrorDialog(
    showDialog: Boolean,
    errorMessage: String,
    onDismiss: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Помилка Підключення") },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text("OK")
                }
            }
        )
    }
}

// Calculate optimal column widths based on content
@Composable
fun calculateColumnWidths(
    headers: List<String>,
    items: List<HasMetadata>,
    resourceType: String,
    minColumnWidth: Int = 60,
    maxColumnWidth: Int = 500,
    padding: Int = 16
): List<Int> {
    // Text measurer to calculate text dimensions
    val textMeasurer = rememberTextMeasurer()
    val headerStyle = MaterialTheme.typography.titleSmall
    val cellStyle = MaterialTheme.typography.bodyMedium

    return remember(headers, items, resourceType) {
        // Initialize with minimum widths
        val widths = MutableList(headers.size) { minColumnWidth }

        // Measure header widths
        headers.forEachIndexed { index, header ->
            val textWidth = measureTextWidth(textMeasurer, header, headerStyle)
            widths[index] = maxOf(
                widths[index], (textWidth + padding).coerceIn(minColumnWidth, maxColumnWidth)
            )
        }

        // Measure data widths (sample up to 100 items for performance)
        val sampleItems = if (items.size > 100) items.take(100) else items
        sampleItems.forEach { item ->
            headers.forEachIndexed { colIndex, _ ->
                val cellData = getCellData(item, colIndex, resourceType)
                val textWidth = measureTextWidth(textMeasurer, cellData, cellStyle)
                widths[colIndex] = maxOf(
                    widths[colIndex], (textWidth + padding).coerceIn(minColumnWidth, maxColumnWidth)
                )
            }
        }

        widths
    }
}

// Helper function to measure text width
fun measureTextWidth(
    textMeasurer: TextMeasurer, text: String, style: TextStyle
): Int {
    val textLayoutResult = textMeasurer.measure(
        text = text, style = style
    )
    return textLayoutResult.size.width
}

fun convertJsonToYaml(jsonString: String): String {
    try {
        // Parse JSON to object
        val jsonMapper = ObjectMapper().registerKotlinModule()
        val jsonObject = jsonMapper.readValue(jsonString, Any::class.java)

        // Convert object to YAML
        val yamlMapper = ObjectMapper(
            YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
        ).registerKotlinModule()

        return yamlMapper.writeValueAsString(jsonObject)
    } catch (e: Exception) {
        return "Error converting JSON to YAML: ${e.message}"
    }
}