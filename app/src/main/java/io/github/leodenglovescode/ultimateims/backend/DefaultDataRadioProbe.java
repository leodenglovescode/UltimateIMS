package io.github.leodenglovescode.ultimateims.backend;

import android.os.PersistableBundle;
import android.telephony.*;
import java.util.Arrays;

/** 仅只读默认数据卡，不读取 eSIM 配置、标识、位置或呼叫号码；不改变网络选择。 */
public final class DefaultDataRadioProbe {
    public static void main(String[] arguments) throws Exception {
        if (android.os.Process.myUid() != 2000) throw new SecurityException("ADB shell identity required");
        PlatformMethods.enableAccess();
        Object subscriptions = PlatformMethods.service("isub","com.android.internal.telephony.ISub");
        int sub = (int)PlatformMethods.invoke(subscriptions,"getDefaultDataSubId",int.class,new Class<?>[]{});
        SubscriptionInfo record = (SubscriptionInfo)PlatformMethods.invoke(subscriptions,"getActiveSubscriptionInfo",SubscriptionInfo.class,new Class<?>[]{int.class,String.class,String.class},sub,"com.android.shell",null);
        if (record == null || record.isEmbedded()) { System.out.println("Default data physical SIM unavailable; no other SIM queried"); return; }
        Object phone = PlatformMethods.service("phone","com.android.internal.telephony.ITelephony");
        for (String reason : new String[]{"USER","POWER","CARRIER"}) {
            int code = PlatformMethods.intField("android.telephony.TelephonyManager","ALLOWED_NETWORK_TYPES_REASON_"+reason);
            long mask = (long)PlatformMethods.invoke(phone,"getAllowedNetworkTypesForReason",long.class,new Class<?>[]{int.class,int.class},sub,code);
            System.out.println(reason+" allows NR: "+((mask & TelephonyManager.NETWORK_TYPE_BITMASK_NR)!=0));
        }
        for (String name : new String[]{"getVoiceNetworkTypeForSubscriber","getCallStateForSubscription"}) {
            try { System.out.println(name+": "+PlatformMethods.invoke(phone,name,int.class,new Class<?>[]{int.class,String.class,String.class},sub,"com.android.shell",null)); }
            catch (Exception failure) { System.out.println(name+": unavailable ("+failure.getClass().getSimpleName()+")"); }
        }
        for (String name : new String[]{"isImsRegistered","isAdvancedCallingSettingEnabled","isVoNrEnabled"}) {
            try { System.out.println(name+": "+PlatformMethods.invoke(phone,name,boolean.class,new Class<?>[]{int.class},sub)); }
            catch (Exception failure) { System.out.println(name+": unavailable ("+failure.getClass().getSimpleName()+")"); }
        }
        try { System.out.println("IMS registration technology: "+PlatformMethods.invoke(phone,"getImsRegTechnologyForMmTel",int.class,new Class<?>[]{int.class},sub)); }
        catch (Exception failure) { System.out.println("IMS registration technology: unavailable"); }
        int data = (int)PlatformMethods.invoke(phone,"getDataNetworkTypeForSubscriber",int.class,new Class<?>[]{int.class,String.class,String.class},sub,"com.android.shell",null);
        System.out.println("Data RAT code: "+data);
        ServiceState state = (ServiceState)PlatformMethods.invoke(phone,"getServiceStateForSlot",ServiceState.class,new Class<?>[]{int.class,boolean.class,boolean.class,String.class,String.class},record.getSimSlotIndex(),true,true,"com.android.shell",null);
        if (state != null) {
            System.out.println("NR state code: "+PlatformMethods.invoke(state,"getNrState",int.class,new Class<?>[]{}));
            NetworkRegistrationInfo ps = (NetworkRegistrationInfo)PlatformMethods.invoke(state,"getNetworkRegistrationInfo",NetworkRegistrationInfo.class,new Class<?>[]{int.class,int.class},NetworkRegistrationInfo.DOMAIN_PS,AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
            if (ps != null) {
                System.out.println("Packet-service RAT code: "+ps.getAccessNetworkTechnology());
                Object details = PlatformMethods.invoke(ps,"getDataSpecificInfo",Class.forName("android.telephony.DataSpecificRegistrationInfo"),new Class<?>[]{});
                if (details != null) for (String field : new String[]{"isEnDcAvailable","isNrAvailable","isDcNrRestricted"}) {
                    java.lang.reflect.Field flag = details.getClass().getField(field);
                    if (flag.getType() != boolean.class) throw new NoSuchFieldException("NR availability flag signature");
                    System.out.println(field+": "+flag.getBoolean(details));
                }
            }
        }
        Object loader = PlatformMethods.service("carrier_config","com.android.internal.telephony.ICarrierConfigLoader");
        PersistableBundle config = (PersistableBundle)PlatformMethods.invoke(loader,"getConfigForSubIdWithFeature",PersistableBundle.class,new Class<?>[]{int.class,String.class,String.class},sub,"com.android.shell",null);
        for (String key : new String[]{"carrier_nr_availabilities_int_array","vonr_enabled_bool","nr_advanced_threshold_bandwidth_khz_int","include_lte_for_nr_advanced_threshold_bandwidth_bool","additional_nr_advanced_bands_int_array","nr_advanced_capable_pco_id_int","5g_icon_configuration_string","5g_nr_ssrsrp_thresholds_int_array","signal_strength_nr_nsa_use_lte_as_primary_bool"}) {
            Object value = config == null ? null : config.get(key);
            System.out.println(key+": "+(value instanceof int[] array ? Arrays.toString(array) : value == null ? "unknown" : value));
        }
    }
    private DefaultDataRadioProbe() { }
}
