plugins {
    kotlin("jvm")
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21)) // oder 17
    }
}

javafx {
    version = "21.0.4"
    modules = listOf(
        "javafx.controls",
        "javafx.fxml"
    )
}

dependencies {
    // ⚠️ NUR UI – keine Domain-Module, bis UI läuft
}

application {
    mainClass.set("crossnote.desktop.DesktopAppKt")
}
