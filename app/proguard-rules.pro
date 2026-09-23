# Состояние и контент читаются kotlinx.serialization — сериализаторы сохраняются.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class ru.vinteno.finni.**$$serializer { *; }
-keepclassmembers class ru.vinteno.finni.** { *** Companion; }
-keepclasseswithmembers class ru.vinteno.finni.** { kotlinx.serialization.KSerializer serializer(...); }
