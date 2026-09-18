plugins { id("universalmedia.android.library") }
android { namespace = "app.universalmedia.playback.media3" }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":source:api"))
    implementation(project(":playback:api"))

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
}
