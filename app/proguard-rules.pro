-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.alltestsareus.app.model.**$$serializer { *; }
-keepclassmembers class com.alltestsareus.app.model.** {
    *** Companion;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

