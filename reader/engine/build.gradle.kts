plugins {
    id("openstory.kotlin.jvm")
}

dependencies {
    implementation(project(":core:common"))
    testImplementation(kotlin("test-junit"))
}
