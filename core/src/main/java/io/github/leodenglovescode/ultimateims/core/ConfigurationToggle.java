package io.github.leodenglovescode.ultimateims.core;

import java.util.*;

/** 开关只映射已知配置；默认值/不可读值不能伪装成关闭。 */
public final class ConfigurationToggle {
    private ConfigurationToggle() { }
    public enum State { ON, OFF, UNKNOWN }
    public static boolean supported(ConfigKey key) {
        return key.kind == RawValue.Kind.BOOLEAN || (key.scope == ConfigKey.Scope.SUBSCRIPTION && key != ConfigKey.WFC_USER_MODE && key != ConfigKey.WFC_USER_ROAMING_MODE);
    }
    public static State state(ConfigKey key, RawValue value) {
        if (!supported(key)) throw new IllegalArgumentException("Not a toggle setting");
        if (value.kind() != key.kind) return State.UNKNOWN;
        return switch (value.payload()) { case "true", "1" -> State.ON; case "false", "0" -> State.OFF; default -> State.UNKNOWN; };
    }
    public static RawValue target(ConfigKey key, boolean enabled) {
        if (!supported(key)) throw new IllegalArgumentException("Not a toggle setting");
        return key.kind == RawValue.Kind.BOOLEAN ? RawValue.bool(enabled) : RawValue.integer(enabled ? 1 : 0);
    }
    public static State nrState(RawValue value, int technology) {
        if (technology != 1 && technology != 2) throw new IllegalArgumentException("Unknown NR technology");
        if (value.kind() != RawValue.Kind.INTEGER_ARRAY) return State.UNKNOWN;
        try { ConfigKey.NR_AVAILABILITY.validate(value,33); } catch (IllegalArgumentException error) { return State.UNKNOWN; }
        return Arrays.asList(value.payload().split(",")).contains(Integer.toString(technology)) ? State.ON : State.OFF;
    }
    public static RawValue nrTarget(RawValue current, int technology, boolean enabled) {
        if (nrState(current,technology) == State.UNKNOWN) throw new IllegalArgumentException("NR configuration unreadable");
        Set<String> entries = new TreeSet<>(); if (!current.payload().isEmpty()) entries.addAll(Arrays.asList(current.payload().split(",")));
        if (enabled) entries.add(Integer.toString(technology)); else entries.remove(Integer.toString(technology));
        return new RawValue(RawValue.Kind.INTEGER_ARRAY,String.join(",",entries));
    }
}
