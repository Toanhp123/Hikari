plugins { id("universalmedia.kotlin.jvm") }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    testImplementation(libs.junit4)
}
