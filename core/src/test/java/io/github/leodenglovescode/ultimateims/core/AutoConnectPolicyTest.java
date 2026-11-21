package io.github.leodenglovescode.ultimateims.core;

import org.junit.Test;
import static org.junit.Assert.*;

/** 自动连接不得扩大既有授权、切换后端或清除不确定状态。 */
public final class AutoConnectPolicyTest {
    private AutoConnectPolicy.Decision decision(BackendSession.Mode mode, boolean enabled, boolean foreground, boolean ready, boolean occupied, boolean unknown, boolean running, boolean granted) {
        return AutoConnectPolicy.decide(mode,enabled,foreground,ready,occupied,unknown,running,granted);
    }
    @Test public void selectedRunningAuthorizedShizukuCanConnectInForeground() {
        assertEquals(AutoConnectPolicy.Decision.CONNECT,decision(BackendSession.Mode.SHIZUKU,true,true,false,false,false,true,true));
    }
    @Test public void noSelectionDisabledOrBackgroundNeverStartsService() {
        assertEquals(AutoConnectPolicy.Decision.NONE,decision(null,true,true,false,false,false,true,true));
        assertEquals(AutoConnectPolicy.Decision.NONE,decision(BackendSession.Mode.SHIZUKU,false,true,false,false,false,true,true));
        assertEquals(AutoConnectPolicy.Decision.NONE,decision(BackendSession.Mode.SHIZUKU,true,false,false,false,false,true,true));
    }
    @Test public void missingAuthorizationNeverRequestsItAutomatically() {
        assertEquals(AutoConnectPolicy.Decision.NEED_PERMISSION,decision(BackendSession.Mode.SHIZUKU,true,true,false,false,false,true,false));
    }
    @Test public void unavailableShizukuWaitsForAvailabilityRatherThanStartingAdbOrRoot() {
        assertEquals(AutoConnectPolicy.Decision.WAIT_FOR_SHIZUKU,decision(BackendSession.Mode.SHIZUKU,true,true,false,false,false,false,true));
    }
    @Test public void timeoutOrUnknownResultCannotTriggerReconnect() {
        assertEquals(AutoConnectPolicy.Decision.RECOVERY_REQUIRED,decision(BackendSession.Mode.SHIZUKU,true,true,false,false,true,true,true));
    }
    @Test public void inProgressOrReadySessionsCannotBeStartedAgain() {
        assertEquals(AutoConnectPolicy.Decision.NONE,decision(BackendSession.Mode.SHIZUKU,true,true,false,true,false,true,true));
        assertEquals(AutoConnectPolicy.Decision.NONE,decision(BackendSession.Mode.SHIZUKU,true,true,true,false,false,true,true));
    }
    @Test public void privateBackendDoesNotAcquireRootOrPairOnLaunch() {
        for (var mode : new BackendSession.Mode[]{BackendSession.Mode.EXISTING_ROOT,BackendSession.Mode.EMBEDDED_ADB})
            assertEquals(AutoConnectPolicy.Decision.MANUAL_PRIVATE_START,decision(mode,true,true,false,false,false,true,true));
    }
    @Test public void alreadyAuthenticatedPrivateSessionRemainsUsableWithoutRestart() {
        assertEquals(AutoConnectPolicy.Decision.NONE,decision(BackendSession.Mode.EMBEDDED_ADB,true,true,true,false,false,false,false));
    }
}
