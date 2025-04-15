import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder

fun createKubernetesClient(): KubernetesClient {
    val config = Config.autoConfigure(null) // Автоматично завантажує конфігурацію
    return KubernetesClientBuilder().withConfig(config).build()
}

fun main() {
    val client = createKubernetesClient()
    println("Клієнт Kubernetes ініціалізовано: ${client.kubernetesVersion.gitVersion}")
}