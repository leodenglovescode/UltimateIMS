package io.github.leodenglovescode.ultimateims.backend;

import android.content.Context;
import android.os.*;
import android.provider.Settings;
import android.telephony.*;
import android.telephony.ims.*;
import android.telephony.ims.feature.MmTelFeature;
import io.github.leodenglovescode.ultimateims.core.*;
import java.util.*;
import java.util.concurrent.*;

/** 所有实际框架写入均在应用 UID 的隔离 broker 进程中，依靠短时明确委托。 */
public final class PlatformOperations {
    private final Context context;
    PlatformOperations(Context context) { this.context = context; }
    public Bundle execute(int operation, Bundle args) {
        Bundle result = new Bundle();
        try {
            int sub = args.getInt("sub", -1);
            switch (operation) {
                case Protocol.SNAPSHOT -> result.putAll(snapshot());
                case Protocol.APN_READ, Protocol.APN_APPLY, Protocol.APN_RESTORE -> {
                    verifySim(sub,args.getString("expectedIccid"));
                    if (operation != Protocol.APN_READ) requireConfirmedIdle(sub,args);
                    result.putAll(new ApnOperations(context).execute(operation,sub,args));
                }
                case Protocol.IDENTITY -> result.putString("iccid", identity(sub));
                case Protocol.READ -> {
                    if (sub != -2) verifySim(sub, args.getString("expectedIccid"));
                    putRaw(result, read(sub, key(args)));
                }
                case Protocol.WRITE -> {
                    ConfigKey key = key(args);
                    RawValue value = new RawValue(RawValue.Kind.valueOf(args.getString("kind", "UNKNOWN")), args.getString("value", ""));
                    if ((sub == -2) != (key.scope == ConfigKey.Scope.DEVICE)) throw new SecurityException("Scope mismatch");
                    if (sub != -2) verifySim(sub, args.getString("expectedIccid"));
                    if (!args.getBoolean("restore", false)) key.validate(value, Build.VERSION.SDK_INT);
                    write(sub, key, value);
                    RawValue after = read(sub, key);
                    long deadline = android.os.SystemClock.elapsedRealtime() + 4000;
                    while (!after.equals(value) && android.os.SystemClock.elapsedRealtime() < deadline) { Thread.sleep(100); if (sub != -2) verifySim(sub,args.getString("expectedIccid")); after = read(sub,key); }
                    if (!after.equals(value)) throw new IllegalStateException("Readback mismatch");
                    putRaw(result, after);
                }
                case Protocol.RESET_CARRIER -> {
                    requireConfirmedIdle(sub, args);
                    override(sub, null);
                    result.putString("status", "Reset requested; override deletion cannot be independently verified");
                    result.putBoolean("verified", false);
                }
                case Protocol.RESTART_IMS -> {
                    requireConfirmedIdle(sub, args);
                    int slot = sim(sub).getSimSlotIndex();
                    Object telephony = PlatformMethods.service("phone", "com.android.internal.telephony.ITelephony");
                    PlatformMethods.invoke(telephony, "disableIms", void.class, new Class<?>[]{int.class}, slot);
                    // enable 必须在 finally 尝试；失败结果保留为恢复需要，不反复重启。
                    try { Thread.sleep(300); }
                    finally { PlatformMethods.invoke(telephony, "enableIms", void.class, new Class<?>[]{int.class}, slot); }
                    result.putBoolean("verified", false); result.putString("status", "IMS restart requested; registration must be observed separately");
                }
                default -> throw new IllegalArgumentException("Unsupported operation");
            }
            result.putBoolean("ok", true);
        } catch (Exception e) { result.putBoolean("ok", false); result.putString("error", ActionFailure.describe(e)); }
        return result;
    }
    private static void readFailure(String stage, Exception error) { android.util.Log.w("UltimateIMS", "Read[" + stage + "]: " + PlatformMethods.failure(error)); }
    private ConfigKey key(Bundle arguments) { return ConfigKey.valueOf(arguments.getString("key", "")); }
    // 权限在认证服务内短时委托；拒绝异常由操作边界捕获，不能据此推断值。
    @android.annotation.SuppressLint("MissingPermission")
    private SubscriptionInfo sim(int sub) {
        List<SubscriptionInfo> active = context.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
        if (active != null) for (SubscriptionInfo info : active) if (info.getSubscriptionId() == sub) return info;
        throw new IllegalStateException("Subscription inactive or unreadable");
    }
    // ICCID 仅用于安装私有 HMAC 和换卡校验，不显示、不记录、不导出。
    @android.annotation.SuppressLint({"MissingPermission", "HardwareIds"})
    private String identity(int sub) {
        SubscriptionInfo selected = sim(sub);
        String serial = null;
        try { serial = context.getSystemService(TelephonyManager.class).createForSubscriptionId(sub).getSimSerialNumber(); }
        catch (Exception e) { readFailure("sim-serial",e); }
        return VerifiedSimIdentity.choose(selected.getIccId(),serial);
    }
    private void verifySim(int sub, String expected) {
        String current = identity(sub);
        if (expected == null || !expected.matches("[0-9]{18,22}[Ff]?") || !expected.equals(current)) throw new SecurityException("SIM identity changed or unavailable");
    }
    private void requireConfirmedIdle(int sub, Bundle args) {
        if (!args.getBoolean("confirmed")) throw new SecurityException("Explicit confirmation required");
        verifySim(sub, args.getString("expectedIccid"));
        if (context.getSystemService(TelephonyManager.class).getCallState() != TelephonyManager.CALL_STATE_IDLE) throw new IllegalStateException("A call is active; operation refused");
    }
    // 权限在认证服务内短时委托；拒绝异常由操作边界捕获，不能据此推断值。
    @android.annotation.SuppressLint("MissingPermission")
    private RawValue read(int sub, ConfigKey key) throws Exception {
        if (key.scope == ConfigKey.Scope.DEVICE) {
            if (sub != -2) throw new SecurityException("Device scope required");
            String value = Settings.Global.getString(context.getContentResolver(), key.platformName);
            return value == null ? RawValue.unset() : RawValue.string(value);
        }
        sim(sub);
        if (key.scope == ConfigKey.Scope.SUBSCRIPTION) {
            Object subscriptions = PlatformMethods.service("isub", "com.android.internal.telephony.ISub");
            String value = (String) PlatformMethods.invoke(subscriptions, "getSubscriptionProperty", String.class,
                    new Class<?>[]{int.class, String.class, String.class, String.class}, sub, key.platformName, context.getPackageName(), context.getAttributionTag());
            return value == null || value.isEmpty() ? RawValue.unset() : new RawValue(RawValue.Kind.INTEGER, value);
        }
        PersistableBundle config = context.getSystemService(CarrierConfigManager.class).getConfigForSubId(sub);
        if (config == null || !CarrierConfigManager.isConfigForIdentifiedCarrier(config) || !config.containsKey(key.platformName)) return RawValue.unknown();
        Object value = config.get(key.platformName);
        return switch (key.kind) {
            case BOOLEAN -> value instanceof Boolean v ? RawValue.bool(v) : RawValue.unknown();
            case INTEGER -> value instanceof Integer v ? RawValue.integer(v) : RawValue.unknown();
            case STRING -> value instanceof String v ? RawValue.string(v) : RawValue.unknown();
            case INTEGER_ARRAY -> value instanceof int[] v ? RawValue.integers(Arrays.stream(v).boxed().collect(java.util.stream.Collectors.toList())) : RawValue.unknown();
            default -> RawValue.unknown();
        };
    }
    private void write(int sub, ConfigKey key, RawValue value) throws Exception {
        if (value.kind() == RawValue.Kind.UNKNOWN) throw new IllegalArgumentException("Cannot write unknown value");
        if (key.scope == ConfigKey.Scope.DEVICE) {
            if (!Settings.Global.putString(context.getContentResolver(), key.platformName, value.kind() == RawValue.Kind.UNSET ? null : value.payload())) throw new IllegalStateException("SettingsProvider rejected write");
        } else if (key.scope == ConfigKey.Scope.SUBSCRIPTION) {
            if (value.kind() == RawValue.Kind.UNSET) throw new UnsupportedOperationException("Framework has no verified raw-unset subscription restoration on this build");
            Object subscriptions = PlatformMethods.service("isub", "com.android.internal.telephony.ISub");
            int setting = Integer.parseInt(value.payload());
            if (key == ConfigKey.VOIMS_OPT_IN) PlatformMethods.intField("android.telephony.ims.ProvisioningManager","KEY_VOIMS_OPT_IN_STATUS");
            if (key != ConfigKey.VOIMS_OPT_IN && setting >= 0) {
                Object phone = PlatformMethods.service("phone","com.android.internal.telephony.ITelephony");
                String setter = switch(key) {
                    case ADVANCED_CALLING_USER -> "setAdvancedCallingSettingEnabled";
                    case VT_USER -> "setVtSettingEnabled";
                    case WFC_USER -> "setVoWiFiSettingEnabled";
                    case WFC_USER_ROAMING -> "setVoWiFiRoamingSettingEnabled";
                    case CROSS_SIM_USER -> "setCrossSimCallingEnabled";
                    case WFC_USER_MODE -> "setVoWiFiModeSetting";
                    case WFC_USER_ROAMING_MODE -> "setVoWiFiRoamingModeSetting";
                    case VONR_USER -> "setVoNrEnabled";
                    default -> throw new IllegalArgumentException("Unsupported user setting");
                };
                if (key == ConfigKey.WFC_USER_MODE || key == ConfigKey.WFC_USER_ROAMING_MODE) PlatformMethods.invoke(phone,setter,void.class,new Class<?>[]{int.class,int.class},sub,setting);
                else if (key == ConfigKey.VONR_USER) {
                    int status = (int)PlatformMethods.invoke(phone,setter,int.class,new Class<?>[]{int.class,boolean.class},sub,setting==1);
                    if (status != 0) throw new IllegalStateException("VoNR user preference rejected");
                } else PlatformMethods.invoke(phone,setter,void.class,new Class<?>[]{int.class,boolean.class},sub,setting==1);
            } else PlatformMethods.setSubscriptionProperty(subscriptions,sub,key.platformName,value.payload());
            // 刷新 IMS 的 opt-in 通知；其他订阅字段依靠系统设置服务通知。
            if (key == ConfigKey.VOIMS_OPT_IN && !value.payload().equals("-1")) {
                Object phone = PlatformMethods.service("phone", "com.android.internal.telephony.ITelephony");
                int provisioningKey = PlatformMethods.intField("android.telephony.ims.ProvisioningManager", "KEY_VOIMS_OPT_IN_STATUS");
                int status = (int) PlatformMethods.invoke(phone, "setImsProvisioningInt", int.class, new Class<?>[]{int.class, int.class, int.class}, sub, provisioningKey, Integer.parseInt(value.payload()));
                if (status != 0) throw new IllegalStateException("IMS provisioning notification failed");
            }
        } else {
            if (value.kind() == RawValue.Kind.UNSET) throw new UnsupportedOperationException("Selective override deletion cannot be verified");
            PersistableBundle delta = new PersistableBundle();
            switch (value.kind()) {
                case BOOLEAN -> delta.putBoolean(key.platformName, Boolean.parseBoolean(value.payload()));
                case INTEGER -> delta.putInt(key.platformName, Integer.parseInt(value.payload()));
                case STRING -> delta.putString(key.platformName, value.payload());
                case INTEGER_ARRAY -> delta.putIntArray(key.platformName, value.payload().isEmpty() ? new int[0] : Arrays.stream(value.payload().split(",")).mapToInt(Integer::parseInt).toArray());
                default -> throw new IllegalArgumentException("Unsupported value type");
            }
            override(sub, delta);
        }
    }
    private void override(int sub, PersistableBundle delta) throws Exception {
        PlatformMethods.invoke(context.getSystemService(CarrierConfigManager.class), "overrideConfig", void.class,
                new Class<?>[]{int.class, PersistableBundle.class, boolean.class}, sub, delta, false);
    }
    static void putRaw(Bundle bundle, RawValue value) { bundle.putString("kind", value.kind().name()); bundle.putString("value", value.payload()); }
    // 权限在认证服务内短时委托；拒绝异常由操作边界捕获，不能据此推断值。
    @android.annotation.SuppressLint("MissingPermission")
    private Bundle snapshot() throws Exception {
        Bundle result = new Bundle(); ArrayList<Bundle> sims = new ArrayList<>();
        android.util.Log.i("UltimateIMS", "Read permissions: basic=" + (context.checkSelfPermission("android.permission.READ_PHONE_STATE") == android.content.pm.PackageManager.PERMISSION_GRANTED) + ", privileged=" + (context.checkSelfPermission("android.permission.READ_PRIVILEGED_PHONE_STATE") == android.content.pm.PackageManager.PERMISSION_GRANTED) + ", precise=" + (context.checkSelfPermission("android.permission.READ_PRECISE_PHONE_STATE") == android.content.pm.PackageManager.PERMISSION_GRANTED));
        SubscriptionManager manager = context.getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> active = manager.getActiveSubscriptionInfoList();
        if (active != null) for (SubscriptionInfo info : active) {
            Bundle item = new Bundle(); int sub = info.getSubscriptionId();
            item.putInt("sub", sub); item.putInt("slot", info.getSimSlotIndex()); item.putString("carrier", info.getCarrierName().toString());
            try { item.putString("iccid",identity(sub)); } catch (Exception e) { readFailure("sim-identity",e); } item.putBoolean("embedded", info.isEmbedded()); item.putBoolean("active", true);
            item.putBoolean("defaultData", sub == SubscriptionManager.getDefaultDataSubscriptionId());
            TelephonyManager phone = context.getSystemService(TelephonyManager.class).createForSubscriptionId(sub);
            item.putString("country", phone.getSimCountryIso());
            for (ConfigKey key : ConfigKey.values()) if (key.scope != ConfigKey.Scope.DEVICE) {
                Bundle value = new Bundle();
                try { putRaw(value, read(sub, key)); } catch (Exception e) { putRaw(value, RawValue.unknown()); }
                item.putBundle(key.name(), value);
            }
            for (String reason : List.of("USER", "POWER", "CARRIER")) {
                try {
                    int code = PlatformMethods.intField("android.telephony.TelephonyManager", "ALLOWED_NETWORK_TYPES_REASON_" + reason);
                    item.putLong("mask_" + reason, phone.getAllowedNetworkTypesForReason(code));
                } catch (Exception e) { readFailure("network-policy-" + reason, e); }
            }
            try { item.putInt("dataRat", phone.getDataNetworkType()); item.putInt("voiceRat", phone.getVoiceNetworkType()); item.putInt("callState", phone.getCallState()); } catch (Exception e) { /* 未知。 */ }
            try {
                ServiceState state = (ServiceState) PlatformMethods.invoke(phone,"getServiceState",ServiceState.class,new Class<?>[]{int.class}, PlatformMethods.intField("android.telephony.TelephonyManager","INCLUDE_LOCATION_DATA_NONE"));
                if (state != null) {
                    int nrState = (int)PlatformMethods.invoke(state,"getNrState",int.class,new Class<?>[]{}); item.putInt("nrState",nrState);
                    int connected = PlatformMethods.intField("android.telephony.NetworkRegistrationInfo","NR_STATE_CONNECTED"); item.putBoolean("nrConnected",nrState == connected);
                    int domain = PlatformMethods.intField("android.telephony.NetworkRegistrationInfo","DOMAIN_PS");
                    Object ps = PlatformMethods.invoke(state,"getNetworkRegistrationInfo",NetworkRegistrationInfo.class,new Class<?>[]{int.class,int.class},domain,AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
                    if (ps != null) { item.putBoolean("psRegistered",(boolean)PlatformMethods.invoke(ps,"isNetworkRegistered",boolean.class,new Class<?>[]{})); item.putInt("psRat",(int)PlatformMethods.invoke(ps,"getAccessNetworkTechnology",int.class,new Class<?>[]{})); }
                }
            } catch (Exception e) { readFailure("radio-registration", e); }
            class DisplayObservation extends TelephonyCallback implements TelephonyCallback.DisplayInfoListener {
                final CountDownLatch event = new CountDownLatch(1); volatile TelephonyDisplayInfo display;
                @Override public void onDisplayInfoChanged(TelephonyDisplayInfo value) { display = value; event.countDown(); }
            }
            DisplayObservation display = new DisplayObservation();
            try { phone.registerTelephonyCallback(Runnable::run,display); if (display.event.await(1000,TimeUnit.MILLISECONDS) && display.display != null) { item.putInt("displayBaseRat",display.display.getNetworkType()); item.putInt("displayOverride",display.display.getOverrideNetworkType()); } }
            catch (Exception e) { /* 展示回调不可读就未知，不从配置生成观测。 */ }
            finally { try { phone.unregisterTelephonyCallback(display); } catch (Exception e) { /* 权限撤销时保留未知。 */ } }
            ims(sub, item); sims.add(item);
        }
        result.putParcelableArrayList("sims", sims);
        result.putLong("observedAt",System.currentTimeMillis());
        Bundle portal = new Bundle();
        for (ConfigKey key : List.of(ConfigKey.PORTAL_HTTP,ConfigKey.PORTAL_HTTPS)) {
            Bundle value = new Bundle(); try { putRaw(value,read(-2,key)); } catch (Exception e) { putRaw(value,RawValue.unknown()); } portal.putBundle(key.name(),value);
        }
        result.putBundle("portal",portal);
        try {
            Object all = PlatformMethods.invoke(manager, "getAllSubscriptionInfoList", List.class, new Class<?>[]{});
            ArrayList<Bundle> inactive = new ArrayList<>();
            for (Object entry : (List<?>) all) if (entry instanceof SubscriptionInfo info && (active == null || active.stream().noneMatch(s -> s.getSubscriptionId() == info.getSubscriptionId()))) {
                Bundle item = new Bundle(); item.putInt("sub", info.getSubscriptionId()); item.putString("carrier", info.getCarrierName().toString()); item.putBoolean("embedded", info.isEmbedded()); inactive.add(item);
            }
            result.putParcelableArrayList("inactive", inactive);
        } catch (Exception e) { /* 不从活动列表推断非活动订阅状态。 */ }
        return result;
    }
    // 权限在认证服务内短时委托；拒绝异常由操作边界捕获，不能据此推断值。
    @android.annotation.SuppressLint("MissingPermission")
    private void ims(int sub, Bundle item) {
        Object phone = null;
        try { phone = PlatformMethods.service("phone", "com.android.internal.telephony.ITelephony"); }
        catch (Exception e) { readFailure("ims-service",e); }
        if (phone != null) {
            try { item.putBoolean("imsRegistered", (boolean) PlatformMethods.invoke(phone,"isImsRegistered",boolean.class,new Class<?>[]{int.class},sub)); }
            catch (Exception e) { readFailure("ims-registration-legacy",e); }
            try {
                int nr = PlatformMethods.intField("android.telephony.ims.stub.ImsRegistrationImplBase","REGISTRATION_TECH_NR");
                item.putBoolean("voiceOverNrCapable",(boolean)PlatformMethods.invoke(phone,"isCapable",boolean.class,new Class<?>[]{int.class,int.class,int.class},sub,MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,nr));
                item.putBoolean("voiceOverNrAvailable",(boolean)PlatformMethods.invoke(phone,"isAvailable",boolean.class,new Class<?>[]{int.class,int.class,int.class},sub,MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE,nr));
            } catch (Exception e) { readFailure("vonr-capability",e); }
        }
        ImsMmTelManager ims;
        try { ims = context.getSystemService(android.telephony.ims.ImsManager.class).getImsMmTelManager(sub); }
        catch (Exception e) { readFailure("ims-manager",e); return; }
        // 公共异步状态、传输类型和能力互不阻塞；旧隐藏方法失败不能抹掉其余观测。
        try {
            CountDownLatch stateReceived = new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicInteger state = new java.util.concurrent.atomic.AtomicInteger(-1);
            ims.getRegistrationState(Runnable::run,value -> { state.set(value); stateReceived.countDown(); });
            if (stateReceived.await(1200,TimeUnit.MILLISECONDS) && state.get() != -1) item.putBoolean("imsRegistered",state.get() == RegistrationManager.REGISTRATION_STATE_REGISTERED);
        } catch (Exception e) { readFailure("ims-registration-state",e); }
        try {
            CountDownLatch registrationReceived = new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicInteger technology = new java.util.concurrent.atomic.AtomicInteger(Integer.MIN_VALUE);
            RegistrationManager.RegistrationCallback registration = new RegistrationManager.RegistrationCallback() {
                @Override public void onRegistered(ImsRegistrationAttributes attributes) {
                    try { technology.set((int)PlatformMethods.invoke(attributes,"getRegistrationTechnology",int.class,new Class<?>[]{})); }
                    catch (Exception e) { readFailure("ims-registration-technology",e); }
                    registrationReceived.countDown();
                }
                @Override public void onUnregistered(ImsReasonInfo reason) { registrationReceived.countDown(); }
            };
            ims.registerImsRegistrationCallback(Runnable::run,registration);
            try { if (registrationReceived.await(1200,TimeUnit.MILLISECONDS) && technology.get() != Integer.MIN_VALUE) item.putInt("imsRegistrationTech",technology.get()); }
            finally { ims.unregisterImsRegistrationCallback(registration); }
        } catch (Exception e) { readFailure("ims-registration-callback",e); }
        try {
            CountDownLatch received = new CountDownLatch(1);
            ImsMmTelManager.CapabilityCallback callback = new ImsMmTelManager.CapabilityCallback() {
                @Override public void onCapabilitiesStatusChanged(MmTelFeature.MmTelCapabilities caps) {
                    synchronized(item) { item.putBoolean("voice",caps.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE)); item.putBoolean("video",caps.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO)); item.putBoolean("ut",caps.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT)); }
                    received.countDown();
                }
            };
            ims.registerMmTelCapabilityCallback(Runnable::run,callback);
            try { received.await(1200,TimeUnit.MILLISECONDS); } finally { ims.unregisterMmTelCapabilityCallback(callback); }
        } catch (Exception e) { readFailure("ims-capability-callback",e); }
        try {
            CountDownLatch received = new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicInteger transport = new java.util.concurrent.atomic.AtomicInteger(-1);
            ims.getRegistrationTransportType(Runnable::run,value -> { transport.set(value); received.countDown(); });
            if (received.await(1200,TimeUnit.MILLISECONDS) && transport.get() != -1) item.putInt("imsTransport",transport.get());
        } catch (Exception e) { readFailure("ims-transport",e); }
    }
}
