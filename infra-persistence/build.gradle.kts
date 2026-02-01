plugins {
    kotlin("jvm") version "2.0.20"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core-domain"))
}