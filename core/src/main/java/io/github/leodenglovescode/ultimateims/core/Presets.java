package io.github.leodenglovescode.ultimateims.core;

import java.util.*;

/** 预设只是可审阅草稿；不自动应用，不触碰 SIM 地区、门户、名称或持久化 opt-in。 */
public final class Presets {
    public enum Name { RECOMMENDED, CHINA_5G, CHINA_LTE, CALLING_AVAILABLE }
    public static Map<ConfigKey, RawValue> build(Name name, int api) {
        Map<ConfigKey, RawValue> values = new EnumMap<>(ConfigKey.class);
        values.put(ConfigKey.VOLTE, RawValue.bool(true)); values.put(ConfigKey.LTE, RawValue.bool(true));
        if (name == Name.CHINA_LTE) {
            values.put(ConfigKey.NR_AVAILABILITY, RawValue.integers(List.of()));
            if (api >= 34) { values.put(ConfigKey.VONR, RawValue.bool(false)); values.put(ConfigKey.VONR_USER, RawValue.integer(0)); }
        } else {
            values.put(ConfigKey.NR_AVAILABILITY, RawValue.integers(List.of(1,2)));
            values.put(ConfigKey.VT, RawValue.bool(true));
            if (api >= 34) values.put(ConfigKey.VONR, RawValue.bool(true));
            if (name == Name.CHINA_5G) { values.putAll(RadioDisplayProfiles.target(RadioDisplayProfiles.Name.NR_ADVANCED)); values.putAll(RadioDisplayProfiles.target(RadioDisplayProfiles.Name.MAINLAND_BARS)); values.put(ConfigKey.HIDE_LTE_PLUS, RawValue.bool(false)); }
        }
        if (name == Name.CALLING_AVAILABLE) for (ConfigKey key : List.of(ConfigKey.VOWIFI,ConfigKey.WIFI_ONLY,ConfigKey.VT,ConfigKey.CROSS_SIM,ConfigKey.UT)) values.put(key, RawValue.bool(true));
        return Map.copyOf(values);
    }
    private Presets() { }
}
