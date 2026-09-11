# RadioSO keep rules
-keep class com.seruiso.radio1.RadioWatchService { *; }
-keep class com.seruiso.radio1.BluetoothReceiver { *; }
-keep class com.seruiso.radio1.BootReceiver { *; }
-keep class com.seruiso.radio1.BluetoothAutoPlayPlugin { *; }
-keep class com.seruiso.radio1.PlaybackPrefs { *; }
-keep class com.seruiso.radio1.BtAudio { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# MediaBrowser / session compat
-keep class android.support.v4.media.** { *; }
-keep class androidx.media.** { *; }

# Keep Parcelable / enums used in prefs JSON path indirectly
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
