package io.github.leodenglovescode.ultimateims.core;

/** 只解释已读取的分组注册；展示图标和IMS语音能力都不能替代通话技术证据。 */
public final class RadioObservation {
    public enum State { UNKNOWN, NOT_REGISTERED, NR_SA, NR_NSA, OTHER_REGISTERED }
    public static State classify(Boolean registered, Integer packetRat, Boolean secondaryNrConnected) {
        if (registered == null) return State.UNKNOWN;
        if (!registered) return State.NOT_REGISTERED;
        if (packetRat == null) return State.UNKNOWN;
        if (packetRat == 20) return State.NR_SA;
        if (packetRat == 13 && Boolean.TRUE.equals(secondaryNrConnected)) return State.NR_NSA;
        if (packetRat == 13 && secondaryNrConnected == null) return State.UNKNOWN;
        return State.OTHER_REGISTERED;
    }
    private RadioObservation() { }
}
