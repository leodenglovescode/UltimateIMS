package io.github.leodenglovescode.ultimateims.core;
import org.junit.Test;
import static org.junit.Assert.*;

/** 使用虚构标识验证脱敏 SubscriptionInfo 的备用读源，不放宽换卡保护。 */
public class VerifiedSimIdentityTest {
    private static final String A = "8900000000000000001", B = "8900000000000000002";
    @Test public void acceptsMatchingIndependentReads() { assertEquals(A,VerifiedSimIdentity.choose(A,A)); }
    @Test public void usesPrivilegedSerialWhenSubscriptionInfoIsRedacted() { assertEquals(A,VerifiedSimIdentity.choose("",A)); }
    @Test public void usesVerifiedSubscriptionWhenSerialIsUnreadable() { assertEquals(A,VerifiedSimIdentity.choose(A,null)); }
    @Test public void replacementOrRaceIsRejected() { assertThrows(SecurityException.class,() -> VerifiedSimIdentity.choose(A,B)); }
    @Test public void bothUnreadableAreRejected() { assertThrows(SecurityException.class,() -> VerifiedSimIdentity.choose("",null)); }
    @Test public void subscriptionIdCannotBecomeIdentity() { assertThrows(SecurityException.class,() -> VerifiedSimIdentity.choose("1","2")); }
}
