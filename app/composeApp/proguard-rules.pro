# BeatMyBeat — reglas R8/ProGuard para la build release minificada.
# Objetivo: conservar lo que NewPipe Extractor, ffmpeg-kit y Media3 necesitan en runtime.

# --- Atributos generales útiles para stacktraces y reflexión ---
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,Exceptions
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# ----------------------------------------------------------------------------
# NewPipe Extractor
# Usa Rhino (Mozilla JS) para descifrar firmas de YouTube y carga servicios por
# reflexión; además depende de nanojson, jsoup y autolink.
# ----------------------------------------------------------------------------
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# Rhino / Mozilla JavaScript (descifrado de firmas)
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**

# Dependencias transitivas de NewPipe
-keep class com.grack.nanojson.** { *; }
-dontwarn com.grack.nanojson.**
-keep class org.nibor.autolink.** { *; }
-dontwarn org.nibor.autolink.**
-dontwarn org.jsoup.**

# ----------------------------------------------------------------------------
# ffmpeg-kit (JNI: la capa nativa invoca métodos Java por nombre)
# ----------------------------------------------------------------------------
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smartexception.** { *; }
-dontwarn com.arthenica.ffmpegkit.**
-dontwarn com.arthenica.smartexception.**

# ----------------------------------------------------------------------------
# AndroidX Media3 (ExoPlayer). Trae sus propias reglas consumer; estos keeps
# son una red de seguridad para constructores accedidos por reflexión.
# ----------------------------------------------------------------------------
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ----------------------------------------------------------------------------
# OkHttp / Okio
# ----------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
