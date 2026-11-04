package io.github.leodenglovescode.ultimateims.backend;

import android.telephony.SubscriptionInfo;
import io.github.leodenglovescode.ultimateims.core.VerifiedSimIdentity;

/** 本地 ADB 固定只读探针；只输出空值/格式/一致性分类，绝不输出 ICCID、subId 或标识摘要。 */
public final class SimIdentityReadProbe {
    private static String shape(String value) {
        if (value == null) return "null";
        if (value.isEmpty()) return "empty";
        String metadata = " length=" + value.length() + " digits=" + value.matches("[0-9]+") + " hex=" + value.matches("[0-9A-Fa-f]+") + " uniform=" + (value.chars().distinct().limit(2).count() == 1);
        try { VerifiedSimIdentity.choose(null,value); return "accepted" + metadata; }
        catch (SecurityException rejected) {
            return (value.matches("[0-9]+[Ff]?") ? "numeric-length-rejected" : "character-format-rejected") + metadata;
        }
    }
    public static void main(String[] arguments) throws Exception {
        int uid = android.os.Process.myUid();
        if (uid != 0 && uid != 2000) throw new SecurityException("ADB identity required");
        PlatformMethods.enableAccess();
        String attribution = uid == 2000 ? "com.android.shell" : null;
        Object subscriptions = PlatformMethods.service("isub","com.android.internal.telephony.ISub");
        Object serials = PlatformMethods.service("iphonesubinfo","com.android.internal.telephony.IPhoneSubInfo");
        int[] active = (int[])PlatformMethods.invoke(subscriptions,"getActiveSubIdList",int[].class,new Class<?>[]{boolean.class},false);
        System.out.println("Active subscription count: " + (active == null ? "unreadable" : active.length));
        if (active == null) return;
        int index = 0;
        for (int sub : active) {
            index++;
            String serial = null, record = null;
            try {
                serial = (String)PlatformMethods.invoke(serials,"getIccSerialNumberForSubscriber",String.class,new Class<?>[]{int.class,String.class,String.class},sub,attribution,null);
                System.out.println("SIM " + index + " modem serial: " + shape(serial));
            } catch (Exception error) { System.out.println("SIM " + index + " modem serial error: " + PlatformMethods.failure(error)); }
            try {
                SubscriptionInfo info = (SubscriptionInfo)PlatformMethods.invoke(subscriptions,"getActiveSubscriptionInfo",SubscriptionInfo.class,new Class<?>[]{int.class,String.class,String.class},sub,attribution,null);
                record = info == null ? null : info.getIccId();
                System.out.println("SIM " + index + " privileged subscription record: " + shape(record));
            } catch (Exception error) { System.out.println("SIM " + index + " subscription record error: " + PlatformMethods.failure(error)); }
            if (serial != null && record != null) {
                System.out.println("SIM " + index + " sources equal: " + serial.equalsIgnoreCase(record));
                System.out.println("SIM " + index + " modem serial is full-record prefix: " + record.startsWith(serial));
            }
            try {
                String verified = PrivilegedSimIdentityReader.read(sub);
                System.out.println("SIM " + index + " new repeated full-record reader: " + shape(verified));
                System.out.println("SIM " + index + " new reader matches full record: " + (record != null && record.equalsIgnoreCase(verified)));
            } catch (Exception error) { System.out.println("SIM " + index + " new identity reader error: " + PlatformMethods.failure(error)); }
        }
    }
    private SimIdentityReadProbe() { }
}
