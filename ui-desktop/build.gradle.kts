plugins {
    kotlin("jvm")
    application
    id("org.openjfx.javafxplugin")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core-application"))
    implementation(project(":infra-persistence"))
}

application {
    mainClass.set("crossnote.desktop.DesktopAppKt")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls")
}