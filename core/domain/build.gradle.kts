plugins { id("universalmedia.kotlin.jvm") }

dependencies {
    implementation(project(":core:model"))
    testImplementation(libs.junit4)
}
