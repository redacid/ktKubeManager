import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import ua.`in`.ios.theme1.*
import kotlin.collections.firstOrNull

fun main() = application {
    val settingsManager = remember { SettingsManager() }
    val savedSettings = remember { settingsManager.settings }

    val windowState = remember {
        WindowState(
            width = savedSettings.windowSize.width.dp,
            height = savedSettings.windowSize.height.dp
        )
    }

    // Встановлюємо початкову тему
    ThemeManager.setDarkTheme(when (savedSettings.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    })

    val iconPainter = IconsBase64.getIcon(32)?.let {
        BitmapPainter(it.toComposeImageBitmap())
    }

    Window(
        onCloseRequest = { exitApplication(windowState, settingsManager) },
        title = "Kube Manager",
        state = windowState,
        icon = iconPainter
    ) {
        AppTheme {
            LaunchedEffect(Unit) {
                val awtWindow = java.awt.Window.getWindows().firstOrNull()
                awtWindow?.let { IconsBase64.setWindowIcon(it) }
            }

            App(
                windowState = windowState,
                settingsManager = settingsManager
            )
        }
    }
}

