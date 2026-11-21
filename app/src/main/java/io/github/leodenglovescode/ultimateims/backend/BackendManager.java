package io.github.leodenglovescode.ultimateims.backend;

import android.content.*;
import android.os.*;
import io.github.leodenglovescode.ultimateims.core.BackendSession;
import io.github.leodenglovescode.ultimateims.core.BackendStatus;
import io.github.leodenglovescode.ultimateims.core.AutoConnectPolicy;
import rikka.shizuku.Shizuku;
import java.security.*;
import java.util.concurrent.*;

/** 后端由明确操作或用户开启的前台重连触发；超时后不得假定远端已取消。 */
public final class BackendManager {
    public interface Listener { void changed(); }
    private final Context context;
    private final Listener listener;
    private final ExecutorService rpc = Executors.newSingleThreadExecutor();
    private final ExecutorService setup = Executors.newSingleThreadExecutor();
    private final IBinder clientLife = new Binder();
    private final Handler main = new Handler(Looper.getMainLooper());
    private byte[] secret = new byte[32];
    private IBrokerChannel broker;
    private volatile IPrivilegedChannel channel;
    private volatile IPrivilegedChannel failedChannel;
    private volatile boolean bound, transitioning, authenticatedRead;
    private volatile Bundle initialSnapshot;
    private volatile boolean initializing, officialBinding;
    private volatile long epoch;
    private volatile Future<?> pending;
    private volatile boolean uncertain;
    private volatile String state = "Backend not started";
    private volatile BackendStatus status = BackendStatus.IDLE;
    private boolean autoForeground, autoEnabled, autoAttempted;
    private BackendSession.Mode autoSelected;
    private BackendSession.Mode mode;
    private String launchNonce;
    private volatile boolean permissionRequested;
    private int permissionRequestCode;
    private long permissionEpoch;
    private Shizuku.UserServiceArgs serviceArgs;
    public BackendManager(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
        new SecureRandom().nextBytes(secret);
        Shizuku.addRequestPermissionResultListener((request, granted) -> {
            if (request != permissionRequestCode || !permissionRequested || permissionEpoch != epoch || mode != BackendSession.Mode.SHIZUKU) return;
            permissionRequested = false;
            if (granted == android.content.pm.PackageManager.PERMISSION_GRANTED) bindBroker();
            else update(BackendStatus.PERMISSION_DENIED,"Shizuku permission denied; grant access explicitly in Shizuku before retrying");
        });
        Shizuku.addBinderReceivedListenerSticky(() -> main.post(this::tryAutoConnect));
        Shizuku.addBinderDeadListener(() -> {
            if (mode == BackendSession.Mode.SHIZUKU) {
                IPrivilegedChannel old = channel;
                if (inFlight()) uncertain = true;
                if (old != null && old.asBinder().isBinderAlive()) failedChannel = old;
                authenticatedRead = false; initialSnapshot = null; permissionRequested = false; officialBinding = false; channel = null;
                update(uncertain || failedChannel != null ? BackendStatus.RECOVERY_REQUIRED : BackendStatus.DISCONNECTED,"Official backend disconnected; live or uncertain sessions are retained for explicit recovery");
                main.post(() -> { autoAttempted = false; tryAutoConnect(); });
            }
        });
    }
    public BackendSession.Mode mode() { return mode; }
    public String state() { return state; }
    public BackendStatus status() { return status; }
    public byte[] token() { return secret.clone(); }
    public boolean ready() { return authenticatedRead && channel != null && channel.asBinder().isBinderAlive() && !uncertain; }
    public boolean bound() { return bound || transitioning || permissionRequested || failedChannel != null; }
    public synchronized Bundle takeInitialSnapshot() {
        if (!ready()) return null; Bundle result = initialSnapshot; initialSnapshot = null; return result == null ? null : result.deepCopy();
    }
    public boolean inFlight() { return pending != null && !pending.isDone(); }
    public void reconcile() { if (inFlight()) throw new IllegalStateException("An uncertain remote operation is still running"); uncertain = false; }
    private void update(BackendStatus code, String message) {
        status = code; state = message;
        android.util.Log.i("UltimateIMS","Backend state: " + code.name());
        main.post(listener::changed);
    }
    public void foregroundAutoConnect(BackendSession.Mode selected, boolean enabled, boolean foreground) {
        if (!foreground || selected != autoSelected || enabled && !autoEnabled) autoAttempted = false;
        autoSelected = selected; autoEnabled = enabled; autoForeground = foreground;
        if (foreground) main.post(this::tryAutoConnect);
    }
    private void tryAutoConnect() {
        // 只释放已观察到死亡且没有在途/未知结果的本地绑定；活服务保留给明确恢复。
        if (autoEnabled && autoForeground && autoSelected == BackendSession.Mode.SHIZUKU && status == BackendStatus.DISCONNECTED
                && channel == null && failedChannel == null && !initializing && !officialBinding && !transitioning && !permissionRequested && !inFlight() && !uncertain) {
            if (bound) { try { context.unbindService(brokerConnection); } catch (IllegalArgumentException ignored) { /* 绑定可能已由系统释放。 */ } }
            bound = false; broker = null; serviceArgs = null; autoAttempted = false;
        }
        if (autoAttempted) return;
        boolean running = false, authorized = false;
        if (autoEnabled && autoForeground && autoSelected == BackendSession.Mode.SHIZUKU) {
            try { running = Shizuku.pingBinder() && !Shizuku.isPreV11(); authorized = running && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED; }
            catch (RuntimeException ignored) { /* 缺少服务时等待明确授权，不弹权限框。 */ }
        }
        var decision = AutoConnectPolicy.decide(autoSelected,autoEnabled,autoForeground,ready(),bound() || initializing || inFlight() || channel != null,uncertain,running,authorized);
        switch (decision) {
            case CONNECT -> {
                autoAttempted = true;
                android.util.Log.i("UltimateIMS","Automatic authorized Shizuku connection requested");
                try { start(autoSelected,true); }
                catch (RuntimeException error) { update(BackendStatus.FAILED,"Automatic connection failed: " + PlatformMethods.failure(error)); }
            }
            case WAIT_FOR_SHIZUKU -> update(BackendStatus.SHIZUKU_UNAVAILABLE,"Shizuku unavailable; waiting while the app is open");
            case NEED_PERMISSION -> update(BackendStatus.PERMISSION_REQUIRED,"Shizuku authorization required; automatic connection will not request permission");
            case MANUAL_PRIVATE_START -> update(BackendStatus.PRIVATE_START_REQUIRED,"Private backend is not running; start explicitly");
            case RECOVERY_REQUIRED -> { /* 保留不确定结果，不自动清理或重试。 */ }
            case NONE -> { }
        }
    }
    private final ServiceConnection brokerConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) { if (!bound || transitioning) return; broker = IBrokerChannel.Stub.asInterface(service); startChosenTransport(); }
        @Override public void onServiceDisconnected(ComponentName name) { if (broker != null && broker.asBinder().isBinderAlive()) return; authenticatedRead = false; initialSnapshot = null; broker = null; channel = null; update(BackendStatus.RECOVERY_REQUIRED,"Broker disconnected; recovery may be required"); }
    };
    private final ServiceConnection officialConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) { if (bound && !transitioning && mode == BackendSession.Mode.SHIZUKU) initialize(IPrivilegedChannel.Stub.asInterface(service)); }
        @Override public void onServiceDisconnected(ComponentName name) { if (channel != null && channel.asBinder().isBinderAlive()) return; authenticatedRead = false; initialSnapshot = null; channel = null; update(BackendStatus.DISCONNECTED,"Official backend disconnected"); }
    };
    public void start(BackendSession.Mode selected) { start(selected,false); }
    private void start(BackendSession.Mode selected, boolean automatic) {
        if (selected == null || inFlight() || channel != null || bound()) throw new IllegalStateException("Stop the current backend before starting another");
        epoch++; uncertain = false; pending = null; authenticatedRead = false; initialSnapshot = null; mode = selected; launchNonce = null; new SecureRandom().nextBytes(secret);
        try {
        if (selected == BackendSession.Mode.SHIZUKU) {
            if (!Shizuku.pingBinder()) { update(BackendStatus.SHIZUKU_UNAVAILABLE,"Shizuku is not running; start it explicitly in Shizuku, then retry"); return; }
            if (Shizuku.isPreV11()) { update(BackendStatus.SHIZUKU_UNSUPPORTED,"Shizuku API version is unsupported; update official Shizuku"); return; }
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (automatic) { update(BackendStatus.PERMISSION_REQUIRED,"Shizuku permission changed before automatic connection"); return; }
                if (Shizuku.shouldShowRequestPermissionRationale()) { update(BackendStatus.PERMISSION_DENIED,"Shizuku permission was denied; review authorization in Shizuku"); return; }
                permissionRequested = true; permissionEpoch = epoch; permissionRequestCode = 910 + (int)(epoch % 60000);
                update(BackendStatus.PERMISSION_REQUIRED,"Waiting for explicit Shizuku authorization");
                try { Shizuku.requestPermission(permissionRequestCode); }
                catch (RuntimeException e) { permissionRequested = false; update(BackendStatus.FAILED,"Shizuku permission request failed: " + PlatformMethods.failure(e)); }
                return;
            }
        }
        } catch (RuntimeException error) {
            permissionRequested = false; update(BackendStatus.FAILED,"Shizuku preflight failed: " + PlatformMethods.failure(error)); return;
        }
        bindBroker();
    }
    private void bindBroker() {
        if (bound || transitioning) return;
        update(BackendStatus.PREPARING,"Preparing selected backend");
        try { bound = context.bindService(new Intent(context,BrokerService.class),brokerConnection,Context.BIND_AUTO_CREATE); }
        catch (RuntimeException e) { bound = false; update(BackendStatus.FAILED,"Broker binding failed: " + PlatformMethods.failure(e)); return; }
        if (!bound) update(BackendStatus.FAILED,"Broker binding failed; retry explicitly");
    }
    private void startChosenTransport() {
        if (mode == BackendSession.Mode.SHIZUKU) {
            boolean authorized;
            try { authorized = Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED; }
            catch (RuntimeException error) { authorized = false; }
            if (!authorized) {
                try { context.unbindService(brokerConnection); } catch (IllegalArgumentException e) { /* 清理未进入认证阶段的本地绑定。 */ }
                bound = false; broker = null; update(BackendStatus.PERMISSION_REQUIRED,"Shizuku authorization unavailable; retry explicitly"); return;
            }
            bindOfficial();
        } else {
            launchNonce = java.util.UUID.randomUUID().toString();
            if (mode == BackendSession.Mode.EXISTING_ROOT) {
                final long requestedEpoch = epoch; final String command = launchCommand();
                update(BackendStatus.ROOT_STARTING,"Existing-root start requested; waiting for authenticated attachment");
                setup.execute(() -> {
                    try { if (requestedEpoch == epoch && bound && !transitioning) new ProcessBuilder("su","-c",command).start(); }
                    catch (Exception e) { if (requestedEpoch == epoch) update(BackendStatus.FAILED,"Root startup failed: " + e.getClass().getSimpleName()); }
                });
            }
            else update(BackendStatus.PRIVATE_START_REQUIRED,"Wireless ADB selected; pair/connect explicitly to start the private backend");
        }
    }
    private void bindOfficial() {
        if (officialBinding || initializing || channel != null || broker == null || !bound || transitioning) return;
        officialBinding = true;
        serviceArgs = new Shizuku.UserServiceArgs(new ComponentName(context, PrivilegeService.class)).tag("ultimateims-official").daemon(false).processNameSuffix("uims").version(io.github.leodenglovescode.ultimateims.BuildConfig.VERSION_CODE);
        try { Shizuku.bindUserService(serviceArgs, officialConnection); }
        catch (RuntimeException e) { officialBinding = false; update(BackendStatus.FAILED,"Shizuku binding failed: " + e.getClass().getSimpleName()); }
    }
    private synchronized void initialize(IPrivilegedChannel candidate) {
        if (initializing || channel != null) return;
        initializing = true; officialBinding = false;
        long requestedEpoch = epoch; BackendSession.Mode selectedMode = mode;
        setup.execute(() -> {
            try {
                if (requestedEpoch != epoch || !bound || transitioning) return;
                initializeRemote(candidate,selectedMode);
                if (requestedEpoch != epoch || transitioning) { failedChannel = candidate; return; }
                candidate.asBinder().linkToDeath(() -> { if (channel == candidate) { authenticatedRead = false; initialSnapshot = null; channel = null; update(BackendStatus.RECOVERY_REQUIRED,"Privileged backend died; recovery may be required"); } }, 0);
                channel = candidate;
                Bundle result = invoke(Protocol.SNAPSHOT, new Bundle());
                if (!result.getBoolean("ok")) throw new IllegalStateException("Preparation[initial-read]: " + result.getString("error", "Framework operation failed: IllegalStateException"));
                if (requestedEpoch != epoch || transitioning) { failedChannel = candidate; channel = null; return; }
                if (selectedMode == BackendSession.Mode.SHIZUKU && !Shizuku.pingBinder()) throw new IllegalStateException("Shizuku disconnected during authentication");
                initialSnapshot = result.deepCopy(); authenticatedRead = true; update(BackendStatus.READY,"Selected backend ready and authenticated");
            } catch (Exception e) {
                failedChannel = candidate;
                if (requestedEpoch == epoch) { authenticatedRead = false; initialSnapshot = null; channel = null; update(uncertain ? BackendStatus.RECOVERY_REQUIRED : BackendStatus.FAILED,"Backend preparation failed: " + PlatformMethods.failure(e)); }
            } finally { initializing = false; }
        });
    }
    private synchronized void initializeRemote(IPrivilegedChannel candidate, BackendSession.Mode selectedMode) throws Exception {
        if (inFlight()) throw new IllegalStateException("Another remote operation is still running");
        final byte[] token = secret.clone(); final IBrokerChannel selectedBroker = broker;
        final long requestEpoch = epoch;
        Future<?> request = rpc.submit(() -> {
            try { candidate.initialize(android.os.Process.myUid(),selectedMode.ordinal(),token,selectedBroker,clientLife); return null; }
            finally { signalConvergence(requestEpoch); }
        });
        pending = request;
        try { request.get(20,TimeUnit.SECONDS); }
        catch (TimeoutException error) { uncertain = true; update(BackendStatus.RECOVERY_REQUIRED,"Authentication timeout; remote result unknown, wait for convergence before explicit stop"); throw error; }
        finally { if (request.isDone()) pending = null; }
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
        final long requestEpoch = epoch;
        Future<Bundle> request = rpc.submit(() -> { try { return selected.invoke(token,operation,args); } finally { signalConvergence(requestEpoch); } }); pending = request;
        try {
            Bundle result = request.get(20, TimeUnit.SECONDS);
            if (result == null) throw new IllegalStateException("Backend returned no result");
            return result;
        } catch (TimeoutException e) {
            uncertain = true; update(BackendStatus.RECOVERY_REQUIRED,"Remote timeout; result unknown, recovery required"); throw e;
        } finally { if (request.isDone()) pending = null; }
    }
    private void signalConvergence(long requestEpoch) {
        // 只刷新不确定操作的 UI；保持“未知”并要求明确恢复，不重试写入。
        if (uncertain) main.postDelayed(() -> { if (requestEpoch == epoch && uncertain && !inFlight()) listener.changed(); },50);
    }
    private void terminate(IPrivilegedChannel service) throws Exception {
        if (service == null || !service.asBinder().isBinderAlive()) return;
        try { service.destroy(); } catch (RemoteException e) { /* System.exit 可能使调用收到 DeadObject，仍独立观察死亡。 */ }
        for (int n=0;n<10 && service.asBinder().isBinderAlive();n++) Thread.sleep(50);
        if (service.asBinder().isBinderAlive()) throw new IllegalStateException("Backend termination unverified");
    }
    public void stop() {
        autoAttempted = true;
        if (inFlight() || transitioning) throw new IllegalStateException("A remote operation is still running");
        epoch++; authenticatedRead = false; initialSnapshot = null; officialBinding = false; transitioning = true; permissionRequested = false; launchNonce = null;
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
                update(BackendStatus.STOPPED,!hadChannel ? "No authenticated backend channel; local bindings released" : brokerConfirmed ? "Backend stopped; Binder death observed" : "Backend stopped; Binder death observed, broker cleanup was not acknowledged; recovery backups retained");
            } catch (Exception e) {
                transitioning = false; update(BackendStatus.STOP_FAILED,"Backend stop not verified; keep recovery backups and retry after the remote operation converges");
            }
        });
    }
}
