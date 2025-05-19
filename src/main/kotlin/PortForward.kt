import androidx.compose.foundation.background
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
import io.fabric8.kubernetes.client.KubernetesClient
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
            title = { Text("Налаштування Port Forward") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Налаштування перенаправлення портів для поду: $podName у просторі імен: $namespace"
                    )

                    // Вибір порту контейнера
                    Text("Порт контейнера:", fontWeight = FontWeight.Bold)
                    if (availableContainerPorts.isEmpty()) {
                        Text("Не знайдено доступних портів у контейнері")
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
                    Text("Локальний порт:", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = localPort,
                        onValueChange = {
                            localPort = it
                            localPortError = validateLocalPort(it)
                        },
                        label = { Text("Локальний порт (0 для автоматичного вибору)") },
                        isError = localPortError != null,
                        supportingText = {
                            if (localPortError != null) {
                                Text(localPortError!!)
                            } else {
                                Text("Залиште 0 для автоматичного вибору доступного порту")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Вибір типу адреси прив'язки
                    Text("Тип прив'язки:", fontWeight = FontWeight.Bold)
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
                                Text("Всі інтерфейси (0.0.0.0)")
                                Text(
                                    "Доступно з будь-якого інтерфейсу (IPv4 та IPv6)",
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
                                Text("Тільки IPv4 (127.0.0.1)")
                                Text(
                                    "Доступно тільки з localhost через IPv4",
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
                                Text("Тільки IPv6 (::1)")
                                Text(
                                    "Доступно тільки з localhost через IPv6",
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
                    Text("Створити")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Скасувати")
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
        portStr.isBlank() -> "Порт не може бути порожнім"
        port == null -> "Некоректний формат порту"
        port < 0 -> "Порт не може бути від'ємним"
        port > 0 && port < 1024 -> "Порти 1-1023 потребують прав адміністратора"
        port > 65535 -> "Порт не може бути більше 65535"
        else -> null
    }
}

@Composable
fun PortForwardWindow(
    onClose: () -> Unit
) {
    val dialogState = remember { DialogState(width = 1200.dp, height = 800.dp) }

    DialogWindow(
        onCloseRequest = onClose,
        title = "Port Forwards",
        state = dialogState
    ) {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            // Використовуємо існуючий компонент PortForwardPanel
            PortForwardPanel(
                portForwardService = portForwardService,
                kubernetesClient = activeClient,
                onCreatePortForward = { namespace, podName ->
                    // Цей колбек не використовується в PortForwardPanel, оскільки
                    // створення port forward відбувається через PodActions
                }
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
    kubernetesClient: KubernetesClient?,
    onCreatePortForward: (namespace: String, podName: String) -> Unit
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
            "Активні Port Forward сесії",
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
                    Text("Немає активних сесій Port Forward")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Натисніть кнопку 'Port Forward' у деталях поду, щоб створити нову сесію",
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
                        text = "URL скопійовано: $lastCopiedUrl",
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
                Text("Зупинити всі сесії")
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
                    "Натисніть щоб скопіювати",
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
                    "Локальний порт: ${session.localPort} → Порт поду: ${session.podPort}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Тривалість: $durationText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
            Text("Зупинити")
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
        "$minutes хв $seconds с"
    } else {
        "$seconds с"
    }
}