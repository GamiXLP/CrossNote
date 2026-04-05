plugins {
    kotlin("jvm")
    `java-library`
    jacoco
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core-domain"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testImplementation(project(":infra-persistence"))
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