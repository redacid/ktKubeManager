import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import ua.`in`.ios.theme.*
import kotlin.system.exitProcess

// Створюємо клас для пункту меню
//data class MenuItem(
//    val text: String,
//    val icon: ImageVector,
//    val onClick: () -> Unit
//)

fun exitApplication(windowState: WindowState, settingsManager: SettingsManager
) {
    try {
        // Save application state if needed
        saveApplicationState(windowState, settingsManager
        )
        // Close all active connections
        closeConnections()
        // Cleanup resources
        cleanup()
        // Exit the application
        exitProcess(0)
    } catch (e: Exception) {
        logger.error("Error during application exit: ${e.message}")
        // Force exit if cleanup failed
        exitProcess(1)
    }
}
private fun saveApplicationState(windowState: WindowState, settingsManager: SettingsManager
) {
    settingsManager.updateSettings {
        copy(
            theme = if (ThemeManager.isDarkTheme()) "dark" else "light",
            lastCluster = "currentCluster",
            windowSize = WindowSize(
                width = windowState.size.width.value.toInt(),
                height = windowState.size.height.value.toInt()


        )
        )
    }
}

private fun closeConnections() {
    try {
        // TODO
        activeClient?.close()
        portForwardService.stopAllPortForwards()
    } catch (e: Exception) {
        logger.error("Failed to close connections: ${e.message}")
    }
}
private fun cleanup() {
    try {
        // TODO
        // Clean up any temporary files or resources
        // For example:
        // tempDir.deleteRecursively()
    } catch (e: Exception) {
        logger.error("Failed to cleanup resources: ${e.message}")
    }
}

@Composable
@Preview
fun MainMenu(windowState: WindowState, settingsManager: SettingsManager
) {
    var showAddClusterDialog by remember { mutableStateOf(false) }
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var showEditProfilesDialog by remember { mutableStateOf(false) }
    var showEditClustersDialog by remember { mutableStateOf(false) }
    var showPortForwardWindow by remember { mutableStateOf(false) }


    if (showEditClustersDialog) {
        EditClustersDialog(
            onDismiss = { showEditClustersDialog = false },
            settingsManager = settingsManager
        )
    }

    if (showAddClusterDialog) {
        ClusterAddDialog(
            onDismiss = { showAddClusterDialog = false },
            settingsManager = settingsManager
        )
    }

    if (showAddProfileDialog) {
        AwsProfileAddDialog(
            onDismiss = { showAddProfileDialog = false },
            settingsManager = settingsManager
        )
    }

    if (showEditProfilesDialog) {
        AwsProfilesEditDialog(
            onDismiss = { showEditProfilesDialog = false },
            settingsManager = settingsManager
        )
    }
    if (showPortForwardWindow) {
        PortForwardWindow(
            onClose = { showPortForwardWindow = false }
        )
    }

    MenuBar {
        Menu(
            text = "File",
        ) { closeMenu ->
        DropdownMenuItem(
                text = { Text("Add AWS Profile") },
                onClick = {
                    showAddProfileDialog = true
                    closeMenu()
                },
                leadingIcon = { Icon(ICON_ADD_USER, "Add Profile") }
            )
            DropdownMenuItem(
                text = { Text("Edit AWS Profiles") },  // Новий пункт меню
                onClick = {
                    showEditProfilesDialog = true
                    closeMenu()
                },
                leadingIcon = { Icon(ICON_EDIT, "Edit Profiles") }
            )

            DropdownMenuItem(
                text = { Text("Add cluster connection") },
                onClick = {
                    showAddClusterDialog = true
                    closeMenu()
                          },
                leadingIcon = { Icon(ICON_ADD, "Connect") }
            )
            DropdownMenuItem(
                text = { Text("Edit cluster connections") },
                onClick = {
                    showEditClustersDialog = true
                    closeMenu()
                          },
                leadingIcon = { Icon(ICON_EDIT, "Edit") }
            )

            HorizontalDivider()

            var showExitDialog by remember { mutableStateOf(false) }

            if (showExitDialog) {
                ConfirmExitDialog(
                    onConfirm = { exitApplication(windowState, settingsManager) },
                    onDismiss = { showExitDialog = false }
                )
            }

            DropdownMenuItem(
                text = { Text("Exit") },
                onClick = {
                    showExitDialog = true
                    closeMenu()
                          },
                leadingIcon = { Icon(ICON_CLOSE, "Exit") }
            )
        }
        Menu(
            text = "Settings",
        ) { closeMenu ->
        DropdownMenuItem(
                text = { Text("Port Forwards") },
                onClick = {
                    showPortForwardWindow = true
                    closeMenu()
                          },
                leadingIcon = { Icon(ICON_SERVER, "Port Forwards") }
            )
        }

        Menu(
            text = "View",
        ) { closeMenu ->
        DropdownMenuItem(
                text = { Text(if (ThemeManager.isDarkTheme()) "Light Theme" else "Dark Theme") },
                onClick = {
                    ThemeManager.toggleTheme()
                    closeMenu()
                          },

                leadingIcon = {
                    Icon(
                        if (ThemeManager.isDarkTheme()) ICON_LIGHT_THEME else ICON_DARK_THEME,
                        if (ThemeManager.isDarkTheme()) "Light Theme" else "Dark Theme"
                    )
                }
            )
        }

        Menu(
            text = "Help",
        ) {
            DropdownMenuItem(
                text = { Text("Documentation") },
                onClick = { TODO() /* Open documentation */ },
                leadingIcon = { Icon(ICON_HELP, "Documentation") }
            )
            DropdownMenuItem(
                text = { Text("About") },
                onClick = { TODO() /* Show about info */ },
                leadingIcon = { Icon(ICON_INFO, "About") }
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
    content: @Composable (closeMenu: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val closeMenu = { expanded = false }

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
            content(closeMenu)
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
