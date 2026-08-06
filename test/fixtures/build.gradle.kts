plugins {
    id("openstory.kotlin.jvm")
}

dependencies {
    api(project(":core:common"))
    api(project(":core:model"))
    api(project(":core:plugin-api"))

    testImplementation(libs.junit)
}
