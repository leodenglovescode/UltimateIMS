package io.github.leodenglovescode.ultimateims.core;

/** 只判定明确的 NR 掩码证据，不把省电状态本身当作禁用 NR 的证据。 */
public final class NetworkAssessment {
    public enum Finding { UNKNOWN, POWER_EXCLUDES_NR, USER_EXCLUDES_NR, CARRIER_EXCLUDES_NR, ALL_READ_MASKS_ALLOW_NR }
    private static final long NR_MASK = 1L << 19;
    public static Finding assess(Long user, Long power, Long carrier) {
        if (power != null && (power & NR_MASK) == 0) return Finding.POWER_EXCLUDES_NR;
        if (user != null && (user & NR_MASK) == 0) return Finding.USER_EXCLUDES_NR;
        if (carrier != null && (carrier & NR_MASK) == 0) return Finding.CARRIER_EXCLUDES_NR;
        if (user == null || power == null || carrier == null) return Finding.UNKNOWN;
        return Finding.ALL_READ_MASKS_ALLOW_NR;
    }
    private NetworkAssessment() { }
}
