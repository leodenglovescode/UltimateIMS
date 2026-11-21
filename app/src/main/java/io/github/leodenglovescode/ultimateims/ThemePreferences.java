package io.github.leodenglovescode.ultimateims;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

/** 外观偏好独立于后端和恢复备份；默认跟随系统。 */
final class ThemePreferences {
    private ThemePreferences() { }
    private static SharedPreferences preferences(Context c) { return c.getSharedPreferences("appearance",Context.MODE_PRIVATE); }
    static void apply(Context context) {
        AppCompatDelegate.setDefaultNightMode(switch (mode(context)) {
            case 1 -> AppCompatDelegate.MODE_NIGHT_NO;
            case 2 -> AppCompatDelegate.MODE_NIGHT_YES;
            default -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        });
    }
    static int mode(Context c) { int value = preferences(c).getInt("mode",0); return value >= 0 && value <= 2 ? value : 0; }
    static void setMode(Context c, int value) { preferences(c).edit().putInt("mode",value).apply(); apply(c); }
    static boolean dynamic(Context c) { return preferences(c).getBoolean("dynamic",true); }
    static void setDynamic(Context c, boolean value) { set(c,"dynamic",value); }
    static boolean glass(Context c) { return preferences(c).getBoolean("glass",false); }
    static boolean glassActive(Context c) { return glass(c) && !solid(c); }
    static boolean dark(Context c) { return (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES; }
    static int backgroundVisibility(Context c) { return integer(c,"background_visibility",glass(c) ? 90 : 18,0,100); }
    static int glassOpacity(Context c) { return integer(c,"glass_opacity",38,10,90); }
    static int glassBlur(Context c) { return integer(c,"glass_blur",18,0,40); }
    private static int integer(Context c, String key, int fallback, int min, int max) { return Math.max(min,Math.min(max,preferences(c).getInt(key,fallback))); }
    static void setInteger(Context c, String key, int value) { preferences(c).edit().putInt(key,value).apply(); }
    static boolean solid(Context c) { return preferences(c).getBoolean("solid",false); }
    static boolean compact(Context c) { return preferences(c).getBoolean("compact",false); }
    static boolean keepAwake(Context c) { return preferences(c).getBoolean("keep_awake",false); }
    static void set(Context c, String key, boolean value) { preferences(c).edit().putBoolean(key,value).apply(); }
    static void reset(Context c) { preferences(c).edit().clear().apply(); apply(c); }
}
