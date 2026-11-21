package io.github.leodenglovescode.ultimateims.core;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

/** 主页面只根据实时值显示，不让保存历史或开关点击伪造启用状态。 */
public final class PersistentVolteStateTest {
    @Test public void bothLiveValuesAreRequiredForEnabled() {
        assertEquals(PersistentVolteState.State.ON,PersistentVolteState.read(RawValue.integer(1),RawValue.integer(1)));
        assertEquals(PersistentVolteState.State.OFF,PersistentVolteState.read(RawValue.integer(1),RawValue.integer(0)));
        assertEquals(PersistentVolteState.State.OFF,PersistentVolteState.read(RawValue.integer(-1),RawValue.integer(1)));
    }
    @Test public void unreadableUnsetOrUnexpectedValuesRemainUnknown() {
        for (RawValue value : new RawValue[]{RawValue.unknown(),RawValue.unset(),RawValue.integer(3),RawValue.bool(true)})
            assertEquals(PersistentVolteState.State.UNKNOWN,PersistentVolteState.read(value,RawValue.integer(1)));
    }
    @Test public void noBackupCannotOfferToRestoreAnEnabledSetting() {
        assertFalse(PersistentVolteState.canToggle(PersistentVolteState.State.ON,true,false,false,false));
        assertTrue(PersistentVolteState.canToggle(PersistentVolteState.State.ON,true,false,false,true));
    }
    @Test public void knownDisabledValuesCanBeEnabledAndBackedUp() { assertTrue(PersistentVolteState.canToggle(PersistentVolteState.State.OFF,true,false,false,false)); }
    @Test public void recoveryPermissionLossAndUnreadableValuesDisableMutation() {
        assertFalse(PersistentVolteState.canToggle(PersistentVolteState.State.UNKNOWN,true,false,false,true));
        assertFalse(PersistentVolteState.canToggle(PersistentVolteState.State.OFF,false,false,false,true));
        assertFalse(PersistentVolteState.canToggle(PersistentVolteState.State.ON,true,true,false,true));
        assertFalse(PersistentVolteState.canToggle(PersistentVolteState.State.ON,true,false,true,true));
    }
    @Test public void backupMustContainBothRestorableOriginals() {
        assertFalse(PersistentVolteState.originalsRestorable(Map.of()));
        assertFalse(PersistentVolteState.originalsRestorable(Map.of(ConfigKey.VOIMS_OPT_IN,RawValue.integer(0),ConfigKey.ADVANCED_CALLING_USER,RawValue.unset())));
        assertTrue(PersistentVolteState.originalsRestorable(Map.of(ConfigKey.VOIMS_OPT_IN,RawValue.integer(-1),ConfigKey.ADVANCED_CALLING_USER,RawValue.integer(0))));
    }
}
