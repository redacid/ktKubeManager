import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.ExecWatch
import io.fabric8.kubernetes.client.dsl.PodResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import kotlin.random.Random

class PortForwardService {

    private val activePortForwards = mutableMapOf<String, PortForwardSession>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Розпочинає сесію port-forward між локальним портом та портом поду в Kubernetes.
     * Прив'язується до IPv4 (0.0.0.0) для забезпечення доступності як з IPv4, так і з IPv6.
     *
     * @param client Kubernetes клієнт
     * @param namespace Namespace поду
     * @param podName Ім'я поду
     * @param localPort Локальний порт (якщо 0, буде вибрано випадковий порт)
     * @param podPort Порт поду
     * @param bindAddress Адреса, до якої прив'язується port-forward (за замовчуванням 0.0.0.0)
     * @return Об'єкт PortForwardSession, який містить інформацію про сесію та дозволяє її закрити
     */
    fun startPortForward(
        client: KubernetesClient,
        namespace: String,
        podName: String,
        localPort: Int = 0, // 0 означає вибір випадкового доступного порту
        podPort: Int,
        bindAddress: String = "0.0.0.0" // За замовчуванням прив'язується до всіх інтерфейсів
    ): PortForwardSession {
        // Визначаємо локальний порт (випадковий, якщо передано 0)
        val actualLocalPort = if (localPort == 0) findRandomAvailablePort(bindAddress) else localPort

        // Створюємо унікальний ID для цієї сесії
        val sessionId = "${namespace}_${podName}_${actualLocalPort}_${podPort}_${System.currentTimeMillis()}"

        // Отримуємо podResource для подальшого використання
        val podResource = client.pods()
            .inNamespace(namespace)
            .withName(podName)

        // Ініціюємо port-forward через Fabric8 із явним зазначенням адреси прив'язки
        val portForward = createPortForward(podResource, actualLocalPort, podPort, bindAddress)

        // Створюємо об'єкт сесії
        val session = PortForwardSession(
            id = sessionId,
            namespace = namespace,
            podName = podName,
            localPort = actualLocalPort,
            podPort = podPort,
            bindAddress = bindAddress,
            portForward = portForward
        )

        // Зберігаємо активну сесію
        activePortForwards[sessionId] = session

        return session
    }

    /**
     * Створює port-forward з явною прив'язкою до вказаної адреси.
     * Fabric8 не надає прямого методу для вказання адреси прив'язки, тому
     * ми використовуємо нижчий рівень API для створення порт-форварду.
     */
    private fun createPortForward(
        podResource: PodResource,
        localPort: Int,
        podPort: Int,
        bindAddress: String
    ): Closeable {
        // Використання внутрішнього API Fabric8 для явного вказання адреси прив'язки
        val inetAddress = InetAddress.getByName(bindAddress)
        // Викликаємо portForward з підтримуваними параметрами
        return podResource.portForward(podPort, inetAddress, localPort)
    }

    /**
     * Припиняє сесію port-forward за її ID.
     *
     * @param sessionId ID сесії
     * @return true якщо сесія була успішно закрита, false якщо сесія не знайдена
     */
    fun stopPortForward(sessionId: String): Boolean {
        val session = activePortForwards[sessionId] ?: return false
        session.close()
        activePortForwards.remove(sessionId)
        return true
    }

    /**
     * Припиняє всі активні сесії port-forward.
     */
    fun stopAllPortForwards() {
        activePortForwards.values.forEach { it.close() }
        activePortForwards.clear()
    }

    /**
     * Повертає список всіх активних сесій port-forward.
     */
    fun getActivePortForwards(): List<PortForwardSession> {
        return activePortForwards.values.toList()
    }

    /**
     * Знаходить випадковий доступний порт на вказаній адресі.
     *
     * @param bindAddress Адреса для перевірки доступності порту
     * @return Випадковий доступний порт
     */
    private fun findRandomAvailablePort(bindAddress: String): Int {
        // Спершу пробуємо випадковий порт в діапазоні від 10000 до 65000
        val portRange = (10000..65000)

        for (attempt in 1..10) {
            val randomPort = Random.nextInt(portRange.first, portRange.last)
            if (isPortAvailable(randomPort, bindAddress)) {
                return randomPort
            }
        }

        // Якщо не вдалося знайти випадковий порт, створюємо сокет і
        // дозволяємо системі призначити доступний порт
        return ServerSocket().use { socket ->
            socket.bind(InetSocketAddress(bindAddress, 0))
            socket.localPort
        }
    }

    /**
     * Перевіряє, чи доступний порт на вказаній адресі.
     *
     * @param port Порт для перевірки
     * @param bindAddress Адреса для перевірки
     * @return true, якщо порт доступний, false - інакше
     */
    private fun isPortAvailable(port: Int, bindAddress: String): Boolean {
        return try {
            ServerSocket().use { socket ->
                socket.bind(InetSocketAddress(bindAddress, port))
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Клас, що представляє активну сесію port-forward.
 */
class PortForwardSession(
    val id: String,
    val namespace: String,
    val podName: String,
    val localPort: Int,
    val podPort: Int,
    val bindAddress: String,
    private val portForward: Closeable
) : Closeable {

    // Визначаємо декілька URL для доступу
    val ipv4Url: String = if (bindAddress == "0.0.0.0" || bindAddress == "127.0.0.1") "127.0.0.1:$localPort" else "$bindAddress:$localPort"
    val ipv6Url: String = if (bindAddress == "0.0.0.0" || bindAddress == "::1") "[::1]:$localPort" else "[$bindAddress]:$localPort"
    val localUrl: String = "localhost:$localPort"

    // URL для відображення користувачу (використаємо localhost як найбільш універсальний)
    val url: String = localUrl

    // Можемо повертати всі варіанти URL
    val allUrls: List<String> = listOfNotNull(
        localUrl,
        if (ipv4Url != localUrl) ipv4Url else null,
        if (ipv6Url != localUrl) ipv6Url else null
    )

    val startTime: Long = System.currentTimeMillis()

    /**
     * Закриває сесію port-forward.
     */
    override fun close() {
        try {
            portForward.close()
        } catch (e: Exception) {
            // Обробка помилок при закритті
            println("Помилка при закритті port-forward сесії: ${e.message}")
        }
    }

    /**
     * Повертає тривалість сесії в мілісекундах.
     */
    fun getDuration(): Long {
        return System.currentTimeMillis() - startTime
    }

    /**
     * Повертає рядкове представлення сесії.
     */
    override fun toString(): String {
        return "PortForwardSession(namespace='$namespace', pod='$podName', local=$localPort, remote=$podPort, url='$url')"
    }
}