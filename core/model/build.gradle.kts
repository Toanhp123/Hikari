plugins {
    id("openstory.kotlin.jvm")
}

dependencies {
    api(project(":core:common"))

    testImplementation(libs.junit)
}
