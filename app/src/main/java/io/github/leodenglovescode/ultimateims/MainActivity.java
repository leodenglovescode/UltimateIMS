package io.github.leodenglovescode.ultimateims;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.*;
import android.telephony.SubscriptionManager;
import android.telephony.ims.*;
import android.telephony.ims.feature.MmTelFeature;
import android.view.*;
import android.widget.*;
import io.github.leodenglovescode.ultimateims.core.*;
import java.util.*;
import java.util.concurrent.*;

/** 原创只读工作台；配置写入入口在后端和恢复链路验收后才开放。 */
public final class MainActivity extends Activity {
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private final int[] pages = {R.string.home, R.string.ims, R.string.diagnostics, R.string.system, R.string.recovery, R.string.settings};
    private LinearLayout body;
    private FrameworkReader.Snapshot snapshot;
    private int page;
    private int selectedSub = -1;
    private boolean reading;
    private boolean started;
    private long readGeneration;
    private final Map<Integer, ImsObservation> observations = new HashMap<>();
    private SubscriptionManager.OnSubscriptionsChangedListener subscriptionListener;
    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };
    private final class ImsObservation {
        final ImsMmTelManager manager;
        int registrationText = R.string.reading;
        Integer transport;
        String capabilities;
        boolean registrationAttached;
        boolean capabilityAttached;
        final int subId;
        final RegistrationManager.RegistrationCallback registration = new RegistrationManager.RegistrationCallback() {
            @Override public void onRegistered(int technology) { registrationText = R.string.registered; render(); }
            @Override public void onRegistering(int technology) { registrationText = R.string.registering; transport = null; capabilities = null; render(); }
            @Override public void onUnregistered(ImsReasonInfo info) { registrationText = R.string.not_registered; transport = null; capabilities = null; render(); }
        };
        final ImsMmTelManager.CapabilityCallback capability = new ImsMmTelManager.CapabilityCallback() {
            @Override public void onCapabilitiesStatusChanged(MmTelFeature.MmTelCapabilities value) {
                capabilities = bool(value.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE)) + " / "
                        + bool(value.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO)) + " / "
                        + bool(value.isCapable(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT));
                render();
            }
        };
        ImsObservation(ImsMmTelManager manager, int subId) { this.manager = manager; this.subId = subId; }
        // 回调支持运营商权限；读取被拒时保留未知，不能用配置替代注册观测。
        @android.annotation.SuppressLint("MissingPermission")
        void attach() {
            try {
                var phone = getSystemService(android.telephony.TelephonyManager.class).createForSubscriptionId(subId);
                boolean precise = checkSelfPermission("android.permission.READ_PRECISE_PHONE_STATE") == PackageManager.PERMISSION_GRANTED;
                boolean privileged = checkSelfPermission("android.permission.READ_PRIVILEGED_PHONE_STATE") == PackageManager.PERMISSION_GRANTED;
                if (!precise && !privileged && !phone.hasCarrierPrivileges()) return;
            } catch (SecurityException | UnsupportedOperationException e) { return; }
            try { manager.registerImsRegistrationCallback(getMainExecutor(), registration); registrationAttached = true; }
            catch (SecurityException | ImsException | UnsupportedOperationException e) { /* 无法读取时保持未知。 */ }
            try { manager.registerMmTelCapabilityCallback(getMainExecutor(), capability); capabilityAttached = true; }
            catch (SecurityException | ImsException | UnsupportedOperationException e) { /* 不把配置值当作能力。 */ }
            try { manager.getRegistrationTransportType(getMainExecutor(), value -> { transport = value; render(); }); }
            catch (SecurityException | UnsupportedOperationException e) { /* 未知。 */ }
        }
        @android.annotation.SuppressLint("MissingPermission")
        void detach() {
            try {
                if (registrationAttached) manager.unregisterImsRegistrationCallback(registration);
                if (capabilityAttached) manager.unregisterMmTelCapabilityCallback(capability);
            } catch (SecurityException | UnsupportedOperationException e) { /* 权限撤销后仍清理本地观测。 */ }
        }
    }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) { page = savedInstanceState.getInt("page"); selectedSub = savedInstanceState.getInt("sim", -1); }
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), 0);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(dp(20) + bars.left, dp(12) + bars.top, dp(20) + bars.right, bars.bottom); return insets;
        });
        TextView title = new TextView(this); title.setText(R.string.app_name); title.setTextSize(28); title.setTypeface(null, Typeface.BOLD); root.addView(title);
        Spinner navigation = new Spinner(this);
        List<String> labels = new ArrayList<>(); for (int id : pages) labels.add(getString(id));
        navigation.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        navigation.setContentDescription(getString(R.string.app_name)); navigation.setMinimumHeight(dp(48)); root.addView(navigation);
        navigation.setSelection(page);
        navigation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { page = position; render(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        ScrollView scroll = new ScrollView(this); body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root); render();
    }
    @Override protected void onStart() {
        super.onStart(); started = true;
        registerReceiver(powerReceiver, new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        subscriptionListener = new SubscriptionManager.OnSubscriptionsChangedListener() {
            @Override public void onSubscriptionsChanged() { refresh(); }
        };
        getSystemService(SubscriptionManager.class).addOnSubscriptionsChangedListener(getMainExecutor(), subscriptionListener);
        refresh();
    }
    @Override protected void onStop() {
        started = false; readGeneration++;
        unregisterReceiver(powerReceiver);
        getSystemService(SubscriptionManager.class).removeOnSubscriptionsChangedListener(subscriptionListener);
        for (var observation : observations.values()) observation.detach(); observations.clear();
        // 回到前台时重新读取，禁止把离线观测作为当前状态。
        snapshot = null; super.onStop();
    }
    @Override protected void onDestroy() { reader.shutdownNow(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putInt("page", page); out.putInt("sim", selectedSub); }
    private void refresh() {
        readGeneration++;
        if (reading || !started) return;
        final long requestedGeneration = readGeneration;
        reading = true; snapshot = null;
        for (var observation : observations.values()) observation.detach(); observations.clear(); render();
        reader.execute(() -> {
            FrameworkReader.Snapshot result;
            try { result = new FrameworkReader(this).read(); }
            catch (RuntimeException e) { result = null; android.util.Log.w("UltimateIMS", "Status read failed; values remain unknown"); }
            var completed = result;
            runOnUiThread(() -> {
                reading = false;
                if (!started || isDestroyed()) return;
                if (requestedGeneration != readGeneration) { refresh(); return; }
                snapshot = completed;
                if (snapshot != null) {
                    if (snapshot.sims().stream().noneMatch(s -> s.subId() == selectedSub)) selectedSub = snapshot.sims().isEmpty() ? -1 : snapshot.sims().get(0).subId();
                    for (var sim : snapshot.sims()) {
                        try {
                            var observation = new ImsObservation(getSystemService(android.telephony.ims.ImsManager.class).getImsMmTelManager(sim.subId()), sim.subId());
                            observations.put(sim.subId(), observation); observation.attach();
                        } catch (RuntimeException e) { /* 不支持或权限不足，状态保持未知。 */ }
                    }
                }
                render();
            });
        });
    }
    private void render() {
        if (body == null || isDestroyed()) return;
        body.removeAllViews(); text(getString(R.string.foundation), false); heading(pages[page]);
        if (page == 5) { settings(); return; }
        button(R.string.refresh, this::refresh);
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) button(R.string.permission, () -> requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, 1));
        if (page == 4) { text(getString(R.string.evidence), false); text(getString(R.string.backup_explain), false); return; }
        text(getString(R.string.backend, backendName()), false);
        text(getString(R.string.device, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT), false);
        text(getString(R.string.battery, bool(getSystemService(PowerManager.class).isPowerSaveMode())), false);
        if (snapshot == null) { text(getString(reading ? R.string.loading : R.string.error_read), false); return; }
        if (page == 3) { system(); return; }
        if (snapshot.sims().isEmpty()) { text(getString(R.string.no_sims), false); return; }
        simSelector();
        FrameworkReader.Sim sim = snapshot.sims().stream().filter(s -> s.subId() == selectedSub).findFirst().orElse(null);
        if (sim == null) return;
        if (page == 0) {
            text(getString(R.string.default_data, bool(sim.defaultData())), false);
            text(getString(R.string.sim_country, sim.country() == null || sim.country().isEmpty() ? unknown() : sim.country()), false);
            text(getString(R.string.rat, sim.dataRat() == null || sim.dataRat() == 0 ? unknown() : rat(sim.dataRat())), false);
            var ims = observations.get(sim.subId());
            text(getString(R.string.ims_registration, ims == null ? unknown() : getString(ims.registrationText)), false);
            text(getString(R.string.capabilities, ims == null || ims.capabilities == null ? unknown() : ims.capabilities), false);
            text(getString(R.string.ims_transport, ims == null || ims.transport == null ? unknown() : transport(ims.transport)), false);
            text(getString(R.string.observation_note), false);
        } else if (page == 1) {
            text(getString(R.string.read_only), false);
            if (sim.carrierConfig().isEmpty()) text(getString(R.string.config_unreadable), false);
            for (var key : ConfigKey.values()) if (key.scope == ConfigKey.Scope.CARRIER) {
                text(key.platformName, true);
                Object value = sim.carrierConfig().get(key);
                text(value == null ? unknown() : FrameworkReader.format(value), false);
            }
        } else if (page == 2) {
            text(getString(R.string.mask_user, mask(sim.userMask())), false); text(getString(R.string.mask_power, mask(sim.powerMask())), false); text(getString(R.string.mask_carrier, mask(sim.carrierMask())), false);
            int finding = switch (NetworkAssessment.assess(sim.userMask(), sim.powerMask(), sim.carrierMask())) {
                case POWER_EXCLUDES_NR -> R.string.power_nr;
                case USER_EXCLUDES_NR -> R.string.user_nr;
                case CARRIER_EXCLUDES_NR -> R.string.carrier_nr;
                case ALL_READ_MASKS_ALLOW_NR -> R.string.masks_allow;
                case UNKNOWN -> R.string.masks_unknown;
            };
            text(getString(finding), false);
            network(); text(getString(R.string.observation_note), false);
        }
    }
    private void simSelector() {
        Spinner sims = new Spinner(this); List<String> labels = new ArrayList<>(); int selection = 0;
        for (var sim : snapshot.sims()) {
            if (sim.subId() == selectedSub) selection = labels.size();
            labels.add(getString(R.string.sim, sim.slot() + 1, sim.carrier(), getString(sim.embedded() ? R.string.esim : R.string.physical)));
        }
        sims.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels)); sims.setSelection(selection); sims.setMinimumHeight(dp(48));
        sims.setContentDescription(getString(R.string.home)); body.addView(sims);
        sims.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                if (snapshot != null && selectedSub != snapshot.sims().get(position).subId()) { selectedSub = snapshot.sims().get(position).subId(); render(); }
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
    }
    private void system() {
        network(); text(getString(R.string.portal_explain), false);
        text("captive_portal_http_url", true); text(!snapshot.portalReadable() ? unknown() : snapshot.portalHttp() == null ? getString(R.string.unset) : snapshot.portalHttp(), false);
        text("captive_portal_https_url", true); text(!snapshot.portalReadable() ? unknown() : snapshot.portalHttps() == null ? getString(R.string.unset) : snapshot.portalHttps(), false);
    }
    private void network() {
        text(getString(R.string.network_validated, bool(snapshot.validated())), false);
        text(getString(R.string.network_captive, bool(snapshot.captive())), false);
        text(getString(R.string.network_metered, bool(snapshot.unmetered())), false);
    }
    private void settings() {
        text(getString(R.string.privacy), false); text(getString(R.string.backend, backendName()), false);
        button(R.string.backend_choose, () -> new AlertDialog.Builder(this).setTitle(R.string.backend_choose)
                .setItems(new String[]{getString(R.string.shizuku), getString(R.string.adb), getString(R.string.root)}, (d, index) -> {
                    getPreferences(MODE_PRIVATE).edit().putString("selected_backend", BackendSession.Mode.values()[index].name()).apply(); render();
                }).show());
        if (!getPreferences(MODE_PRIVATE).getString("selected_backend", "").isEmpty()) text(getString(R.string.backend_unavailable), false);
        button(R.string.language, () -> new AlertDialog.Builder(this).setTitle(R.string.language)
                .setItems(new String[]{getString(R.string.auto), getString(R.string.english), getString(R.string.chinese)}, (d, index) ->
                    getSystemService(LocaleManager.class).setApplicationLocales(index == 0 ? LocaleList.getEmptyLocaleList() : LocaleList.forLanguageTags(index == 1 ? "en" : "zh-CN"))).show());
    }
    private String backendName() {
        String value = getPreferences(MODE_PRIVATE).getString("selected_backend", "");
        return getString(switch (value) { case "SHIZUKU" -> R.string.shizuku; case "EMBEDDED_ADB" -> R.string.adb; case "EXISTING_ROOT" -> R.string.root; default -> R.string.backend_none; });
    }
    private String bool(Boolean value) { return value == null ? unknown() : getString(value ? R.string.yes : R.string.no); }
    private String unknown() { return getString(R.string.unknown); }
    private String mask(Long value) { return value == null ? unknown() : "0x" + Long.toHexString(value); }
    private String rat(int value) { return switch (value) { case 13 -> "LTE"; case 20 -> "NR"; default -> Integer.toString(value); }; }
    private String transport(int value) { return value == 1 ? getString(R.string.cellular) : value == 2 ? getString(R.string.wifi) : unknown(); }
    private void heading(int id) { text(getString(id), true); }
    private void text(String value, boolean title) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(title ? 20 : 16);
        view.setPadding(0, dp(10), 0, dp(8)); view.setTextColor(0xFF153D39);
        if (title) { view.setTypeface(null, Typeface.BOLD); view.setAccessibilityHeading(true); }
        view.setTextIsSelectable(true); body.addView(view);
    }
    private void button(int id, Runnable action) {
        Button view = new Button(this); view.setText(id); view.setMinimumHeight(dp(48)); view.setOnClickListener(v -> action.run()); body.addView(view);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results); refresh();
    }
}
