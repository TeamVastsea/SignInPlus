import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.2.2"
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

tasks {
    processResources {
        inputs.property("pluginVersion", pluginVersion)
        filesMatching("plugin.yml") {
            expand(mapOf("version" to pluginVersion))
        }
    }

    shadowJar {
        archiveClassifier.set(null as String?)
        relocate("dev.triumphteam.gui", "cc.vastsea.signinplus.lib.gui")
        relocate("com.google.gson", "cc.vastsea.signinplus.lib.gson")

        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }

    runServer {
        minecraftVersion("1.21.11")
    }

    test {
        useJUnitPlatform()
    }
}
