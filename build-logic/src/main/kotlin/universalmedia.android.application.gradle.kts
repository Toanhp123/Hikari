plugins {
    id("com.android.application")
}

android {
    compileSdk = 37

    defaultConfig {
        minSdk = 23
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        lintConfig = rootProject.file("config/lint/lint.xml")
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}
