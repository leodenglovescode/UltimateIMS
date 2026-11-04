package io.github.leodenglovescode.ultimateims.backend;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import io.github.leodenglovescode.ultimateims.core.BrokerHandshake;

/** 非导出绑定服务；服务端口只通过用户选定的后端传递。 */
public final class BrokerService extends Service {
    private final BrokerHandshake handshake = new BrokerHandshake();
    private final IBrokerChannel.Stub channel = new IBrokerChannel.Stub() {
        @Override public synchronized void initialize(byte[] value) {
            handshake.initialize(Binder.getCallingUid(), value);
        }
        @Override public synchronized void ready(byte[] value) {
            handshake.ready(Binder.getCallingUid(), value);
        }
        @Override public synchronized Bundle execute(byte[] value, int operation, Bundle args) {
            int caller = Binder.getCallingUid();
            handshake.requireReady(caller, value);
            long identity = Binder.clearCallingIdentity();
            try {
                if (operation == Protocol.STOP) {
                    handshake.close(caller, value);
                    Bundle result = new Bundle(); result.putBoolean("ok", true); return result;
                }
                return new PlatformOperations(BrokerService.this).execute(operation, args);
            } finally { Binder.restoreCallingIdentity(identity); }
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        // 隐藏 API 豁免按进程生效，不能沿用主进程或 Shizuku 进程中的状态。
        PlatformMethods.enableAccess();
    }
    @Override public IBinder onBind(Intent intent) { return channel; }
    @Override public boolean onUnbind(Intent intent) {
        synchronized(channel) { handshake.revoke(); }
        stopSelf(); return false;
    }
}
