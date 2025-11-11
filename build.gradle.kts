import java.io.File
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.10.4"
}

group = "com.ncviewer"
version = "0.0.1"

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

if (localJbrHome != null && System.getProperty("org.gradle.java.installations.paths").isNullOrBlank()) {
    System.setProperty("org.gradle.java.installations.paths", localJbrHome.absolutePath)
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

intellijPlatform {
    pluginConfiguration {
        id = "com.ncviewer.idea"
        name = "NC Viewer (IDEA)"
        version = project.version.toString()
        description = "Interactive 3D G-code viewer and editor helper for IntelliJ-based IDEs."
        vendor {
            name = "nc-view"
            email = "yygggg@foxmail.com"
        }
    }
}
