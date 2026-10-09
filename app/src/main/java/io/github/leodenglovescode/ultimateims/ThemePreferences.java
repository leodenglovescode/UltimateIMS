package io.github.leodenglovescode.ultimateims;

import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;

/** 主题独立于后端；切换外观不会启动特权服务或修改通信配置。 */
final class ThemePreferences {
    private ThemePreferences() { }
    static void apply(Context context) {
        // 始终跟随当前系统外观，忽略旧版保存的手动浅深色模式。
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    static boolean dynamic(Context context) { return context.getSharedPreferences("appearance",Context.MODE_PRIVATE).getBoolean("dynamic",true); }
    static void setDynamic(Context context, boolean enabled) { context.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putBoolean("dynamic",enabled).apply(); }
}
