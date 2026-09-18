plugins { id("universalmedia.kotlin.jvm") }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":source:api"))
    testImplementation(libs.junit4)
}
