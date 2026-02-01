plugins {
    kotlin("jvm") version "2.0.20"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core-application"))
    implementation(project(":infra-persistence"))
}

application {
    // Das setzen wir gleich, sobald eure Main-Klasse existiert
    mainClass.set("crossnote.desktop.DesktopAppKt")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls")
}