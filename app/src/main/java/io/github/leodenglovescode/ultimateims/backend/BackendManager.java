package io.github.leodenglovescode.ultimateims.backend;

import android.content.*;
import android.os.*;
import io.github.leodenglovescode.ultimateims.core.BackendSession;
import rikka.shizuku.Shizuku;
import java.security.*;
import java.util.concurrent.*;

/** 后端启动只由明确用户操作调用；超时后不得假定远端已取消。 */
public final class BackendManager {
    public interface Listener { void changed(); }
    private final Context context;
    private final Listener listener;
    private final ExecutorService rpc = Executors.newSingleThreadExecutor();
    private final ExecutorService setup = Executors.newSingleThreadExecutor();
    private final IBinder clientLife = new Binder();
    private byte[] secret = new byte[32];
    private IBrokerChannel broker;
    private volatile IPrivilegedChannel channel;
    private volatile IPrivilegedChannel failedChannel;
    private volatile boolean bound, transitioning, authenticatedRead;
    private volatile long epoch;
    private volatile Future<?> pending;
    private volatile boolean uncertain;
    private volatile String state = "Backend not started";
    private BackendSession.Mode mode;
    private String launchNonce;
    private boolean permissionRequested;
    private Shizuku.UserServiceArgs serviceArgs;
    public BackendManager(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
        new SecureRandom().nextBytes(secret);
        Shizuku.addRequestPermissionResultListener((request, granted) -> {
            if (request == 910 && permissionRequested) { permissionRequested = false; if (granted == android.content.pm.PackageManager.PERMISSION_GRANTED) bindOfficial(); else update("Shizuku permission denied"); }
        });
        Shizuku.addBinderDeadListener(() -> { if (mode == BackendSession.Mode.SHIZUKU) { channel = null; update("Official backend disconnected"); } });
    }
    public BackendSession.Mode mode() { return mode; }
    public String state() { return state; }
    public byte[] token() { return secret.clone(); }
    public boolean ready() { return authenticatedRead && channel != null && channel.asBinder().isBinderAlive() && !uncertain; }
    public boolean bound() { return bound || transitioning; }
    public boolean inFlight() { return pending != null && !pending.isDone(); }
    public void reconcile() { if (inFlight()) throw new IllegalStateException("An uncertain remote operation is still running"); uncertain = false; }
    private void update(String message) { state = message; new Handler(Looper.getMainLooper()).post(listener::changed); }
    private final ServiceConnection brokerConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) { if (!bound || transitioning) return; broker = IBrokerChannel.Stub.asInterface(service); startChosenTransport(); }
        @Override public void onServiceDisconnected(ComponentName name) { if (broker != null && broker.asBinder().isBinderAlive()) return; authenticatedRead = false; broker = null; channel = null; update("Broker disconnected; recovery may be required"); }
    };
    private final ServiceConnection officialConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) { if (bound && !transitioning && mode == BackendSession.Mode.SHIZUKU) initialize(IPrivilegedChannel.Stub.asInterface(service)); }
        @Override public void onServiceDisconnected(ComponentName name) { if (channel != null && channel.asBinder().isBinderAlive()) return; authenticatedRead = false; channel = null; update("Official backend disconnected"); }
    };
    public void start(BackendSession.Mode selected) {
        if (selected == null || inFlight() || channel != null || bound()) throw new IllegalStateException("Stop the current backend before starting another");
        epoch++; authenticatedRead = false; mode = selected; launchNonce = null; new SecureRandom().nextBytes(secret);
        update("Preparing selected backend");
        bound = context.bindService(new Intent(context, BrokerService.class), brokerConnection, Context.BIND_AUTO_CREATE);
        if (!bound) update("Broker binding failed");
    }
    private void startChosenTransport() {
        if (mode == BackendSession.Mode.SHIZUKU) {
            if (!Shizuku.pingBinder()) { update("Shizuku is not running; start it explicitly in Shizuku"); return; }
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) { permissionRequested = true; Shizuku.requestPermission(910); }
            else bindOfficial();
        } else {
            launchNonce = java.util.UUID.randomUUID().toString();
            if (mode == BackendSession.Mode.EXISTING_ROOT) setup.execute(() -> {
                try { new ProcessBuilder("su", "-c", launchCommand()).start(); update("Existing-root start requested; waiting for authenticated attachment"); }
                catch (Exception e) { update("Root startup failed: " + e.getClass().getSimpleName()); }
            });
            else update("Wireless ADB selected; pair/connect explicitly to start the private backend");
        }
    }
    private void bindOfficial() {
        serviceArgs = new Shizuku.UserServiceArgs(new ComponentName(context, PrivilegeService.class)).tag("ultimateims-official").daemon(false).processNameSuffix("uims").version(io.github.leodenglovescode.ultimateims.BuildConfig.VERSION_CODE);
        try { Shizuku.bindUserService(serviceArgs, officialConnection); }
        catch (RuntimeException e) { update("Shizuku binding failed: " + e.getClass().getSimpleName()); }
    }
    private void initialize(IPrivilegedChannel candidate) {
        long requestedEpoch = epoch; BackendSession.Mode selectedMode = mode;
        setup.execute(() -> {
            try {
                if (requestedEpoch != epoch || !bound || transitioning) return;
                candidate.initialize(android.os.Process.myUid(), selectedMode.ordinal(), secret, broker, clientLife);
                if (requestedEpoch != epoch || transitioning) { failedChannel = candidate; return; }
                candidate.asBinder().linkToDeath(() -> { if (channel == candidate) { authenticatedRead = false; channel = null; update("Privileged backend died; recovery may be required"); } }, 0);
                channel = candidate;
                Bundle result = invoke(Protocol.SNAPSHOT, new Bundle());
                if (!result.getBoolean("ok")) throw new IllegalStateException("Initial authenticated read failed");
                if (requestedEpoch != epoch || transitioning) { failedChannel = candidate; channel = null; return; }
                authenticatedRead = true; update("Selected backend ready and authenticated");
            } catch (Exception e) { authenticatedRead = false; failedChannel = candidate; channel = null; update("Backend preparation failed: " + PlatformMethods.failure(e)); }
        });
    }
    public synchronized boolean attach(String nonce, IBinder service, int callerUid) {
        if (mode == null || mode == BackendSession.Mode.SHIZUKU || launchNonce == null || !launchNonce.equals(nonce) || service == null) return false;
        if (callerUid != (mode == BackendSession.Mode.EXISTING_ROOT ? 0 : 2000)) return false;
        launchNonce = null; initialize(IPrivilegedChannel.Stub.asInterface(service)); return true;
    }
    public String launchCommand() {
        if (mode == null || mode == BackendSession.Mode.SHIZUKU || launchNonce == null) throw new IllegalStateException("Private startup was not requested");
        String apk = context.getApplicationInfo().sourceDir;
        if (!apk.matches("/[A-Za-z0-9_./=+~-]+")) throw new SecurityException("Unexpected APK path");
        return "CLASSPATH='" + apk + "' /system/bin/setsid /system/bin/app_process /system/bin --nice-name=ultimateims.backend "
                + EmbeddedMain.class.getName() + " " + launchNonce + " " + (android.os.Process.myUid()/100000) + " >/dev/null 2>&1 </dev/null &";
    }
    public synchronized Bundle invoke(int operation, Bundle args) throws Exception {
        if (channel == null || uncertain || inFlight()) throw new IllegalStateException("Backend unavailable or previous result unknown");
        IPrivilegedChannel selected = channel; byte[] token = secret.clone();
        Future<Bundle> request = rpc.submit(() -> selected.invoke(token, operation, args)); pending = request;
        try {
            Bundle result = request.get(20, TimeUnit.SECONDS);
            if (result == null) throw new IllegalStateException("Backend returned no result");
            return result;
        } catch (TimeoutException e) {
            uncertain = true; update("Remote timeout; result unknown, recovery required"); throw e;
        } finally { if (request.isDone()) pending = null; }
    }
    private void terminate(IPrivilegedChannel service) throws Exception {
        if (service == null || !service.asBinder().isBinderAlive()) return;
        try { service.destroy(); } catch (RemoteException e) { /* System.exit 可能使调用收到 DeadObject，仍独立观察死亡。 */ }
        for (int n=0;n<10 && service.asBinder().isBinderAlive();n++) Thread.sleep(50);
        if (service.asBinder().isBinderAlive()) throw new IllegalStateException("Backend termination unverified");
    }
    public void stop() {
        if (inFlight() || transitioning) throw new IllegalStateException("A remote operation is still running");
        epoch++; authenticatedRead = false; transitioning = true; permissionRequested = false; launchNonce = null;
        setup.execute(() -> {
            IPrivilegedChannel oldChannel = channel, oldFailed = failedChannel;
            boolean hadChannel = oldChannel != null || oldFailed != null;
            boolean brokerConfirmed = false;
            try { if (channel != null) { reconcile(); brokerConfirmed = invoke(Protocol.STOP,new Bundle()).getBoolean("ok"); } }
            catch (Exception e) { android.util.Log.w("UltimateIMS","Broker stop not acknowledged; explicit process termination required"); }
            try {
                if (serviceArgs != null && Shizuku.pingBinder()) Shizuku.unbindUserService(serviceArgs,officialConnection,true);
                terminate(oldChannel); terminate(oldFailed);
                channel = null; failedChannel = null; broker = null; serviceArgs = null;
                try { context.unbindService(brokerConnection); } catch (IllegalArgumentException e) { /* 已释放的绑定。 */ }
                bound = false; transitioning = false;
                mode = null;
                update(!hadChannel ? "No authenticated backend channel; local bindings released" : brokerConfirmed ? "Backend stopped; Binder death observed" : "Backend stopped; Binder death observed, broker cleanup was not acknowledged; recovery backups retained");
            } catch (Exception e) {
                transitioning = false; update("Backend stop not verified; keep recovery backups and retry after the remote operation converges");
            }
        });
    }
}
