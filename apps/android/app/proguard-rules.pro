# Proguard rules for app
-keepattributes *Annotation*
-dontwarn javax.annotation.**
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}
