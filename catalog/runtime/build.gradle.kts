plugins {
    id("openstory.android.library")
}

android {
    namespace = "app.openstory.catalog.runtime"
}

androidComponents {
    beforeVariants(selector().withBuildType("benchmarkRelease")) { variantBuilder ->
        variantBuilder.hostTests["UnitTest"]?.enable = true
    }
}

dependencies {
    implementation(project(":catalog:domain"))
    implementation(project(":catalog:storage"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
