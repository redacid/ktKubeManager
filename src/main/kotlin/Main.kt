import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import ua.`in`.ios.theme1.AppTheme
import kotlin.collections.firstOrNull

fun main() = application {
    // Створюємо іконку з Base64-даних для вікна
    val iconPainter = IconsBase64.getIcon(32)?.let {
        BitmapPainter(it.toComposeImageBitmap())
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kube Manager",
        icon = iconPainter
    ) {
        AppTheme {
            LaunchedEffect(Unit) {
                val awtWindow = java.awt.Window.getWindows().firstOrNull()
                awtWindow?.let { IconsBase64.setWindowIcon(it) }
            }
            App()
        }
    }
}
