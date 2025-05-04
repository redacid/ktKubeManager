import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.system.exitProcess
import java.util.prefs.Preferences
import ua.`in`.ios.theme1.*

// Створюємо клас для пункту меню
data class MenuItem(
    val text: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)
fun exitApplication() {
    try {
        // Save application state if needed
        saveApplicationState()
        // Close all active connections
        closeConnections()
        // Cleanup resources
        cleanup()
        // Exit the application
        exitProcess(0)
    } catch (e: Exception) {
        println("Error during application exit: ${e.message}")
        // Force exit if cleanup failed
        exitProcess(1)
    }
}
private fun saveApplicationState() {
    try {
        val prefs = Preferences.userRoot().node("ua.in.ios.kubemanager")
        // Save your application settings here
        // For example:
        prefs.put("lastCluster", "currentCluster")
        prefs.put("windowSize", "800,500")
        prefs.flush()
    } catch (e: Exception) {
        println("Failed to save application state: ${e.message}")
    }
}
private fun closeConnections() {
    try {
        // Close any active kubernetes connections
        // For example:
        // kubernetesClient?.close()
    } catch (e: Exception) {
        println("Failed to close connections: ${e.message}")
    }
}
private fun cleanup() {
    try {
        // Clean up any temporary files or resources
        // For example:
        // tempDir.deleteRecursively()
    } catch (e: Exception) {
        println("Failed to cleanup resources: ${e.message}")
    }
}
@Composable
@Preview
fun MainMenu() {
    var showMenu by remember { mutableStateOf(false) }
    val isDarkTheme = useTheme()

    MenuBar {
        Menu(
            text = "File",
            onClick = { showMenu = !showMenu }
        ) {
            DropdownMenuItem(
                text = { Text("Connect to cluster") },
                onClick = { /* Add connection logic */ },
                leadingIcon = { Icon(Icons.Default.Add, "Connect") }
            )
            DropdownMenuItem(
                text = { Text("Disconnect") },
                onClick = { /* Add disconnection logic */ },
                leadingIcon = { Icon(Icons.Default.Close, "Disconnect") }
            )
            HorizontalDivider()

            var showExitDialog by remember { mutableStateOf(false) }

            if (showExitDialog) {
                ConfirmExitDialog(
                    onConfirm = { exitApplication() },
                    onDismiss = { showExitDialog = false }
                )
            }

            DropdownMenuItem(
                text = { Text("Exit") },
                onClick = { showExitDialog = true },
                leadingIcon = { Icon(Icons.Default.Close, "Exit") }
            )
        }

        Menu(
            text = "View",
            onClick = { showMenu = !showMenu }
        ) {
            DropdownMenuItem(
                text = { Text("Refresh") },
                onClick = { /* Add refresh logic */ },
                leadingIcon = { Icon(Icons.Default.Refresh, "Refresh") }
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = { /* Add settings logic */ },
                leadingIcon = { Icon(Icons.Default.Settings, "Settings") }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(if (isDarkTheme.value) "Світла тема" else "Темна тема") },
                onClick = { isDarkTheme.value = !isDarkTheme.value },
                leadingIcon = {
                    Icon(
                        if (isDarkTheme.value) ICON_LIGHT_THEME else ICON_DARK_THEME,
                        if (isDarkTheme.value) "Світла тема" else "Темна тема"
                    )
                }
            )

        }

        Menu(
            text = "Help",
            onClick = { showMenu = !showMenu }
        ) {
            DropdownMenuItem(
                text = { Text("Documentation") },
                onClick = { /* Open documentation */ },
                leadingIcon = { Icon(Icons.Default.Info, "Documentation") }
            )
            DropdownMenuItem(
                text = { Text("About") },
                onClick = { /* Show about info */ },
                leadingIcon = { Icon(Icons.Default.Info, "About") }
            )
        }
    }
}
@Composable
fun MenuBar(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth() ,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
        ) {
            content()
        }
    }
}
@Composable
fun Menu(
    text: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.wrapContentSize(align = Alignment.TopStart)
    ) {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(0.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            content()
        }
    }
}
@Composable
fun ConfirmExitDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmation") },
        text = { Text("Are you sure you want to exit?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Yes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("No")
            }
        }
    )
}
