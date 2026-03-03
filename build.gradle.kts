import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.2.2"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "cc.vastsea"
version = "1.5.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.triumphteam.dev/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.5")
    compileOnly(kotlin("stdlib"))

    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation("dev.triumphteam:triumph-gui:3.1.10")

    compileOnly("org.xerial:sqlite-jdbc:3.46.0.0")
    compileOnly("org.jetbrains.exposed:exposed-core:0.58.0")
    compileOnly("org.jetbrains.exposed:exposed-jdbc:0.58.0")
    compileOnly("org.jetbrains.exposed:exposed-java-time:0.58.0")
    compileOnly("org.postgresql:postgresql:42.7.5")
    compileOnly("com.mysql:mysql-connector-j:9.2.0")
    compileOnly("com.zaxxer:HikariCP:6.2.1")
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
        relocate("dev.triumphteam.gui", "cc.vastsea.signinplus.lib.gui")
        relocate("org.bstats", "cc.vastsea.signinplus.lib.bstats")

        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }

    runServer {
        minecraftVersion("1.21")
    }
}
