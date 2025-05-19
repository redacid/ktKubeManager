import org.jetbrains.compose.desktop.application.dsl.TargetFormat

tasks.wrapper {
    gradleVersion = "8.14"
    distributionType = Wrapper.DistributionType.BIN
}

plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
    id("org.jetbrains.compose") version "1.7.3"
    id("co.uzzu.dotenv.gradle") version "2.0.0" //https://github.com/uzzu/dotenv-gradle/
}


fun getGitHash(): String {
    return try {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        process.inputStream.bufferedReader().use { it.readText().trim() }
    } catch (e: Exception) {
        "unknown"
    }
}

val buildNumber = getGitHash()
version = env.RELEASE_VERSION.value ?: "0.0.0"
val buildVersion = "$version-$buildNumber"
group = "ua.in.ios.kubemanager"

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
    implementation("com.sebastianneubauer.jsontree:jsontree:2.4.1")
    implementation(compose.desktop.currentOs)
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
    implementation("software.amazon.awssdk:http-auth-aws-crt:${awssdkVersion}")
    implementation("software.amazon.awssdk:aws-query-protocol:${awssdkVersion}")
    testImplementation(kotlin("test"))
}

tasks.register("uploadDebToGithub") {
    group = "publishing"
    dependsOn("packageReleaseDeb")
    doLast {
        println("Upload DEB package...")
        providers.exec {
            workingDir = projectDir
            environment("GH_TOKEN", env.GITHUB_TOKEN.value)
            commandLine("gh", "release", "upload", project.version.toString(),
                "./build/compose/binaries/main-release/deb/kubemanager_${project.version}-1_amd64.deb",
                "--repo", env.GIT_REPO.value,
                "--clobber")
        }.standardOutput
    }
}
tasks.register("uploadRpmToGithub") {
    group = "publishing"
    dependsOn("packageReleaseRpm")
    doLast {
        println("Upload RPM package...")
        providers.exec {
            workingDir = projectDir
            environment("GH_TOKEN", env.GITHUB_TOKEN.value)
            commandLine("gh", "release", "upload", project.version.toString(),
                "./build/compose/binaries/main-release/rpm/kubemanager-${project.version}-1.x86_64.rpm",
                "--repo", env.GIT_REPO.value,
                "--clobber")
        }
    }
}
tasks.register("createGithubRelease") {
    group = "publishing"
    doLast {
        println("Create GitHub release...")

        providers.exec {
            workingDir = projectDir
            environment("GH_TOKEN", env.GITHUB_TOKEN.value)
            commandLine("gh", "release", "delete", project.version.toString(),
                "--cleanup-tag", "-y",
                "--repo", env.GIT_REPO.value)
            isIgnoreExitValue = true
        }

        providers.exec {
            workingDir = projectDir
            commandLine("git", "tag", "-d", project.version.toString())
            isIgnoreExitValue = true
        }

        providers.exec {
            workingDir = projectDir
            environment("GH_TOKEN", env.GITHUB_TOKEN.value)
            commandLine("gh", "release", "create", project.version.toString(),
                "--generate-notes",
                "--notes", project.version.toString(),
                "--repo", env.GIT_REPO.value)
        }
    }
}
tasks.register("uploadToGithubRelease") {
    group = "publishing"
    description = "Upload built artifacts into release on GitHub"
    dependsOn("createGithubRelease", "uploadDebToGithub", "uploadRpmToGithub")
}

compose.desktop {
    application {
        mainClass = "MainKt"

        jvmArgs += listOf(
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens=java.base/java.text=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED"
        )

        buildTypes.release.proguard {
            isEnabled.set(false)
            //configurationFiles.from("proguard-rules.pro")
            jvmArgs += "-DbuildVersion=$buildVersion"

        }
        nativeDistributions {
            jvmArgs += listOf(
                "--add-modules=java.naming",
                "-DbuildVersion=$buildVersion"
            )
            modules(
                "java.naming",
                "java.security.jgss",
                "java.security.sasl",
                "jdk.naming.dns",
                "java.management",
                "java.net.http"
            )

            //includeAllModules = true

            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "kubemanager"
            packageVersion = version.toString()
            description = "Kubernetes Manager"

            macOS {
                iconFile.set(project.file("kubernetes_manager_icon.icns"))
                bundleID = "ua.in.ios.kubemanager"
//                signing {
//                    sign.set(true)
//                    identity.set("<NAME>")
//                    keychain.set("/Users/user/Library/Keychains/login.keychain-db")
//                }
//                notarization {
//                    appleID.set("<EMAIL>")
//                    password.set("<PASSWORD>")
//                }
            }
            windows {
                iconFile.set(project.file("kubernetes_manager_icon.ico"))
                exePackageVersion = packageVersion
                msiPackageVersion = packageVersion
                vendor = "Serhii Rudenko sr@ios.in.ua"
                copyright = "Copyright © 2025 Serhii Rudenko. All rights reserved."
                menuGroup = "Development;System;Network"
                shortcut = true
                upgradeUuid = "3181D6E5-84E3-4F1F-9153-531F1534859B"
                perUserInstall = true
                console = true
                dirChooser = true
                menu = true
                description = "Kubernetes Manager"
            }
            linux {
                iconFile.set(project.file("kubernetes_manager_icon.png"))
                menuGroup = "Development;System;Network"
                shortcut = true
                debMaintainer = "Serhii Rudenko <sr@ios.in.ua>"
                appCategory = "System"
                appRelease = "1"
                debPackageVersion = packageVersion
                rpmPackageVersion = packageVersion
                appCategory = "Development/Tools"
            }

        }
    }
}