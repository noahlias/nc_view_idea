import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.0.1"
}

group = "com.ncviewer"
version = "0.0.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(17)
}

val localIdePath = providers.gradleProperty("idea.local.path").orNull

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(kotlin("test"))

    intellijPlatform {
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
