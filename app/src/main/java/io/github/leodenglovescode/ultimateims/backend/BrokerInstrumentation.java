package io.github.leodenglovescode.ultimateims.backend;

import android.app.Instrumentation;
import android.os.*;
import java.security.MessageDigest;
import java.util.Arrays;

/** 主进程仅维持 NO_RESTART 会话；配置读取和写入仍在独立 broker 进程执行。 */
public final class BrokerInstrumentation extends Instrumentation {
    private byte[] secret;
    private boolean closed;
    private synchronized void finishSession() {
        if (closed) return;
        closed = true;
        if (secret != null) Arrays.fill(secret, (byte) 0);
        finish(0, new Bundle());
    }
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        if (arguments == null || arguments.getBinder("channel") == null || arguments.getByteArray("secret") == null || arguments.getByteArray("secret").length != 32) { finishSession(); return; }
        secret = arguments.getByteArray("secret").clone();
        try {
            PlatformMethods.enableAccess();
            IPrivilegedChannel channel = IPrivilegedChannel.Stub.asInterface(arguments.getBinder("channel"));
            arguments.getBinder("channel").linkToDeath(() -> new Handler(Looper.getMainLooper()).post(this::finishSession), 0);
            channel.ready(secret, new IInstrumentationSession.Stub() {
                @Override public void close(byte[] token) {
                    synchronized (BrokerInstrumentation.this) {
                        int caller = Binder.getCallingUid();
                        if (closed || (caller != 0 && caller != 2000) || token == null || !MessageDigest.isEqual(secret, token)) throw new SecurityException("Instrumentation close rejected");
                        long identity = Binder.clearCallingIdentity();
                        try { finishSession(); } finally { Binder.restoreCallingIdentity(identity); }
                    }
                }
            });
        } catch (Exception error) {
            android.util.Log.e("UltimateIMS", "Instrumentation handshake failed: " + PlatformMethods.failure(error));
            finishSession();
        }
    }
}
