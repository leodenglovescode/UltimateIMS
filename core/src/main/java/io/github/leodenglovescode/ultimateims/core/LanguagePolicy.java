package io.github.leodenglovescode.ultimateims.core;

import java.util.*;

/** 明确选择优先；自动模式只看系统首选语言，不回落到列表中的其他语言。 */
public final class LanguagePolicy {
    private static final Set<String> CHINESE = Set.of("zh","zho","chi","cmn","yue","wuu","hak","nan","gan","hsn","cdo","cjy","cpx","czh","czo","lzh","mnp");
    public static String resolve(String appTag, String systemTag) {
        String chosen = appTag == null || appTag.isBlank() ? systemTag : appTag;
        if (chosen == null) return "en";
        Locale locale = Locale.forLanguageTag(chosen.replace('_','-'));
        return CHINESE.contains(locale.getLanguage().toLowerCase(Locale.ROOT)) ? "zh-CN" : "en";
    }
    private LanguagePolicy() { }
}
