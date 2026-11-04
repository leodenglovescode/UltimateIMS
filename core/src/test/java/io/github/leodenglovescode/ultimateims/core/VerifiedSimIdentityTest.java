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
    @Test public void fullHexIccidIsAcceptedWithoutUsingShortModemValue() {
        String full = "898600A1234567890123";
        assertEquals(full,VerifiedSimIdentity.choose(full,"898600"));
        assertEquals(full,VerifiedSimIdentity.canonical(full.toLowerCase(java.util.Locale.ROOT)));
    }
    @Test public void mismatchedFullHexRecordsAreRejected() {
        assertThrows(SecurityException.class,() -> VerifiedSimIdentity.choose("898600A1234567890123","898600B1234567890123"));
    }
    @Test public void sixDigitModemPrefixIsNeverIdentity() { assertThrows(SecurityException.class,() -> VerifiedSimIdentity.choose(null,"898600")); }
    @Test public void arbitraryAlphanumericIdentityIsRejected() { assertThrows(SecurityException.class,() -> VerifiedSimIdentity.canonical("898600Z1234567890123")); }
    @Test public void zeroAndFillerPlaceholdersAreRejected() {
        assertFalse(VerifiedSimIdentity.valid("00000000000000000000"));
        assertFalse(VerifiedSimIdentity.valid("FFFFFFFFFFFFFFFFFFFF"));
    }
    @Test public void oldBackupInputRemainsByteForByteCompatible() {
        assertEquals(A,VerifiedSimIdentity.canonical(A));
        assertEquals(A+"F",VerifiedSimIdentity.canonical(A+"f"));
    }
    @Test public void existingDecimalBackupDigestMatchesGoldenValue() {
        byte[] key = new byte[32]; java.util.Arrays.fill(key,(byte)7);
        assertEquals("5c08c62c32d146cf00c17b4e4fcf30ca8c45312af7c9b2596a4afd785a29b930",SimIdentity.verifiedIccid(A,key).digest());
    }
    @Test public void fullHexIdentityHashUsesFullRecordNotNumericPrefix() {
        byte[] key = new byte[32];
        assertNotEquals(SimIdentity.verifiedIccid("898600A1234567890123",key),SimIdentity.verifiedIccid("898600B1234567890123",key));
        assertEquals(SimIdentity.verifiedIccid("898600A1234567890123",key),SimIdentity.verifiedIccid("898600a1234567890123",key));
    }
}
