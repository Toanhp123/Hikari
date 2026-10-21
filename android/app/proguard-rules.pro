# Preserve generic signatures, annotations, and enclosing methods
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Do not obfuscate class/member names so external Tachiyomi extensions can link dynamically
-dontobfuscate

# Injekt dependency injection framework
-keep class uy.kohesive.injekt.** { *; }
-keep class * extends uy.kohesive.injekt.api.FullTypeReference { *; }
-keep class * extends uy.kohesive.injekt.api.TypeReference { *; }

# Tachiyomi / Mihon compatibility layer
-keep class eu.kanade.tachiyomi.** { *; }

# Runtime libraries provided to extensions
-keep class androidx.preference.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class kotlin.time.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
# libzstd-kmp resolves compressor/decompressor classes and fields by JNI names.
-keep class com.squareup.zstd.** { *; }
-keep class org.jsoup.** { *; }
-keep class rx.** { *; }
-keep class com.github.zhanghai.quickjs.** { *; }
-keep class io.github.toanhp123.hikari.** { *; }

