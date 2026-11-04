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

/** 原创工作台；所有写入均由具备身份验证和恢复日志的控制器执行。 */
public final class MainActivity extends androidx.appcompat.app.AppCompatActivity {
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private final int[] pages = {R.string.home, R.string.ims, R.string.diagnostics, R.string.system, R.string.recovery, R.string.settings};
    private LinearLayout body;
    private com.google.android.material.bottomnavigation.BottomNavigationView navigation;
    private com.google.android.material.appbar.MaterialToolbar toolbar;
    private ScrollView scroll;
    private com.google.android.material.navigation.NavigationView drawerNavigation;
    private androidx.drawerlayout.widget.DrawerLayout drawer;
    private LinearLayout content;
    private int renderedPage = -1;
    private int renderedSub = -1;
    private FeaturePanels panels;
    private WorkspaceController workspace;
    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { render(); }
    };
    private FrameworkReader.Snapshot snapshot;
    private int page;
    private int selectedSub = -1;
    private boolean reading;
    private boolean started;
    private long readGeneration;
    private SubscriptionManager.OnSubscriptionsChangedListener subscriptionListener;
    private CarrierObservation carrierObservation;
    @androidx.annotation.RequiresApi(34)
    private final class CarrierObservation {
        private final android.telephony.CarrierConfigManager manager = getSystemService(android.telephony.CarrierConfigManager.class);
        private final android.telephony.CarrierConfigManager.CarrierConfigChangeListener listener = (slot, sub, carrier, specificCarrier) -> refresh();
        void attach() { manager.registerCarrierConfigChangeListener(getMainExecutor(), listener); }
        void detach() { manager.unregisterCarrierConfigChangeListener(listener); }
    }
    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        workspace = ((UltimateApplication)getApplication()).workspace();
        panels = new FeaturePanels(this, workspace, this::render);
        if (savedInstanceState != null) panels.restoreState(savedInstanceState.getBundle("panels"));
        page = getIntent().getIntExtra("page",0);
        if (savedInstanceState != null) { page = savedInstanceState.getInt("page"); selectedSub = savedInstanceState.getInt("sim", -1); }
        page = Math.max(0,Math.min(page,pages.length-1));
        LinearLayout root = MaterialUi.column(this);
        root.setBackgroundColor(MaterialUi.color(this,com.google.android.material.R.attr.colorSurface));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            var keyboard = insets.getInsets(WindowInsets.Type.ime());
            v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,keyboard.bottom)); return insets;
        });
        boolean dark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        var barsController = androidx.core.view.WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView());
        barsController.setAppearanceLightStatusBars(!dark); barsController.setAppearanceLightNavigationBars(!dark);
        toolbar = new com.google.android.material.appbar.MaterialToolbar(this);
        toolbar.getMenu().add(0,200,0,R.string.refresh).setIcon(R.drawable.ic_refresh).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        toolbar.getMenu().add(0,201,1,R.string.settings).setIcon(R.drawable.ic_settings).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        toolbar.setOnMenuItemClickListener(item -> { if (item.getItemId() == 200) refresh(); else navigate(5); return true; });
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,-2));
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        content = MaterialUi.column(this); body = content;
        content.setPadding(dp(16),dp(12),dp(16),dp(24)); scroll.addView(content);
        // 宽屏保持可读行长；窄屏留出一致的内容边距。
        scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            int side = Math.max(dp(16),(r-l-dp(840))/2);
            if (content.getPaddingLeft() != side) content.setPadding(side,dp(12),side,dp(24));
        });
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        navigation = new com.google.android.material.bottomnavigation.BottomNavigationView(this);
        navigation.setLabelVisibilityMode(com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED);
        int[] destinations = {0,1,3,4};
        int[] labels = {R.string.nav_home,R.string.nav_ims,R.string.nav_network,R.string.nav_tools};
        int[] icons = {R.drawable.ic_call,R.drawable.ic_settings,R.drawable.ic_network,R.drawable.ic_tools};
        for (int i=0;i<destinations.length;i++) navigation.getMenu().add(0,100+destinations[i],i,labels[i]).setIcon(icons[i]);
        navigation.setOnItemSelectedListener(item -> { int target = item.getItemId()-100; if (target != page) navigate(target); return true; });
        for (int i=0;i<toolbar.getMenu().size();i++) toolbar.getMenu().getItem(i).setIconTintList(android.content.res.ColorStateList.valueOf(MaterialUi.color(this,com.google.android.material.R.attr.colorOnSurface)));
        if (getResources().getConfiguration().fontScale >= 1.3f || getResources().getConfiguration().screenWidthDp >= 600) {
            drawer = new androidx.drawerlayout.widget.DrawerLayout(this); drawer.addView(root,new androidx.drawerlayout.widget.DrawerLayout.LayoutParams(-1,-1));
            drawerNavigation = new com.google.android.material.navigation.NavigationView(this); drawerNavigation.setItemMaxLines(3);
            drawerNavigation.setFitsSystemWindows(true);
            int[] drawerPages = {0,1,3,4,5};
            for (int i=0;i<drawerPages.length;i++) drawerNavigation.getMenu().add(0,300+drawerPages[i],i,pages[drawerPages[i]]).setIcon(i < icons.length ? icons[i] : R.drawable.ic_settings).setCheckable(true);
            drawerNavigation.setNavigationItemSelectedListener(item -> { drawer.closeDrawers(); navigate(item.getItemId()-300); return true; });
            var params = new androidx.drawerlayout.widget.DrawerLayout.LayoutParams(Math.min(dp(360),getResources().getDisplayMetrics().widthPixels-dp(56)),-1); params.gravity = androidx.core.view.GravityCompat.START;
            drawer.addView(drawerNavigation,params);
            toolbar.setNavigationIcon(R.drawable.ic_menu); toolbar.setNavigationIconTint(MaterialUi.color(this,com.google.android.material.R.attr.colorOnSurface));
            toolbar.setNavigationContentDescription(R.string.browse_sections); toolbar.setNavigationOnClickListener(v -> drawer.openDrawer(androidx.core.view.GravityCompat.START));
            var back = new androidx.activity.OnBackPressedCallback(false) { @Override public void handleOnBackPressed() { drawer.closeDrawers(); } };
            getOnBackPressedDispatcher().addCallback(this,back);
            drawer.addDrawerListener(new androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
                @Override public void onDrawerOpened(View view) { back.setEnabled(true); }
                @Override public void onDrawerClosed(View view) { back.setEnabled(false); }
            });
            setContentView(drawer);
        } else { root.addView(navigation,new LinearLayout.LayoutParams(-1,-2)); setContentView(root); }
        render();
    }
    private void navigate(int destination) {
        if (destination == page) return;
        page = destination; scroll.scrollTo(0,0); render();
    }
    @Override protected void onStart() {
        super.onStart(); started = true;
        registerReceiver(statusReceiver, new IntentFilter(UltimateApplication.STATUS_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        registerReceiver(powerReceiver, new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), Context.RECEIVER_NOT_EXPORTED);
        subscriptionListener = new SubscriptionManager.OnSubscriptionsChangedListener() {
            @Override public void onSubscriptionsChanged() { refresh(); }
        };
        getSystemService(SubscriptionManager.class).addOnSubscriptionsChangedListener(getMainExecutor(), subscriptionListener);
        if (Build.VERSION.SDK_INT >= 34) {
            try { carrierObservation = new CarrierObservation(); carrierObservation.attach(); }
            catch (SecurityException | UnsupportedOperationException e) { /* 不可订阅时仍支持明确刷新。 */ }
        }
        refresh();
    }
    @Override protected void onStop() {
        started = false; readGeneration++;
        unregisterReceiver(powerReceiver); unregisterReceiver(statusReceiver);
        if (Build.VERSION.SDK_INT >= 34 && carrierObservation != null) {
            try { carrierObservation.detach(); } catch (RuntimeException e) { /* 生命周期仍清除当前观测。 */ }
            carrierObservation = null;
        }
        getSystemService(SubscriptionManager.class).removeOnSubscriptionsChangedListener(subscriptionListener);
        // 回到前台时重新读取，禁止把离线观测作为当前状态。
        snapshot = null; super.onStop();
    }
    @Override protected void onDestroy() { reader.shutdownNow(); panels.close(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putInt("page", page); out.putInt("sim", selectedSub); out.putBundle("panels",panels.saveState()); }
    private void refresh() {
        if (workspace.backend.ready() && !workspace.busy()) workspace.refresh();
        else workspace.invalidate();
        readGeneration++;
        if (reading || !started) return;
        final long requestedGeneration = readGeneration;
        reading = true; snapshot = null;
        render();
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
                }
                render();
            });
        });
    }
    private void render() {
        if (body == null || isDestroyed()) return;
        int previousScroll = renderedPage == page && renderedSub == selectedSub ? scroll.getScrollY() : 0;
        View focus = getCurrentFocus(); boolean searchFocused = focus != null && "config_search".equals(focus.getTag());
        int searchPosition = searchFocused ? ((EditText)focus).getSelectionStart() : -1;
        body = content; body.removeAllViews(); toolbar.setTitle(R.string.app_name); toolbar.setSubtitle(new int[]{R.string.nav_home,R.string.nav_ims,R.string.nav_diagnostics,R.string.nav_network,R.string.nav_tools,R.string.nav_settings}[page]);
        if (drawerNavigation != null) drawerNavigation.setCheckedItem(300+(page == 2 ? 0 : page));
        if (page < 5) navigation.getMenu().findItem(100+(page == 2 ? 0 : page)).setChecked(true);
        else for (int i=0;i<navigation.getMenu().size();i++) navigation.getMenu().getItem(i).setChecked(false);
        renderedPage = page; renderedSub = selectedSub;
        scroll.post(() -> {
            if (searchFocused && page == 1) { View replacement = content.findViewWithTag("config_search"); if (replacement instanceof EditText input) { input.requestFocus(); input.setSelection(Math.min(Math.max(0,searchPosition),input.length())); } }
            scroll.scrollTo(0,previousScroll);
        });

        if (page == 5) { settings(); return; }

        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) button(R.string.permission, () -> requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, 1));
        if (!workspace.sims().isEmpty() && workspace.selected(selectedSub) == null) selectedSub = workspace.sims().get(0).getInt("sub");
        panels.attach(body,selectedSub);
        if (page == 1) {
            backendSetup(); body = content; privilegedSimSelector(); panels.attach(content,selectedSub); panels.ims(); return;
        }
        if (page == 4) { privilegedSimSelector(); panels.attach(body,selectedSub); panels.status(); panels.recovery(); return; }
        if (page == 0) {
            privilegedSimSelector(); panels.attach(content,selectedSub);
            if (!workspace.backend.ready()) button(R.string.connect_settings,this::openConfigurationPage);
            panels.radio(); body = content; button(R.string.diagnostics,() -> navigate(2));
            heading(R.string.device_section);
            text(getString(R.string.device,Build.MODEL,Build.VERSION.RELEASE,Build.VERSION.SDK_INT),false);
            text(getString(R.string.battery,bool(getSystemService(PowerManager.class).isPowerSaveMode())),false); text(getString(R.string.security_patch,Build.VERSION.SECURITY_PATCH),false);
            return;
        }
        if (page == 3) { panels.status(); panels.system(); return; }
        if (page == 2) {
            privilegedSimSelector(); panels.attach(body,selectedSub); panels.status(); panels.diagnostics();
            heading(R.string.policy_section);
            if (snapshot != null) {
                var sim = snapshot.sims().stream().filter(item -> item.subId() == selectedSub).findFirst().orElse(null);
                if (sim != null) {
                    text(getString(R.string.mask_user,mask(sim.userMask())),false); text(getString(R.string.mask_power,mask(sim.powerMask())),false); text(getString(R.string.mask_carrier,mask(sim.carrierMask())),false);
                    text(getString(switch (NetworkAssessment.assess(sim.userMask(),sim.powerMask(),sim.carrierMask())) {
                        case POWER_EXCLUDES_NR -> R.string.power_nr; case USER_EXCLUDES_NR -> R.string.user_nr;
                        case CARRIER_EXCLUDES_NR -> R.string.carrier_nr; case ALL_READ_MASKS_ALLOW_NR -> R.string.masks_allow; case UNKNOWN -> R.string.masks_unknown;
                    }),false);
                }
                network();
            } else text(unknown(),false);
            return;
        }
    }
    private void simSelector() {
        List<String> labels = new ArrayList<>(); int selection = 0;
        for (var sim : snapshot.sims()) {
            if (sim.subId() == selectedSub) selection = labels.size();
            labels.add(getString(R.string.sim,sim.slot()+1,sim.carrier(),getString(sim.embedded()?R.string.esim:R.string.physical)));
        }
        MaterialUi.add(body,new MaterialUi.Choice(this,getString(R.string.selected_sim),labels,selection,position -> {
            if (snapshot != null && position < snapshot.sims().size() && selectedSub != snapshot.sims().get(position).subId()) { selectedSub = snapshot.sims().get(position).subId(); render(); }
        }));
    }
    private void network() {
        text(getString(R.string.network_validated, bool(snapshot.validated())), false);
        text(getString(R.string.network_captive, bool(snapshot.captive())), false);
        text(getString(R.string.network_metered, bool(snapshot.unmetered())), false);
    }
    private void settings() {
        heading(R.string.appearance);
        text(getString(R.string.theme_system_automatic),false);
        com.google.android.material.materialswitch.MaterialSwitch dynamic = new com.google.android.material.materialswitch.MaterialSwitch(this);
        dynamic.setText(R.string.dynamic_color); dynamic.setMinHeight(dp(56)); dynamic.setChecked(ThemePreferences.dynamic(this));
        dynamic.setOnCheckedChangeListener((v,checked) -> { ThemePreferences.setDynamic(this,checked); recreate(); }); MaterialUi.add(body,dynamic);
        text(getString(R.string.dynamic_color_explain),false);
        button(R.string.language, () -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(R.string.language)
                .setItems(new String[]{getString(R.string.auto),getString(R.string.english),getString(R.string.chinese)}, (d, index) ->
                    getSystemService(LocaleManager.class).setApplicationLocales(index == 0 ? LocaleList.getEmptyLocaleList() : LocaleList.forLanguageTags(index == 1 ? "en" : "zh-CN"))).show());
        panels.attach(content,selectedSub); panels.about();
        body = content; heading(R.string.privacy_section); text(getString(R.string.privacy),false);
    }
    private void backendSetup() {
        BackendSession.Mode chosen = chosenBackend();
        String[] labels = {getString(R.string.backend_none),getString(R.string.shizuku),getString(R.string.adb),getString(R.string.root)};
        MaterialUi.Choice choice = new MaterialUi.Choice(this,getString(R.string.backend_choose),java.util.Arrays.asList(labels),chosen == null ? 0 : chosen.ordinal()+1,index -> {
            if (workspace.busy() || workspace.backend.bound()) { Toast.makeText(this,R.string.busy,Toast.LENGTH_LONG).show(); render(); return; }
            getPreferences(MODE_PRIVATE).edit().putString("selected_backend",index == 0 ? "" : BackendSession.Mode.values()[index-1].name()).apply(); render();
        });
        choice.setEnabled(!workspace.backend.bound() && !workspace.busy()); MaterialUi.add(body,choice);
        panels.attach(content,selectedSub); panels.settings(chosenBackend());
    }
    BackendSession.Mode chosenBackend() {
        String chosen = getPreferences(MODE_PRIVATE).getString("selected_backend", "");
        return chosen.isEmpty() ? null : BackendSession.Mode.valueOf(chosen);
    }
    void openImsPage() { navigate(1); }
    void openConfigurationPage() { navigate(1); }
    boolean configurationVisible() { return page == 1; }
    @Override protected void onActivityResult(int request, int result, Intent data) { super.onActivityResult(request,result,data); panels.fileResult(request,result,data); }
    private void privilegedSimSelector() {
        var records = workspace.sims();
        if (records.isEmpty()) { if (snapshot != null && !snapshot.sims().isEmpty()) simSelector(); return; }
        List<String> labels = new ArrayList<>(); int selection = 0;
        for (var sim : records) { if (sim.getInt("sub") == selectedSub) selection = labels.size(); labels.add(getString(R.string.sim,sim.getInt("slot")+1,sim.getString("carrier"),getString(sim.getBoolean("embedded")?R.string.esim:R.string.physical))); }
        MaterialUi.add(body,new MaterialUi.Choice(this,getString(R.string.selected_sim),labels,selection,position -> {
            int next = records.get(position).getInt("sub"); if (selectedSub != next) { selectedSub = next; render(); }
        }));
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
    private void heading(int id) { body = MaterialUi.card(content,getString(id)); }
    private void text(String value, boolean title) { MaterialUi.add(body,MaterialUi.text(this,value,title)); }
    private void button(int id, Runnable action) { MaterialUi.add(body,MaterialUi.button(this,id,action,false)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results); refresh();
    }
}
