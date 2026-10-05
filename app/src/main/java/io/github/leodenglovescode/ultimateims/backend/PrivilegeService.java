package io.github.leodenglovescode.ultimateims.backend;

import android.content.ComponentName;
import android.os.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** 官方与私有后端共用自写协议服务，不接收任意 shell 指令。 */
public final class PrivilegeService extends IPrivilegedChannel.Stub {
    private int appUid = -1;
    private byte[] secret;
    private IBrokerChannel broker;
    private final CountDownLatch prepared = new CountDownLatch(1);
    private volatile boolean closed;
    public PrivilegeService() { PlatformMethods.enableAccess(); }
    private void authenticate(byte[] token) {
        if (closed || secret == null || Binder.getCallingUid() != appUid || token == null || !MessageDigest.isEqual(secret, token)) throw new SecurityException("Backend authentication failed");
    }
    @Override public synchronized void initialize(int uid, int mode, byte[] token, IBrokerChannel channel, IBinder clientLife) {
        if (secret != null || uid != Binder.getCallingUid() || uid < 10000 || token == null || token.length != 32 || channel == null || clientLife == null) throw new SecurityException("Invalid backend initialization");
        if (mode == 2 && android.os.Process.myUid() != 0) throw new SecurityException("Root mode requires existing root");
        if (mode < 0 || mode > 2 || (android.os.Process.myUid() != 0 && android.os.Process.myUid() != 2000)) throw new SecurityException("Invalid backend identity");
        appUid = uid; secret = token.clone(); broker = channel;
        try {
            // 应用死亡时让正在执行的 finally 完成（特别是 IMS enable），随后退出。
            clientLife.linkToDeath(() -> { synchronized(PrivilegeService.this) { closed = true; System.exit(0); } },0);
            broker.initialize(secret);
            Object am = PlatformMethods.activityManager();
            List<?> occupied = (List<?>) PlatformMethods.invoke(am, "getDelegatedShellPermissions", List.class, new Class<?>[]{});
            if (!occupied.isEmpty()) throw new SecurityException("Shell permission delegation is occupied by another client");
            Class<?> uiType = Class.forName("android.app.IUiAutomationConnection");
            Object automation = Class.forName("android.app.UiAutomationConnection").getConstructor().newInstance();
            int flags = PlatformMethods.intField("android.app.ActivityManager", "INSTR_FLAG_NO_RESTART")
                    | PlatformMethods.intField("android.app.ActivityManager", "INSTR_FLAG_DISABLE_HIDDEN_API_CHECKS");
            Bundle arguments = new Bundle(); arguments.putBinder("channel", asBinder()); arguments.putByteArray("secret", secret);
            boolean accepted = (boolean) PlatformMethods.invoke(am, "startInstrumentation", boolean.class,
                    new Class<?>[]{ComponentName.class, String.class, int.class, Bundle.class, Class.forName("android.app.IInstrumentationWatcher"), uiType, int.class, String.class},
                    new ComponentName(Protocol.PACKAGE, BrokerInstrumentation.class.getName()), null, flags, arguments, null, automation, uid / 100000, null);
            if (!accepted) throw new IllegalStateException("Broker instrumentation was rejected");
        } catch (Exception e) { closed = true; throw new IllegalStateException("Backend preparation failed: " + PlatformMethods.failure(e)); }
    }
    @Override public void ready(byte[] token) { authenticate(token); prepared.countDown(); }
    @Override public synchronized Bundle invoke(byte[] token, int operation, Bundle args) {
        authenticate(token);
        if (operation < Protocol.SNAPSHOT || operation > Protocol.APN_RESTORE || args == null) throw new IllegalArgumentException("Invalid operation");
        boolean delegated = false;
        Object am = null;
        long identity = Binder.clearCallingIdentity();
        try {
            if (!prepared.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Broker readiness timeout; recovery required");
            if (operation == Protocol.STOP) return broker.execute(token, operation, args);
            am = PlatformMethods.activityManager();
            List<?> occupied = (List<?>) PlatformMethods.invoke(am, "getDelegatedShellPermissions", List.class, new Class<?>[]{});
            if (!occupied.isEmpty()) throw new SecurityException("Shell permission delegation is occupied");
            PlatformMethods.invoke(am, "startDelegateShellPermissionIdentity", void.class, new Class<?>[]{int.class, String[].class}, appUid, Protocol.PERMISSIONS);
            delegated = true;
            List<?> granted = (List<?>) PlatformMethods.invoke(am, "getDelegatedShellPermissions", List.class, new Class<?>[]{});
            if (!granted.containsAll(Arrays.asList(Protocol.PERMISSIONS))) throw new SecurityException("Permission delegation was not established");
            return broker.execute(token, operation, args);
        } catch (Exception e) {
            Bundle failure = new Bundle(); failure.putBoolean("ok", false); failure.putString("error", PlatformMethods.failure(e)); return failure;
        } finally {
            try { if (delegated && am != null) {
                try { PlatformMethods.invoke(am, "stopDelegateShellPermissionIdentity", void.class, new Class<?>[]{}); }
                catch (Exception e) { closed = true; android.util.Log.e("UltimateIMS", "Permission delegation release failed; backend closed"); throw new IllegalStateException("Permission release failed; operation result uncertain"); }
            }
            } finally { Binder.restoreCallingIdentity(identity); }
        }
    }
    @Override public synchronized void destroy() { int caller = Binder.getCallingUid(); if (caller != appUid && caller != 0 && caller != 2000) throw new SecurityException("Unauthorized backend stop"); closed = true; if (secret != null) Arrays.fill(secret, (byte) 0); System.exit(0); }
}
