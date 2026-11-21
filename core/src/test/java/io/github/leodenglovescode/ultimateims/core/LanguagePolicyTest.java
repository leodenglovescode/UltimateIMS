package io.github.leodenglovescode.ultimateims.core;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

/** 覆盖中文变体、非中文回退与用户明确选择优先级。 */
public final class LanguagePolicyTest {
    @Test public void allChineseScriptsAndRegionsResolveToSimplifiedChinese() {
        for (String tag : List.of("zh","zh-CN","zh-TW","zh-HK","zh-MO","zh-SG","zh-Hans","zh-Hant","zh-Hant-HK","zh-Hans-SG","zh_Latn_CN","cmn-Hans-CN","yue-Hant-HK"))
            assertEquals(tag,"zh-CN",LanguagePolicy.resolve(null,tag));
    }
    @Test public void otherPrimarySystemLanguagesResolveToEnglish() {
        for (String tag : List.of("en-US","en-GB","fr-FR","de-DE","ja-JP","ko-KR","ar-SA","ru-RU","es-ES","und",""))
            assertEquals(tag,"en",LanguagePolicy.resolve(null,tag));
    }
    @Test public void explicitChineseWinsOverNonChineseSystemLanguage() {
        assertEquals("zh-CN",LanguagePolicy.resolve("zh-CN","en-US"));
        assertEquals("zh-CN",LanguagePolicy.resolve("zh-Hant-TW","fr-FR"));
    }
    @Test public void explicitEnglishWinsOverChineseSystemLanguage() { assertEquals("en",LanguagePolicy.resolve("en","zh-Hant-TW")); }
    @Test public void emptyApplicationLanguageKeepsFollowingSystemChanges() {
        assertEquals("zh-CN",LanguagePolicy.resolve("","zh-TW"));
        assertEquals("en",LanguagePolicy.resolve("","fr-FR"));
        assertEquals("zh-CN",LanguagePolicy.resolve("","zh-HK"));
    }
    @Test public void missingOrUnsupportedLanguageUsesEnglish() {
        assertEquals("en",LanguagePolicy.resolve(null,null));
        assertEquals("en",LanguagePolicy.resolve("fr-FR","zh-CN"));
    }
}
