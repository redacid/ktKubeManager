import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Діалог для створення нової port-forward сесії.
 */
@Composable
fun PortForwardDialog(
    isOpen: Boolean,
    namespace: String,
    podName: String,
    availableContainerPorts: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (localPort: Int, podPort: Int, bindAddress: String) -> Unit
) {
    if (isOpen) {
        var selectedPodPort by remember { mutableStateOf(availableContainerPorts.firstOrNull() ?: 80) }
        var localPort by remember { mutableStateOf("0") } // 0 = автоматичний вибір порту
        var localPortError by remember { mutableStateOf<String?>(null) }
        var bindAddressType by remember { mutableStateOf("all") } // "all", "ipv4", "ipv6"

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Setting Port Forward") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Setting up port redirection for a pod: $podName in namespace: $namespace"
                    )

                    // Вибір порту контейнера
                    Text("Container port:", fontWeight = FontWeight.Bold)
                    if (availableContainerPorts.isEmpty()) {
                        Text("No available ports found in a container")
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableContainerPorts.forEach { port ->
                                OutlinedButton(
                                    onClick = { selectedPodPort = port },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (port == selectedPodPort)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Text(port.toString())
                                }
                            }
                        }
                    }

                    // Введення локального порту
                    Text("Local port:", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = localPort,
                        onValueChange = {
                            localPort = it
                            localPortError = validateLocalPort(it)
                        },
                        label = { Text("Local port (0 for automatic choice)") },
                        isError = localPortError != null,
                        supportingText = {
                            if (localPortError != null) {
                                Text(localPortError!!)
                            } else {
                                Text("Leave 0 to automatically select the available port")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Вибір типу адреси прив'язки
                    Text("Type of binding:", fontWeight = FontWeight.Bold)
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { bindAddressType = "all" }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = bindAddressType == "all",
                                onClick = { bindAddressType = "all" }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("All interfaces (0.0.0.0)")
                                Text(
                                    "Available from any interface (IPv4 та IPv6)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { bindAddressType = "ipv4" }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = bindAddressType == "ipv4",
                                onClick = { bindAddressType = "ipv4" }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("IPv4 only (127.0.0.1)")
                                Text(
                                    "Only available with Localhost through IPv4",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { bindAddressType = "ipv6" }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = bindAddressType == "ipv6",
                                onClick = { bindAddressType = "ipv6" }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("IPv6 only (::1)")
                                Text(
                                    "Only available with Localhost through IPv6",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bindAddress = when (bindAddressType) {
                            "ipv4" -> "127.0.0.1"
                            "ipv6" -> "::1"
                            else -> "0.0.0.0"
                        }
                        onConfirm(localPort.toIntOrNull() ?: 0, selectedPodPort, bindAddress)
                        onDismiss()
                    },
                    enabled = localPortError == null && availableContainerPorts.isNotEmpty()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}


/**
 * Валідація локального порту
 */
private fun validateLocalPort(portStr: String): String? {
    val port = portStr.toIntOrNull()
    return when {
        portStr.isBlank() -> "The port cannot be empty"
        port == null -> "Incorrect port format"
        port < 0 -> "The port cannot be negative"
        port > 0 && port < 1024 -> "Ports 1-1023 need administrator rights"
        port > 65535 -> "The port cannot be more 65535"
        else -> null
    }
}

@Composable
fun PortForwardWindow(
    onClose: () -> Unit
) {
    val dialogState = remember { DialogState(width = 800.dp, height = 600.dp) }

    DialogWindow(
        onCloseRequest = onClose,
        title = "Port Forward",
        state = dialogState
    ) {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            // Використовуємо існуючий компонент PortForwardPanel
            PortForwardPanel(
                portForwardService = portForwardService
            )
        }
    }
}

/**
 * Головний компонент для відображення активних port-forward сесій.
 */
@Composable
fun PortForwardPanel(
    portForwardService: PortForwardService,
) {
    val scope = rememberCoroutineScope()
    var activeSessions by remember { mutableStateOf(emptyList<PortForwardSession>()) }
    val clipboardManager = LocalClipboardManager.current
    var lastCopiedUrl by remember { mutableStateOf<String?>(null) }

    // Показуємо повідомлення про копіювання
    var showCopyToast by remember { mutableStateOf(false) }

    // Оновлення списку активних сесій кожну секунду
    LaunchedEffect(Unit) {
        while (true) {
            activeSessions = portForwardService.getActivePortForwards()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Заголовок
        Text(
            "Active Port Forward sessions",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Список активних сесій
        if (activeSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No active sessions Port Forward")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Click the 'Port Forward' in the Pod Details to create a new session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(1.dp)
            ) {
                items(activeSessions) { session ->
                    PortForwardSessionItem(
                        session = session,
                        onCopyUrl = { url ->
                            clipboardManager.setText(AnnotatedString(url))
                            lastCopiedUrl = url
                            showCopyToast = true
                            scope.launch {
                                delay(2000)
                                showCopyToast = false
                            }
                        },
                        onStop = {
                            scope.launch {
                                portForwardService.stopPortForward(session.id)
                                activeSessions = portForwardService.getActivePortForwards()
                            }
                        }
                    )
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                }
            }
        }
        // Показуємо повідомлення про копіювання
        if (showCopyToast && lastCopiedUrl != null) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "URL is copied: $lastCopiedUrl",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Кнопка для зупинки всіх сесій
        if (activeSessions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        portForwardService.stopAllPortForwards()
                        activeSessions = emptyList()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Stop all sessions")
            }
        }
    }
}

/**
 * Елемент списку, що відображає одну port-forward сесію.
 */
@Composable
fun PortForwardSessionItem(
    session: PortForwardSession,
    onCopyUrl: (String) -> Unit, // Змінено тип на (String) -> Unit
    onStop: () -> Unit
) {
    var durationText by remember { mutableStateOf(formatDuration(session.getDuration())) }

    // Оновлення часу тривалості кожну секунду
    LaunchedEffect(session.id) {
        while (true) {
            durationText = formatDuration(session.getDuration())
            delay(1000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Інформація про сесію
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            // Верхній рядок: Namespace і Pod
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Namespace: ${session.namespace}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Pod: ${session.podName}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Середній рядок: URL для копіювання
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { onCopyUrl(session.url) }) // Передаємо URL у функцію колбеку
                    .padding(vertical = 4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text(
                        "URL: ${session.url}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    "Click to copy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Нижній рядок: Порти і тривалість
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Local port: ${session.localPort} → Pod Port: ${session.podPort}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Duration: $durationText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        var showErrorDialog by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

// Діалог помилки
        if (showErrorDialog && errorMessage != null) {
            AlertDialog(
                onDismissRequest = { showErrorDialog = false },
                title = { Text("Помилка відкриття браузера") },
                text = { Text(errorMessage!!) },
                confirmButton = {
                    Button(onClick = { showErrorDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

        // Кнопка відкриття в браузері
        Button(
            onClick = {
                val desktop = java.awt.Desktop.getDesktop()
                try {
                    // Додаємо http:// до URL, якщо схема відсутня
                    val urlWithScheme = if (!session.url.startsWith("http://") && !session.url.startsWith("https://")) {
                        "http://${session.url}"
                    } else {
                        session.url
                    }

                    desktop.browse(java.net.URI(urlWithScheme))
                } catch (e: Exception) {
                    logger.error("Error when opening a browser: ${e.message}")
                    showErrorDialog = true
                    errorMessage = "Помилка при відкритті браузера: ${e.message}"
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = ICON_LINK,
                contentDescription = "Open in Browser"
            )
            Spacer(Modifier.width(4.dp))
            Text("Open in Browser")
        }


        // Кнопка зупинки
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Text("Stop")
        }
    }
}


/**
 * Форматує тривалість в мілісекундах у зручний для читання формат.
 */
private fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) - TimeUnit.MINUTES.toSeconds(minutes)

    return if (minutes > 0) {
        "$minutes min $seconds sec"
    } else {
        "$seconds sec"
    }
}