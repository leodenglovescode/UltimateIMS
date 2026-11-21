package io.github.leodenglovescode.ultimateims;

import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import io.github.leodenglovescode.ultimateims.core.LanguagePolicy;

/** 选择存储使用 Android 每应用语言；资源上下文落实严格的中英文自动回退。 */
final class LanguagePreferences {
    static int selected(Context context) {
        LocaleList requested = context.getSystemService(LocaleManager.class).getApplicationLocales();
        return requested.isEmpty() ? 0 : LanguagePolicy.resolve(requested.get(0).toLanguageTag(),null).equals("zh-CN") ? 2 : 1;
    }
    static String resolved(Context context) {
        LocaleManager manager = context.getSystemService(LocaleManager.class);
        LocaleList requested = manager.getApplicationLocales(), system = manager.getSystemLocales();
        return LanguagePolicy.resolve(requested.isEmpty() ? null : requested.get(0).toLanguageTag(),system.isEmpty() ? null : system.get(0).toLanguageTag());
    }
    static Context wrap(Context context) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        LocaleList locales = LocaleList.forLanguageTags(resolved(context));
        configuration.setLocales(locales); configuration.setLayoutDirection(locales.get(0));
        return context.createConfigurationContext(configuration);
    }
    static void select(Context context, int choice) {
        if (choice < 0 || choice > 2) throw new IllegalArgumentException("Unknown language choice");
        // 自动模式保留系统的空列表语义，不伪装成用户明确选择英语或中文。
        context.getSystemService(LocaleManager.class).setApplicationLocales(choice == 0 ? LocaleList.getEmptyLocaleList() : LocaleList.forLanguageTags(choice == 1 ? "en" : "zh-CN"));
    }
    private LanguagePreferences() { }
}
