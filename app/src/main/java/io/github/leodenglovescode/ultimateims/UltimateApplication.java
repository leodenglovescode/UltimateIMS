package io.github.leodenglovescode.ultimateims;

import android.app.Application;
import android.content.Context;
import io.github.leodenglovescode.ultimateims.backend.BackendManager;
import rikka.shizuku.ShizukuProvider;

/** 不自动初始化 Sui/root；后端只在用户明确选择并启动后连接。 */
public final class UltimateApplication extends Application {
    private BackendManager backend;
    private WorkspaceController workspace;
    public static final String STATUS_CHANGED = "io.github.leodenglovescode.ultimateims.STATUS_CHANGED";
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(base); ShizukuProvider.disableAutomaticSuiInitialization(); }
    public synchronized BackendManager backend() {
        if (backend == null) backend = new BackendManager(this, () -> {
            sendBroadcast(new android.content.Intent(STATUS_CHANGED).setPackage(getPackageName()));
            if (workspace != null) workspace.backendChanged();
        }); return backend;
    }
    public synchronized WorkspaceController workspace() {
        if (workspace == null) workspace = new WorkspaceController(this, backend()); return workspace;
    }
}
