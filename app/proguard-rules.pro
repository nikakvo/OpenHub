# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/MicroVideo/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontwarn
-verbose

-dontoptimize
-dontpreverify

-ignorewarnings

-dontwarn javax.annotation.**
-dontwarn javax.inject.**

-keep class com.thirtydegreesray.openhub.http.model.** { *; }
-keep class com.thirtydegreesray.openhub.mvp.model.** { *; }

# DataAutoAccess (util.state): reflection on @AutoAccess fields. Field names are
# Bundle keys used by intent extras, so they must not be renamed or removed.
-keep @interface com.thirtydegreesray.openhub.util.state.AutoAccess
-keepclassmembers class * { @com.thirtydegreesray.openhub.util.state.AutoAccess <fields>; }


# App event bus (common.AppEventBus): finds the @Subscribe methods by reflection
-keepattributes *Annotation*
-keep @interface com.thirtydegreesray.openhub.common.Subscribe
-keepclassmembers class ** {
    @com.thirtydegreesray.openhub.common.Subscribe <methods>;
}

#Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.AppGlideModule
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

-keep class android.support.**{*;}

# OkHttp3
-dontwarn okhttp3.logging.**
-keep class okhttp3.internal.**{*;}
-dontwarn okio.**
# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
#-keepattributes Signature-keepattributes Exceptions
# RxJava 3 ships its own consumer rules
-dontwarn sun.misc.**
# Gson
-keep class com.google.gson.stream.** { *; }
-keepattributes EnclosingMethod
-keep class com.thirtydegreesray.openhub.mvp.model.**{*;}
-keep class com.thirtydegreesray.openhub.dao.**{*;}
