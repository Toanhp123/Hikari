plugins { id("universalmedia.android.library") }
android { namespace = "app.universalmedia.ingestion.local" }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))

    implementation(libs.androidx.work.runtime.ktx)
}
