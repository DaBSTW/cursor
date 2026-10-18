import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    java
    checkstyle
    id("com.diffplug.spotless") version "7.2.1"
}

group = "dev.pregen"
version = providers.gradleProperty("pluginVersion").get()

val paperApiVersion = providers.gradleProperty("paperApiVersion").get()
val latestPaperApiVersion = providers.gradleProperty("latestPaperApiVersion").get()

java {
    // Paper 1.21.x requires Java 21; Java 21 bytecode also runs unchanged on the Java 25 servers of 26.x.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val latestPaper: Configuration by configurations.creating {
    isCanBeConsumed = false
    // Resolve the compile-time view of the API, which includes its annotation dependencies.
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    latestPaper("io.papermc.paper:paper-api:$latestPaperApiVersion")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial", "-Werror"))
}

// Compiles the same sources against the newest Paper API. The newest API jars are Java 25 class files, so this
// needs a Java 25 compiler, but it still emits Java 21 bytecode. Failing here means an API this plugin uses was
// removed or changed upstream.
val compileAgainstLatestPaper by tasks.registering(JavaCompile::class) {
    description = "Compiles the main sources against the newest Paper API to detect incompatibilities."
    group = "verification"
    source = sourceSets.main.get().java
    classpath = latestPaper
    destinationDirectory.set(layout.buildDirectory.dir("latest-paper-classes"))
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    // Deprecation warnings differ between API generations; only real errors matter for this check.
    options.compilerArgs.removeAll(listOf("-Werror"))
}

tasks.check {
    dependsOn(compileAgainstLatestPaper)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("Pregen")
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
    toolVersion = "10.21.4"
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
