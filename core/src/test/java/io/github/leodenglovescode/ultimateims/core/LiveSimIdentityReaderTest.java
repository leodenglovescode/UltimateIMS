package io.github.leodenglovescode.ultimateims.core;

import org.junit.Test;
import static org.junit.Assert.*;

/** 运行时用同一读算法；虚构标识覆盖活动卡、空值及读取中换卡。 */
public class LiveSimIdentityReaderTest {
    private static final String A = "8900000000000000001", B = "8900000000000000002";
    private static class Source implements LiveSimIdentityReader.Source {
        int activeCalls, serialCalls, seenSub;
        boolean active = true, removed;
        String first = A, second = A;
        public boolean active(int sub) { activeCalls++; seenSub = sub; return active && !(removed && activeCalls > 1); }
        public String serial(int sub) { seenSub = sub; return serialCalls++ == 0 ? first : second; }
    }
    @Test public void verifiesBothReadsAndFullSubscriptionId() throws Exception {
        Source source = new Source(); assertEquals(A,LiveSimIdentityReader.read(source,42));
        assertEquals(42,source.seenSub); assertEquals(2,source.activeCalls); assertEquals(2,source.serialCalls);
    }
    @Test public void invalidTargetDoesNotReachFramework() {
        Source source = new Source(); assertThrows(SecurityException.class,() -> LiveSimIdentityReader.read(source,-1)); assertEquals(0,source.activeCalls);
    }
    @Test public void inactiveSubscriptionDoesNotReadIdentifiers() {
        Source source = new Source(); source.active = false;
        assertThrows(SecurityException.class,() -> LiveSimIdentityReader.read(source,42)); assertEquals(0,source.serialCalls);
    }
    @Test public void removedSimDuringReadIsRejected() {
        Source source = new Source(); source.removed = true;
        assertThrows(SecurityException.class,() -> LiveSimIdentityReader.read(source,42)); assertEquals(1,source.serialCalls);
    }
    @Test public void replacementAtSameSubscriptionIdIsRejected() {
        Source source = new Source(); source.second = B;
        assertThrows(SecurityException.class,() -> LiveSimIdentityReader.read(source,42));
    }
    @Test public void placeholderDoesNotBecomeIdentity() {
        Source source = new Source(); source.first = "";
        assertThrows(SecurityException.class,() -> LiveSimIdentityReader.read(source,42)); assertEquals(1,source.serialCalls);
    }
    @Test public void serviceDenialDoesNotRetryWithAnotherIdentity() {
        Source source = new Source() { @Override public String serial(int sub) { serialCalls++; throw new SecurityException("Denied"); } };
        assertThrows(SecurityException.class,() -> LiveSimIdentityReader.read(source,42)); assertEquals(1,source.serialCalls);
    }
    @Test public void repeatedFullHexSubscriptionRecordIsVerified() throws Exception {
        Source source = new Source(); source.first = source.second = "898600A1234567890123";
        assertEquals(source.first,LiveSimIdentityReader.read(source,42));
    }
    @Test public void replacementBetweenHexRecordReadsIsRejected() {
        Source source = new Source(); source.first = "898600A1234567890123"; source.second = "898600B1234567890123";
        assertThrows(SecurityException.class,() -> LiveSimIdentityReader.read(source,42));
    }
}
