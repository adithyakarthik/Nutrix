# kotlinx.serialization keeps generated serializers referenced only reflectively.
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.nutrix.app.**$$serializer { *; }
-keepclassmembers class com.nutrix.app.** { *** Companion; }

# OkHttp platform classes referenced only on some JVMs.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
