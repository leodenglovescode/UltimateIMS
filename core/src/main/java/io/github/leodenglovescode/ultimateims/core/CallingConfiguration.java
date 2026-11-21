package io.github.leodenglovescode.ultimateims.core;

import java.util.*;

/** 可用性开关的配套设置；用户偏好、开通状态和注册证据始终独立。 */
public final class CallingConfiguration {
    public static Map<ConfigKey,RawValue> target(ConfigKey primary, boolean enabled) {
        if (!Set.of(ConfigKey.VOLTE,ConfigKey.VOWIFI,ConfigKey.VONR,ConfigKey.CROSS_SIM).contains(primary)) return Map.of(primary,ConfigurationToggle.target(primary,enabled));
        Map<ConfigKey,RawValue> result = new EnumMap<>(ConfigKey.class);
        result.put(primary,RawValue.bool(enabled));
        // 关闭只关闭所选可用性；配套显示设置的恢复必须使用各卡保存原值。
        if (enabled) switch (primary) {
            case VOLTE -> { result.put(ConfigKey.EDITABLE_LTE,RawValue.bool(true)); result.put(ConfigKey.HIDE_LTE_SETTING,RawValue.bool(false)); }
            case VOWIFI -> {
                for (ConfigKey key : List.of(ConfigKey.WIFI_ONLY,ConfigKey.EDITABLE_WFC,ConfigKey.EDITABLE_WFC_ROAMING,ConfigKey.WFC_ICON)) result.put(key,RawValue.bool(true));
            }
            case VONR -> result.put(ConfigKey.VONR_VISIBLE,RawValue.bool(true));
            case CROSS_SIM -> result.put(ConfigKey.CROSS_SIM_OPPORTUNISTIC,RawValue.bool(true));
            default -> throw new IllegalArgumentException("Unsupported calling configuration");
        }
        return Map.copyOf(result);
    }
    private CallingConfiguration() { }
}
