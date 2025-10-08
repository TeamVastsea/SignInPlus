plugins {
    kotlin("jvm") version "1.9.23"
    id("com.gradleup.shadow") version "9.2.2"
}

group = "cc.vastsea"
version = "1.0.1"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.5")
    implementation(kotlin("stdlib"))
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("org.bstats:bstats-bukkit:3.0.2")
}

kotlin {
    jvmToolchain(17)
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand(mapOf("version" to project.version))
        }
    }
    shadowJar {
        relocate("org.bstats", "cc.vastsea.bstats")
        archiveClassifier.set("")
    }
    build {
        dependsOn(shadowJar)
    }
}