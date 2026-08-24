import org.gradle.api.tasks.bundling.Jar
import java.util.zip.ZipFile

plugins {
    kotlin("jvm") version "2.2.20"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "cc.vastsea"
version = "1.7.0"
val pluginVersion = version.toString()

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.triumphteam.dev/repository/maven-public/")
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.5")
    implementation(kotlin("stdlib"))

    implementation("dev.triumphteam:triumph-gui:3.1.10")
    implementation("com.google.code.gson:gson:2.14.0")

    implementation("org.xerial:sqlite-jdbc:3.46.0.0") {
        exclude(group = "org.slf4j")
    }
    implementation("org.jetbrains.exposed:exposed-core:0.58.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.58.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.58.0")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("com.mysql:mysql-connector-j:9.2.0")
    implementation("com.zaxxer:HikariCP:6.2.1")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")
    testImplementation("org.xerial:sqlite-jdbc:3.46.0.0")
    testImplementation("org.jetbrains.exposed:exposed-core:0.58.0")
    testImplementation("org.jetbrains.exposed:exposed-jdbc:0.58.0")
    testImplementation("org.jetbrains.exposed:exposed-java-time:0.58.0")
}

kotlin {
    jvmToolchain(21)
}

val liteJar = tasks.named<Jar>("jar")

val verifyLiteJar by tasks.registering {
    dependsOn(liteJar)
    doLast {
        val artifact = liteJar.get().archiveFile.get().asFile
        val forbiddenPrefixes = listOf(
            "kotlin/",
            "org/jetbrains/exposed/",
            "org/sqlite/",
            "org/postgresql/",
            "com/mysql/",
            "com/zaxxer/hikari/",
            "dev/triumphteam/",
            "com/google/gson/",
        )
        ZipFile(artifact).use { archive ->
            val embeddedLibrary = archive.entries().asSequence()
                .map { it.name }
                .firstOrNull { entry -> forbiddenPrefixes.any(entry::startsWith) }
            check(embeddedLibrary == null) {
                "Lite JAR contains an embedded runtime library: $embeddedLibrary"
            }
        }
        check(artifact.length() < 1_000_000L) {
            "Lite JAR unexpectedly exceeds 1 MB: ${artifact.length()} bytes"
        }
    }
}

tasks {
    processResources {
        inputs.property("pluginVersion", pluginVersion)
        filesMatching("plugin.yml") {
            expand(mapOf("version" to pluginVersion))
        }
    }

    runServer {
        minecraftVersion("1.21.11")
    }

    test {
        useJUnitPlatform()
    }

    check {
        dependsOn(verifyLiteJar)
    }
}
