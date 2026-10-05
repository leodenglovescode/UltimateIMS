package io.github.leodenglovescode.ultimateims.backend;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import java.security.MessageDigest;

/** 非导出绑定服务；服务端口只通过用户选定的后端传递。 */
public final class BrokerService extends Service {
    private byte[] secret;
    private int backendUid = -1;
    private final IBrokerChannel.Stub channel = new IBrokerChannel.Stub() {
        @Override public synchronized void initialize(byte[] value) {
            int caller = Binder.getCallingUid();
            if ((caller != 0 && caller != 2000) || secret != null || value == null || value.length != 32) throw new SecurityException("Broker initialization rejected");
            backendUid = caller; secret = value.clone();
        }
        @Override public synchronized Bundle execute(byte[] value, int operation, Bundle args) {
            if (secret == null || Binder.getCallingUid() != backendUid || value == null || !MessageDigest.isEqual(secret, value) || BrokerInstrumentation.active == null) throw new SecurityException("Broker authentication failed");
            long identity = Binder.clearCallingIdentity();
            try {
                if (operation == Protocol.STOP) {
                    BrokerInstrumentation.active.finish(0, new Bundle()); BrokerInstrumentation.active = null;
                    secret = null; backendUid = -1; Bundle result = new Bundle(); result.putBoolean("ok", true); return result;
                }
                return new PlatformOperations(BrokerService.this).execute(operation, args);
            } finally { Binder.restoreCallingIdentity(identity); }
        }
    };
    @Override public void onCreate() { super.onCreate(); BrokerInstrumentation.operationLock = channel; }
    @Override public IBinder onBind(Intent intent) { return channel; }
    @Override public boolean onUnbind(Intent intent) {
        synchronized(channel) {
            if (BrokerInstrumentation.active != null) { BrokerInstrumentation.active.finish(0,new Bundle()); BrokerInstrumentation.active = null; }
            if (secret != null) java.util.Arrays.fill(secret,(byte)0); secret = null; backendUid = -1;
        }
        stopSelf(); return false;
    }
}
