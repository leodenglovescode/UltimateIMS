package io.github.leodenglovescode.ultimateims.backend;

/** 窄协议不含命令、文件路径、任意服务名或任意键名操作。 */
public final class Protocol {
    public static final int SNAPSHOT = 1, READ = 2, WRITE = 3, RESET_CARRIER = 4, RESTART_IMS = 5, STOP = 6, IDENTITY = 7, APN_READ = 8, APN_APPLY = 9, APN_RESTORE = 10, WRITE_CARRIER_BATCH = 11;
    public static final String PACKAGE = "io.github.leodenglovescode.ultimateims";
    public static final String[] PERMISSIONS = {
        "android.permission.READ_PRIVILEGED_PHONE_STATE", "android.permission.READ_PRECISE_PHONE_STATE",
        "android.permission.MODIFY_PHONE_STATE", "android.permission.WRITE_SECURE_SETTINGS", "android.permission.WRITE_APN_SETTINGS"
    };
    private Protocol() { }
}
