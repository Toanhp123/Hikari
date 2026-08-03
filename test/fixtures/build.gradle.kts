plugins {
    id("openstory.kotlin.jvm")
}

dependencies {
    api(project(":core:common"))
    api(project(":core:model"))

    testImplementation(libs.junit)
}
