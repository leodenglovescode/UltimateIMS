package io.github.leodenglovescode.ultimateims.core;

import java.util.*;

/** 同卡白名单批量写入；不是跨订阅或跨设置提供者的原子事务。 */
public final class CarrierWriteBatch {
    public static Map<ConfigKey,RawValue> validate(Map<ConfigKey,RawValue> values, int api, boolean restoring, boolean countryConfirmed) {
        if (values.isEmpty()) throw new IllegalArgumentException("Empty carrier batch");
        Map<ConfigKey,RawValue> result = new EnumMap<>(ConfigKey.class);
        for (var item : values.entrySet()) {
            ConfigKey key = Objects.requireNonNull(item.getKey()); RawValue value = Objects.requireNonNull(item.getValue());
            if (key.scope != ConfigKey.Scope.CARRIER || value.kind() != key.kind) throw new IllegalArgumentException("Invalid carrier batch scope or type");
            if (!restoring) {
                key.validate(value,api);
                if (key == ConfigKey.SIM_COUNTRY && !countryConfirmed) throw new SecurityException("Explicit SIM region confirmation required");
            }
            result.put(key,value);
        }
        return Map.copyOf(result);
    }
    private CarrierWriteBatch() { }
}
