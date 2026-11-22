# HiveMind Mobile — regras ProGuard/R8 (isMinifyEnabled = true desde a Onda 5).
# Fonte das regras: androidx/okhttp/bouncycastle consumer rules + kotlinx.serialization docs.

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# --- kotlinx.serialization: serializadores acessados via reflection ---
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Modelos do app: Companion com serializer() usado por encodeToString/decodeFromString.
-keepclassmembers @kotlinx.serialization.Serializable class dev.hivemind.mobile.** {
    *** Companion;
}
-keepclasseswithmembers class dev.hivemind.mobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Campos serializados não podem ser renomeados (nomes fazem parte do contrato de fio).
-keepclassmembers @kotlinx.serialization.Serializable class dev.hivemind.mobile.** {
    <fields>;
}

# --- BouncyCastle (NaClBox X25519 + XSalsa20-Poly1305): crypto via reflection ---
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# --- OkHttp/Okio: consumer rules embutidas cobrem o essencial; silencia checks ---
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**

# --- Room: gerado via KSP e já coberto pelas consumer rules do runtime;
#     expõe os nomes de coluna via annotation sem reflexão extra. ---

# --- Coroutines debug ---
-dontwarn kotlinx.coroutines.debug.**

# --- ML Kit / CameraX: consumer rules embutidas; apenas silencia verificadores ---
-dontwarn com.google.android.gms.**
