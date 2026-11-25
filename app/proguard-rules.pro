# Room and Compose ship their own consumer R8 rules. Add project-specific rules below if needed.

# ---- ExecuTorch (on-device Llama) ----
# The native library looks these classes up by name through JNI; shrinking or renaming them breaks loading.
-keep class org.pytorch.executorch.** { *; }
-keep class com.facebook.jni.** { *; }
-keep class com.facebook.soloader.** { *; }
-keepclassmembers class * { @com.facebook.jni.annotations.DoNotStrip *; }
-dontwarn org.pytorch.executorch.**
-dontwarn com.facebook.**
