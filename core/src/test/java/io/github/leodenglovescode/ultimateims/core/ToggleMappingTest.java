package io.github.leodenglovescode.ultimateims.core;

import org.junit.Test;
import static org.junit.Assert.*;

/** 核验真实平台枚举与默认/未知语义，避免开关越权扩展作用域。 */
public class ToggleMappingTest {
    @Test public void unavailableNeverMeansOff() {
        assertEquals(ConfigurationToggle.State.UNKNOWN,ConfigurationToggle.state(ConfigKey.VOLTE,RawValue.unknown()));
        assertEquals(ConfigurationToggle.State.UNKNOWN,ConfigurationToggle.state(ConfigKey.VOLTE,RawValue.unset()));
        assertEquals(ConfigurationToggle.State.UNKNOWN,ConfigurationToggle.state(ConfigKey.WFC_USER,RawValue.integer(-1)));
    }
    @Test public void carrierAndUserSwitchesHaveDifferentTypes() {
        assertEquals(RawValue.bool(true),ConfigurationToggle.target(ConfigKey.VOLTE,true));
        assertEquals(RawValue.integer(0),ConfigurationToggle.target(ConfigKey.VONR_USER,false));
        assertFalse(ConfigurationToggle.supported(ConfigKey.WFC_USER_MODE));
    }
    @Test public void nsaChangePreservesSa() {
        RawValue both = new RawValue(RawValue.Kind.INTEGER_ARRAY,"1,2");
        assertEquals("2",ConfigurationToggle.nrTarget(both,1,false).payload());
        assertEquals(ConfigurationToggle.State.ON,ConfigurationToggle.nrState(ConfigurationToggle.nrTarget(both,1,false),2));
    }
    @Test public void disablingBothIsExplicitEmptyArray() {
        RawValue onlySa = new RawValue(RawValue.Kind.INTEGER_ARRAY,"2");
        RawValue off = ConfigurationToggle.nrTarget(onlySa,2,false);
        assertEquals("",off.payload()); assertEquals(RawValue.Kind.INTEGER_ARRAY,off.kind());
        assertEquals(ConfigurationToggle.State.OFF,ConfigurationToggle.nrState(off,1));
    }
    @Test public void unknownFrameworkNrEnumIsNotGuessed() {
        assertEquals(ConfigurationToggle.State.UNKNOWN,ConfigurationToggle.nrState(new RawValue(RawValue.Kind.INTEGER_ARRAY,"3"),1));
    }
    @Test(expected=IllegalArgumentException.class) public void unreadableNrCannotGenerateTargets() {
        ConfigurationToggle.nrTarget(RawValue.unknown(),1,true);
    }
}
