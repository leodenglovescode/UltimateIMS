package io.github.leodenglovescode.ultimateims.core;

import org.junit.Test;
import static org.junit.Assert.*;

/** 业务结果可以本地化；框架名、错误类名、数值与任意原文不能误匹配。 */
public final class OperationResultTest {
    @Test public void batchResultsKeepTheirSimAndVerificationMeaning() {
        assertEquals(new OperationResult(2,OperationResult.Kind.VERIFIED),OperationResult.parse("SIM 2: VERIFIED"));
        assertEquals(OperationResult.Kind.FAILED,OperationResult.parse("SIM 1: failed or unknown; originals retained").kind());
        assertEquals(OperationResult.Kind.SKIPPED,OperationResult.parse("SIM 3: not executed").kind());
    }
    @Test public void resetAndRestartCannotBePresentedAsVerifiedRegistration() {
        assertEquals(OperationResult.Kind.RESET_UNVERIFIED,OperationResult.parse("Reset requested; override deletion cannot be independently verified").kind());
        assertEquals(OperationResult.Kind.IMS_REQUESTED,OperationResult.parse("IMS restart requested; registration must be observed separately").kind());
    }
    @Test public void restoreResultsDoNotDiscardPartialAndRecoveryStates() {
        assertEquals(OperationResult.Kind.PARTIAL,OperationResult.parse("Original restoration: PARTIALLY_RESTORED; backups retained").kind());
        assertEquals(OperationResult.Kind.RECOVERY,OperationResult.parse("Original restoration: RECOVERY_REQUIRED; backups retained").kind());
    }
    @Test public void frameworkTokensAndDiagnosticTextAreNotTranslatedBySubstring() {
        for (String raw : new String[]{"carrier_nr_availabilities_int_array","IllegalStateException","Selected backend ready and authenticated","SIM 1: VERIFIED extra","prefix SIM 1: VERIFIED","true","[1, 2]","NR","0x1234","https://example.com/SIM 1: VERIFIED"})
            assertEquals(raw,OperationResult.Kind.UNKNOWN,OperationResult.parse(raw).kind());
    }
}
