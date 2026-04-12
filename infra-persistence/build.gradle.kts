plugins {
    kotlin("jvm")
    jacoco
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core-domain"))
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}