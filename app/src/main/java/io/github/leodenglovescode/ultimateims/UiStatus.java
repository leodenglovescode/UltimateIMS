package io.github.leodenglovescode.ultimateims;

import android.content.Context;
import io.github.leodenglovescode.ultimateims.backend.BackendManager;
import io.github.leodenglovescode.ultimateims.core.*;

/** 人类可读状态走资源；英文技术日志和原始框架值不经过翻译器。 */
final class UiStatus {
    static String backend(Context context, BackendManager backend) {
        return context.getString(switch (backend.status()) {
            case IDLE -> R.string.backend_idle; case READY -> R.string.backend_ready;
            case PREPARING -> R.string.backend_preparing; case SHIZUKU_UNAVAILABLE -> R.string.backend_shizuku_unavailable;
            case SHIZUKU_UNSUPPORTED -> R.string.backend_shizuku_unsupported; case PERMISSION_REQUIRED -> R.string.backend_permission_required;
            case PERMISSION_DENIED -> R.string.backend_permission_denied; case DISCONNECTED -> R.string.backend_disconnected;
            case RECOVERY_REQUIRED -> R.string.backend_recovery_required; case FAILED -> R.string.backend_failed;
            case STOPPED -> R.string.backend_stopped; case STOP_FAILED -> R.string.backend_stop_failed;
            case PRIVATE_START_REQUIRED -> R.string.backend_private_manual; case ROOT_STARTING -> R.string.backend_root_starting;
        });
    }
    static String operation(Context context, WorkspaceController workspace) {
        return context.getString(switch (workspace.phase()) {
            case IDLE -> R.string.operation_idle; case RUNNING -> R.string.operation_running; case COMPLETED -> R.string.operation_finished;
            case FAILED_OR_UNKNOWN -> R.string.operation_failed_unknown; case BUSY -> R.string.operation_busy;
        });
    }
    static String result(Context context, String raw) {
        OperationResult result = OperationResult.parse(raw);
        String value = context.getString(switch (result.kind()) {
            case VERIFIED -> R.string.result_verified; case RESTORED -> R.string.result_restored; case PARTIAL -> R.string.result_partial;
            case RECOVERY -> R.string.result_recovery; case FAILED -> R.string.result_failed; case SKIPPED -> R.string.result_skipped;
            case IMS_REQUESTED -> R.string.result_ims_requested; case RESET_UNVERIFIED -> R.string.result_reset_unverified; case UNKNOWN -> R.string.result_technical;
        });
        return result.sim() == 0 ? value : context.getString(R.string.sim_result,result.sim(),value);
    }
    static String historyState(Context context, String raw) {
        return context.getString(switch (raw) {
            case "VERIFIED" -> R.string.result_verified; case "RESTORED" -> R.string.result_restored;
            case "PARTIALLY_RESTORED" -> R.string.result_partial; case "RECOVERY_REQUIRED" -> R.string.result_recovery;
            case "PREPARED", "APPLYING", "RESTORING" -> R.string.operation_running;
            default -> R.string.result_technical;
        });
    }
    private UiStatus() { }
}
