# TFLite
-keep class org.tensorflow.** { *; }
-keep class com.google.mlkit.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# DataStore
-keep class androidx.datastore.** { *; }

# Guardian engines
-keep class com.guardian.app.engine.** { *; }
-keep class com.guardian.app.lock.** { *; }
