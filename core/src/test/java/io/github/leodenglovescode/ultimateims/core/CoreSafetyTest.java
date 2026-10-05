package io.github.leodenglovescode.ultimateims.core;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

/** 使用故障注入验证安全性质；不代表真实手机、IMS 或无线 ADB 已通过测试。 */
public final class CoreSafetyTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final byte[] identityKey = new byte[32];
    private final SimIdentity sim = SimIdentity.verifiedIccid("89860012345678901234", identityKey);
    private BackendSession session;
    private RecoveryJournal journal;
    private FakeBackend backend;
    private OperationCoordinator coordinator;
    @Before public void setup() throws Exception {
        session = new BackendSession(12345, BackendSession.Mode.SHIZUKU);
        journal = new RecoveryJournal(temporary.newFolder().toPath());
        backend = new FakeBackend(); coordinator = new OperationCoordinator(journal, backend, session);
    }
    private final class FakeBackend implements OperationCoordinator.Backend {
        Map<ConfigKey, RawValue> values = new EnumMap<>(ConfigKey.class);
        SimIdentity current = sim;
        boolean timeout, unreadable, mismatch, replaceAfterWrite, revokeAfterWrite;
        int writes;
        @Override public BackendSession.Mode mode() { return BackendSession.Mode.SHIZUKU; }
        @Override public SimIdentity verifiedIdentity(int subId) { return current; }
        @Override public RawValue readRaw(int subId, ConfigKey key) { return unreadable ? RawValue.unknown() : values.getOrDefault(key, RawValue.unset()); }
        @Override public void writeRaw(int subId, ConfigKey key, RawValue value) throws Exception {
            writes++; values.put(key, mismatch ? RawValue.bool(false) : value);
            if (replaceAfterWrite) current = SimIdentity.verifiedIccid("89860012345678901235", identityKey);
            if (revokeAfterWrite) session.revoke();
            if (timeout) throw new java.util.concurrent.TimeoutException("Injected uncertain write");
        }
    }
    private OperationCoordinator.Request request(Map<ConfigKey, RawValue> values, boolean confirmed) {
        return new OperationCoordinator.Request(12345, BackendSession.Mode.SHIZUKU, session.token(), sim, 1, 37, values, confirmed);
    }
    private OperationCoordinator.Request volte() { return request(Map.of(ConfigKey.VOIMS_OPT_IN, RawValue.integer(1), ConfigKey.ADVANCED_CALLING_USER, RawValue.integer(1)), false); }
    @Test public void unknownAndUnsetDiffer() { assertNotEquals(RawValue.unset(), RawValue.unknown()); assertFalse(RawValue.unknown().readable()); }
    @Test public void identityFollowsSimNotSubscriptionNumber() { assertEquals(sim, SimIdentity.verifiedIccid("89860012345678901234", identityKey)); }
    @Test public void identityRejectsMissingOrMaskedIccid() { assertThrows(IllegalArgumentException.class, () -> SimIdentity.verifiedIccid("****1234", identityKey)); }
    @Test public void identityIsInstallationScoped() { byte[] other = identityKey.clone(); other[0] = 1; assertNotEquals(sim, SimIdentity.verifiedIccid("89860012345678901234", other)); }
    @Test public void identityDoesNotExposeIdentifierInText() { assertFalse(sim.toString().contains("898600")); }
    @Test public void sessionRejectsWrongUidAndMode() {
        assertThrows(SecurityException.class, () -> session.authenticate(12346, BackendSession.Mode.SHIZUKU, session.token()));
        assertThrows(SecurityException.class, () -> session.authenticate(12345, BackendSession.Mode.EXISTING_ROOT, session.token()));
    }
    @Test public void sessionRejectsCopiedWrongSecretAndRevocation() {
        byte[] token = session.token(); token[0] ^= 1;
        assertThrows(SecurityException.class, () -> session.authenticate(12345, BackendSession.Mode.SHIZUKU, token));
        final byte[] previous = session.token(); session.revoke();
        assertThrows(SecurityException.class, () -> session.authenticate(12345, BackendSession.Mode.SHIZUKU, previous));
    }
    @Test public void successfulWriteRequiresReadback() throws Exception {
        assertEquals(RecoveryJournal.State.VERIFIED, coordinator.apply(volte()));
        assertEquals(RawValue.unset(), journal.read(sim).originals().get(ConfigKey.VOIMS_OPT_IN));
    }
    @Test public void unknownOriginalPreventsAllWrites() {
        backend.unreadable = true;
        assertThrows(IllegalStateException.class, () -> coordinator.apply(volte())); assertEquals(0, backend.writes);
    }
    @Test public void uncertainTimeoutSurvivesRestart() throws Exception {
        backend.timeout = true;
        assertThrows(java.util.concurrent.TimeoutException.class, () -> coordinator.apply(volte()));
        assertEquals(RecoveryJournal.State.RECOVERY_REQUIRED, journal.read(sim).state());
        coordinator = new OperationCoordinator(journal, backend, session);
        assertThrows(IllegalStateException.class, () -> coordinator.apply(volte()));
    }
    @Test public void restorePreservesUnsetAndKeepsBackup() throws Exception {
        coordinator.apply(volte()); coordinator.restore(request(Map.of(), false));
        assertEquals(RawValue.unset(), backend.values.get(ConfigKey.ADVANCED_CALLING_USER));
        assertEquals(RecoveryJournal.State.RESTORED, journal.read(sim).state()); assertEquals(2, journal.read(sim).originals().size());
    }
    @Test public void repeatedApplyNeverOverwritesOriginal() throws Exception {
        backend.values.put(ConfigKey.VOIMS_OPT_IN, RawValue.integer(-1));
        coordinator.apply(volte()); coordinator.apply(volte());
        assertEquals(RawValue.integer(-1), journal.read(sim).originals().get(ConfigKey.VOIMS_OPT_IN));
    }
    @Test public void failedRestoreKeepsOriginals() throws Exception {
        coordinator.apply(volte()); backend.timeout = true;
        assertThrows(java.util.concurrent.TimeoutException.class, () -> coordinator.restore(request(Map.of(), false)));
        assertEquals(2, journal.read(sim).originals().size()); assertTrue(journal.read(sim).requiresRecovery());
    }
    @Test public void changedSimNeverGetsAnotherCardsBackup() throws Exception {
        coordinator.apply(volte()); int before = backend.writes;
        backend.current = SimIdentity.verifiedIccid("89860012345678901235", identityKey);
        assertThrows(SecurityException.class, () -> coordinator.restore(request(Map.of(), false))); assertEquals(before, backend.writes);
    }
    @Test public void subscriptionRenumberingAllowsVerifiedSameSim() throws Exception {
        coordinator.apply(volte());
        var restore = new OperationCoordinator.Request(12345, BackendSession.Mode.SHIZUKU, session.token(), sim, 99, 37, Map.of(), false);
        assertEquals(RecoveryJournal.State.RESTORED, coordinator.restore(restore));
    }
    @Test public void readbackMismatchIsRecoveryRequired() throws Exception {
        backend.mismatch = true;
        assertThrows(IllegalStateException.class, () -> coordinator.apply(request(Map.of(ConfigKey.VOLTE, RawValue.bool(true)), false)));
        assertTrue(journal.read(sim).requiresRecovery());
    }
    @Test public void simSwapDuringWriteStopsFurtherWrites() throws Exception {
        backend.replaceAfterWrite = true;
        assertThrows(SecurityException.class, () -> coordinator.apply(volte())); assertEquals(1, backend.writes); assertTrue(journal.read(sim).requiresRecovery());
    }
    @Test public void revokedPermissionAfterWriteStopsFurtherWrites() throws Exception {
        backend.revokeAfterWrite = true;
        assertThrows(SecurityException.class, () -> coordinator.apply(volte())); assertEquals(1, backend.writes); assertTrue(journal.read(sim).requiresRecovery());
    }
    @Test public void preparedJournalFromInterruptedProcessBlocksNewApply() throws Exception {
        journal.save(new RecoveryJournal.Entry(sim, RecoveryJournal.State.PREPARED, Map.of(ConfigKey.VOLTE, RawValue.unset()), Map.of(ConfigKey.VOLTE, RawValue.bool(true))));
        assertThrows(IllegalStateException.class, () -> coordinator.apply(volte())); assertEquals(0, backend.writes);
    }
    @Test public void journalCannotDiscardOrReplaceOriginals() throws Exception {
        coordinator.apply(volte()); var old = journal.read(sim);
        assertThrows(IOException.class, () -> journal.save(new RecoveryJournal.Entry(sim, old.state(), Map.of(), old.target())));
    }
    @Test public void corruptJournalFailsClosed() throws Exception {
        Path directory = temporary.newFolder().toPath(); Files.write(directory.resolve(sim.digest() + ".journal"), new byte[]{1,2,3});
        assertThrows(IOException.class, () -> new RecoveryJournal(directory).read(sim));
    }
    @Test public void countryOverrideRequiresConfirmationAndStableTarget() throws Exception {
        var values = Map.of(ConfigKey.SIM_COUNTRY, RawValue.string("123"));
        assertThrows(SecurityException.class, () -> coordinator.apply(request(values, false))); assertEquals(0, backend.writes);
        backend.values.put(ConfigKey.SIM_COUNTRY, RawValue.string("cn"));
        coordinator.apply(request(values, true)); assertEquals("123", journal.read(sim).target().get(ConfigKey.SIM_COUNTRY).payload());
        coordinator.restore(request(Map.of(), false)); assertEquals(RawValue.string("cn"), backend.values.get(ConfigKey.SIM_COUNTRY));
    }
    @Test public void android13CountryOverrideNotAssumedSupported() { assertThrows(IllegalArgumentException.class, () -> ConfigKey.SIM_COUNTRY.validate(RawValue.string("123"), 33)); }
    @Test public void nrConfigurationSeparatesNsaAndSa() {
        ConfigKey.NR_AVAILABILITY.validate(RawValue.integers(List.of(1)),37); ConfigKey.NR_AVAILABILITY.validate(RawValue.integers(List.of(2)),37);
        assertThrows(IllegalArgumentException.class, () -> ConfigKey.NR_AVAILABILITY.validate(RawValue.integers(List.of(1,1)),37));
    }
    @Test public void thresholdsAndWfcModesAreBounded() {
        ConfigKey.NR_RSRP.validate(RawValue.integers(List.of(-120,-110,-100,-90)),37);
        assertThrows(IllegalArgumentException.class, () -> ConfigKey.NR_RSRP.validate(RawValue.integers(List.of(-120,-100,-110,-90)),37));
        assertThrows(IllegalArgumentException.class, () -> ConfigKey.WFC_MODE.validate(RawValue.integer(3),37));
    }
    @Test public void portalRejectsSecretsAndWrongScheme() {
        ConfigKey.PORTAL_HTTP.validate(RawValue.string("http://example.com/generate_204"),37);
        assertThrows(IllegalArgumentException.class, () -> ConfigKey.PORTAL_HTTPS.validate(RawValue.string("https://secret:pass@example.com"),37));
    }
    @Test public void powerPolicyOverridesOtherwiseAllowedNr() { assertEquals(NetworkAssessment.Finding.POWER_EXCLUDES_NR, NetworkAssessment.assess(1L<<19,0L,1L<<19)); }
    @Test public void unreadablePolicyStaysUnknown() { assertEquals(NetworkAssessment.Finding.UNKNOWN, NetworkAssessment.assess(1L<<19,null,1L<<19)); }
}
