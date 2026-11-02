plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.myjoint.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.myjoint.browser"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}


dependencies {
    // Keep minSdk 23 compatibility while adding Google Mobile Ads.
    implementation("com.google.android.gms:play-services-ads:24.9.0")
}
