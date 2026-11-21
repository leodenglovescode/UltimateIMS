package io.github.leodenglovescode.ultimateims.core;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** 复合显示预设必须可审阅、保留其他草稿，并按各卡原值恢复。 */
public class RadioDisplayProfilesTest {
    @Test public void advancedDisplayIncludesEligibilityAndBandwidthGates() {
        var target = RadioDisplayProfiles.target(RadioDisplayProfiles.Name.NR_ADVANCED);
        assertEquals(Set.of(ConfigKey.NR_ICON,ConfigKey.NR_ADVANCED_BANDWIDTH,ConfigKey.NR_ADVANCED_BANDS,ConfigKey.NR_ADVANCED_PCO,ConfigKey.NR_ADVANCED_INCLUDE_LTE),target.keySet());
        assertEquals(RawValue.integer(110000),target.get(ConfigKey.NR_ADVANCED_BANDWIDTH));
        assertFalse(target.containsKey(ConfigKey.NR_AVAILABILITY)); assertFalse(target.containsKey(ConfigKey.VONR));
    }
    @Test public void signalProfileLeavesCarrierMeasurementAndNsaSourceUnchanged() {
        var target = RadioDisplayProfiles.target(RadioDisplayProfiles.Name.MAINLAND_BARS);
        assertEquals(RawValue.integers(List.of(-128,-118,-108,-98)),target.get(ConfigKey.NR_RSRP));
        assertFalse(target.containsKey(ConfigKey.NR_SIGNAL_METRICS));
        assertFalse(target.containsKey(ConfigKey.NR_NSA_LTE_SIGNAL));
    }
    @Test public void noUnrelatedCarrierOrSimRegionChanges() {
        for (var name : RadioDisplayProfiles.Name.values()) for (var key : RadioDisplayProfiles.target(name).keySet()) {
            assertEquals(ConfigKey.Scope.CARRIER,key.scope); assertNotEquals(ConfigKey.SIM_COUNTRY,key); assertNotEquals(ConfigKey.VOIMS_OPT_IN,key);
        }
    }
    @Test public void profileValuesValidateOnSupportedApis() {
        for (int api : List.of(33,34,37)) for (var name : RadioDisplayProfiles.Name.values()) RadioDisplayProfiles.target(name).forEach((key,value) -> key.validate(value,api));
    }
    @Test public void onStateRequiresAllValuesToMatch() {
        var name = RadioDisplayProfiles.Name.MAINLAND_BARS;
        Map<ConfigKey,RawValue> values = new EnumMap<>(RadioDisplayProfiles.target(name));
        assertEquals(RadioDisplayProfiles.State.ON,RadioDisplayProfiles.state(name,values));
        values.put(ConfigKey.NR_RSRP,RawValue.integers(List.of(-110,-100,-90,-80)));
        assertEquals(RadioDisplayProfiles.State.OFF,RadioDisplayProfiles.state(name,values));
    }
    @Test public void missingOrUnknownValuesAreNeverInferredFromHistory() {
        var name = RadioDisplayProfiles.Name.MAINLAND_BARS;
        assertEquals(RadioDisplayProfiles.State.UNKNOWN,RadioDisplayProfiles.state(name,Map.of()));
        Map<ConfigKey,RawValue> values = new EnumMap<>(RadioDisplayProfiles.target(name)); values.put(ConfigKey.NR_RSRP,RawValue.unknown());
        assertEquals(RadioDisplayProfiles.State.UNKNOWN,RadioDisplayProfiles.state(name,values));
    }
    @Test public void unreadableValueWinsOverEarlierMismatch() {
        var name = RadioDisplayProfiles.Name.NR_ADVANCED;
        assertEquals(RadioDisplayProfiles.State.UNKNOWN,RadioDisplayProfiles.state(name,Map.of(ConfigKey.NR_ADVANCED_BANDWIDTH,RawValue.integer(0),ConfigKey.NR_ICON,RawValue.unknown())));
    }
    @Test public void cancellingStagedProfilePreservesUnrelatedAndEarlierCustomDraft() {
        var name = RadioDisplayProfiles.Name.NR_ADVANCED;
        Map<ConfigKey,RawValue> draft = new EnumMap<>(ConfigKey.class);
        draft.put(ConfigKey.VOLTE,RawValue.bool(true)); draft.put(ConfigKey.NR_ADVANCED_BANDWIDTH,RawValue.integer(50000));
        var previous = RadioDisplayProfiles.overlayBeforeEnable(name,draft); draft.putAll(RadioDisplayProfiles.target(name));
        var cancelled = RadioDisplayProfiles.cancelStaged(name,draft,previous);
        assertEquals(RawValue.bool(true),cancelled.get(ConfigKey.VOLTE)); assertEquals(RawValue.integer(50000),cancelled.get(ConfigKey.NR_ADVANCED_BANDWIDTH));
        assertFalse(cancelled.containsKey(ConfigKey.NR_ICON));
    }
    @Test public void originalRestoreIsSelective() {
        var original = originals(0, ""); original.put(ConfigKey.VOLTE,RawValue.bool(false));
        var restored = RadioDisplayProfiles.savedOriginals(RadioDisplayProfiles.Name.NR_ADVANCED,original);
        assertEquals(5,restored.size()); assertFalse(restored.containsKey(ConfigKey.VOLTE));
    }
    @Test public void eachSimKeepsItsOwnOriginalValues() {
        var name = RadioDisplayProfiles.Name.NR_ADVANCED;
        var first = RadioDisplayProfiles.savedOriginals(name,originals(0, ""));
        var second = RadioDisplayProfiles.savedOriginals(name,originals(50000, "connected:5G"));
        assertNotEquals(first,second); assertEquals(RawValue.integer(50000),second.get(ConfigKey.NR_ADVANCED_BANDWIDTH));
    }
    @Test public void noInventedDefaultsWhenOriginalMissing() {
        assertThrows(IllegalStateException.class,() -> RadioDisplayProfiles.savedOriginals(RadioDisplayProfiles.Name.NR_ADVANCED,Map.of()));
    }
    @Test public void unsetOriginalCannotBeWrittenAsFalseOrZero() {
        assertThrows(IllegalStateException.class,() -> RadioDisplayProfiles.savedOriginals(RadioDisplayProfiles.Name.NR_ADVANCED,Map.of(ConfigKey.NR_ADVANCED_BANDWIDTH,RawValue.unset(),ConfigKey.NR_ICON,RawValue.string(""))));
    }
    @Test public void alreadyOnCarrierOriginalDoesNotPretendToDisable() {
        var name = RadioDisplayProfiles.Name.NR_ADVANCED;
        assertThrows(IllegalStateException.class,() -> RadioDisplayProfiles.savedOriginals(name,RadioDisplayProfiles.target(name)));
    }
    @Test public void metricsMaskRejectsUnknownBits() {
        assertThrows(IllegalArgumentException.class,() -> ConfigKey.NR_SIGNAL_METRICS.validate(RawValue.integer(8),37));
    }
    private static Map<ConfigKey,RawValue> originals(int bandwidth, String icon) {
        Map<ConfigKey,RawValue> result = new EnumMap<>(ConfigKey.class);
        result.put(ConfigKey.NR_ADVANCED_BANDWIDTH,RawValue.integer(bandwidth));
        result.put(ConfigKey.NR_ICON,RawValue.string(icon));
        result.put(ConfigKey.NR_ADVANCED_BANDS,RawValue.integers(List.of()));
        result.put(ConfigKey.NR_ADVANCED_PCO,RawValue.integer(0));
        result.put(ConfigKey.NR_ADVANCED_INCLUDE_LTE,RawValue.bool(false));
        return result;
    }
    @Test public void keepsInternationalBandsAndRecognizesSupersetAsEnabled() {
        var name = RadioDisplayProfiles.Name.NR_ADVANCED;
        var current = originals(0, ""); current.put(ConfigKey.NR_ADVANCED_BANDS,RawValue.integers(List.of(77,78,261)));
        var target = RadioDisplayProfiles.target(name,current);
        assertEquals(RawValue.integers(List.of(1,3,8,28,41,77,78,79,261)),target.get(ConfigKey.NR_ADVANCED_BANDS));
        assertEquals(RadioDisplayProfiles.State.ON,RadioDisplayProfiles.state(name,target));
        assertEquals(RawValue.integers(List.of(77,78,261)),current.get(ConfigKey.NR_ADVANCED_BANDS));
    }
    @Test public void unknownExistingBandsCannotBeReplacedWithPresetDefaults() {
        assertThrows(IllegalStateException.class,() -> RadioDisplayProfiles.target(RadioDisplayProfiles.Name.NR_ADVANCED,Map.of()));
    }
    @Test public void oldTwoFieldBackupCannotInventNewOriginalsAfterUpgrade() {
        assertThrows(IllegalStateException.class,() -> RadioDisplayProfiles.savedOriginals(RadioDisplayProfiles.Name.NR_ADVANCED,
            Map.of(ConfigKey.NR_ADVANCED_BANDWIDTH,RawValue.integer(20000),ConfigKey.NR_ICON,RawValue.string("connected:5G"))));
    }
    @Test public void bandAndPcoValidationRejectsMalformedTargets() {
        for (var bands : List.of(List.of(0),List.of(1025),List.of(78,78)))
            assertThrows(IllegalArgumentException.class,() -> ConfigKey.NR_ADVANCED_BANDS.validate(RawValue.integers(bands),37));
        ConfigKey.NR_ADVANCED_BANDS.validate(RawValue.integers(List.of()),37);
        assertThrows(IllegalArgumentException.class,() -> ConfigKey.NR_ADVANCED_PCO.validate(RawValue.integer(-1),37));
    }
}
