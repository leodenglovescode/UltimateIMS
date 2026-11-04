package io.github.leodenglovescode.ultimateims.core;

import java.util.Locale;

/** 接受框架完整 ICCID 的十进制旧格式与 10 字节十六进制格式；短截断值不能充当身份。 */
public final class VerifiedSimIdentity {
    public static boolean valid(String value) {
        if (value == null) return false;
        boolean format = value.matches("[0-9]{18,22}[Ff]?") || value.matches("[0-9A-Fa-f]{20}");
        // 常见空卡/占位内容不能成为恢复目标。
        return format && !value.matches("0+[Ff]*") && !value.matches("[Ff]+");
    }
    public static String canonical(String value) {
        if (!valid(value)) throw new SecurityException("Verified SIM identity unavailable");
        return value.toUpperCase(Locale.ROOT);
    }
    public static String choose(String subscriptionIccid, String telephonyIccid) {
        boolean subscription = valid(subscriptionIccid), telephony = valid(telephonyIccid);
        if (subscription && telephony && !subscriptionIccid.equalsIgnoreCase(telephonyIccid)) throw new SecurityException("SIM identity changed or unavailable");
        if (telephony) return telephonyIccid;
        if (subscription) return subscriptionIccid;
        throw new SecurityException("Verified SIM identity unavailable");
    }
    private VerifiedSimIdentity() { }
}
