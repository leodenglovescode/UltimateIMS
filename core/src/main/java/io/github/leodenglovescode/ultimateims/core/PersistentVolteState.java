package io.github.leodenglovescode.ultimateims.core;

/** 开关状态来自实时订阅值；备份只决定能否恢复，不充当当前启用证据。 */
public final class PersistentVolteState {
    public enum State { ON, OFF, UNKNOWN }
    public static State read(RawValue optIn, RawValue userPreference) {
        if (optIn == null || userPreference == null || optIn.kind() != RawValue.Kind.INTEGER || userPreference.kind() != RawValue.Kind.INTEGER) return State.UNKNOWN;
        for (RawValue value : new RawValue[]{optIn,userPreference}) if (!value.payload().matches("-1|0|1")) return State.UNKNOWN;
        return optIn.payload().equals("1") && userPreference.payload().equals("1") ? State.ON : State.OFF;
    }
    public static boolean originalsRestorable(java.util.Map<ConfigKey,RawValue> originals) {
        return read(originals.get(ConfigKey.VOIMS_OPT_IN),originals.get(ConfigKey.ADVANCED_CALLING_USER)) != State.UNKNOWN;
    }
    public static boolean canToggle(State state, boolean backendReady, boolean busy, boolean recoveryRequired, boolean originalsSaved) {
        return backendReady && !busy && !recoveryRequired && state != State.UNKNOWN && (state != State.ON || originalsSaved);
    }
    private PersistentVolteState() { }
}
