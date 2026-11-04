package io.github.leodenglovescode.ultimateims.core;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

/** 在同一解析器上覆盖旧接口、新接口、拒绝及释放结果不确定。 */
public class DelegationMethodsTest {
    public static class Base {
        List<String> permissions = List.of();
        int uid;
        public void startDelegateShellPermissionIdentity(int uid, String[] permissions) { this.uid = uid; this.permissions = List.of(permissions); }
        public List<String> getDelegatedShellPermissions() { return permissions; }
    }
    public static class Legacy extends Base {
        int stops;
        public void stopDelegateShellPermissionIdentity() { stops++; permissions = List.of(); }
    }
    public static class Scoped extends Base {
        int stoppedUid;
        public void stopDelegateShellPermissionIdentity(int uid) { stoppedUid = uid; permissions = List.of(); }
    }
    public static class Both extends Scoped {
        int legacyStops;
        public void stopDelegateShellPermissionIdentity() { legacyStops++; permissions = List.of(); }
    }
    public static class Denied extends Both {
        @Override public void stopDelegateShellPermissionIdentity(int uid) { throw new SecurityException("Denied"); }
    }
    public static class UnknownRelease extends Scoped {
        @Override public void stopDelegateShellPermissionIdentity(int uid) { permissions = null; }
    }
    public static class IneffectiveRelease extends Scoped {
        @Override public void stopDelegateShellPermissionIdentity(int uid) { }
    }
    public static class WrongReturn extends Legacy {
        public boolean stopDelegateShellPermissionIdentity(int uid) { return true; }
    }
    public static class AllScoped {
        int readUid, stopUid;
        List<String> permissions = List.of();
        public void startDelegateShellPermissionIdentity(int uid, String[] names) { permissions = List.of(names); }
        public List<String> getDelegatedShellPermissions(int uid) { readUid = uid; return permissions; }
        public void stopDelegateShellPermissionIdentity(int uid) { stopUid = uid; permissions = List.of(); }
    }
    public static class DeniedRead extends Base {
        int legacyReads;
        public List<String> getDelegatedShellPermissions(int uid) { throw new SecurityException("Denied"); }
        @Override public List<String> getDelegatedShellPermissions() { legacyReads++; return List.of(); }
        public void stopDelegateShellPermissionIdentity(int uid) { }
    }
    @Test public void actualPixelSignatureSetUsesFullUidForReadAndRelease() throws Exception {
        AllScoped service = new AllScoped(); DelegationMethods methods = new DelegationMethods(service);
        assertTrue(methods.uidScopedRead()); methods.start(1012345,new String[]{"allowed"});
        assertEquals(List.of("allowed"), methods.permissions(1012345)); assertEquals(1012345,service.readUid);
        methods.stop(1012345); assertEquals(1012345,service.stopUid); assertEquals(1012345,service.readUid);
    }
    @Test public void failedUidReadDoesNotFallBackToGlobalRead() throws Exception {
        DeniedRead service = new DeniedRead(); DelegationMethods methods = new DelegationMethods(service);
        assertThrows(SecurityException.class, () -> methods.permissions(10001)); assertEquals(0,service.legacyReads);
    }
    @Test public void oldFirmwareReleasesAndVerifies() throws Exception {
        Legacy service = new Legacy(); DelegationMethods methods = new DelegationMethods(service);
        assertFalse(methods.uidScopedStop()); methods.start(10001, new String[]{"allowed"});
        assertEquals(List.of("allowed"), methods.permissions(10001)); methods.stop(10001); assertEquals(1, service.stops);
    }
    @Test public void newFirmwareReceivesExactAppUid() throws Exception {
        Scoped service = new Scoped(); DelegationMethods methods = new DelegationMethods(service);
        assertTrue(methods.uidScopedStop()); methods.start(1012345, new String[]{"allowed"}); methods.stop(1012345);
        assertEquals(1012345, service.stoppedUid);
    }
    @Test public void prefersUidScopedMethodWhenBothExist() throws Exception {
        Both service = new Both(); DelegationMethods methods = new DelegationMethods(service); methods.stop(10001);
        assertEquals(10001, service.stoppedUid); assertEquals(0, service.legacyStops);
    }
    @Test public void invocationDenialDoesNotTryLegacyMethod() throws Exception {
        Denied service = new Denied(); DelegationMethods methods = new DelegationMethods(service);
        assertThrows(SecurityException.class, () -> methods.stop(10001)); assertEquals(0, service.legacyStops);
    }
    @Test public void missingReleaseFailsBeforeDelegationCanStart() {
        Base service = new Base(); assertThrows(NoSuchMethodException.class, () -> new DelegationMethods(service));
        assertEquals(List.of(), service.permissions);
    }
    @Test public void mismatchedReturnDoesNotFallBack() {
        WrongReturn service = new WrongReturn(); assertThrows(NoSuchMethodException.class, () -> new DelegationMethods(service));
        assertEquals(0, service.stops);
    }
    @Test public void unreadableReleaseNeverReportsSuccess() throws Exception {
        DelegationMethods methods = new DelegationMethods(new UnknownRelease());
        assertThrows(IllegalStateException.class, () -> methods.stop(10001));
    }
    @Test public void ineffectiveReleaseNeverReportsSuccess() throws Exception {
        DelegationMethods methods = new DelegationMethods(new IneffectiveRelease()); methods.start(10001, new String[]{"allowed"});
        assertThrows(IllegalStateException.class, () -> methods.stop(10001));
    }
    @Test public void rejectsNonAppUidBeforeRemoteCall() throws Exception {
        Scoped service = new Scoped(); DelegationMethods methods = new DelegationMethods(service);
        assertThrows(IllegalArgumentException.class, () -> methods.start(2000, new String[]{"allowed"}));
        assertThrows(IllegalArgumentException.class, () -> methods.stop(-1)); assertEquals(0, service.uid); assertEquals(0, service.stoppedUid);
    }
}
