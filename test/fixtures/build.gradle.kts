plugins {
    id("openstory.kotlin.jvm")
}

dependencies {
    api(project(":core:common"))
    api(project(":core:common"))
    api(project(":catalog"))

    testImplementation(libs.junit)
}
