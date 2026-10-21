# Project-specific ProGuard / R8 rules

# Preserve line numbers and file names for readable stack traces in crash reports / Play Console
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve annotations and type signatures
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep data models used in app / widgets
-keep class net.bonstio.traintimes.TrainService { *; }
-keep class net.bonstio.traintimes.Station { *; }
-keep class net.bonstio.traintimes.WidgetConfiguration { *; }

# WorkManager
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Ktor / Coroutines / Logging
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.slf4j.**

# ColorPicker library
-dontwarn com.github.dhaval2404.colorpicker.**
-keep class com.github.dhaval2404.colorpicker.** { *; }
