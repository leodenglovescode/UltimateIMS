package io.github.leodenglovescode.ultimateims;

import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;

/** 主题独立于后端；切换外观不会启动特权服务或修改通信配置。 */
final class ThemePreferences {
    private ThemePreferences() { }
    static int selected(Context context) { return Math.max(0,Math.min(2,context.getSharedPreferences("appearance",Context.MODE_PRIVATE).getInt("mode",0))); }
    static void apply(Context context) {
        AppCompatDelegate.setDefaultNightMode(switch (selected(context)) {
            case 1 -> AppCompatDelegate.MODE_NIGHT_NO;
            case 2 -> AppCompatDelegate.MODE_NIGHT_YES;
            default -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        });
    }
    static void set(Context context, int mode) {
        context.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putInt("mode",mode).apply(); apply(context);
    }
    static boolean dynamic(Context context) { return context.getSharedPreferences("appearance",Context.MODE_PRIVATE).getBoolean("dynamic",false); }
    static void setDynamic(Context context, boolean enabled) { context.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putBoolean("dynamic",enabled).apply(); }
}
