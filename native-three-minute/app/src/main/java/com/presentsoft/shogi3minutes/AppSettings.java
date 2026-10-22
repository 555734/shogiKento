package com.presentsoft.shogi3minutes;
import android.content.*;

/** App gain is independent of the device media volume. Defaults retain the existing SE level. */
final class AppSettings {
 private final SharedPreferences prefs;
 AppSettings(Context context){prefs=context.getSharedPreferences("feedback_settings",Context.MODE_PRIVATE);}
 int volume(){return Math.max(0,Math.min(100,prefs.getInt("volume",65)));}
 boolean sound(){return prefs.getBoolean("sound",true);}
 boolean vibration(){return prefs.getBoolean("vibration",false);}
 void volume(int value){prefs.edit().putInt("volume",Math.max(0,Math.min(100,value))).apply();}
 void sound(boolean enabled){prefs.edit().putBoolean("sound",enabled).apply();}
 void vibration(boolean enabled){prefs.edit().putBoolean("vibration",enabled).apply();}
}
