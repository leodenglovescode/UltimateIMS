package io.github.leodenglovescode.ultimateims.core;

import java.util.Set;
import java.util.concurrent.ExecutionException;

/** 只展示本项目固定的拒绝原因，任意框架消息与私密值不进入 UI 或日志。 */
public final class ActionFailure {
    private static final Set<String> REASONS = Set.of(
        "Automatic reapply paused; recovery required", "Recovery required before new operation",
        "Original value unavailable", "No verifiable original backup available", "Write verification failed", "Final verification failed",
        "Identity key missing; preserve recovery backups", "Framework has no verified raw-unset subscription restoration on this build",
        "SIM draft identity changed", "Restored draft identity must be reviewed", "Draft identity changed",
        "Draft identity unavailable", "Review the draft for this SIM before editing", "SIM changed since live selection",
        "SIM identity changed or unavailable", "SIM identifier unavailable", "Verified SIM identity unavailable", "No selected changes or subscriptions",
        "Raw subscription original unset; verified restoration unavailable", "Selective unset restoration unavailable",
        "Backend unavailable or previous result unknown", "Readback mismatch", "An uncertain remote operation is still running",
        "Preparation[initial-read]: Permission denied or permission delegation occupied"
    );
    public static String describe(Throwable error) {
        while (error instanceof ExecutionException && error.getCause() != null) error = error.getCause();
        String message = error.getMessage();
        return message != null && REASONS.contains(message) ? message : BackendFailure.describe(error);
    }
    private ActionFailure() { }
}
