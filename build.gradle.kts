import org.jetbrains.compose.desktop.application.dsl.TargetFormat

tasks.wrapper {
    gradleVersion = "8.14"
    distributionType = Wrapper.DistributionType.BIN
}

tasks.register("printDependencies") {
    doLast {
        configurations
            .filter { it.isCanBeResolved }
            .forEach { config ->
                println("\nConfiguration ${config.name}")
                config.resolvedConfiguration.resolvedArtifacts.forEach {
                    println("${it.moduleVersion.id}:${it.classifier ?: ""}")
                }
            }
    }
}


plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "ua.in.ios.kubemanager"
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
    //implementation("androidx.compose.foundation:foundation-android:1.8.0")
    val fabric8Version = "6.13.5"
    implementation("io.fabric8:kubernetes-client-api:${fabric8Version}")
    implementation("io.fabric8:kubernetes-client:${fabric8Version}")
    implementation(compose.material3)
    implementation("com.materialkolor:material-kolor:2.0.0") //DON`T UP VERSION
    implementation("ch.qos.logback:logback-classic:1.5.18") //1.4.14
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.23") // 1.9.23
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0") //1.9.0
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0") //1.9.0
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") //DON`T UP VERSION
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.3")
    implementation("br.com.devsrsouza.compose.icons:feather:1.1.1") // https://feathericons.com/
    implementation("br.com.devsrsouza.compose.icons:simple-icons:1.1.1") // https://simpleicons.org/
    val awssdkVersion = "2.31.25"
    implementation("software.amazon.awssdk:sts:${awssdkVersion}")
    implementation("software.amazon.awssdk:auth:${awssdkVersion}")
    implementation("software.amazon.awssdk:regions:${awssdkVersion}")
    implementation("software.amazon.awssdk:http-client-spi:${awssdkVersion}")
    implementation("software.amazon.awssdk:sdk-core:${awssdkVersion}")
    implementation("software.amazon.awssdk:http-auth-aws:${awssdkVersion}")
    implementation("software.amazon.awssdk:http-auth-spi:${awssdkVersion}")
    implementation("software.amazon.awssdk:eks:${awssdkVersion}")
    //implementation("software.amazon.awssdk:http-auth-aws-crt:${awssdkVersion}") // DON'T ENABLE IT, CONNECT TO CONTEXT NOT WORKED
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "MainKt"

        buildTypes.release.proguard {
            isEnabled.set(false)
            //configurationFiles.from("proguard-rules.pro")
        }
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "KubeManager"
            packageVersion = "1.0.2"
            macOS {
                iconFile.set(project.file("kubernetes_manager_icon.png"))
            }
            windows {
                iconFile.set(project.file("kubernetes_manager_icon.png"))
            }
            linux {
                iconFile.set(project.file("kubernetes_manager_icon.png"))
                menuGroup = "Development;System;Network"
                shortcut = true
                debMaintainer = "Serhii Rudenko <sr@ios.in.ua>"
                appCategory = "System"
                appRelease = "1"
                debPackageVersion = packageVersion
            }

        }
    }
}