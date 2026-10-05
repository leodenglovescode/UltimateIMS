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
    @Test public void selectiveRestoreCannotClearUncertainOtherFields() throws Exception {
        coordinator.apply(volte());
        journal.save(journal.read(sim).state(RecoveryJournal.State.RECOVERY_REQUIRED));
        assertEquals(RecoveryJournal.State.RECOVERY_REQUIRED, coordinator.restoreSelected(request(Map.of(),false),Set.of(ConfigKey.VOIMS_OPT_IN)));
        assertThrows(IllegalStateException.class, () -> coordinator.apply(volte()));
        assertEquals(2,journal.read(sim).originals().size());
    }
    @Test public void selectedRestoreKeepsOtherDesiredTargets() throws Exception {
        coordinator.apply(volte());
        coordinator.restoreSelected(request(Map.of(),false),Set.of(ConfigKey.VOIMS_OPT_IN));
        assertFalse(journal.read(sim).target().containsKey(ConfigKey.VOIMS_OPT_IN));
        assertTrue(journal.read(sim).target().containsKey(ConfigKey.ADVANCED_CALLING_USER));
        assertEquals(2,journal.read(sim).originals().size());
    }
    @Test public void independentDraftsPreserveDesiredConfiguration() throws Exception {
        coordinator.apply(request(Map.of(ConfigKey.VOLTE,RawValue.bool(true)),false));
        coordinator.apply(request(Map.of(ConfigKey.VOWIFI,RawValue.bool(true)),false));
        assertEquals(2,journal.read(sim).target().size()); assertEquals(2,backend.writes);
    }
    @Test public void deviceScopeCannotUseSubscriptionScope() {
        assertThrows(IllegalArgumentException.class, () -> coordinator.apply(request(Map.of(ConfigKey.PORTAL_HTTP,RawValue.string("http://example.com/generate_204")),false)));
        assertEquals(0,backend.writes);
    }
    @Test public void portalUnsetIsRestorableInDeviceScope() throws Exception {
        var req = new OperationCoordinator.Request(12345,BackendSession.Mode.SHIZUKU,session.token(),sim,-2,37,Map.of(ConfigKey.PORTAL_HTTP,RawValue.string("http://example.com/generate_204")),false);
        coordinator.apply(req); coordinator.restore(req); assertEquals(RawValue.unset(),backend.values.get(ConfigKey.PORTAL_HTTP));
    }
    @Test public void wfcPreferenceHasThreeModesAndPreservesMinusOne() {
        for (int mode : List.of(-1,0,1,2)) ConfigKey.WFC_USER_MODE.validate(RawValue.integer(mode),33);
        assertThrows(IllegalArgumentException.class, () -> ConfigKey.WFC_USER_MODE.validate(RawValue.integer(3),33));
    }
    @Test public void signalDomainsAreDistinct() {
        ConfigKey.NR_RSRQ.validate(RawValue.integers(List.of(-43,-20,0,20)),33);
        ConfigKey.NR_SINR.validate(RawValue.integers(List.of(-23,0,20,40)),33);
        assertThrows(IllegalArgumentException.class, () -> ConfigKey.NR_RSRQ.validate(RawValue.integers(List.of(-44,-20,0,20)),33));
        assertThrows(IllegalArgumentException.class, () -> ConfigKey.NR_SINR.validate(RawValue.integers(List.of(-23,0,20,41)),33));
    }
    @Test public void presetsDoNotSilentlyChangeRegionOrPersistentOptIn() {
        for (int api : List.of(33,34,37)) for (var preset : Presets.Name.values()) {
            var values = Presets.build(preset,api); for (var entry : values.entrySet()) entry.getKey().validate(entry.getValue(),api);
            assertFalse(values.containsKey(ConfigKey.SIM_COUNTRY)); assertFalse(values.containsKey(ConfigKey.VOIMS_OPT_IN)); assertFalse(values.containsKey(ConfigKey.WFC_USER));
        }
    }
    @Test public void reviewedApnMatchesOnlyVerifiedCarrier() {
        assertEquals("fast.t-mobile.com",ApnProfile.reviewed("TMOBILE","310260").apn());
        assertEquals("cmnet",ApnProfile.reviewed("CMNET","46000").apn());
        assertThrows(IllegalArgumentException.class, () -> ApnProfile.reviewed("CMNET","310260"));
        assertThrows(IllegalArgumentException.class, () -> ApnProfile.reviewed("TMOBILE","46000"));
        assertThrows(IllegalArgumentException.class, () -> ApnProfile.reviewed("CMNET",""));
        assertThrows(IllegalArgumentException.class, () -> ApnProfile.reviewed("ARBITRARY","46000"));
    }
    @Test public void apnOperationTagCannotInjectProviderSelection() {
        assertThrows(IllegalArgumentException.class, () -> ApnProfile.ownedName("' OR 1=1"));
        assertTrue(ApnProfile.ownedName("12345678-1234-1234-1234-123456789abc").startsWith("UltimateIMS "));
    }
    @Test public void iconMappingDoesNotInventModemCapability() {
        ConfigKey.NR_ICON.validate(RawValue.string("connected_mmwave:5G_Plus,connected:5G"),33);
        assertThrows(IllegalArgumentException.class, () -> ConfigKey.NR_ICON.validate(RawValue.string("connected:5GA"),33));
        assertThrows(IllegalArgumentException.class, () -> ConfigKey.NR_ICON.validate(RawValue.string("connected:5G,connected:None"),33));
    }
    @Test public void apnGroupIsStablePositiveAndBoundToReviewedTag() {
        String tag = "12345678-1234-1234-1234-123456789abc";
        assertTrue(ApnProfile.ownedSetId(tag) > 0); assertEquals(ApnProfile.ownedSetId(tag),ApnProfile.ownedSetId(tag));
        assertNotEquals(ApnProfile.ownedSetId(tag),ApnProfile.ownedSetId("12345678-1234-1234-1234-123456789abd"));
        assertThrows(IllegalArgumentException.class, () -> ApnProfile.ownedSetId("unverified"));
    }
    @Test public void radioObservationRequiresRegistrationRatherThanIcon() {
        assertEquals(RadioObservation.State.UNKNOWN,RadioObservation.classify(null,20,true));
        assertEquals(RadioObservation.State.NOT_REGISTERED,RadioObservation.classify(false,20,true));
        assertEquals(RadioObservation.State.NR_SA,RadioObservation.classify(true,20,null));
        assertEquals(RadioObservation.State.NR_NSA,RadioObservation.classify(true,13,true));
        assertEquals(RadioObservation.State.UNKNOWN,RadioObservation.classify(true,13,null));
        assertEquals(RadioObservation.State.OTHER_REGISTERED,RadioObservation.classify(true,13,false));
    }
    @Test public void emptyResetBackupCannotClaimSuccessfulRestore() throws Exception {
        journal.save(new RecoveryJournal.Entry(sim,RecoveryJournal.State.RECOVERY_REQUIRED,Map.of(),Map.of()));
        assertThrows(IllegalStateException.class, () -> coordinator.restore(request(Map.of(),false)));
        assertTrue(journal.read(sim).requiresRecovery()); assertEquals(0,backend.writes);
    }
    @Test public void httpProbePolicyCannotBroadenToArbitraryOrSubdomainHosts() {
        assertTrue(ProbePolicy.allowsHttp("captive.v2ex.co")); assertTrue(ProbePolicy.allowsHttp("CONNECTIVITYCHECK.GSTATIC.COM"));
        assertFalse(ProbePolicy.allowsHttp("arbitrary.example")); assertFalse(ProbePolicy.allowsHttp("evil.captive.v2ex.co")); assertFalse(ProbePolicy.allowsHttp("captive.v2ex.co.evil.example")); assertFalse(ProbePolicy.allowsHttp(null));
    }
}
