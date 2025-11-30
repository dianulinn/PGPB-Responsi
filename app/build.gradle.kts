plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // TIDAK pakai kapt lagi
}

android {
    namespace = "com.example.spotlyapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.spotlyapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // bawaan template
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Fragment & Lifecycle (buat Fragment, coroutine, dsb.)
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    // MapLibre (peta, pengganti Google Maps)
    implementation("org.maplibre.gl:android-sdk:11.5.0")

    // TIDAK ada lagi Room / kapt di sini
}
