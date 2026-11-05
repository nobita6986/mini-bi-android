# Add project specific ProGuard / R8 rules here.
# Preserve file names and line numbers for readable stack traces on Google Play Console
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile

# Room Database, DAO & Entity rules (preserve table/column field names for SQLite mapping)
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep enum methods required by Kotlin / serialization
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# OkHttp3 / Okio / Retrofit2 platform warnings
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn retrofit2.**

# Google Play Services & ML Kit Barcode Scanning
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.android.odml.**
-dontwarn com.google.barhopper.**

# CameraX & Guava ListenableFuture
-dontwarn androidx.camera.**
-dontwarn com.google.common.**
