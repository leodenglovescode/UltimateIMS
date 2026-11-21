package io.github.leodenglovescode.ultimateims.core;

import java.util.*;

/** 显示配置与真实射频能力分离；预设不是运营商统一标准，关闭时使用已保存原值。 */
public final class RadioDisplayProfiles {
    public enum Name { NR_ADVANCED, MAINLAND_BARS }
    public enum State { ON, OFF, UNKNOWN }
    public static Map<ConfigKey,RawValue> target(Name profile) {
        return switch (profile) {
            case NR_ADVANCED -> Map.of(
                ConfigKey.NR_ICON,RawValue.string("connected_mmwave:5G_Plus,connected:5G,connected_rrc_idle:5G,not_restricted_rrc_idle:5G,not_restricted_rrc_con:5G"),
                ConfigKey.NR_ADVANCED_BANDWIDTH,RawValue.integer(110000),
                ConfigKey.NR_ADVANCED_BANDS,RawValue.integers(List.of(1,3,8,28,41,78,79)),
                ConfigKey.NR_ADVANCED_INCLUDE_LTE,RawValue.bool(false),
                ConfigKey.NR_ADVANCED_PCO,RawValue.integer(0));
            case MAINLAND_BARS -> Map.of(
                ConfigKey.NR_RSRP,RawValue.integers(List.of(-128,-118,-108,-98)));
        };
    }
    /** 添加显示资格频段时保留运营商已有国际频段，不改变基带射频支持。 */
    public static Map<ConfigKey,RawValue> target(Name profile, Map<ConfigKey,RawValue> current) {
        Map<ConfigKey,RawValue> result = new EnumMap<>(target(profile));
        if (profile == Name.NR_ADVANCED) {
            RawValue existing = current.get(ConfigKey.NR_ADVANCED_BANDS);
            if (existing == null || existing.kind() != RawValue.Kind.INTEGER_ARRAY) throw new IllegalStateException("Current NR display bands unavailable");
            ConfigKey.NR_ADVANCED_BANDS.validate(existing,33);
            SortedSet<Integer> bands = new TreeSet<>();
            for (RawValue value : List.of(existing,result.get(ConfigKey.NR_ADVANCED_BANDS)))
                if (!value.payload().isEmpty()) for (String part : value.payload().split(",")) bands.add(Integer.parseInt(part));
            RawValue merged = RawValue.integers(new ArrayList<>(bands));
            ConfigKey.NR_ADVANCED_BANDS.validate(merged,33);
            result.put(ConfigKey.NR_ADVANCED_BANDS,merged);
        }
        return Map.copyOf(result);
    }
    public static State state(Name profile, Map<ConfigKey,RawValue> values) {
        boolean matches = true;
        for (var expected : target(profile).entrySet()) {
            RawValue current = values.get(expected.getKey());
            if (current == null || current.kind() != expected.getKey().kind) return State.UNKNOWN;
            if (expected.getKey() == ConfigKey.NR_ADVANCED_BANDS) {
                Set<String> actual = new HashSet<>(Arrays.asList(current.payload().split(",")));
                if (!actual.containsAll(Arrays.asList(expected.getValue().payload().split(",")))) matches = false;
            } else if (!expected.getValue().equals(current)) matches = false;
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
