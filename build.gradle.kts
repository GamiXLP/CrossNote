plugins {
    kotlin("jvm") version "2.0.20" apply false
    id("org.openjfx.javafxplugin") version "0.1.0" apply false
}

allprojects {
    group = "de.crossnote"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}