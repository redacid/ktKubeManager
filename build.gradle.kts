import org.jetbrains.compose.desktop.application.dsl.TargetFormat
//import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm") version "1.9.23" // Або 1.9.23
    id("org.jetbrains.compose") version "1.7.3"
    //id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}

group = "com.example.kubemanager"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://mvnrepository.com")
    google()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    //implementation("androidx.compose.foundation:foundation-android:1.7.8")
    val fabric8Version = "6.13.5" // Ваша версія
    implementation("io.fabric8:kubernetes-client-api:${fabric8Version}")
    implementation("io.fabric8:kubernetes-client:${fabric8Version}")
    implementation(compose.material3)
    //implementation("androidx.compose.material3:material3:1.3.2")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3") //extract helm release
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3") //extract helm release
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    implementation("br.com.devsrsouza.compose.icons:feather:1.1.1") // https://feathericons.com/
    implementation("br.com.devsrsouza.compose.icons:simple-icons:1.1.1") // https://simpleicons.org/

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "KotlinKubeManager"
            packageVersion = "0.0.1"
        }
    }
}