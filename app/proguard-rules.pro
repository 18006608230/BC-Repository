# ProGuard rules for Brawn Catalog

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Gson
-keep class com.bc.app.data.entity.** { *; }
