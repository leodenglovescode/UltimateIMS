package io.github.leodenglovescode.ultimateims.core;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** 配套键必须出现在审阅草稿，不能顺便改偏好、漫游、SIM 地区或电源策略。 */
public final class CallingConfigurationTest {
    @Test public void vonrAvailabilityAlsoExposesItsSettingButKeepsUserPreferenceSeparate() {
        var target = CallingConfiguration.target(ConfigKey.VONR,true);
        assertEquals(Map.of(ConfigKey.VONR,RawValue.bool(true),ConfigKey.VONR_VISIBLE,RawValue.bool(true)),target);
        assertFalse(target.containsKey(ConfigKey.VONR_USER)); assertFalse(target.containsKey(ConfigKey.NR_AVAILABILITY));
    }
    @Test public void wifiCallingIncludesModeControlsAndIconWithoutChangingRoamingOrPreference() {
        var target = CallingConfiguration.target(ConfigKey.VOWIFI,true);
        assertEquals(Set.of(ConfigKey.VOWIFI,ConfigKey.WIFI_ONLY,ConfigKey.EDITABLE_WFC,ConfigKey.EDITABLE_WFC_ROAMING,ConfigKey.WFC_ICON),target.keySet());
    }
    @Test public void crossSimIncludesOpportunisticAvailabilityButNotUserOptIn() {
        assertEquals(Set.of(ConfigKey.CROSS_SIM,ConfigKey.CROSS_SIM_OPPORTUNISTIC),CallingConfiguration.target(ConfigKey.CROSS_SIM,true).keySet());
    }
    @Test public void volteMakesCarrierSettingVisibleWithoutChangingLteIconOrUserPreference() {
        var target = CallingConfiguration.target(ConfigKey.VOLTE,true);
        assertEquals(RawValue.bool(false),target.get(ConfigKey.HIDE_LTE_SETTING));
        assertEquals(RawValue.bool(true),target.get(ConfigKey.EDITABLE_LTE));
        assertFalse(target.containsKey(ConfigKey.HIDE_LTE_PLUS)); assertFalse(target.containsKey(ConfigKey.ADVANCED_CALLING_USER));
    }
    @Test public void disableIsExplicitAvailabilityFalseWithoutInventedCompanionDefaults() {
        for (var key : List.of(ConfigKey.VOLTE,ConfigKey.VONR,ConfigKey.VOWIFI,ConfigKey.CROSS_SIM))
            assertEquals(Map.of(key,RawValue.bool(false)),CallingConfiguration.target(key,false));
    }
    @Test public void presetsAndCallingSwitchesUseSameMappingsWithoutTouchingSimRegion() {
        var preset = Presets.build(Presets.Name.CALLING_AVAILABLE,37);
        for (var key : List.of(ConfigKey.VOLTE,ConfigKey.VOWIFI,ConfigKey.VONR,ConfigKey.CROSS_SIM))
            assertTrue(preset.entrySet().containsAll(CallingConfiguration.target(key,true).entrySet()));
        assertFalse(preset.containsKey(ConfigKey.SIM_COUNTRY));
        assertFalse(preset.containsKey(ConfigKey.WFC_USER_ROAMING));
    }
    @Test public void companionMappingsValidateOnSupportedVersions() {
        for (int api : List.of(33,34,37)) for (var key : List.of(ConfigKey.VOLTE,ConfigKey.VOWIFI,ConfigKey.CROSS_SIM))
            CallingConfiguration.target(key,true).forEach((field,value) -> field.validate(value,api));
        CallingConfiguration.target(ConfigKey.VONR,true).forEach((field,value) -> field.validate(value,34));
    }
    @Test public void radioPresetsDoNotIncidentallyEnableVideoOrChangeSubscriptionPreferences() {
        for (var name : List.of(Presets.Name.RECOMMENDED,Presets.Name.CHINA_5G,Presets.Name.CHINA_LTE)) {
            var values = Presets.build(name,37);
            assertFalse(values.containsKey(ConfigKey.VT));
            assertFalse(values.containsKey(ConfigKey.VOWIFI));
            for (ConfigKey key : values.keySet()) assertNotEquals(ConfigKey.Scope.SUBSCRIPTION,key.scope);
        }
    }
}
