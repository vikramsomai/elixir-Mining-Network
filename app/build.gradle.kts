plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "network.lynx.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "network.lynx.app"
        minSdk = 24
        //noinspection OldTargetApi
        targetSdk = 34
        versionCode = 6
        versionName = "2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.github.lelloman:android-identicons:v11")
    implementation("androidx.work:work-runtime:2.8.1")
        // Add the dependency for the Firebase Authentication library
        // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
    // Import the BoM for the Firebase platform
    implementation("com.github.bumptech.glide:glide:4.16.0")
    // Add the dependency for the Firebase Authentication library
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.firebase:firebase-auth:22.1.1")

    // Also add the dependency for the Google Play services library and specify its version
    implementation("com.google.android.gms:play-services-ads:23.2.0")
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    // Add the dependency for the Realtime Database library
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-analytics")
    implementation(libs.activity)
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation(libs.constraintlayout)
    implementation(libs.firebase.auth)
    implementation(libs.kotlin.reflect)
    implementation ("com.mikhaellopez:circularprogressbar:3.1.0")
        implementation ("com.airbnb.android:lottie:6.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}