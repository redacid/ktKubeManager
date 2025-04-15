import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import java.io.File

fun getKubeConfigPath(): String {
    val kubeConfigEnv = System.getenv("KUBECONFIG")
    if (!kubeConfigEnv.isNullOrEmpty()) {
        return kubeConfigEnv
    }
    val userHome = System.getProperty("user.home")
    return File(userHome, ".kube/config").absolutePath
}

fun getContexts(): List<String> {
    val kubeConfigPath = getKubeConfigPath()
    val kubeConfigFile = File(kubeConfigPath)
    if (!kubeConfigFile.exists()) {
        println("Kubeconfig file not found at: $kubeConfigPath")
        return emptyList()
    }

    val config = Config.fromKubeconfig(null, kubeConfigPath, null)
    return config.contexts.keys.toList().sorted()
}

fun createKubernetesClient(contextName: String? = null): KubernetesClient? {
    val kubeConfigPath = getKubeConfigPath()
    val kubeConfigFile = File(kubeConfigPath)
    if (!kubeConfigFile.exists()) {
        println("Kubeconfig file not found at: $kubeConfigPath")
        return null
    }

    val config = Config.fromKubeconfig(contextName, kubeConfigPath, null)
    return KubernetesClientBuilder().withConfig(config).build()
}

@Composable
@Preview
fun App() {
    var selectedContext by remember { mutableStateOf<String?>(null) }
    val contexts = remember { mutableStateListOf<String>() }
    var expanded by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf("") }
    var client by remember { mutableStateOf<KubernetesClient?>(null) }

    LaunchedEffect(Unit) {
        contexts.addAll(getContexts())
    }

    LaunchedEffect(selectedContext) {
        selectedContext?.let { context ->
            connectionStatus = "Підключення до контексту: $context..."
            client = try {
                createKubernetesClient(context)
            } catch (e: Exception) {
                connectionStatus = "Помилка підключення до контексту: $context - ${e.message}"
                null
            }
            if (client != null) {
                connectionStatus = "Підключено до контексту: $context"
            }
        }
    }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Оберіть контекст Kubernetes:")
            Row {
                OutlinedTextField(
                    value = selectedContext ?: "Оберіть контекст",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(0.8f),
                    label = { Text("Контекст") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.textFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    contexts.forEach { context ->
                        DropdownMenuItem(onClick = {
                            selectedContext = context
                            expanded = false
                        }) {
                            Text(text = context)
                        }
                    }
                }
            }
            Button(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Згорнути" else "Розгорнути")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(connectionStatus)
            if (client != null) {
                Text("Клієнт Kubernetes ініціалізовано (для контексту: ${selectedContext})")
                // Подальша логіка для використання клієнта
            } else if (connectionStatus.startsWith("Помилка")) {
                // Можна відобразити більш детальну інформацію про помилку
            }
        }
    }
}

fun main() = application {
    Window(title = "Kotlin Kube Manager", onCloseRequest = ::exitApplication) {
        App()
    }
}