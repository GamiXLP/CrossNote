plugins {
    // keine Plugins im Root nötig – wir konfigurieren in den Subprojekten
}

allprojects {
    group = "de.crossnote"
    version = "0.1.0"
}

subprojects {
    repositories {
        mavenCentral()
    }
}