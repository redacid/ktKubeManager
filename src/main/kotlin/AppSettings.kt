import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Serializable
data class ClusterConfig(
    val alias: String,
    val profileName: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val region: String,
    val clusterName: String,
    val endpoint: String = "",
    val certificateAuthority: String = "",
    val token: String = "",
    val roleArn: String? = null
)

@Serializable
data class AwsProfile(
    val profileName: String,
    val accessKeyId: String,
    val secretAccessKey: String
)


@Serializable
data class AppSettings(
    val theme: String = "system",
    val windowSize: WindowSize = WindowSize(800, 600),
    val lastCluster: String = "",
    val awsProfiles: List<AwsProfile> = emptyList(),
    val clusters: List<ClusterConfig> = emptyList()
)


@Serializable
data class WindowSize(
    val width: Int,
    val height: Int
)

class SettingsManager {
    private val configDir = File(System.getProperty("user.home"), ".kubemanager")
    private val configFile = File(configDir, "settings.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    var settings by mutableStateOf(loadSettings())
        private set

    init {
        configDir.mkdirs()
    }

    private fun loadSettings(): AppSettings {
        return try {
            if (configFile.exists()) {
                json.decodeFromString<AppSettings>(configFile.readText())
            } else {
                AppSettings()
            }
        } catch (e: Exception) {
            println("Помилка завантаження налаштувань: ${e.message}")
            AppSettings()
        }
    }

    fun updateSettings(update: AppSettings.() -> AppSettings) {
        try {
            settings = settings.update()
            saveSettings()
        } catch (e: Exception) {
            println("Помилка оновлення налаштувань: ${e.message}")
        }
    }

    private fun saveSettings() {
        try {
            configFile.writeText(json.encodeToString(AppSettings.serializer(), settings))
        } catch (e: Exception) {
            println("Помилка збереження налаштувань: ${e.message}")
        }
    }
}
