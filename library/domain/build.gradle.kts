plugins {
    `java-library`
    id("openstory.kotlin.jvm")
}

dependencies {
    api(project(":catalog:domain"))
    api(project(":core:common"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test-junit"))
}
