// build.gradle.kts (Без Fabric8 KubeConfig Helper)
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.0" // Або 1.9.23
    id("org.jetbrains.compose") version "1.6.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
}

group = "com.example.kubemanager"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(compose.desktop.currentOs)

    // --- Fabric8 Kubernetes Client (Без хелперів) ---
    val fabric8Version = "6.13.5" // Ваша версія
    // Явно додаємо API, бо Config і NamedContext там
    implementation("io.fabric8:kubernetes-client-api:${fabric8Version}")
    implementation("io.fabric8:kubernetes-client:${fabric8Version}")
    // --- io.fabric8:kubernetes-client-kubeconfig ВИДАЛЕНО ---
    // --- io.fabric8:kubernetes-client-okhttp-helper ВИДАЛЕНО ---
    // ----------------------------------------------------

    implementation("ch.qos.logback:logback-classic:1.4.14")
    // Повертаємо корутини, бо будемо їх використовувати для autoConfigure
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "KotlinKubeManager"
            packageVersion = "1.0.0"
        }
    }
}