package io.github.leodenglovescode.ultimateims.core;

import java.util.List;
import java.util.Objects;

/** 区分无法读取、未设置和显式值；不得用默认布尔值替代原始值。 */
public record RawValue(Kind kind, String payload) {
    public enum Kind { UNKNOWN, UNSET, BOOLEAN, INTEGER, STRING, INTEGER_ARRAY }
    public RawValue {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(payload);
        switch (kind) {
            case UNKNOWN, UNSET -> { if (!payload.isEmpty()) throw new IllegalArgumentException("Invalid empty state"); }
            case BOOLEAN -> { if (!payload.equals("true") && !payload.equals("false")) throw new IllegalArgumentException("Invalid boolean"); }
            case INTEGER -> Integer.parseInt(payload);
            case INTEGER_ARRAY -> { if (!payload.isEmpty()) for (String s : payload.split(",", -1)) Integer.parseInt(s); }
            case STRING -> { }
        }
    }
    public static RawValue unknown() { return new RawValue(Kind.UNKNOWN, ""); }
    public static RawValue unset() { return new RawValue(Kind.UNSET, ""); }
    public static RawValue bool(boolean value) { return new RawValue(Kind.BOOLEAN, Boolean.toString(value)); }
    public static RawValue integer(int value) { return new RawValue(Kind.INTEGER, Integer.toString(value)); }
    public static RawValue string(String value) { return new RawValue(Kind.STRING, value); }
    public static RawValue integers(List<Integer> values) {
        return new RawValue(Kind.INTEGER_ARRAY, values.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(",")));
    }
    public boolean readable() { return kind != Kind.UNKNOWN; }
}
