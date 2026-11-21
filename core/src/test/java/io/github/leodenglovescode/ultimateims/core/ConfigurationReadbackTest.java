package io.github.leodenglovescode.ultimateims.core;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** 用可控单调时钟验证异步合并与身份边界，不真实等待。 */
public final class ConfigurationReadbackTest {
    private final Map<ConfigKey,RawValue> expected = Map.of(ConfigKey.VONR,RawValue.bool(true),ConfigKey.VOLTE,RawValue.bool(true));
    @Test public void waitsForWholeSnapshotToConverge() throws Exception {
        long[] now = {0}; AtomicInteger guards = new AtomicInteger();
        ConfigurationReadback.await(expected,key -> now[0] >= 200 ? RawValue.bool(true) : RawValue.unknown(),guards::incrementAndGet,() -> now[0],delay -> now[0]+=delay,4000);
        assertEquals(200,now[0]); assertEquals(6,guards.get());
    }
    @Test public void partialMatchesFromDifferentReadsCannotBeCombined() {
        long[] now = {0};
        assertThrows(IllegalStateException.class,() -> ConfigurationReadback.await(expected,
            key -> RawValue.bool((now[0]/100 % 2 == 0) == (key == ConfigKey.VONR)),() -> {},() -> now[0],delay -> now[0]+=delay,300));
        assertEquals(300,now[0]);
    }
    @Test public void simChangeAfterMatchingReadIsStillRefused() {
        AtomicInteger guards = new AtomicInteger();
        assertThrows(SecurityException.class,() -> ConfigurationReadback.await(expected,key -> RawValue.bool(true),
            () -> { if (guards.incrementAndGet() == 2) throw new SecurityException("SIM changed"); },() -> 0,delay -> fail("Unexpected sleep"),4000));
    }
    @Test public void permissionLossIsNotRetriedOrReportedAsMismatch() {
        assertThrows(SecurityException.class,() -> ConfigurationReadback.await(expected,key -> { throw new SecurityException("Permission lost"); },
            () -> {},() -> 0,delay -> fail("Unexpected retry"),4000));
    }
    @Test public void matchingReadAfterDeadlineCannotReportSuccess() {
        long[] now = {0};
        assertThrows(IllegalStateException.class,() -> ConfigurationReadback.await(expected,key -> { now[0]+=3000; return RawValue.bool(true); },
            () -> {},() -> now[0],delay -> fail("Unexpected retry"),4000));
    }
    @Test public void unknownExpectedValueCannotVerifyAnotherUnknown() {
        assertThrows(IllegalArgumentException.class,() -> ConfigurationReadback.await(Map.of(ConfigKey.VONR,RawValue.unknown()),
            key -> { fail("Unreadable target should be rejected first"); return RawValue.unknown(); },() -> {},() -> 0,delay -> {},4000));
    }
}
