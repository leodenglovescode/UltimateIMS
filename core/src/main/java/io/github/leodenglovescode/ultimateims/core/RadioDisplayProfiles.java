package io.github.leodenglovescode.ultimateims.core;

import java.util.*;

/** 显示配置与真实射频能力分离；预设不是运营商统一标准，关闭时使用已保存原值。 */
public final class RadioDisplayProfiles {
    public enum Name { NR_ADVANCED, MAINLAND_BARS }
    public enum State { ON, OFF, UNKNOWN }
    public static Map<ConfigKey,RawValue> target(Name profile) {
        return switch (profile) {
            case NR_ADVANCED -> Map.of(
                ConfigKey.NR_ICON,RawValue.string("connected_mmwave:5G_Plus,connected:5G"),
                ConfigKey.NR_ADVANCED_BANDWIDTH,RawValue.integer(20000));
            case MAINLAND_BARS -> Map.of(
                ConfigKey.NR_RSRP,RawValue.integers(List.of(-110,-100,-90,-80)),
                ConfigKey.NR_SIGNAL_METRICS,RawValue.integer(1),
                ConfigKey.NR_NSA_LTE_SIGNAL,RawValue.bool(false));
        };
    }
    public static State state(Name profile, Map<ConfigKey,RawValue> values) {
        boolean matches = true;
        for (var expected : target(profile).entrySet()) {
            RawValue current = values.get(expected.getKey());
            if (current == null || current.kind() != expected.getKey().kind) return State.UNKNOWN;
            if (!expected.getValue().equals(current)) matches = false;
        }
        return matches ? State.ON : State.OFF;
    }
    public static Map<ConfigKey,RawValue> savedOriginals(Name profile, Map<ConfigKey,RawValue> originals) {
        Map<ConfigKey,RawValue> result = new EnumMap<>(ConfigKey.class);
        for (ConfigKey key : target(profile).keySet()) {
            RawValue value = originals.get(key);
            if (value == null || value.kind() != key.kind) throw new IllegalStateException("Saved original profile values unavailable");
            result.put(key,value);
        }
        if (state(profile,result) == State.ON) throw new IllegalStateException("Saved carrier originals already use this profile");
        return Map.copyOf(result);
    }
    public static Map<ConfigKey,RawValue> overlayBeforeEnable(Name profile, Map<ConfigKey,RawValue> draft) {
        Map<ConfigKey,RawValue> previous = new EnumMap<>(ConfigKey.class);
        for (ConfigKey key : target(profile).keySet()) if (draft.containsKey(key)) previous.put(key,draft.get(key));
        return Map.copyOf(previous);
    }
    public static Map<ConfigKey,RawValue> cancelStaged(Name profile, Map<ConfigKey,RawValue> draft, Map<ConfigKey,RawValue> previous) {
        Map<ConfigKey,RawValue> next = new EnumMap<>(ConfigKey.class); next.putAll(draft);
        for (ConfigKey key : target(profile).keySet()) next.remove(key);
        for (var item : previous.entrySet()) if (target(profile).containsKey(item.getKey())) next.put(item.getKey(),item.getValue());
        return Map.copyOf(next);
    }
    private RadioDisplayProfiles() { }
}
