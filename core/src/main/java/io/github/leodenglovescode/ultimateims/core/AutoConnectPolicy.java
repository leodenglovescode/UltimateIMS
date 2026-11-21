package io.github.leodenglovescode.ultimateims.core;

/** 仅自动连接已选、运行中且已授权的 Shizuku；不请求权限、不启动 root/ADB。 */
public final class AutoConnectPolicy {
    public enum Decision { NONE, WAIT_FOR_SHIZUKU, NEED_PERMISSION, CONNECT, MANUAL_PRIVATE_START, RECOVERY_REQUIRED }
    public static Decision decide(BackendSession.Mode selected, boolean enabled, boolean foreground,
                                  boolean ready, boolean occupied, boolean uncertain, boolean shizukuRunning, boolean authorized) {
        if (!enabled || !foreground || selected == null || ready) return Decision.NONE;
        if (uncertain) return Decision.RECOVERY_REQUIRED;
        if (occupied) return Decision.NONE;
        if (selected != BackendSession.Mode.SHIZUKU) return Decision.MANUAL_PRIVATE_START;
        if (!shizukuRunning) return Decision.WAIT_FOR_SHIZUKU;
        if (!authorized) return Decision.NEED_PERMISSION;
        return Decision.CONNECT;
    }
    private AutoConnectPolicy() { }
}
