plugins {
    id("openstory.kotlin.jvm")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":catalog:model"))
    testImplementation(kotlin("test-junit"))
}
