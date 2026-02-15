import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.2.2"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "cc.vastsea"
version = "1.3.5"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.5")
    compileOnly(kotlin("stdlib"))
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("org.bstats:bstats-bukkit:3.0.2")

    // Database dependencies
    implementation("org.jetbrains.exposed:exposed-core:0.58.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.58.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.58.0")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("com.mysql:mysql-connector-j:9.2.0")
    implementation("com.zaxxer:HikariCP:6.2.1")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand(mapOf("version" to project.version))
        }
    }

    shadowJar {
        dependsOn("processResources")
        doFirst {
            val pluginFile = layout.buildDirectory.file("resources/main/plugin.yml").get().asFile
            if (pluginFile.exists()) {
                val lines = pluginFile.readText().lines()
                val trimmed = if (lines.size > 9) lines.dropLast(10) else emptyList()
                pluginFile.writeText(trimmed.joinToString("\n").trimEnd() + "\n")
            }
        }
    }

    runServer {
        minecraftVersion("1.21")
    }
}
