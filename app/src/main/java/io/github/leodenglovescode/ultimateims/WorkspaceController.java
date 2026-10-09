package io.github.leodenglovescode.ultimateims;

import android.content.*;
import android.os.*;
import android.util.AtomicFile;
import io.github.leodenglovescode.ultimateims.backend.*;
import io.github.leodenglovescode.ultimateims.core.*;
import org.json.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** 私有工作区协调当前观测、历史与恢复；历史只用于用户请求的重放，不冒充实时值。 */
public final class WorkspaceController {
    private final Context context;
    public final BackendManager backend;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile Bundle current;
    private volatile boolean busy;
    private volatile String status = "No operation performed";
    private volatile List<String> results = List.of();
    private IdentityStore identities;
    private RecoveryJournal journal;
    private final AtomicFile history;
    public WorkspaceController(Context context, BackendManager backend) {
        this.context = context; this.backend = backend;
        history = new AtomicFile(new java.io.File(context.getNoBackupFilesDir(), "history.json"));
    }
    public boolean busy() { return busy || backend.inFlight(); }
    public String status() { return status; }
    public List<String> results() { return results; }
    public Bundle snapshot() { return backend.ready() ? current : null; }
    public List<Bundle> sims() {
        Bundle snapshot = snapshot(); if (snapshot == null) return List.of();
        ArrayList<Bundle> list = snapshot.getParcelableArrayList("sims", Bundle.class); return list == null ? List.of() : List.copyOf(list);
    }
    public Bundle selected(int sub) { return sims().stream().filter(s -> s.getInt("sub") == sub).findFirst().orElse(null); }
    public RawValue currentValue(int sub, ConfigKey key) {
        Bundle sim = selected(sub); Bundle value = sim == null ? null : sim.getBundle(key.name());
        return unpack(value);
    }
    private static RawValue unpack(Bundle value) {
        if (value == null) return RawValue.unknown();
        return new RawValue(RawValue.Kind.valueOf(value.getString("kind", "UNKNOWN")), value.getString("value", ""));
    }
    public void backendChanged() {
        if (!backend.ready()) { current = null; notifyChanged(); return; }
        if (!busy) task("Reading privileged live values", () -> {
            Bundle firstRead = backend.takeInitialSnapshot();
            if (firstRead != null) current = firstRead; else refreshNow();
            if (preferences().getBoolean("auto_restore", false)) autoRestore();
        });
    }
    private SharedPreferences preferences() { return context.getSharedPreferences("ultimateims", Context.MODE_PRIVATE); }
    public boolean autoRestoreEnabled() { return preferences().getBoolean("auto_restore", false); }
    public void setAutoRestore(boolean enabled) { preferences().edit().putBoolean("auto_restore", enabled).commit(); }
    public void invalidate() { current = null; notifyChanged(); }
    public void refresh() { task("Reading privileged live values", this::refreshNow); }
    private Bundle checked(int op, Bundle args) throws Exception {
        Bundle result = backend.invoke(op, args);
        if (!result.getBoolean("ok", false)) throw new IllegalStateException(result.getString("error", "Backend rejected operation"));
        return result;
    }
    private void refreshNow() throws Exception { current = null; current = checked(Protocol.SNAPSHOT, new Bundle()); }
    private synchronized void storage() throws Exception {
        if (identities == null) identities = new IdentityStore(context);
        if (journal == null) journal = new RecoveryJournal(new java.io.File(context.getNoBackupFilesDir(), "recovery").toPath());
    }
    private final class DeviceBackend implements OperationCoordinator.Backend {
        final Map<Integer,String> expected = new HashMap<>();
        boolean restoring;
        DeviceBackend() { for (Bundle sim : sims()) expected.put(sim.getInt("sub"), sim.getString("iccid")); }
        @Override public BackendSession.Mode mode() { return backend.mode(); }
        @Override public SimIdentity verifiedIdentity(int sub) throws Exception {
            if (sub == -2) return identities.device();
            Bundle args = new Bundle(); args.putInt("sub", sub);
            String actual = checked(Protocol.IDENTITY, args).getString("iccid");
            if (!Objects.equals(expected.get(sub), actual)) throw new SecurityException("SIM changed since live selection");
            return identities.sim(actual);
        }
        Bundle arguments(int sub, ConfigKey key) {
            Bundle args = new Bundle(); args.putInt("sub", sub); args.putString("key", key.name()); args.putString("expectedIccid", expected.get(sub)); return args;
        }
        @Override public RawValue readRaw(int sub, ConfigKey key) throws Exception { return unpack(checked(Protocol.READ, arguments(sub,key))); }
        @Override public void writeRaw(int sub, ConfigKey key, RawValue value) throws Exception {
            Bundle args = arguments(sub,key); args.putString("kind", value.kind().name()); args.putString("value", value.payload()); args.putBoolean("restore", restoring);
            checked(Protocol.WRITE,args);
        }
    }
    private record Operation(DeviceBackend device, OperationCoordinator engine, BackendSession session) { }
    private Operation operation() throws Exception {
        storage(); backend.reconcile();
        DeviceBackend device = new DeviceBackend(); BackendSession session = new BackendSession(android.os.Process.myUid(), backend.mode());
        return new Operation(device, new OperationCoordinator(journal, device, session), session);
    }
    private OperationCoordinator.Request request(Operation op, int sub, Map<ConfigKey,RawValue> target, boolean country) throws Exception {
        return new OperationCoordinator.Request(android.os.Process.myUid(),backend.mode(),op.session.token(),op.device.verifiedIdentity(sub),sub,Build.VERSION.SDK_INT,target,country);
    }
    public void apply(List<Integer> subscriptions, Map<ConfigKey,RawValue> draft, boolean countryConfirmed) {
        Map<ConfigKey,RawValue> changes = Map.copyOf(draft); List<Integer> selected = List.copyOf(subscriptions);
        task("Applying selected changes", () -> applyNow(selected,changes,countryConfirmed));
    }
    private void applyNow(List<Integer> subs, Map<ConfigKey,RawValue> draft, boolean country) throws Exception {
        if (draft.isEmpty() || subs.isEmpty()) throw new IllegalArgumentException("No selected changes or subscriptions");
        Operation op = operation(); ArrayList<String> batch = new ArrayList<>();
        for (int index = 0; index < subs.size(); index++) {
            int sub = subs.get(index);
            try {
                // 真正没有原始订阅值时，缺少可验证的unset恢复路径，不开始写入。
                for (ConfigKey key : draft.keySet()) if (draft.get(key).kind() == RawValue.Kind.UNSET && key.scope != ConfigKey.Scope.DEVICE) throw new IllegalStateException("Selective unset restoration unavailable");
                for (ConfigKey key : draft.keySet()) if (key.scope == ConfigKey.Scope.SUBSCRIPTION && op.device.readRaw(sub,key).kind() == RawValue.Kind.UNSET) throw new IllegalStateException("Raw subscription original unset; verified restoration unavailable");
                var req = request(op,sub,draft,country); var state = op.engine.apply(req);
                record(req.sim(),draft,state.name()); batch.add("SIM " + (index + 1) + ": " + state.name());
            } catch (Exception e) {
                batch.add("SIM " + (index + 1) + ": failed or unknown; originals retained");
                for (int remaining = index + 1; remaining < subs.size(); remaining++) batch.add("SIM " + (remaining + 1) + ": not executed");
                results = List.copyOf(batch); throw e;
            }
        }
        results = List.copyOf(batch); refreshNow();
    }
    public void restore(int sub, Set<ConfigKey> fields) {
        task("Restoring selected originals", () -> {
            Operation op = operation(); op.device.restoring = true;
            var req = request(op,sub,Map.of(),false);
            var result = fields == null ? op.engine.restore(req) : op.engine.restoreSelected(req, fields);
            results = List.of("Original restoration: " + result.name() + "; backups retained"); refreshNow();
        });
    }
    public void persistentVolte(int sub, boolean enabled) {
        if (enabled) apply(List.of(sub),Map.of(ConfigKey.VOIMS_OPT_IN,RawValue.integer(1),ConfigKey.ADVANCED_CALLING_USER,RawValue.integer(1)),false);
        else restore(sub, Set.of(ConfigKey.VOIMS_OPT_IN,ConfigKey.ADVANCED_CALLING_USER));
    }
    public void maintenance(int sub, boolean reset) {
        task(reset ? "Requesting carrier override reset" : "Requesting IMS restart", () -> {
            Operation op = operation(); SimIdentity sim = op.device.verifiedIdentity(sub);
            if (reset) {
                var old = journal.read(sim); Map<ConfigKey,RawValue> originals = new EnumMap<>(ConfigKey.class);
                if (old != null) originals.putAll(old.originals());
                for (ConfigKey key : ConfigKey.values()) if (key.scope == ConfigKey.Scope.CARRIER) {
                    RawValue value = op.device.readRaw(sub,key); if (value.readable()) originals.putIfAbsent(key,value);
                }
                journal.save(new RecoveryJournal.Entry(sim,RecoveryJournal.State.RECOVERY_REQUIRED,originals,old == null ? Map.of() : old.target()));
                setAutoRestore(false);
            }
            Bundle args = new Bundle(); args.putInt("sub",sub); args.putString("expectedIccid", op.device.expected.get(sub)); args.putBoolean("confirmed",true);
            Bundle result = checked(reset ? Protocol.RESET_CARRIER : Protocol.RESTART_IMS,args);
            results = List.of(result.getString("status")); refreshNow();
        });
    }
    private volatile String apnStatus = "APN not inspected";
    public String apnStatus() { return apnStatus; }
    public void apn(int sub, String profile, boolean restore) {
        task(profile == null && !restore ? "Reading preferred APN" : "Changing reviewed APN", () -> {
            Operation op = operation(); var identity = op.device.verifiedIdentity(sub);
            Bundle args = new Bundle(); args.putInt("sub",sub); args.putString("expectedIccid",op.device.expected.get(sub)); args.putBoolean("confirmed",true);
            Bundle before = checked(Protocol.APN_READ,args);
            if (profile == null && !restore) { apnStatus = "Preferred APN: " + before.getString("apn","unknown") + "; protocol=" + before.getString("protocol","unknown"); return; }
            android.util.AtomicFile file = new android.util.AtomicFile(new java.io.File(context.getNoBackupFilesDir(),identity.digest()+".apn.json"));
            JSONObject saved;
            if (file.getBaseFile().exists()) saved = new JSONObject(new String(file.readFully(),java.nio.charset.StandardCharsets.UTF_8));
            else {
                if (restore || !before.containsKey("id")) throw new IllegalStateException("Original APN unset or unavailable; verified restoration unavailable");
                saved = new JSONObject().put("schema",1).put("identity",identity.digest()).put("tag",java.util.UUID.randomUUID().toString());
                JSONObject original = new JSONObject();
                for (String key : List.of("id","name","apn","numeric","type","protocol","roaming_protocol","mmsc","apn_set_id")) original.put(key,before.get(key) == null ? JSONObject.NULL : before.get(key));
                saved.put("original",original).put("state","NEW");
            }
            if (saved.getInt("schema") != 1 || !saved.getString("identity").equals(identity.digest())) throw new SecurityException("APN backup identity mismatch");
            if (!restore && !List.of("NEW","VERIFIED","RESTORED").contains(saved.getString("state"))) throw new IllegalStateException("APN recovery required");
            if (!restore) ApnProfile.reviewed(profile,before.getString("operator",""));
            args.putString("tag",saved.getString("tag")); args.putString("profile",profile);
            if (restore) {
                JSONObject original = saved.getJSONObject("original"); Bundle packed = new Bundle(); packed.putLong("id",original.getLong("id"));
                for (String key : List.of("name","apn","numeric","type","protocol","roaming_protocol","mmsc","apn_set_id")) packed.putString(key,original.isNull(key) ? null : original.getString(key));
                args.putBundle("original",packed);
            }
            saved.put("state","RECOVERY_REQUIRED"); saveApn(file,saved);
            op.device.verifiedIdentity(sub);
            try {
                checked(restore ? Protocol.APN_RESTORE : Protocol.APN_APPLY,args); op.device.verifiedIdentity(sub);
                saved.put("state",restore ? "RESTORED" : "VERIFIED"); saveApn(file,saved);
                apnStatus = restore ? "Original preferred APN and owned-row removal verified; backup retained" : "Reviewed APN insert and selection verified; actual data service must be tested";
            } catch (Exception e) { apnStatus = "APN result unknown or failed; original backup retained; restore required"; throw e; }
        });
    }
    private void saveApn(android.util.AtomicFile file, JSONObject saved) throws Exception {
        java.io.FileOutputStream output = file.startWrite();
        try { output.write(saved.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); file.finishWrite(output); }
        catch (Exception e) { file.failWrite(output); throw e; }
    }
    public void readPortal() { task("Reading portal endpoints", () -> {
        storage(); DeviceBackend device = new DeviceBackend(); Bundle portal = new Bundle();
        for (ConfigKey key : List.of(ConfigKey.PORTAL_HTTP,ConfigKey.PORTAL_HTTPS)) {
            RawValue value = device.readRaw(-2,key); Bundle packed = new Bundle(); packed.putString("kind",value.kind().name()); packed.putString("value",value.payload()); portal.putBundle(key.name(),packed);
        }
        if (current == null) current = new Bundle(); current.putBundle("portal",portal);
    }); }
    public RawValue portal(ConfigKey key) { Bundle s = snapshot(); Bundle p = s == null ? null : s.getBundle("portal"); return unpack(p == null ? null : p.getBundle(key.name())); }
    private void record(SimIdentity sim, Map<ConfigKey,RawValue> changes, String state) throws Exception {
        JSONArray list = history(); JSONObject entry = new JSONObject(); entry.put("identity",sim.digest()); entry.put("time",System.currentTimeMillis()); entry.put("state",state);
        JSONObject values = new JSONObject(); for (var item : changes.entrySet()) values.put(item.getKey().name(),new JSONObject().put("kind",item.getValue().kind().name()).put("value",item.getValue().payload()));
        entry.put("values",values); list.put(entry);
        java.io.FileOutputStream file = history.startWrite();
        try { file.write(list.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); history.finishWrite(file); }
        catch (Exception e) { history.failWrite(file); throw e; }
    }
    public synchronized JSONArray history() throws Exception {
        if (!history.getBaseFile().exists()) return new JSONArray();
        return new JSONArray(new String(history.readFully(),java.nio.charset.StandardCharsets.UTF_8));
    }
    public Map<ConfigKey,RawValue> historyDraft(int index) throws Exception {
        JSONObject values = history().getJSONObject(index).getJSONObject("values"); Map<ConfigKey,RawValue> result = new EnumMap<>(ConfigKey.class);
        for (var names = values.keys(); names.hasNext();) { String name = names.next(); JSONObject value = values.getJSONObject(name); result.put(ConfigKey.valueOf(name),new RawValue(RawValue.Kind.valueOf(value.getString("kind")),value.getString("value"))); }
        return result;
    }
    public String identityDigest(String iccid) throws Exception { storage(); return identities.sim(iccid).digest(); }
    public RecoveryJournal.Entry backup(int sub) throws Exception { storage(); return journal.read(sub == -2 ? identities.device() : identities.sim(Objects.requireNonNull(selected(sub)).getString("iccid"))); }
    private void autoRestore() throws Exception {
        for (Bundle sim : sims()) {
            var entry = backup(sim.getInt("sub"));
            if (entry != null && entry.requiresRecovery()) { setAutoRestore(false); throw new IllegalStateException("Automatic reapply paused; recovery required"); }
            if (entry != null && entry.state() == RecoveryJournal.State.VERIFIED) {
                Map<ConfigKey,RawValue> carrier = new EnumMap<>(ConfigKey.class);
                for (var item : entry.target().entrySet()) if (item.getKey().scope == ConfigKey.Scope.CARRIER) carrier.put(item.getKey(),item.getValue());
                if (!carrier.isEmpty()) applyNow(List.of(sim.getInt("sub")),carrier,true);
            }
        }
    }
    private interface Work { void run() throws Exception; }
    private synchronized void task(String description, Work action) {
        if (busy()) { status = "Operation refused: another operation is active or uncertain"; notifyChanged(); return; }
        busy = true; status = description; notifyChanged();
        worker.execute(() -> {
            try { action.run(); status = "Operation completed; inspect verification evidence"; }
            catch (Exception e) { current = null; status = "Operation failed or unknown: " + e.getClass().getSimpleName(); android.util.Log.w("UltimateIMS", status); }
            finally { busy = false; notifyChanged(); }
        });
    }
    private void notifyChanged() { context.sendBroadcast(new Intent(UltimateApplication.STATUS_CHANGED).setPackage(context.getPackageName())); }
}
