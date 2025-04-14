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
    maven("https://mvnrepository.com/artifact/io.kubernetes/client-java")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") // Репозиторій для Compose
    google()
}

// Вказуємо Java Toolchain для узгодженості компіляції
kotlin {
    jvmToolchain(11) // Компілюємо під Java 11
}

dependencies {
    implementation(compose.desktop.currentOs)
    val clientJavaVersion = "20.0.1" // !!! ЗАМІНІТЬ НА АКТУАЛЬНУ СТАБІЛЬНУ ВЕРСІЮ !!!
    implementation("io.kubernetes:client-java:${clientJavaVersion}")
    //implementation("io.kubernetes:client-java-kubeconfig:${clientJavaVersion}")
    //implementation("io.kubernetes:client-java-okhttp-helper:${clientJavaVersion}")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    implementation("org.slf4j:slf4j-api:2.0.17")
    testImplementation(kotlin("test"))
}


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
