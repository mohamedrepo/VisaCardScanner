# Keep ML Kit text recognition entry points (reflection based model wiring).
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text** { *; }
-keep class com.google.android.gms.internal.mlkit_common** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# Room generates implementations via KSP.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Keep the entity so its schema stays intact under shrinking.
-keep class com.example.cardscanner.data.CardRecord { *; }

# Never strip the security layer.
-keep class com.example.cardscanner.security.** { *; }
