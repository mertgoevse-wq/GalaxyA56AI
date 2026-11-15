# ProGuard rules for Galaxy A56 AI

# Keep native libraries
-keep class com.galaxya56.ai.** { *; }

# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep serialization
-keep class kotlinx.serialization.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Keep Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep Coil
-dontwarn coil.**
-keep class coil.** { *; }

# Keep Room
-keep class androidx.room.** { *; }

# Keep DataStore
-keep class androidx.datastore.** { *; }

# Keep Material3
-dontwarn androidx.compose.material3.**
-keep class androidx.compose.material3.** { *; }

# Keep TensorFlow Lite
-dontwarn org.tensorflow.lite.**
-keep class org.tensorflow.lite.** { *; }

# Keep ONNX Runtime
-dontwarn com.microsoft.onnxruntime.**
-keep class com.microsoft.onnxruntime.** { *; }

# Keep MNN
-dontwarn com.alibaba.mnn.**
-keep class com.alibaba.mnn.** { *; }

# Keep NCNN
-dontwarn com.tencent.ncnn.**
-keep class com.tencent.ncnn.** { *; }

# Keep MediaPipe
-dontwarn com.google.mediapipe.**
-keep class com.google.mediapipe.** { *; }

# Keep Timber
-dontwarn timber.log.**
-keep class timber.log.** { *; }

# Keep Gson
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# Optimize
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively