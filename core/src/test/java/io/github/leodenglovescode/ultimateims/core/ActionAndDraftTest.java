package io.github.leodenglovescode.ultimateims.core;
import org.junit.Test;
import java.util.concurrent.ExecutionException;
import static org.junit.Assert.*;

/** 空草稿的生命周期恢复不能阻止首次编辑；原有 SIM 保护不可跳过。 */
public class ActionAndDraftTest {
    @Test public void emptyRestoredDraftDoesNotBindUnverifiedIdentity() { assertFalse(DraftBinding.requiresRestoredIdentity(false,"unverified")); }
    @Test public void existingDraftStillRequiresBinding() { assertTrue(DraftBinding.requiresRestoredIdentity(true,"verified")); }
    @Test public void replacementSimIsRejected() { assertThrows(SecurityException.class,() -> DraftBinding.verifyRestored("original","replacement")); }
    @Test public void unreadableIdentityIsRejected() { assertThrows(SecurityException.class,() -> DraftBinding.verifyRestored("original",null)); }
    @Test public void matchingVerifiedIdentityIsAccepted() { DraftBinding.verifyRestored("same","same"); }
    @Test public void specificSafeReasonSurvivesWorkerBoundary() { assertEquals("SIM draft identity changed",ActionFailure.describe(new ExecutionException(new IllegalStateException("SIM draft identity changed")))); }
    @Test public void arbitrarySensitiveMessageIsHidden() { assertEquals("Framework operation failed: IllegalStateException",ActionFailure.describe(new IllegalStateException("subscriber=123456 secret=123456"))); }
    @Test public void recoveryBlockIsSpecific() { assertEquals("Recovery required before new operation",ActionFailure.describe(new IllegalStateException("Recovery required before new operation"))); }
    @Test public void sanitizedFrameworkErrorSurvivesOperationBoundary() { assertEquals("Unsupported framework signature: getNrState",ActionFailure.describe(new IllegalStateException("Unsupported framework signature: getNrState"))); }
    @Test public void injectedRemoteMessageIsStillHidden() { assertEquals("Framework operation failed: IllegalStateException",ActionFailure.describe(new IllegalStateException("Unsupported framework signature: secret=1234"))); }
}
