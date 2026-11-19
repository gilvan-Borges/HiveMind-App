# HiveMind Mobile — regras ProGuard/R8 (minify desabilitado por ora).
# Mantém modelos kotlinx.serialization se minify for ativado no futuro.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.hivemind.mobile.core.models.** {
    *** Companion;
}
-keepclasseswithmembers class dev.hivemind.mobile.core.models.** {
    kotlinx.serialization.KSerializer serializer(...);
}
