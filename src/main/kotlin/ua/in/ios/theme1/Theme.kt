package ua.`in`.ios.theme1

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicMaterialThemeState

private val LocalThemeState = compositionLocalOf { mutableStateOf(false) }

object ThemeManager {
    val themeState = mutableStateOf(false)

    fun toggleTheme() {
        themeState.value = !themeState.value
    }

    fun isDarkTheme(): Boolean = themeState.value

    fun setDarkTheme(isDark: Boolean) {
        themeState.value = isDark
    }
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    LaunchedEffect(darkTheme) {
        if (!ThemeManager.isDarkTheme() && darkTheme) {
            ThemeManager.setDarkTheme(true)
        }
    }

    // Використовуємо ThemeManager замість локального стану
    val isDarkTheme = remember { ThemeManager.themeState }.value

    val dynamicThemeState = rememberDynamicMaterialThemeState(
        isDark = isDarkTheme,
        style = PaletteStyle.TonalSpot,
        seedColor = SeedColor,
    )

    DynamicMaterialTheme(
        state = dynamicThemeState,
        animate = true
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        ) {
            content()
        }
    }
}

@Composable
fun useTheme(): MutableState<Boolean> {
    return remember { ThemeManager.themeState }
}


