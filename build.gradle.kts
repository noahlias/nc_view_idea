import java.io.File
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.10.4"
}

group = "com.ncviewer"
version = "0.1.3"

val localIdePath = providers.gradleProperty("idea.local.path").orNull
val localJbrHome = localIdePath
    ?.takeIf { it.isNotBlank() }
    ?.let { ideRoot ->
        val root = File(ideRoot)
        listOf(
            "jbr/Contents/Home",
            "jbr",
            "jre/Contents/Home",
            "jre"
        ).map(root::resolve)
            .firstOrNull(File::exists)
    }

val envGradleJavaHome = System.getenv("ORG_GRADLE_JAVA_HOME")
    ?.takeIf { it.isNotBlank() }
    ?.let(::File)
    ?.takeIf(File::exists)

if (System.getProperty("org.gradle.java.installations.paths").isNullOrBlank()) {
    listOfNotNull(envGradleJavaHome?.absolutePath, localJbrHome?.absolutePath)
        .takeIf { it.isNotEmpty() }
        ?.let { System.setProperty("org.gradle.java.installations.paths", it.joinToString(File.pathSeparator)) }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
        intellijDependencies()
    }
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    intellijPlatform {
        javaCompiler()
        if (!localIdePath.isNullOrBlank()) {
            local(localPath = localIdePath)
        } else {
            intellijIdeaCommunity("2024.2")
        }
        bundledPlugin("com.intellij.java")
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs += "-Xjvm-default=all"
    }
}

tasks.wrapper {
    gradleVersion = "9.2"
    distributionType = Wrapper.DistributionType.BIN
}

val npmInstall by tasks.registering(Exec::class) {
    workingDir = project.projectDir
    val npmCommand = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "npm.cmd" else "npm"
    commandLine(npmCommand, "install")
    inputs.file("package.json")
    outputs.dir("node_modules")
}

val bundleViewer by tasks.registering(Exec::class) {
    dependsOn(npmInstall)
    workingDir = project.projectDir
    val npmCommand = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "npm.cmd" else "npm"
    commandLine(npmCommand, "run", "build:viewer")
    inputs.files(fileTree("viewer-src"))
    outputs.file("src/main/resources/ncviewer/media/bundle.js")
}

tasks.named("processResources") {
    dependsOn(bundleViewer)
}

tasks.withType<RunIdeTask>().configureEach {
    systemProperties["idea.is.internal"] = "true"
}

tasks.withType<PrepareSandboxTask>().configureEach {
    val disabledPlugins = listOf(
        "com.intellij.gradle",
        "org.jetbrains.idea.gradle.dsl"
    )
    doLast {
        val disabledFile = sandboxConfigDirectory.file("disabled_plugins.txt").get().asFile
        disabledFile.parentFile.mkdirs()
        val existing = if (disabledFile.exists()) disabledFile.readLines() else emptyList()
        val merged = (existing + disabledPlugins)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
            .sorted()
        disabledFile.writeText(merged.joinToString("\n"))
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.ncviewer.idea"
        name = "NC Viewer"
        version = project.version.toString()
        description = "Interactive 3D G-code viewer and editor helper for IntelliJ-based IDEs."
        vendor {
            name = "nc-view"
            email = "yygggg@foxmail.com"
        }
    }
}
