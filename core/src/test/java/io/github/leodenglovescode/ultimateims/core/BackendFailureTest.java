package io.github.leodenglovescode.ultimateims.core;

import org.junit.Test;
import java.util.concurrent.ExecutionException;
import static org.junit.Assert.*;

/** 验证远端错误经过工作线程后仍可诊断，并拒绝任意敏感消息。 */
public class BackendFailureTest {
    @Test public void retainsAuthenticatedPreparationStageAcrossFuture() {
        String detail = "Preparation[broker-initialization]: Permission denied or permission delegation occupied";
        assertEquals(detail, BackendFailure.describe(new ExecutionException(new IllegalStateException(detail))));
    }
    @Test public void hidesArbitraryRemoteMessages() {
        assertEquals("Framework operation failed: IllegalStateException", BackendFailure.describe(new IllegalStateException("secret=123456 subscriber=private")));
    }
    @Test public void rejectsInjectedStageDetails() {
        assertEquals("Framework operation failed: IllegalStateException", BackendFailure.describe(new IllegalStateException("Preparation[initial-read]: secret=123456")));
    }
    @Test public void reportsSignatureMismatch() {
        assertEquals("Unsupported framework signature: getDelegatedShellPermissions", BackendFailure.describe(new NoSuchMethodException("getDelegatedShellPermissions")));
    }
    @Test public void refusesSensitiveSignatureMessage() {
        assertEquals("Unsupported framework signature: NoSuchMethodException", BackendFailure.describe(new NoSuchMethodException("secret=123456")));
    }
}
