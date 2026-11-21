package io.github.leodenglovescode.ultimateims.core;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.util.*;
import java.util.concurrent.TimeoutException;
import static org.junit.Assert.*;

/** 批量边界、持久原值和中断恢复测试；不代表基带注册通过。 */
public final class CarrierBatchTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private final SimIdentity sim = SimIdentity.verifiedIccid("89860012345678901234",new byte[32]);
    private RecoveryJournal journal;
    private BackendSession session;
    private Device device;
    private OperationCoordinator engine;
    private final Map<ConfigKey,RawValue> radio = Map.of(ConfigKey.NR_AVAILABILITY,RawValue.integers(List.of(1,2)),ConfigKey.VONR,RawValue.bool(true));
    @Before public void setup() throws Exception {
        journal = new RecoveryJournal(directory.newFolder().toPath());
        session = new BackendSession(12345,BackendSession.Mode.SHIZUKU);
        device = new Device(); engine = new OperationCoordinator(journal,device,session);
        device.values.put(ConfigKey.NR_AVAILABILITY,RawValue.integers(List.of()));
        device.values.put(ConfigKey.VONR,RawValue.bool(false));
        device.values.put(ConfigKey.ADVANCED_CALLING_USER,RawValue.integer(0));
    }
    private OperationCoordinator.Request request(Map<ConfigKey,RawValue> target) {
        return new OperationCoordinator.Request(12345,BackendSession.Mode.SHIZUKU,session.token(),sim,1,37,target,false);
    }
    private final class Device implements OperationCoordinator.Backend {
        final Map<ConfigKey,RawValue> values = new EnumMap<>(ConfigKey.class);
        Map<ConfigKey,RawValue> lastBatch;
        int batches, singles;
        boolean timeout, mismatch, replaced, replaceAfterBatch, revokeAfterBatch, changeCarrierAfterUserWrite;
        public BackendSession.Mode mode() { return BackendSession.Mode.SHIZUKU; }
        public SimIdentity verifiedIdentity(int sub) { return replaced ? SimIdentity.verifiedIccid("89860012345678901235",new byte[32]) : sim; }
        public RawValue readRaw(int sub, ConfigKey key) { return values.getOrDefault(key,RawValue.unset()); }
        public void writeCarrierBatch(int sub, Map<ConfigKey,RawValue> target) throws Exception {
            assertEquals(1,sub); batches++;
            var saved = journal.read(sim);
            assertTrue(saved.requiresRecovery());
            assertTrue(saved.originals().keySet().containsAll(target.keySet()));
            lastBatch = Map.copyOf(target); values.putAll(target);
            if (mismatch) values.put(ConfigKey.VONR,RawValue.bool(false));
            if (replaceAfterBatch) replaced = true;
            if (revokeAfterBatch) session.revoke();
            if (timeout) throw new TimeoutException("Injected uncertain batch result");
        }
        public void writeRaw(int sub, ConfigKey key, RawValue value) {
            assertNotEquals(ConfigKey.Scope.CARRIER,key.scope); singles++; values.put(key,value);
            if (changeCarrierAfterUserWrite) values.put(ConfigKey.VONR,RawValue.bool(false));
        }
    }
    @Test public void carrierTargetsUseOneSubmissionAndSaveAllOriginalsFirst() throws Exception {
        Map<ConfigKey,RawValue> expectedOriginals = new EnumMap<>(device.values);
        var target = new EnumMap<>(radio); target.put(ConfigKey.ADVANCED_CALLING_USER,RawValue.integer(1));
        assertEquals(RecoveryJournal.State.VERIFIED,engine.apply(request(target)));
        assertEquals(1,device.batches); assertEquals(1,device.singles); assertEquals(radio,device.lastBatch);
        assertEquals(expectedOriginals,journal.read(sim).originals());
    }
    @Test public void uncertainBatchNeverFallsBackToIndividualWrites() throws Exception {
        device.timeout = true;
        assertThrows(TimeoutException.class,() -> engine.apply(request(radio)));
        assertEquals(1,device.batches); assertEquals(0,device.singles);
        assertEquals(RecoveryJournal.State.RECOVERY_REQUIRED,journal.read(sim).state());
        assertEquals(RawValue.bool(false),journal.read(sim).originals().get(ConfigKey.VONR));
        engine = new OperationCoordinator(journal,device,session);
        assertThrows(IllegalStateException.class,() -> engine.apply(request(radio)));
        assertEquals(1,device.batches);
    }
    @Test public void everyCarrierFieldMustMatchBeforeUserSettingsAreWritten() throws Exception {
        device.mismatch = true;
        var target = new EnumMap<>(radio); target.put(ConfigKey.ADVANCED_CALLING_USER,RawValue.integer(1));
        assertThrows(IllegalStateException.class,() -> engine.apply(request(target)));
        assertEquals(0,device.singles); assertEquals(RecoveryJournal.State.RECOVERY_REQUIRED,journal.read(sim).state());
    }
    @Test public void simReplacementAfterSubmissionStopsRemainingWrites() {
        device.replaceAfterBatch = true;
        var target = new EnumMap<>(radio); target.put(ConfigKey.ADVANCED_CALLING_USER,RawValue.integer(1));
        assertThrows(SecurityException.class,() -> engine.apply(request(target))); assertEquals(0,device.singles);
    }
    @Test public void permissionLossAfterSubmissionStopsRemainingWrites() {
        device.revokeAfterBatch = true;
        assertThrows(SecurityException.class,() -> engine.apply(request(radio)));
    }
    @Test public void finalWholeSetReadbackDetectsLaterSideEffects() throws Exception {
        device.changeCarrierAfterUserWrite = true;
        var target = new EnumMap<>(radio); target.put(ConfigKey.ADVANCED_CALLING_USER,RawValue.integer(1));
        assertThrows(IllegalStateException.class,() -> engine.apply(request(target)));
        assertEquals(RecoveryJournal.State.RECOVERY_REQUIRED,journal.read(sim).state());
    }
    @Test public void selectedRestoreBatchesOnlySelectedOriginalsAndKeepsBackups() throws Exception {
        engine.apply(request(radio));
        assertEquals(RecoveryJournal.State.PARTIALLY_RESTORED,engine.restoreSelected(request(Map.of()),Set.of(ConfigKey.VONR)));
        assertEquals(Map.of(ConfigKey.VONR,RawValue.bool(false)),device.lastBatch);
        assertEquals(radio.get(ConfigKey.NR_AVAILABILITY),device.values.get(ConfigKey.NR_AVAILABILITY));
        assertEquals(2,journal.read(sim).originals().size());
    }
    @Test public void addingCompanionFieldsAfterUpgradeRetainsOlderOriginalsAndTargets() throws Exception {
        engine.apply(request(radio));
        device.values.put(ConfigKey.VONR_VISIBLE,RawValue.bool(false));
        engine.apply(request(CallingConfiguration.target(ConfigKey.VONR,true)));
        var saved = journal.read(sim);
        assertEquals(3,saved.originals().size());
        assertEquals(RawValue.bool(false),saved.originals().get(ConfigKey.VONR));
        assertEquals(RawValue.bool(false),saved.originals().get(ConfigKey.VONR_VISIBLE));
        assertEquals(RawValue.integers(List.of()),saved.originals().get(ConfigKey.NR_AVAILABILITY));
        assertEquals(radio.get(ConfigKey.NR_AVAILABILITY),saved.target().get(ConfigKey.NR_AVAILABILITY));
    }
    @Test public void subscriptionOnlyOperationNeverUsesCarrierOverride() throws Exception {
        engine.apply(request(Map.of(ConfigKey.ADVANCED_CALLING_USER,RawValue.integer(1))));
        assertEquals(0,device.batches); assertEquals(1,device.singles);
    }
    @Test public void batchRejectsWrongScopesUnreadableAndUnsetValues() {
        for (var target : List.of(Map.of(ConfigKey.WFC_USER,RawValue.integer(1)),Map.of(ConfigKey.PORTAL_HTTP,RawValue.string("http://example.com")),Map.of(ConfigKey.VONR,RawValue.unknown()),Map.of(ConfigKey.VONR,RawValue.unset()),Map.of(ConfigKey.VONR,RawValue.integer(1)))) {
            assertThrows(IllegalArgumentException.class,() -> CarrierWriteBatch.validate(target,37,false,false));
        }
    }
    @Test public void simRegionNeedsExplicitConfirmationEvenInBatch() {
        var target = Map.of(ConfigKey.SIM_COUNTRY,RawValue.string("123"));
        assertThrows(SecurityException.class,() -> CarrierWriteBatch.validate(target,37,false,false));
        assertEquals(target,CarrierWriteBatch.validate(target,37,false,true));
    }
    @Test public void rawRestoreDoesNotCoerceOldCarrierOriginals() {
        var target = Map.of(ConfigKey.SIM_COUNTRY,RawValue.string("cn"));
        assertThrows(IllegalArgumentException.class,() -> CarrierWriteBatch.validate(target,37,false,true));
        assertEquals(target,CarrierWriteBatch.validate(target,37,true,false));
    }
}
