# Загальні правила
-keepattributes *Annotation*, InnerClasses, Signature, Exception
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class org.jetbrains.** { *; }

# AWS SDK правила
-keep class software.amazon.** { *; }
-keep class com.amazonaws.** { *; }
-dontwarn com.amazonaws.**
-dontwarn software.amazon.**
-dontwarn org.apache.http.**
-dontwarn org.apache.commons.**
-dontwarn com.fasterxml.jackson.**
-dontwarn org.joda.time.**
-dontwarn org.joda.convert.**
-dontwarn com.google.common.**
-dontwarn io.netty.**
-dontwarn reactor.netty.**
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.**
-dontwarn org.apache.log4j.**
-dontwarn ch.qos.logback.**
-dontwarn kotlinx.datetime.**
-dontwarn io.netty.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.apache.commons.logging.**

# Jackson правила
-keep class com.fasterxml.** { *; }
-keepnames class com.fasterxml.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.fasterxml.jackson.databind.**

# Kubernetes client правила
-keep class io.fabric8.** { *; }
-dontwarn io.fabric8.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**

# Compose правила
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Зберігати всі моделі
-keep class ua.in.ios.kubemanager.model.** { *; }
-keep class * implements java.io.Serializable { *; }

# Зберігати конструктори, що використовуються для десеріалізації
-keepclassmembers class * {
    private <fields>;
    public <init>();
}

# Додаткові правила для dynamic access
-keep class software.amazon.awssdk.thirdparty.jackson.** { *; }
-keepclassmembers class software.amazon.awssdk.thirdparty.jackson.** {
    <init>();
    *;
}

# Додати в кінець файлу
-keepdirectories META-INF
-adaptresourcefilenames META-INF/MANIFEST.MF,META-INF/INDEX.LIST,META-INF/io.netty.versions.properties
-adaptresourcefilecontents META-INF/MANIFEST.MF,META-INF/INDEX.LIST,META-INF/io.netty.versions.properties
-mergeinterfacesaggressively
-dontpreverify

