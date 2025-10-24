plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.2.2"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "cc.vastsea"
version = "1.2.3"

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
        filesMatching("paper-plugin.yml") {
            expand(mapOf("version" to project.version))
        }
    }
    shadowJar {
        relocate("org.bstats", "cc.vastsea.bstats")
        archiveClassifier.set("")
        // Exclude Kotlin runtime and SLF4J (provided by server/Kotlin plugin)
        dependencies {
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-reflect"))
            exclude(dependency("org.slf4j:slf4j-api"))
        }
        // Full build keeps all drivers for compatibility
    }
    // Lightweight jar: exclude heavy JDBC drivers, keep SQLite only
    register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJarLite") {
        relocate("org.bstats", "cc.vastsea.bstats")
        archiveClassifier.set("lite")
        from(sourceSets.main.get().output)
        configurations = listOf(project.configurations.runtimeClasspath.get())
        dependencies {
            exclude(dependency("org.postgresql:postgresql"))
            exclude(dependency("com.mysql:mysql-connector-j"))
            // Exclude Kotlin runtime and SLF4J
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-reflect"))
            exclude(dependency("org.slf4j:slf4j-api"))
        }
    }
    // Paper-only jar: offload heavy libs via paper-plugin.yml libraries
    register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJarPaper") {
        relocate("org.bstats", "cc.vastsea.bstats")
        archiveClassifier.set("paper")
        from(sourceSets.main.get().output)
        configurations = listOf(project.configurations.runtimeClasspath.get())
        dependencies {
            // Exclude libs that Paper will fetch at runtime
            exclude(dependency("org.xerial:sqlite-jdbc"))
            exclude(dependency("com.zaxxer:HikariCP"))
            exclude(dependency("org.jetbrains.exposed:exposed-core"))
            exclude(dependency("org.jetbrains.exposed:exposed-jdbc"))
            exclude(dependency("org.jetbrains.exposed:exposed-java-time"))
            // Keep drivers optional: will be provided by Paper when needed
            exclude(dependency("org.postgresql:postgresql"))
            exclude(dependency("com.mysql:mysql-connector-j"))
            // Exclude Kotlin runtime and SLF4J
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-reflect"))
            exclude(dependency("org.slf4j:slf4j-api"))
        }
    }
    // No-SQLite jar: exclude SQLite JDBC (for MySQL/PostgreSQL-only deployments)
    register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJarNoSqlite") {
        relocate("org.bstats", "cc.vastsea.bstats")
        archiveClassifier.set("no-sqlite")
        from(sourceSets.main.get().output)
        configurations = listOf(project.configurations.runtimeClasspath.get())
        dependencies {
            exclude(dependency("org.xerial:sqlite-jdbc"))
            // Exclude Kotlin runtime and SLF4J
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-reflect"))
            exclude(dependency("org.slf4j:slf4j-api"))
        }
    }
    build {
        dependsOn(shadowJar)
    }
    runServer {
        minecraftVersion("1.21")
    }
}