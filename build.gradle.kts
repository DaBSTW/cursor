import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
    id("com.diffplug.spotless") version "6.25.0"
    checkstyle
}

group = "dev.bookreports"
version = providers.gradleProperty("pluginVersion").get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
    // CoreProtect's own recommended repository for its API (see its wiki's "Developer API" page) — it isn't
    // published to Maven Central or JitPack.
    maven("https://maven.playpro.com")
}

dependencies {
    // Pinned to match MockBukkit's own bundled paper-api build (see testImplementation below) rather than
    // floating to the newest release — a mismatch there breaks Registry-backed test fixtures (Sound, enchants,
    // etc). `api-version: '1.21'` in paper-plugin.yml is a floor, not a ceiling, so this still loads on any
    // newer Paper build; every API this plugin touches (BukkitScheduler, ItemStack.of, AsyncChatEvent,
    // Enchantment constants) is confirmed unchanged through Paper 26.2. Only bump this pin alongside MockBukkit.
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.coreprotect:coreprotect:21.3")

    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("org.bstats:bstats-bukkit:3.0.2")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.21:3.133.2")
    testImplementation("net.coreprotect:coreprotect:21.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("BookReports")

    relocate("com.zaxxer", "dev.bookreports.libs.hikari")
    relocate("com.github.benmanes.caffeine", "dev.bookreports.libs.caffeine")
    relocate("org.sqlite", "dev.bookreports.libs.sqlite")
    // bStats requires relocation so its own internal update-checking service doesn't clash with other
    // plugins bundling a different bStats version on the same server.
    relocate("org.bstats", "dev.bookreports.libs.bstats")

    // slf4j-api (a transitive of HikariCP/sqlite-jdbc) is deliberately NOT bundled or relocated: Paper's
    // runtime classpath already provides SLF4J with a real, working binding (bridged to its own logger).
    // Shading in our own private, unbound copy — as this used to do — hid that binding from HikariCP's
    // logger lookups, so SLF4J fell back to "no binding found" and printed straight to stderr, which Paper
    // then nags plugin authors about. Excluding it lets HikariCP/sqlite-jdbc's logging just go through the
    // server's own SLF4J, silencing the warning at the source instead of suppressing the symptom.
    dependencies {
        exclude(dependency("org.slf4j:slf4j-api:.*"))
    }

    minimize {
        exclude(dependency("org.xerial:sqlite-jdbc:.*"))
        // Caffeine's cache implementations are selected by dynamically constructing a class name
        // (e.g. "SSMSA") and loading it via reflection — minimize's static analysis can't see that
        // reference, so it strips the class and every cache in the plugin fails at construction with a
        // ClassNotFoundException. Confirmed by actually booting the plugin on a real Paper server.
        exclude(dependency("com.github.ben-manes.caffeine:caffeine:.*"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

configure<SpotlessExtension> {
    java {
        target("src/*/java/**/*.java")
        eclipse().configFile("config/spotless/eclipse-formatter.xml")
        removeUnusedImports()
        importOrder()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

checkstyle {
    toolVersion = "10.17.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(false)
        html.required.set(false)
    }
}
