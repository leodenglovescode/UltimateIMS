package io.github.leodenglovescode.ultimateims.backend;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

/** 仅供本地 ADB 读取固定接口元数据；不读取订阅值、不委托权限、不执行配置写入。 */
public final class FrameworkCompatibilityProbe {
    public static void main(String[] arguments) throws Exception {
        if (android.os.Process.myUid() != 2000 && android.os.Process.myUid() != 0) throw new SecurityException("ADB identity required");
        PlatformMethods.enableAccess();
        Set<String> names = Set.of("startDelegateShellPermissionIdentity", "stopDelegateShellPermissionIdentity", "getDelegatedShellPermissions", "overrideConfig", "setVoNrEnabled", "getSubscriptionProperty", "setSubscriptionProperty", "isImsRegistered", "isCapable", "isAvailable", "getServiceState", "getNrState", "getNetworkRegistrationInfo", "isNetworkRegistered", "getAccessNetworkTechnology", "getRegistrationTechnology", "getRegistrationState", "getRegistrationTransportType");
        for (String type : new String[]{"android.app.IActivityManager", "com.android.internal.telephony.ITelephony", "com.android.internal.telephony.ISub", "android.telephony.CarrierConfigManager", "android.telephony.TelephonyManager", "android.telephony.ServiceState", "android.telephony.NetworkRegistrationInfo", "android.telephony.ims.ImsRegistrationAttributes", "android.telephony.ims.ImsMmTelManager"}) {
            for (Method method : Class.forName(type).getDeclaredMethods()) {
                if (names.contains(method.getName())) System.out.println(type + ": " + method.getReturnType().getSimpleName() + " " + method.getName() + Arrays.toString(method.getParameterTypes()));
            }
        }
    }
    private FrameworkCompatibilityProbe() { }
}
