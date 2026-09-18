plugins { id("universalmedia.android.library") }
android { namespace = "app.universalmedia.data" }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
}
