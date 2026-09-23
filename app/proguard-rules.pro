# R8 rules for the release build.
#
# ML Kit and Play Services ship their own consumer rules, so they are not
# repeated here. TFLite does not, and it is the one library in this app that
# genuinely needs protecting from shrinking.

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
