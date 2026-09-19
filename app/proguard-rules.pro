# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\linux\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep rules here:

# Osmdroid rules (if needed, usually they have their own, but just in case)
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Room rules (usually handled by AGP)
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Koin rules
-keep class org.koin.** { *; }
-dontwarn org.koin.**
