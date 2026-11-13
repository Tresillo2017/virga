# Virga release keep rules. Library consumer rules (OkHttp, Koin, Kotlin coroutines, AndroidX) are merged
# automatically; the rules below cover what R8 cannot see on its own.

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

# ---- Navigation 3 route keys ----------------------------------------------------------------------------
# Saved back stacks are serialized with the key's class name as the polymorphic discriminator. Keeping the
# key classes unrenamed keeps saved state readable across the same build, and stops R8 from merging them.
-keep class * implements androidx.navigation3.runtime.NavKey { *; }
-keepnames class * implements androidx.navigation3.runtime.NavKey

# ---- Koin -----------------------------------------------------------------------------------------------
# Modules are plain Kotlin lambdas; only the optional reflection paths used by koin-android need silencing.
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

