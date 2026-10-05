package io.github.leodenglovescode.ultimateims;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.telephony.*;
import io.github.leodenglovescode.ultimateims.core.ConfigKey;
import java.util.*;

/** 读取器仅访问公共 API。拒绝权限时保持未知，不用缓存或默认值补全。 */
final class FrameworkReader {
    record Sim(int subId, int slot, String carrier, boolean embedded, boolean defaultData,
               String country, Integer dataRat, Long userMask, Long powerMask, Long carrierMask,
               Map<ConfigKey, Object> carrierConfig) { }
    record Snapshot(List<Sim> sims, Boolean validated, Boolean captive, Boolean unmetered,
                    String portalHttp, String portalHttps, boolean portalReadable) { }
    private final Context context;
    FrameworkReader(Context context) { this.context = context.getApplicationContext(); }
    Snapshot read() {
        List<Sim> sims = new ArrayList<>();
        SubscriptionManager subscriptions = context.getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> active;
        try { active = subscriptions.getActiveSubscriptionInfoList(); }
        catch (SecurityException | UnsupportedOperationException e) { active = null; }
        if (active != null) for (SubscriptionInfo info : active) {
            TelephonyManager phone = context.getSystemService(TelephonyManager.class).createForSubscriptionId(info.getSubscriptionId());
            Map<ConfigKey, Object> config = new EnumMap<>(ConfigKey.class);
            try {
                PersistableBundle bundle = context.getSystemService(CarrierConfigManager.class).getConfigForSubId(info.getSubscriptionId());
                // 默认占位 bundle 不是已识别运营商的实时配置，不能当成当前状态。
                if (bundle != null && CarrierConfigManager.isConfigForIdentifiedCarrier(bundle)) for (ConfigKey key : ConfigKey.values()) {
                    if (key.scope == ConfigKey.Scope.CARRIER && bundle.containsKey(key.platformName)) config.put(key, bundle.get(key.platformName));
                }
            } catch (SecurityException | UnsupportedOperationException e) { /* 未知值不补全。 */ }
            String country;
            try { country = phone.getSimCountryIso(); } catch (SecurityException e) { country = null; }
            Integer dataRat;
            try { dataRat = phone.getDataNetworkType(); } catch (SecurityException | UnsupportedOperationException e) { dataRat = null; }
            sims.add(new Sim(info.getSubscriptionId(), info.getSimSlotIndex(), info.getCarrierName().toString(), info.isEmbedded(),
                    info.getSubscriptionId() == SubscriptionManager.getDefaultDataSubscriptionId(), country, dataRat,
                    mask(phone, TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER), FrameworkCompatibility.readPowerMask(phone),
                    mask(phone, TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER), Collections.unmodifiableMap(config)));
        }
        Boolean validated = null, captive = null, unmetered = null;
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities network = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        if (network != null) {
            validated = network.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            captive = network.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL);
            unmetered = network.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        }
        String http = null, https = null;
        boolean portalReadable = false;
        try {
            http = Settings.Global.getString(context.getContentResolver(), "captive_portal_http_url");
            https = Settings.Global.getString(context.getContentResolver(), "captive_portal_https_url");
            portalReadable = true;
        } catch (SecurityException e) { /* 无权限与未设置不同。 */ }
        return new Snapshot(List.copyOf(sims), validated, captive, unmetered, http, https, portalReadable);
    }
    // 此调用也可由运营商权限授权；权限检查与调用之间仍可能撤销，必须捕获异常。
    @android.annotation.SuppressLint("MissingPermission")
    private Long mask(TelephonyManager phone, int reason) {
        try {
            if (context.checkSelfPermission("android.permission.READ_PRIVILEGED_PHONE_STATE") != android.content.pm.PackageManager.PERMISSION_GRANTED && !phone.hasCarrierPrivileges()) return null;
            return phone.getAllowedNetworkTypesForReason(reason);
        }
        catch (SecurityException | UnsupportedOperationException e) { return null; }
    }
    static String format(Object value) {
        if (value instanceof int[] array) return Arrays.toString(array);
        if (value instanceof String[] array) return Arrays.toString(array);
        return String.valueOf(value);
    }
}
