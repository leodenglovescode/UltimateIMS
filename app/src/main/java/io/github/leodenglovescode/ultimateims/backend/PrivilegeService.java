package io.github.leodenglovescode.ultimateims.backend;

import android.content.ComponentName;
import android.os.*;
import java.security.MessageDigest;
import io.github.leodenglovescode.ultimateims.core.DelegationMethods;
import java.util.*;
import java.util.concurrent.*;

/** 官方与私有后端共用自写协议服务，不接收任意 shell 指令。 */
public final class PrivilegeService extends IPrivilegedChannel.Stub {
    private int appUid = -1;
    private byte[] secret;
    private IBrokerChannel broker;
    private DelegationMethods delegation;
    private volatile IInstrumentationSession instrumentation;
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
        String stage = "client-lifetime";
        long identity = Binder.clearCallingIdentity();
        try {
            // 认证完成后才清除入站身份；系统调用必须使用所选后端自身的 shell/root 身份。
            // 应用死亡时让正在执行的 finally 完成（特别是 IMS enable），随后退出。
            clientLife.linkToDeath(() -> { synchronized(PrivilegeService.this) { closed = true; System.exit(0); } },0);
            stage = "broker-initialization";
            broker.initialize(secret);
            stage = "delegation-preflight";
            // 在任何设备写入前验证启动、读取、释放三个接口，兼容固件的实际签名。
            Object am = PlatformMethods.activityManager();
            delegation = new DelegationMethods(am);
            android.util.Log.i("UltimateIMS", delegation.uidScopedStop() ? "Permission release signature: UID scoped" : "Permission release signature: legacy");
            List<?> occupied = delegation.permissions();
            if (occupied == null || !occupied.isEmpty()) throw new SecurityException("Shell permission delegation is occupied by another client");
            stage = "instrumentation-connection";
            Class<?> uiType = Class.forName("android.app.IUiAutomationConnection");
            Object automation = Class.forName("android.app.UiAutomationConnection").getConstructor().newInstance();
            int flags = PlatformMethods.intField("android.app.ActivityManager", "INSTR_FLAG_NO_RESTART")
                    | PlatformMethods.intField("android.app.ActivityManager", "INSTR_FLAG_DISABLE_HIDDEN_API_CHECKS");
            Bundle arguments = new Bundle(); arguments.putBinder("channel", asBinder()); arguments.putByteArray("secret", secret);
            stage = "instrumentation-start";
            boolean accepted = (boolean) PlatformMethods.invoke(am, "startInstrumentation", boolean.class,
                    new Class<?>[]{ComponentName.class, String.class, int.class, Bundle.class, Class.forName("android.app.IInstrumentationWatcher"), uiType, int.class, String.class},
                    new ComponentName(Protocol.PACKAGE, BrokerInstrumentation.class.getName()), null, flags, arguments, null, automation, uid / 100000, null);
            if (!accepted) throw new IllegalStateException("Broker instrumentation was rejected");
        } catch (Exception e) {
            closed = true;
            String detail = "Preparation[" + stage + "]: " + PlatformMethods.failure(e);
            android.util.Log.e("UltimateIMS", detail);
            throw new IllegalStateException(detail);
        } finally { Binder.restoreCallingIdentity(identity); }
    }
    @Override public void ready(byte[] token, IInstrumentationSession session) throws RemoteException {
        authenticate(token);
        if (session == null || instrumentation != null) throw new SecurityException("Instrumentation handshake rejected");
        long identity = Binder.clearCallingIdentity();
        try {
            // NO_RESTART 在主进程创建 instrumentation；跨进程握手才能使独立 broker 就绪。
            broker.ready(token);
            instrumentation = session;
            prepared.countDown();
            android.util.Log.i("UltimateIMS", "Authenticated instrumentation and broker ready");
        } finally { Binder.restoreCallingIdentity(identity); }
    }
    @Override public synchronized Bundle invoke(byte[] token, int operation, Bundle args) {
        authenticate(token);
        if (operation < Protocol.SNAPSHOT || operation > Protocol.APN_RESTORE || args == null) throw new IllegalArgumentException("Invalid operation");
        boolean delegated = false;
        long identity = Binder.clearCallingIdentity();
        try {
            if (!prepared.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Broker readiness timeout; recovery required");
            if (operation == Protocol.STOP) {
                Bundle result = broker.execute(token, operation, args);
                instrumentation.close(token);
                instrumentation = null;
                return result;
            }
            List<?> occupied = delegation.permissions();
            if (occupied == null || !occupied.isEmpty()) throw new SecurityException("Shell permission delegation is occupied");
            delegation.start(appUid, Protocol.PERMISSIONS);
            delegated = true;
            List<?> granted = delegation.permissions();
            if (granted == null || !granted.containsAll(Arrays.asList(Protocol.PERMISSIONS))) throw new SecurityException("Permission delegation was not established");
            return broker.execute(token, operation, args);
        } catch (Exception e) {
            Bundle failure = new Bundle(); failure.putBoolean("ok", false); failure.putString("error", PlatformMethods.failure(e)); return failure;
        } finally {
            try { if (delegated) {
                try { delegation.stop(appUid); }
                catch (Exception e) { closed = true; android.util.Log.e("UltimateIMS", "Permission delegation release failed; backend closed"); throw new IllegalStateException("Permission release failed; operation result uncertain"); }
            }
            } finally { Binder.restoreCallingIdentity(identity); }
        }
    }
    @Override public synchronized void destroy() { int caller = Binder.getCallingUid(); if (caller != appUid && caller != 0 && caller != 2000) throw new SecurityException("Unauthorized backend stop"); closed = true; if (secret != null) Arrays.fill(secret, (byte) 0); System.exit(0); }
}
