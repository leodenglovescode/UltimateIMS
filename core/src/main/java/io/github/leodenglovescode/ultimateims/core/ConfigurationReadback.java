package io.github.leodenglovescode.ultimateims.core;

import java.util.Map;
import java.util.function.LongSupplier;

/** 有限等待整个集合可读且相符；每轮前后重新验证身份，不把分次成功拼成成功。 */
public final class ConfigurationReadback {
    @FunctionalInterface public interface Reader { RawValue read(ConfigKey key) throws Exception; }
    @FunctionalInterface public interface Guard { void check() throws Exception; }
    @FunctionalInterface public interface Sleeper { void sleep(long millis) throws Exception; }
    public static void await(Map<ConfigKey,RawValue> expected, Reader reader, Guard guard,
                             LongSupplier clock, Sleeper sleeper, long timeoutMillis) throws Exception {
        if (expected.isEmpty() || timeoutMillis < 1 || timeoutMillis > 10000) throw new IllegalArgumentException("Invalid verification bounds");
        expected = Map.copyOf(expected);
        if (expected.values().stream().anyMatch(value -> !value.readable())) throw new IllegalArgumentException("Unknown verification target");
        long started = clock.getAsLong();
        while (true) {
            guard.check(); boolean matches = true;
            for (var item : expected.entrySet()) if (!item.getValue().equals(reader.read(item.getKey()))) matches = false;
            guard.check();
            long elapsed = clock.getAsLong() - started;
            if (elapsed > timeoutMillis) throw new IllegalStateException("Configuration readback timed out; recovery required");
            if (matches) return;
            if (elapsed == timeoutMillis) throw new IllegalStateException("Configuration readback mismatch; recovery required");
            sleeper.sleep(Math.min(100,timeoutMillis-elapsed));
        }
    }
    private ConfigurationReadback() { }
}
