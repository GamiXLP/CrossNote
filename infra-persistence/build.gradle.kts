plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core-domain"))
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
}
