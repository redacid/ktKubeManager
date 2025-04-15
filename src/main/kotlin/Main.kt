// src/main/kotlin/Main.kt (Пряме відтворення ConfigGetContextsEquivalent)
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
// --- Імпорти для Fabric8 ---
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.api.model.NamedContext
// ---------------------------
import org.slf4j.LoggerFactory

// Логер
private val logger = LoggerFactory.getLogger("MainKtGetContextsDirect")

@Composable
fun App() {
    var contexts by remember { mutableStateOf<List<String>>(emptyList()) }
    var statusMessage by remember { mutableStateOf<String?>("Завантаження контекстів...") }
    var isLoading by remember { mutableStateOf(true) } // Починаємо із завантаження

    // Виконуємо завантаження ОДИН РАЗ при старті.
    // УВАГА: Це БЛОКУЮЧИЙ виклик у LaunchedEffect без Dispatchers.IO! UI може зависнути!
    LaunchedEffect(Unit) {
        logger.info("LaunchedEffect: Getting context list directly...")
        isLoading = true
        statusMessage = "Завантаження..."
        try {
            // Створюємо дефолтний клієнт (блокуюча операція)
            val k8s: KubernetesClient = KubernetesClientBuilder().build()
            // Отримуємо конфігурацію і контексти (блокуюча операція)
            val contextNames = k8s.configuration?.contexts // Доступ до конфігурації клієнта
                ?.mapNotNull { it.name }
                ?.sorted()
                ?: emptyList()

            // Оновлюємо стан (це вже виконається після завершення блокуючих операцій)
            contexts = contextNames
            statusMessage = if (contextNames.isEmpty()) "Контексти не знайдено" else "Контексти завантажено"
            logger.info("Contexts loaded: ${contextNames.size}")

            // Важливо закрити клієнт, якщо він більше не потрібен тут
            // У реальному додатку клієнт зберігався б у стані
            k8s.close()

        } catch (e: Exception) {
            logger.error("Failed to get contexts: ${e.message}", e)
            statusMessage = "Помилка завантаження контекстів: ${e.message}"
            contexts = emptyList()
        } finally {
            isLoading = false
            logger.info("LaunchedEffect finished.")
        }
    }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Доступні Контексти:", style = MaterialTheme.typography.h6)
            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(statusMessage ?: "Завантаження...")
                }
            } else if (contexts.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxSize().border(1.dp, Color.Gray)) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(contexts) { contextName ->
                            Text(
                                text = contextName,
                                modifier = Modifier.fillMaxWidth().padding(8.dp)
                            )
                        }
                    }
                }
            } else {
                Text(statusMessage ?: "Не вдалося завантажити контексти.")
            }
        }
    }
}

// --- Головна функція ---
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Kotlin Kube Context Lister (Blocking)") {
        App()
    }
}