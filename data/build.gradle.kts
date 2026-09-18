plugins {
    id("universalmedia.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android { namespace = "app.universalmedia.data" }

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
