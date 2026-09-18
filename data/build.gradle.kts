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
    ksp(libs.androidx.room.compiler)
}
