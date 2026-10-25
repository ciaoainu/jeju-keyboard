plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.jeju.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.jeju.keyboard"
        minSdk = 23
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }
}
