plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.0.0"
}

group = "dev.example.soledom"
version = providers.gradleProperty("pluginVersion").get()

val paperApiVersion = providers.gradleProperty("paperApiVersion").get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper already ships Gson, JOML, Adventure and MiniMessage at runtime, so nothing is shaded.
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial"))
}

tasks.compileTestJava {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("Soledom")
}

tasks.runServer {
    // `./gradlew runServer` boots a throwaway Paper server in run/ with the freshly built jar.
    // `-PtestMinecraftVersion=26.3` tries another Paper line; 26.x servers need a local Java 25 toolchain.
    val testVersion = providers.gradleProperty("testMinecraftVersion").getOrElse("1.21.4")
    minecraftVersion(testVersion)
    if (!testVersion.startsWith("1.")) {
        javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    }
}
