package io.github.leodenglovescode.ultimateims.core;

import java.util.Objects;

/** 空草稿没有待恢复的 SIM 绑定；已有草稿仍要求匹配已验证身份。 */
public final class DraftBinding {
    public static boolean requiresRestoredIdentity(boolean hasChanges, String restoredDigest) {
        return hasChanges && restoredDigest != null;
    }
    public static void verifyRestored(String restoredDigest, String currentDigest) {
        if (restoredDigest == null || currentDigest == null || !Objects.equals(restoredDigest,currentDigest)) throw new SecurityException("Draft identity changed");
    }
    private DraftBinding() { }
}
