plugins { id("universalmedia.android.library") }
android { namespace = "app.universalmedia.source.local" }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":source:api"))
}
