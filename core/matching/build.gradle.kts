plugins {
    id("openstory.kotlin.jvm")
}

dependencies {
    implementation(project(":core:common"))
    api(project(":core:model"))

    testImplementation(kotlin("test-junit"))
}
