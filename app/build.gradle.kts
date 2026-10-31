plugins {
    alias(libs.plugins.android.application)

    // Connects Firebase configuration with the app
    id("com.google.gms.google-services")
}

android {
    namespace = "com.alard.spendwise"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.alard.spendwise"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    // Firebase libraries use compatible versions
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))

    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth")
}