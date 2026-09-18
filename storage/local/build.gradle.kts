plugins { id("universalmedia.android.library") }
android { namespace = "app.universalmedia.storage.local" }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
}
