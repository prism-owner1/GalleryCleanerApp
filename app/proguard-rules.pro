# This is a configuration file for ProGuard.
# http://proguard.sourceforge.net/index.html#manual/usage.html

# For Android applications, the default configuration includes generic rules for
# commonly used Android libraries. These rules are optimized to keep the app functional.

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Glide configuration
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

# Keep AndroidX classes
-keep class androidx.** { *; }
-dontwarn androidx.**

# Keep your app's classes (replace with your package name)
-keep class com.galleryclean.app.** { *; }

# General Android rules
-keep class android.** { *; }
-dontwarn android.**
