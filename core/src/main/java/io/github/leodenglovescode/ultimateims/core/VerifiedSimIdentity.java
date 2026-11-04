package io.github.leodenglovescode.ultimateims.core;

/** 两个实时框架读源可相互校验；均不可读或相互矛盾时拒绝，绝不使用 subId 代替 SIM 身份。 */
public final class VerifiedSimIdentity {
    private static boolean valid(String value) { return value != null && value.matches("[0-9]{18,22}[Ff]?"); }
    public static String choose(String subscriptionIccid, String telephonyIccid) {
        boolean subscription = valid(subscriptionIccid), telephony = valid(telephonyIccid);
        if (subscription && telephony && !subscriptionIccid.equalsIgnoreCase(telephonyIccid)) throw new SecurityException("SIM identity changed or unavailable");
        if (telephony) return telephonyIccid;
        if (subscription) return subscriptionIccid;
        throw new SecurityException("Verified SIM identity unavailable");
    }
    private VerifiedSimIdentity() { }
}
