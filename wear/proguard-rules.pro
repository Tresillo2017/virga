# Virga watch release keep rules. They mirror the relevant parts of app/proguard-rules.pro. Library consumer rules
# (OkHttp, Koin, Kotlin coroutines, DataStore, Wear, Play services) are merged automatically; the rules below cover
# what R8 cannot see on its own. The manifest components (services, activity, application) are kept by AAPT.

# ---- kotlinx.serialization -------------------------------------------------------------------------------
# Generated $$serializer classes and the companion serializer() accessors are looked up by name at runtime.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, RuntimeVisibleAnnotations
-keepclassmembers,includedescriptorclasses class com.tomasps.virga.** {
    *** Companion;
}
-keepclasseswithmembers class com.tomasps.virga.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.tomasps.virga.**$$serializer { *; }
-keepclassmembers class com.tomasps.virga.** {
    static ** INSTANCE;
}
-dontnote kotlinx.serialization.**

# ---- Koin -----------------------------------------------------------------------------------------------
-dontwarn org.koin.**

# ---- Ktor and OkHttp -------------------------------------------------------------------------------------
# The OkHttp engine is selected in code, but Ktor resolves engine containers through ServiceLoader.
-keep class io.ktor.client.engine.okhttp.** { *; }
-keep class io.ktor.client.plugins.** { *; }
-dontwarn io.ktor.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# ---- Stack traces ----------------------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
