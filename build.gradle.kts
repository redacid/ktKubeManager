// build.gradle.kts
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
//import org.jetbrains.kotlin.gradle.tasks.KotlinCompile // Імпорт може бути не обов'язковим

plugins {
    kotlin("jvm") version "2.0.0" // Використовуємо Kotlin 2.0.0
    id("org.jetbrains.compose") version "1.6.10" // Використовуємо Compose, сумісний з Kotlin 2.0.0
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" // Додатковий плагін компілятора Compose для Kotlin 2.0.0+
}

group = "com.example.kubemanager"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") // Репозиторій для Compose
    google()
}

// Вказуємо Java Toolchain для узгодженості компіляції
kotlin {
    jvmToolchain(11) // Компілюємо під Java 11
}

dependencies {
    // Compose Desktop (надається плагіном org.jetbrains.compose)
    implementation(compose.desktop.currentOs)

    // Fabric8 Kubernetes Client (вирішили спробувати цю версію)
    implementation("io.fabric8:kubernetes-client:6.10.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Kotlin Coroutines Core Library (версія, яку вимагає Compose 1.6.x)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Інтеграція Coroutines з Swing/AWT для Dispatchers.Main
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0") // Версія має співпадати з -core

    // Тестова залежність
    testImplementation(kotlin("test"))
}

// Конфігурація Compose Desktop
compose.desktop {
    application {
        mainClass = "MainKt" // Головний клас вашого додатку

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm) // Формати для різних ОС
            packageName = "KotlinKubeManager"
            packageVersion = "1.0.0"
            // Налаштування для іконки, вендора тощо
            // vendor = "YourCompany"
            // description = "Simple Kubernetes Manager"
            // copyright = "© 2025 YourCompany"
        }
    }
}
