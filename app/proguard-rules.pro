# R8 rules for the release build.
#
# ML Kit and Play Services ship their own consumer rules, and those are
# relied on, with the one exception below. TFLite ships none.

# ML Kit builds its components from registrar classes named in the merged
# manifest, created by reflection with newInstance(). Its consumer rule keeps
# the registrar class names, but under R8 full mode (the AGP default) a bare
# -keep class no longer keeps the no-arg constructor. Every registrar then
# failed to instantiate, silently, and the first FaceDetection.getClient() in
# a release build threw a NullPointerException: the app crashed the moment a
# camera screen opened. Debug builds are not shrunk, so only a release build
# on a device showed it.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

# The TFLite runtime reaches its Java classes from native code and by
# reflection, so R8 cannot see those references and would strip them.
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn org.tensorflow.lite.**

# Kotlin serialization generates serializers that are only referenced
# reflectively through the @Serializable companion. These are the navigation
# route classes; losing them breaks every argument-carrying destination.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.yasharya.attendance.ui.Route$* {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.yasharya.attendance.ui.Route$* {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room generates an implementation per @Database and looks it up by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Keep line numbers so a crash report from a release build is still readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
