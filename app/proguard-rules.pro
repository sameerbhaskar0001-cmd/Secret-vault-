# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Retrofit & Moshi
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Retrofit interfaces and HTTP annotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep interface com.example.CurrencyApiService { *; }

# Moshi models
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.example.CurrencyResponse { *; }
-keep class com.example.CurrencyResponseJsonAdapter { *; }

# GeckoView internal SnakeYAML references desktop java.beans
-dontwarn java.beans.**


