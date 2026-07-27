# Retrofit creates the API implementation dynamically.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Gson reads these response models by reflection.
-keep class ir.promptall.app.data.remote.** { *; }

# Keep Room database implementations and entities safe across minification.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
