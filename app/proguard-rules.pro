# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ==================== FIREBASE ====================
# Firebase Authentication
-keepattributes Signature
-keepattributes *Annotation*

# Firebase Realtime Database
-keepclassmembers class network.lynx.app.** {
    *;
}

# Keep Firebase model classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# ==================== GOOGLE ADS ====================
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }

# ==================== APP CLASSES ====================
# Keep model classes used with Firebase
-keep class network.lynx.app.User { *; }
-keep class network.lynx.app.ReferralInfo { *; }
-keep class network.lynx.app.ReferralModel { *; }
-keep class network.lynx.app.CommissionInfo { *; }
-keep class network.lynx.app.NewsItem { *; }
-keep class network.lynx.app.Banner { *; }
-keep class network.lynx.app.FaqItem { *; }
-keep class network.lynx.app.LeaderBoardModel { *; }
-keep class network.lynx.app.NotificationInfo { *; }

# Keep managers with static instances
-keep class network.lynx.app.*Manager { *; }

# ==================== GENERAL OPTIMIZATION ====================
# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Optimize code
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Remove unused code
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
