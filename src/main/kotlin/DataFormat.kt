import io.fabric8.kubernetes.api.model.ContainerStatus
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.ServicePort
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus
import io.fabric8.kubernetes.api.model.networking.v1.IngressLoadBalancerIngress
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS
import java.time.Duration
import java.time.OffsetDateTime



//fun formatPodContainers(statuses: List<ContainerStatus>?): String {
//    val total = statuses?.size ?: 0
//    val ready = statuses?.count { it.ready == true } ?: 0;
//    return "$ready/$total"
//}

fun formatPodContainers(statuses: List<ContainerStatus>?): String {
    val total = statuses?.size ?: 0
    val ready = statuses?.count { it.ready == true } ?: 0

    val readySquares = "■".repeat(ready)
    val notReadySquares = "□".repeat(total - ready)

    return "$readySquares$notReadySquares"
}


fun formatPodRestarts(statuses: List<ContainerStatus>?): String {
    return statuses?.sumOf { it.restartCount ?: 0 }?.toString() ?: "0"
}

fun formatPorts(ports: List<ServicePort>?): String {
    if (ports.isNullOrEmpty()) return "<none>"; return ports.joinToString(", ") { p -> "${p.port}${p.nodePort?.let { ":$it" } ?: ""}/${p.protocol ?: "TCP"}${p.name?.let { "($it)" } ?: ""}" }
}

fun formatServiceExternalIP(service: Service?): String {
    if (service == null) return "<none>"
    val ips = mutableListOf<String>()
    when (service.spec?.type) {
        "LoadBalancer" -> {
            service.status?.loadBalancer?.ingress?.forEach { ingress ->
                ingress.ip?.let { ips.add(it) }; ingress.hostname?.let {
                ips.add(
                    it
                )
            }
            }
        }

        "NodePort", "ClusterIP" -> {
            service.spec?.clusterIPs?.let { ips.addAll(it.filterNotNull()) }
        }

        "ExternalName" -> {
            return service.spec?.externalName ?: "<none>"
        }
    }
    return if (ips.isEmpty() || (ips.size == 1 && ips[0].isBlank())) "<none>" else ips.joinToString(",")
}

fun formatIngressHosts(rules: List<IngressRule>?): String {
    val hosts = rules?.mapNotNull { it.host }?.distinct()
        ?: emptyList(); return if (hosts.isEmpty()) "*" else hosts.joinToString(",")
}

fun formatIngressAddress(ingresses: List<IngressLoadBalancerIngress>?): String {
    val addresses = mutableListOf<String>(); ingresses?.forEach { ingress ->
        ingress.ip?.let { addresses.add(it) }; ingress.hostname?.let {
        addresses.add(
            it
        )
    }
    }; return if (addresses.isEmpty()) "<none>" else addresses.joinToString(",")
}

fun formatIngressPorts(tls: List<IngressTLS>?): String {
    return if (tls.isNullOrEmpty()) "80" else "80, 443"
}

fun formatAccessModes(modes: List<String>?): String {
    return modes?.joinToString(",") ?: "<none>"
}

fun formatJobDuration(status: JobStatus?): String {
    val start = status?.startTime?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
    val end = status?.completionTime?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
    return when {
        start == null -> "<pending>"
        end == null -> Duration.between(
            start, 
            OffsetDateTime.now(start.offset)
        ).seconds.toString() + "s (running)"; else -> Duration.between(start, end).seconds.toString() + "s"
    }
}

// Форматування тривалості для відображення
fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

fun formatPolicyTypes(policyTypes: List<String>?): String {
    if (policyTypes.isNullOrEmpty()) return "Ingress"
    return policyTypes.joinToString(", ")
}

fun formatAge(creationTimestamp: String?): String {
    if (creationTimestamp.isNullOrBlank()) return "N/A"
    try {
        val creationTime = OffsetDateTime.parse(creationTimestamp)
        val now = OffsetDateTime.now(creationTime.offset)
        val duration = Duration.between(creationTime, now)
        return "${duration.seconds}s"
//        return when {
//            duration.toDays() > 0 -> "${duration.toDays()}d"
//            duration.toHours() > 0 -> "${duration.toHours()}h"
//            duration.toMinutes() > 5 -> "${duration.toMinutes()}m"
//            else -> "${duration.seconds}s"
//        }
    } catch (e: Exception) {
        logger.warn("Failed to format timestamp '$creationTimestamp': ${e.message}"); return "Invalid"
    }
}

fun formatContextNameForDisplay(context: ClusterContext): String {
    val eksArnPattern = "arn:aws:eks:[a-z0-9-]+:([0-9]+):cluster/([a-zA-Z0-9-]+)".toRegex()

    return when (context.source) {
        "saved" -> context.name // Для збережених кластерів показуємо ім'я як є
        "kubeconfig" -> {
            val matchResult = eksArnPattern.find(context.name)
            if (matchResult != null) {
                // Групи: 1 - account ID, 2 - cluster name
                val accountId = matchResult.groupValues[1]
                val clusterName = matchResult.groupValues[2]
                "$accountId:$clusterName"
            } else {
                context.name
            }

            //context.name.split("/").last()
        }
        else -> context.name
    }
}


//fun formatDataKeys(data: Map<String, String>?, stringData: Map<String, String>?): String {
//    return (data?.size ?: 0).plus(stringData?.size ?: 0).toString()
//}