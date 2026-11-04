package io.github.leodenglovescode.ultimateims.backend;

import io.github.leodenglovescode.ultimateims.core.LiveSimIdentityReader;
import java.util.Arrays;

/** 固定的只读身份操作；运行于用户已选择的 shell/root 后端，不提供任意服务或方法调用。 */
final class PrivilegedSimIdentityReader {
    static String read(int subscription) throws Exception {
        int uid = android.os.Process.myUid();
        if (uid != 0 && uid != 2000) throw new SecurityException("Invalid backend identity");
        Object subscriptions = PlatformMethods.service("isub","com.android.internal.telephony.ISub");
        Object info = PlatformMethods.service("iphonesubinfo","com.android.internal.telephony.IPhoneSubInfo");
        // 使用真实后端归属；root 无应用包，不伪造应用、系统或运营商身份。
        String attribution = uid == 2000 ? "com.android.shell" : null;
        return LiveSimIdentityReader.read(new LiveSimIdentityReader.Source() {
            @Override public boolean active(int sub) throws Exception {
                int[] active = (int[])PlatformMethods.invoke(subscriptions,"getActiveSubIdList",int[].class,new Class<?>[]{boolean.class},false);
                return active != null && Arrays.stream(active).anyMatch(value -> value == sub);
            }
            @Override public String serial(int sub) throws Exception {
                return (String)PlatformMethods.invoke(info,"getIccSerialNumberForSubscriber",String.class,new Class<?>[]{int.class,String.class,String.class},sub,attribution,null);
            }
        },subscription);
    }
    private PrivilegedSimIdentityReader() { }
}
