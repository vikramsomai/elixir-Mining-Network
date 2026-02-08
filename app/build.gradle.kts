plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "network.lynx.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "network.lynx.app"
        minSdk = 24
        //noinspection OldTargetApi
        targetSdk = 35
        versionCode = 22
        versionName = "4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    viewBinding{
        enable=true
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
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
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation ("androidx.core:core-ktx:1.13.0")
    implementation ("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
    // Import the BoM for the Firebase platform
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    implementation("androidx.annotation:annotation:1.0.0")

    // Also add the dependency for the Google Play services library and specify its version
    implementation("com.google.android.gms:play-services-ads:24.4.0")
    implementation("com.unity3d.ads:unity-ads:4.15.0")
    implementation("com.google.ads.mediation:unity:4.15.0.0")
//    implementation("com.facebook.android:audience-network-sdk:6.20.0")
    implementation("com.google.ads.mediation:inmobi:10.8.3.1")
//    implementation("com.google.ads.mediation:facebook:6.20.0.0")
//    implementation ("com.inmobi.monetization:inmobi-ads:10.6.5")

    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))

    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-analytics")
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.auth)
    implementation(libs.kotlin.reflect)
    implementation ("com.airbnb.android:lottie:6.1.0")
    implementation(libs.lifecycle.process)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}