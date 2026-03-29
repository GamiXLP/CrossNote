plugins {
    kotlin("jvm")
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
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
    implementation(project(":core-application"))
    implementation(project(":infra-persistence"))
    implementation(project(":core-domain"))
}

application {
    mainClass.set("crossnote.desktop.DesktopAppKt")
}
