package io.github.leodenglovescode.ultimateims.core;

import java.util.regex.*;

/** 只解析应用生成的完整结果行；框架键、日志片段与其他文本不作词语替换。 */
public record OperationResult(int sim, Kind kind) {
    public enum Kind { VERIFIED, RESTORED, PARTIAL, RECOVERY, FAILED, SKIPPED, IMS_REQUESTED, RESET_UNVERIFIED, UNKNOWN }
    public static OperationResult parse(String raw) {
        if (raw == null) return new OperationResult(0,Kind.UNKNOWN);
        if (raw.equals("IMS restart requested; registration must be observed separately")) return new OperationResult(0,Kind.IMS_REQUESTED);
        if (raw.equals("Reset requested; override deletion cannot be independently verified")) return new OperationResult(0,Kind.RESET_UNVERIFIED);
        Matcher sim = Pattern.compile("^SIM ([1-9][0-9]{0,3}): (VERIFIED|RESTORED|PARTIALLY_RESTORED|RECOVERY_REQUIRED|failed or unknown; originals retained|not executed)$").matcher(raw);
        if (sim.matches()) return new OperationResult(Integer.parseInt(sim.group(1)),kind(sim.group(2)));
        Matcher restored = Pattern.compile("^Original restoration: (RESTORED|PARTIALLY_RESTORED|RECOVERY_REQUIRED); backups retained$").matcher(raw);
        return restored.matches() ? new OperationResult(0,kind(restored.group(1))) : new OperationResult(0,Kind.UNKNOWN);
    }
    private static Kind kind(String code) {
        return switch (code) {
            case "VERIFIED" -> Kind.VERIFIED; case "RESTORED" -> Kind.RESTORED; case "PARTIALLY_RESTORED" -> Kind.PARTIAL;
            case "RECOVERY_REQUIRED" -> Kind.RECOVERY; case "failed or unknown; originals retained" -> Kind.FAILED; case "not executed" -> Kind.SKIPPED;
            default -> Kind.UNKNOWN;
        };
    }
}
