package io.github.leodenglovescode.ultimateims;

import android.telephony.TelephonyManager;
import java.lang.reflect.*;

/** 隐藏 API 兼容处理的唯一入口。未验证字段或权限不足时保留未知，不猜整数编号。 */
final class FrameworkCompatibility {
    static Long readPowerMask(TelephonyManager phone) {
        try {
            Field field = TelephonyManager.class.getDeclaredField("ALLOWED_NETWORK_TYPES_REASON_POWER");
            if (field.getType() != int.class || !Modifier.isStatic(field.getModifiers())) return null;
            Method method = TelephonyManager.class.getMethod("getAllowedNetworkTypesForReason", int.class);
            if (method.getReturnType() != long.class) return null;
            return (Long) method.invoke(phone, field.getInt(null));
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException e) { return null; }
    }
    private FrameworkCompatibility() { }
}
