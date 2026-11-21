package io.github.leodenglovescode.ultimateims;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import android.view.View;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import io.github.leodenglovescode.ultimateims.core.*;
import io.github.leodenglovescode.ultimateims.adb.WirelessAdb;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** 控件均连接实际操作；写入前显示目标、差异、作用域与验证限制。 */
final class FeaturePanels {
    private final MainActivity activity;
    private final WorkspaceController workspace;
    private final Runnable redraw;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout body;
    private LinearLayout panelRoot;
    private String searchQuery = "";
    private boolean allSims;
    private int sub;
    private final Map<ConfigKey,RawValue> draft = new EnumMap<>(ConfigKey.class);
    private final Map<ConfigKey,RawValue> portalDraft = new EnumMap<>(ConfigKey.class);
    private final Map<RadioDisplayProfiles.Name,Map<ConfigKey,RawValue>> profileDraftBaselines = new EnumMap<>(RadioDisplayProfiles.Name.class);
    private final Set<RadioDisplayProfiles.Name> profileRestores = EnumSet.noneOf(RadioDisplayProfiles.Name.class);
    private int draftSub = -1;
    private String draftIccid;
    private String restoredIdentity;
    private String exportText;
    private volatile String appLogs = "";
    private volatile String publicIpLocal = "";
    private volatile String localLog = "";
    FeaturePanels(MainActivity activity, WorkspaceController workspace, Runnable redraw) { this.activity = activity; this.workspace = workspace; this.redraw = redraw; }
    Bundle saveState() {
        Bundle saved = new Bundle(); saved.putString("search",searchQuery); saved.putBoolean("allSims",allSims); saved.putBundle("draft",pack(draft)); saved.putBundle("portalDraft",pack(portalDraft)); saved.putInt("draftSub",draftSub); saved.putString("export",exportText);
        String digest = restoredIdentity;
        if (digest == null && draftIccid != null) try { digest = workspace.identityDigest(draftIccid); } catch (Exception e) { digest = "unverified"; }
        Bundle profiles = new Bundle(); profileDraftBaselines.forEach((name,values) -> profiles.putBundle(name.name(),pack(values)));
        saved.putBundle("profileBaselines",profiles);
        saved.putStringArrayList("profileRestores",new ArrayList<>(profileRestores.stream().map(Enum::name).collect(java.util.stream.Collectors.toList())));
        saved.putString("identity",digest == null ? "unverified" : digest); return saved;
    }
    void restoreState(Bundle saved) {
        if (saved == null) return;
        Bundle profiles = saved.getBundle("profileBaselines");
        if (profiles != null) for (String name : profiles.keySet()) { Map<ConfigKey,RawValue> values = new EnumMap<>(ConfigKey.class); unpack(profiles.getBundle(name),values); profileDraftBaselines.put(RadioDisplayProfiles.Name.valueOf(name),values); }
        for (String name : saved.getStringArrayList("profileRestores") == null ? List.<String>of() : saved.getStringArrayList("profileRestores")) profileRestores.add(RadioDisplayProfiles.Name.valueOf(name));
        searchQuery = saved.getString("search",""); allSims = saved.getBoolean("allSims"); unpack(saved.getBundle("draft"),draft); unpack(saved.getBundle("portalDraft"),portalDraft); draftSub = saved.getInt("draftSub",-1); restoredIdentity = saved.getString("identity","unverified"); exportText = saved.getString("export"); if (draft.isEmpty()) restoredIdentity = null;
    }
    private Bundle pack(Map<ConfigKey,RawValue> values) {
        Bundle packed = new Bundle(); for (var item : values.entrySet()) { Bundle value = new Bundle(); value.putString("kind",item.getValue().kind().name()); value.putString("value",item.getValue().payload()); packed.putBundle(item.getKey().name(),value); } return packed;
    }
    private void unpack(Bundle packed, Map<ConfigKey,RawValue> target) {
        if (packed == null) return;
        for (String name : packed.keySet()) { ConfigKey key = ConfigKey.valueOf(name); Bundle value = packed.getBundle(name); if (value != null) target.put(key,new RawValue(RawValue.Kind.valueOf(value.getString("kind")),value.getString("value",""))); }
    }
    void attach(LinearLayout body, int selected) { this.panelRoot = body; this.body = body; this.sub = selected; }
    private String str(int id) { return activity.getString(id); }
    private void text(String value) { if (value != null && !value.isBlank()) MaterialUi.add(body,MaterialUi.text(activity,value,false)); }
    private void section(int label) { body = MaterialUi.card(panelRoot,str(label)); }
    private void button(int label, Runnable action, boolean enabled) {
        boolean primary = label == R.string.apply || label == R.string.backend_start || label == R.string.portal_apply;
        var v = MaterialUi.button(activity,label,() -> safe(action),primary); v.setEnabled(enabled); MaterialUi.add(body,v);
    }
    private void safe(Runnable action) {
        try { action.run(); }
        catch (Exception error) {
            localLog = "Operation refused: " + ActionFailure.describe(error);
            android.util.Log.w("UltimateIMS",localLog);
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(R.string.action_failed).setMessage(R.string.operation_failed_unknown)
                    .setNeutralButton(R.string.technical_details,(d,n) -> technicalDetails(localLog)).setPositiveButton(R.string.confirm,null).show();
            redraw.run();
        }
    }
    private boolean writable() { return workspace.backend.ready() && !workspace.busy(); }
    String label(ConfigKey key) { return str(switch(key) {
        case VOLTE -> R.string.key_volte;
        case VOWIFI -> R.string.key_vowifi;
        case WIFI_ONLY -> R.string.key_wifi_only;
        case WFC_MODE -> R.string.key_wfc_mode;
        case WFC_ROAMING_MODE -> R.string.key_wfc_roaming_mode;
        case WFC_ROAMING -> R.string.key_wfc_roaming;
        case VT -> R.string.key_vt;
        case VONR -> R.string.key_vonr;
        case VONR_VISIBLE -> R.string.key_vonr_visible;
        case WFC_ICON -> R.string.key_wfc_icon;
        case CROSS_SIM_OPPORTUNISTIC -> R.string.key_cross_sim_opportunistic;
        case ENHANCED_LTE_DEFAULT -> R.string.key_enhanced_lte_default;
        case CROSS_SIM -> R.string.key_cross_sim;
        case UT -> R.string.key_ut;
        case NR_AVAILABILITY -> R.string.key_nr_availability;
        case NR_SIGNAL_METRICS -> R.string.key_nr_signal_metrics;
        case NR_NSA_LTE_SIGNAL -> R.string.key_nr_nsa_lte_signal;
        case NR_RSRP -> R.string.key_nr_rsrp;
        case NR_RSRQ -> R.string.key_nr_rsrq;
        case NR_SINR -> R.string.key_nr_sinr;
        case NR_ICON -> R.string.key_nr_icon;
        case NR_ADVANCED_BANDS -> R.string.key_nr_advanced_bands;
        case NR_ADVANCED_PCO -> R.string.key_nr_advanced_pco;
        case NR_ADVANCED_INCLUDE_LTE -> R.string.key_nr_advanced_include_lte;
        case NR_ADVANCED_BANDWIDTH -> R.string.key_nr_advanced_bandwidth;
        case LTE_PLUS_BANDWIDTH -> R.string.key_lte_plus_bandwidth;
        case LTE -> R.string.key_lte;
        case HIDE_LTE_PLUS -> R.string.key_hide_lte_plus;
        case SHOW_4G -> R.string.key_show_4g;
        case SHOW_4G_LTE -> R.string.key_show_4g_lte;
        case EDITABLE_LTE -> R.string.key_editable_lte;
        case HIDE_LTE_SETTING -> R.string.key_hide_lte_setting;
        case EDITABLE_WFC -> R.string.key_editable_wfc;
        case EDITABLE_WFC_ROAMING -> R.string.key_editable_wfc_roaming;
        case CARRIER_NAME -> R.string.key_carrier_name;
        case CARRIER_NAME_OVERRIDE -> R.string.key_carrier_name_override;
        case IMS_USER_AGENT -> R.string.key_ims_user_agent;
        case SIM_COUNTRY -> R.string.key_sim_country;
        case VOIMS_OPT_IN -> R.string.key_voims_opt_in;
        case ADVANCED_CALLING_USER -> R.string.key_advanced_calling_user;
        case WFC_USER -> R.string.key_wfc_user;
        case WFC_USER_MODE -> R.string.key_wfc_user_mode;
        case WFC_USER_ROAMING_MODE -> R.string.key_wfc_user_roaming_mode;
        case WFC_USER_ROAMING -> R.string.key_wfc_user_roaming;
        case VT_USER -> R.string.key_vt_user;
        case CROSS_SIM_USER -> R.string.key_cross_sim_user;
        case VONR_USER -> R.string.key_vonr_user;
        case PORTAL_HTTP -> R.string.key_portal_http;
        case PORTAL_HTTPS -> R.string.key_portal_https;
    }); }
    private String value(RawValue value) { return value.kind() == RawValue.Kind.UNKNOWN ? str(R.string.unknown) : value.kind() == RawValue.Kind.UNSET ? str(R.string.unset) : value.kind() == RawValue.Kind.BOOLEAN ? str(value.payload().equals("true") ? R.string.true_value : R.string.false_value) : value.kind() == RawValue.Kind.INTEGER_ARRAY && value.payload().isEmpty() ? str(R.string.array_empty) : value.payload(); }
    private void technicalDetails(String raw) {
        TextView text = MaterialUi.text(activity,raw == null ? "" : raw,false);
        text.setTypeface(android.graphics.Typeface.MONOSPACE); text.setTextIsSelectable(true);
        LinearLayout form = MaterialUi.form(activity); MaterialUi.add(form,text);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(R.string.technical_details)
                .setView(MaterialUi.scrollForm(form)).setPositiveButton(R.string.confirm,null).show();
    }
    private void operationDetails() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(R.string.read_status).setMessage(UiStatus.operation(activity,workspace))
                .setNeutralButton(R.string.technical_details,(d,n) -> technicalDetails(workspace.failureReason() == null ? workspace.status() : workspace.failureReason()))
                .setPositiveButton(R.string.confirm,null).show();
    }
    void close() { io.shutdown(); }
    void status() {
        LinearLayout previous = body; section(R.string.live_status);
        text(str(workspace.backend.ready() ? R.string.connection_ready : R.string.connection_not_ready));
        if (!workspace.backend.ready() && !activity.configurationVisible()) button(R.string.connect_settings,activity::openConfigurationPage,true);
        MaterialUi.add(body,MaterialUi.row(activity,str(R.string.connection_status),UiStatus.backend(activity,workspace.backend),() -> technicalDetails(workspace.backend.state())));
        if (workspace.snapshot() != null) text(str(R.string.observed_at)+" " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.MEDIUM,activity.getResources().getConfiguration().getLocales().get(0)).format(new java.util.Date(workspace.snapshot().getLong("observedAt"))));
        MaterialUi.add(body,MaterialUi.row(activity,str(R.string.status_local),UiStatus.operation(activity,workspace),() -> technicalDetails(workspace.status())));
        for (String result : workspace.results()) MaterialUi.add(body,MaterialUi.row(activity,UiStatus.result(activity,result),str(R.string.technical_details),() -> technicalDetails(result)));
        if (workspace.busy()) {
            var progress = new com.google.android.material.progressindicator.LinearProgressIndicator(activity); progress.setIndeterminate(true); MaterialUi.add(body,progress); text(str(R.string.busy));
        }
        body = previous;
    }
    void ims() {
        section(R.string.draft_section);
        button(R.string.presets, () -> {
            String[] names = {str(R.string.preset_recommended),str(R.string.preset_china_5g),str(R.string.preset_china_lte),str(R.string.preset_calling_available)};
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(R.string.presets).setItems(names,(d,n) -> safe(() -> {
                ensureDraftIdentity();
                Map<ConfigKey,RawValue> changes = new EnumMap<>(Presets.build(Presets.Name.values()[n],Build.VERSION.SDK_INT));
                if (Presets.Name.values()[n] == Presets.Name.CHINA_5G) changes.putAll(RadioDisplayProfiles.target(RadioDisplayProfiles.Name.NR_ADVANCED,profileValues(RadioDisplayProfiles.Name.NR_ADVANCED,false)));
                changes.forEach((key,value) -> key.validate(value,Build.VERSION.SDK_INT));
                bindDraftIdentity(); draft.clear(); clearProfileDrafts(); draft.putAll(changes); if (Presets.Name.values()[n] == Presets.Name.CHINA_5G) profileDraftBaselines.put(RadioDisplayProfiles.Name.NR_ADVANCED,Map.of()); redraw.run();
            })).show();
        }, !workspace.busy());
        body = panelRoot;
        TextInputEditText search = new TextInputEditText(activity); search.setSingleLine(true); search.setTag("config_search"); search.setText(searchQuery);
        MaterialUi.input(body,search,str(R.string.search_settings));
        LinearLayout groups = MaterialUi.column(activity); MaterialUi.add(body,groups);
        renderGroups(groups);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value,int start,int count,int after) { }
            @Override public void onTextChanged(CharSequence value,int start,int before,int count) { searchQuery = value.toString(); renderGroups(groups); }
            @Override public void afterTextChanged(android.text.Editable value) { }
        });
        draftActions();
        button(R.string.export_draft, () -> exportDraft(draft), !draft.isEmpty());
        button(R.string.import_draft, () -> activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE),301), !workspace.busy());
    }
    private void draftActions() {
        section(R.string.review_section); text(activity.getString(R.string.draft_count,draft.size()));
        MaterialUi.switchRow(body,str(R.string.all_sims),allSims,!workspace.busy(),checked -> allSims = checked);
        button(R.string.apply, () -> {
            Bundle selected = workspace.selected(sub);
            if (selected == null) throw new SecurityException("Verified SIM identity unavailable");
            String selectedDigest = draftDigest(selected.getString("iccid"));
            boolean needsBinding = (restoredIdentity != null && !restoredIdentity.equals(selectedDigest)) || draftSub != sub || (draftIccid != null && !draftIccid.equals(selected.getString("iccid")));
            if (needsBinding) {
                // 旧草稿或换卡必须明确审阅目标；本次确认只绑定草稿，绝不直接写入。
                String target = activity.getString(R.string.sim,selected.getInt("slot")+1,selected.getString("carrier"),str(selected.getBoolean("embedded") ? R.string.esim : R.string.physical));
                confirm(target + "\n\n" + str(R.string.draft_review_confirm),() -> { bindDraftIdentity(); redraw.run(); });
                return;
            }
            if (restoredIdentity != null) bindDraftIdentity();
            List<Integer> targets = allSims ? workspace.sims().stream().map(s -> s.getInt("sub")).collect(java.util.stream.Collectors.toList()) : List.of(sub);
            Map<Integer,Map<ConfigKey,RawValue>> plans = new LinkedHashMap<>();
            Map<Integer,String> identities = new LinkedHashMap<>();
            StringBuilder review = new StringBuilder(activity.getString(R.string.apply_confirm,draft.size(),targets.size()));
            for (int target : targets) {
                Bundle targetSim = workspace.selected(target);
                if (targetSim == null) throw new SecurityException("Verified SIM identity unavailable");
                String iccid = targetSim.getString("iccid"); draftDigest(iccid); identities.put(target,iccid);
                Map<ConfigKey,RawValue> changes = new EnumMap<>(ConfigKey.class); changes.putAll(draft);
                // 关闭预设时逐卡读取各自原值，不把 SIM1 的原值复制到其他 SIM。
                for (RadioDisplayProfiles.Name profile : profileRestores) changes.putAll(savedProfileOriginals(target,profile));
                // 显式全卡操作也保留每张目标卡已有频段，避免复制所选卡的显示限制。
                if (profileDraftBaselines.containsKey(RadioDisplayProfiles.Name.NR_ADVANCED) && !profileRestores.contains(RadioDisplayProfiles.Name.NR_ADVANCED) && changes.containsKey(ConfigKey.NR_ADVANCED_BANDS)) {
                    Map<ConfigKey,RawValue> targetValues = new EnumMap<>(ConfigKey.class);
                    targetValues.put(ConfigKey.NR_ADVANCED_BANDS,workspace.currentValue(target,ConfigKey.NR_ADVANCED_BANDS));
                    changes.put(ConfigKey.NR_ADVANCED_BANDS,RadioDisplayProfiles.target(RadioDisplayProfiles.Name.NR_ADVANCED,targetValues).get(ConfigKey.NR_ADVANCED_BANDS));
                }
                plans.put(target,Map.copyOf(changes));
                review.append("\n\n").append(activity.getString(R.string.sim,targetSim.getInt("slot")+1,targetSim.getString("carrier"),str(targetSim.getBoolean("embedded") ? R.string.esim : R.string.physical)));
                for (var change : changes.entrySet()) review.append("\n").append(label(change.getKey())).append(": ").append(value(workspace.currentValue(target,change.getKey()))).append(" → ").append(value(change.getValue()));
            }
            confirm(review.toString(), () -> {
                if (draft.containsKey(ConfigKey.SIM_COUNTRY)) confirm(str(R.string.country_confirm), () -> workspace.applyPerSim(plans,identities,true));
                else workspace.applyPerSim(plans,identities,false);
            });
        }, writable() && !draft.isEmpty());
        if (!workspace.backend.ready()) text(str(R.string.backend_needed));
        if (restoredIdentity != null || (!draft.isEmpty() && draftSub != sub)) button(R.string.review_draft,() -> confirm(str(R.string.draft_review_confirm), () -> { bindDraftIdentity(); redraw.run(); }),writable() && sub >= 0);
        if (!draft.isEmpty()) button(R.string.clear_draft, () -> { draft.clear(); clearProfileDrafts(); draftIccid = null; redraw.run(); }, !workspace.busy());
    }
    private boolean radioKey(ConfigKey key) { return EnumSet.of(ConfigKey.VOLTE,ConfigKey.VOWIFI,ConfigKey.VONR,ConfigKey.VT,ConfigKey.CROSS_SIM,ConfigKey.UT,ConfigKey.LTE,ConfigKey.NR_AVAILABILITY).contains(key); }
    void radio() {
        section(R.string.registration_section);
        Bundle observed = workspace.selected(sub);
        if (observed == null) {
            text(str(workspace.backend.ready() ? R.string.live_read_unavailable : R.string.connect_to_read));
        } else {
            String registered = !observed.containsKey("imsRegistered") ? str(R.string.unknown) : str(observed.getBoolean("imsRegistered") ? R.string.registered : R.string.not_registered);
            MaterialUi.add(body,MaterialUi.row(activity,str(R.string.diag_ims_registered),registered,null));
            String transport = !observed.containsKey("imsTransport") ? str(R.string.unknown) : str(observed.getInt("imsTransport") == 2 ? R.string.wifi : observed.getInt("imsTransport") == 1 ? R.string.cellular : R.string.unknown);
            MaterialUi.add(body,MaterialUi.row(activity,str(R.string.diag_ims_transport),transport,null));
            var radio = RadioObservation.classify(observed.containsKey("psRegistered") ? observed.getBoolean("psRegistered") : null,observed.containsKey("psRat") ? observed.getInt("psRat") : null,observed.containsKey("nrConnected") ? observed.getBoolean("nrConnected") : null);
            String network = radio == RadioObservation.State.NR_SA ? "5G SA" : radio == RadioObservation.State.NR_NSA ? "5G NSA" : observed.containsKey("psRat") && observed.getInt("psRat") == 13 ? "LTE" : str(R.string.unknown);
            MaterialUi.add(body,MaterialUi.row(activity,str(R.string.network_status),network,null));
            if (observed.containsKey("identityError")) MaterialUi.add(body,MaterialUi.row(activity,str(R.string.sim_access),str(R.string.access_blocked),() -> technicalDetails(observed.getString("identityError"))));
        }
        persistentVolte();
        section(R.string.radio_controls);
        for (ConfigKey key : ConfigKey.values()) if (radioKey(key) && Build.VERSION.SDK_INT >= key.minApi) {
            if (key == ConfigKey.NR_AVAILABILITY) { nrSwitch(1,R.string.nr_nsa_toggle); nrSwitch(2,R.string.nr_sa_toggle); }
            else toggleRow(body,key);
        }
        section(R.string.radio_display_section);
        profileRow(RadioDisplayProfiles.Name.NR_ADVANCED,R.string.profile_5g_plus,R.string.profile_5g_plus_help);
        profileRow(RadioDisplayProfiles.Name.MAINLAND_BARS,R.string.profile_china_bars,R.string.profile_china_bars_help);
        if (!draft.isEmpty()) draftActions();
    }
    private void ensureDraftIdentity() {
        if (!draft.isEmpty() && draftSub != sub) throw new SecurityException("Review the draft for this SIM before editing");
        if (DraftBinding.requiresRestoredIdentity(!draft.isEmpty(),restoredIdentity)) {
            Bundle selected = workspace.selected(sub);
            DraftBinding.verifyRestored(restoredIdentity,selected == null ? null : draftDigest(selected.getString("iccid")));
        }
        Bundle liveSim = workspace.selected(sub);
        if (liveSim == null) throw new SecurityException("Verified SIM identity unavailable");
        if (liveSim.containsKey("identityError")) throw new IllegalStateException(liveSim.getString("identityError"));
        draftDigest(liveSim.getString("iccid"));
    }
    private void saveToggle(ConfigKey key, RawValue target) { saveChanges(Map.of(key,target)); }
    private void saveChanges(Map<ConfigKey,RawValue> changes) {
        ensureDraftIdentity();
        // 整个复合草稿先验证，再一次性修改；不得在第二项失败时留下一半预设。
        changes.forEach((key,target) -> key.validate(target,Build.VERSION.SDK_INT));
        bindDraftIdentity();
        profileRestores.removeIf(profile -> changes.keySet().stream().anyMatch(RadioDisplayProfiles.target(profile)::containsKey));
        changes.forEach((key,target) -> { if (target.equals(workspace.currentValue(sub,key))) draft.remove(key); else draft.put(key,target); });
        redraw.run();
    }
    private void clearProfileDrafts() { profileDraftBaselines.clear(); profileRestores.clear(); }
    private Map<ConfigKey,RawValue> profileValues(RadioDisplayProfiles.Name profile, boolean pending) {
        Map<ConfigKey,RawValue> values = new EnumMap<>(ConfigKey.class);
        for (ConfigKey key : RadioDisplayProfiles.target(profile).keySet()) values.put(key,pending ? draft.getOrDefault(key,workspace.currentValue(sub,key)) : workspace.currentValue(sub,key));
        return values;
    }
    private Map<ConfigKey,RawValue> savedProfileOriginals(int subscription, RadioDisplayProfiles.Name profile) {
        try { return workspace.profileOriginals(subscription,profile); }
        catch (Exception error) { throw new IllegalStateException(ActionFailure.describe(error)); }
    }
    private void toggleProfile(RadioDisplayProfiles.Name profile, boolean enabled) throws Exception {
        ensureDraftIdentity();
        if (enabled) {
            Map<ConfigKey,RawValue> previous = RadioDisplayProfiles.overlayBeforeEnable(profile,draft);
            saveChanges(RadioDisplayProfiles.target(profile,profileValues(profile,true)));
            profileDraftBaselines.putIfAbsent(profile,previous); profileRestores.remove(profile);
        } else if (RadioDisplayProfiles.state(profile,profileValues(profile,false)) != RadioDisplayProfiles.State.ON) {
            Map<ConfigKey,RawValue> previous = profileDraftBaselines.getOrDefault(profile,Map.of());
            Map<ConfigKey,RawValue> next = RadioDisplayProfiles.cancelStaged(profile,draft,previous);
            draft.clear(); draft.putAll(next); profileDraftBaselines.remove(profile); profileRestores.remove(profile);
            bindDraftIdentity(); redraw.run();
        } else {
            Map<ConfigKey,RawValue> original = savedProfileOriginals(sub,profile);
            saveChanges(original); profileRestores.add(profile); profileDraftBaselines.remove(profile);
        }
    }
    private void profileRow(RadioDisplayProfiles.Name profile, int title, int help) {
        var current = RadioDisplayProfiles.state(profile,profileValues(profile,false));
        var shown = RadioDisplayProfiles.state(profile,profileValues(profile,true));
        String name = str(title) + (RadioDisplayProfiles.target(profile).keySet().stream().anyMatch(draft::containsKey) ? " •" : "");
        if (current == RadioDisplayProfiles.State.UNKNOWN) name += " · " + str(R.string.unknown);
        var toggle = MaterialUi.switchRow(body,name,shown == RadioDisplayProfiles.State.ON,writable() && current != RadioDisplayProfiles.State.UNKNOWN,enabled -> safe(() -> {
            try { toggleProfile(profile,enabled); } catch (RuntimeException error) { throw error; } catch (Exception error) { throw new IllegalStateException(ActionFailure.describe(error)); }
        }));
        toggle.setStateDescription(current == RadioDisplayProfiles.State.UNKNOWN ? str(R.string.unknown) : str(shown == RadioDisplayProfiles.State.ON ? R.string.true_value : R.string.false_value));
        toggle.setTooltipText(str(help));
        toggle.setOnLongClickListener(view -> { new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(title).setMessage(help).setPositiveButton(R.string.confirm,null).show(); return true; });
        MaterialUi.divider(body);
    }
    private void toggleRow(LinearLayout parent, ConfigKey key) {
        RawValue current = workspace.currentValue(sub,key), shown = draft.getOrDefault(key,current);
        ConfigurationToggle.State state = ConfigurationToggle.state(key,shown);
        String title = label(key) + (draft.containsKey(key) ? " •" : "");
        if (state == ConfigurationToggle.State.UNKNOWN) title += " · " + str(current.kind() == RawValue.Kind.INTEGER && current.payload().equals("-1") ? R.string.preference_default : R.string.unknown);
        var toggle = MaterialUi.switchRow(parent,title,state == ConfigurationToggle.State.ON,!workspace.busy() && (state != ConfigurationToggle.State.UNKNOWN || (current.kind() == RawValue.Kind.INTEGER && current.payload().equals("-1"))),enabled -> safe(() -> saveChanges(CallingConfiguration.target(key,enabled))));
        toggle.setStateDescription(state == ConfigurationToggle.State.UNKNOWN ? str(R.string.unknown) : str(state == ConfigurationToggle.State.ON ? R.string.true_value : R.string.false_value));
        toggle.setTooltipText(activity.getString(R.string.live_value,value(current)));
        MaterialUi.divider(parent);
    }
    private void nrSwitch(int technology, int label) {
        RawValue current = workspace.currentValue(sub,ConfigKey.NR_AVAILABILITY), shown = draft.getOrDefault(ConfigKey.NR_AVAILABILITY,current);
        ConfigurationToggle.State state = ConfigurationToggle.nrState(shown,technology);
        String title = str(label) + (draft.containsKey(ConfigKey.NR_AVAILABILITY) ? " •" : "") + (state == ConfigurationToggle.State.UNKNOWN ? " · " + str(R.string.unknown) : "");
        var toggle = MaterialUi.switchRow(body,title,state == ConfigurationToggle.State.ON,!workspace.busy() && state != ConfigurationToggle.State.UNKNOWN,enabled -> safe(() -> saveToggle(ConfigKey.NR_AVAILABILITY,ConfigurationToggle.nrTarget(draft.getOrDefault(ConfigKey.NR_AVAILABILITY,current),technology,enabled))));
        toggle.setStateDescription(state == ConfigurationToggle.State.UNKNOWN ? str(R.string.unknown) : str(state == ConfigurationToggle.State.ON ? R.string.true_value : R.string.false_value));
        MaterialUi.divider(body);
    }
    private int group(ConfigKey key) {
        return switch (key) {
            case VOLTE, VONR, VONR_VISIBLE, VT, CROSS_SIM, CROSS_SIM_OPPORTUNISTIC, UT -> 0;
            case VOWIFI, WFC_ICON, WIFI_ONLY, WFC_MODE, WFC_ROAMING_MODE, WFC_ROAMING, EDITABLE_WFC, EDITABLE_WFC_ROAMING -> 1;
            case ADVANCED_CALLING_USER, WFC_USER, WFC_USER_MODE, WFC_USER_ROAMING_MODE, WFC_USER_ROAMING, VT_USER, CROSS_SIM_USER, VONR_USER -> 2;
            case NR_AVAILABILITY, NR_RSRP, NR_RSRQ, NR_SINR, NR_SIGNAL_METRICS, NR_NSA_LTE_SIGNAL -> 3;
            case ENHANCED_LTE_DEFAULT, NR_ICON, NR_ADVANCED_BANDWIDTH, NR_ADVANCED_BANDS, NR_ADVANCED_PCO, NR_ADVANCED_INCLUDE_LTE, LTE_PLUS_BANDWIDTH, LTE, HIDE_LTE_PLUS, SHOW_4G, SHOW_4G_LTE, EDITABLE_LTE, HIDE_LTE_SETTING -> 4;
            default -> 5;
        };
    }
    private void renderGroups(LinearLayout groups) {
        groups.removeAllViews();
        int[] titles = {R.string.group_calling,R.string.group_wifi,R.string.group_preferences,R.string.group_5g,R.string.group_display,R.string.group_advanced};
        String query = searchQuery.trim().toLowerCase(java.util.Locale.ROOT); boolean any = false;
        for (int index=0;index<titles.length;index++) {
            final int category = index;
            List<ConfigKey> keys = Arrays.stream(ConfigKey.values()).filter(k -> k.scope != ConfigKey.Scope.DEVICE && Build.VERSION.SDK_INT >= k.minApi && !radioKey(k) && group(k) == category && (query.isEmpty() || label(k).toLowerCase(java.util.Locale.ROOT).contains(query))).collect(java.util.stream.Collectors.toList());
            if (keys.isEmpty()) continue; any = true;
            LinearLayout card = MaterialUi.card(groups,null);
            MaterialUi.add(card,MaterialUi.text(activity,str(titles[index]),true));
            for (ConfigKey key : keys) {
                if (ConfigurationToggle.supported(key)) { toggleRow(card,key); continue; }
                String live = activity.getString(R.string.live_value,value(workspace.currentValue(sub,key)));
                String change = draft.containsKey(key) ? "\n"+activity.getString(R.string.draft_value,value(draft.get(key))) : "";
                View row = MaterialUi.row(activity,label(key),live+change,() -> safe(() -> edit(key,draft,workspace.currentValue(sub,key))));
                row.setEnabled(!workspace.busy()); MaterialUi.add(card,row); MaterialUi.divider(card);
            }
        }
        if (!any) MaterialUi.note(groups,str(R.string.no_settings_match));
    }
    private String draftDigest(String iccid) { try { return workspace.identityDigest(iccid); } catch (Exception e) { throw new SecurityException("Draft identity unavailable"); } }
    private void bindDraftIdentity() { restoredIdentity = null; draftSub = sub; Bundle selected = workspace.selected(sub); draftIccid = selected == null ? null : selected.getString("iccid"); }
    private void edit(ConfigKey key, Map<ConfigKey,RawValue> changes, RawValue current) {
        LinearLayout input = MaterialUi.form(activity);
        String[] options = key.kind == RawValue.Kind.BOOLEAN ? new String[]{str(R.string.keep),str(R.string.true_value),str(R.string.false_value)} : new String[]{str(R.string.keep),str(R.string.edit),str(R.string.unset)};
        int initial = changes.containsKey(key) ? key.kind == RawValue.Kind.BOOLEAN ? (changes.get(key).payload().equals("true") ? 1 : 2) : changes.get(key).kind() == RawValue.Kind.UNSET ? 2 : 1 : 0;
        MaterialUi.Choice choice = new MaterialUi.Choice(activity,str(R.string.change_action),Arrays.asList(options),initial,null); MaterialUi.add(input,choice);
        TextInputEditText field = new TextInputEditText(activity); field.setText(changes.containsKey(key) ? changes.get(key).payload() : current.readable() ? current.payload() : "");
        field.setSaveEnabled(false); field.setInputType(key.kind == RawValue.Kind.INTEGER ? InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED : InputType.TYPE_CLASS_TEXT);
        TextInputLayout fieldLayout = null;
        if (key.kind != RawValue.Kind.BOOLEAN) { fieldLayout = MaterialUi.input(input,field,str(R.string.new_value)); fieldLayout.setHelperText(str(key.kind == RawValue.Kind.INTEGER_ARRAY ? R.string.array_hint : R.string.value_hint)); }
        final TextInputLayout validationField = fieldLayout;
        var dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(label(key)).setView(MaterialUi.scrollForm(input)).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.save,null).create();
        dialog.setOnShowListener(v -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            try {
                int selected = choice.selected();
                if (selected == 0) changes.remove(key);
                else {
                    RawValue target;
                    if (key.kind == RawValue.Kind.BOOLEAN) target = RawValue.bool(selected == 1);
                    else if (selected == 2) target = RawValue.unset();
                    else target = key.kind == RawValue.Kind.INTEGER ? RawValue.integer(Integer.parseInt(field.getText().toString().trim())) : new RawValue(key.kind,field.getText().toString().trim());
                    key.validate(target,Build.VERSION.SDK_INT); if (changes == draft) bindDraftIdentity(); changes.put(key,target);
                }
                dialog.dismiss(); redraw.run();
            } catch (Exception error) {
                if (validationField != null) { validationField.setError(str(R.string.parse_error)); field.requestFocus(); }
                else Toast.makeText(activity,R.string.parse_error,Toast.LENGTH_LONG).show();
            }
        })); dialog.show();
    }
    private void confirm(String message, Runnable action) { new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setMessage(message).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.confirm,(d,n) -> safe(action)).show(); }
    void settings(BackendSession.Mode chosen) {
        section(R.string.backend_section);
        MaterialUi.switchRow(body,str(R.string.connection_switch),workspace.backend.bound(),chosen != null && !workspace.busy() && !workspace.backend.inFlight(),enabled -> safe(() -> { if (enabled) workspace.backend.start(chosen); else workspace.backend.stop(); }));
        MaterialUi.add(body,MaterialUi.row(activity,str(R.string.connection_status),UiStatus.backend(activity,workspace.backend),() -> technicalDetails(workspace.backend.state())));
        MaterialUi.add(body,MaterialUi.row(activity,str(R.string.read_status),str(workspace.snapshot() == null ? R.string.read_unavailable : R.string.read_ready),this::operationDetails));
        if (chosen == BackendSession.Mode.EMBEDDED_ADB) { section(R.string.wireless_section); text(str(R.string.pair_help));
        button(R.string.pair, () -> wireless(true), workspace.backend.mode() == BackendSession.Mode.EMBEDDED_ADB && !workspace.busy());
        button(R.string.connect_adb, () -> wireless(false), workspace.backend.mode() == BackendSession.Mode.EMBEDDED_ADB && !workspace.busy());
        }
        section(R.string.automation_section);
        MaterialUi.switchRow(body,str(R.string.auto_reapply),workspace.autoRestoreEnabled(),!workspace.busy(),enabled -> workspace.setAutoRestore(enabled)).setTooltipText(str(R.string.auto_explain));

    }
    void about() {
        section(R.string.about_app);
        text(activity.getString(R.string.app_version,BuildConfig.VERSION_NAME));
        MaterialUi.add(body,MaterialUi.row(activity,str(R.string.author_credit),"leodenglovescode",() -> activity.startActivity(
                new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/leodenglovescode")))));
        section(R.string.licenses);
        button(R.string.licenses, () -> {
            try { StringBuilder contents = new StringBuilder(); for (String name : activity.getAssets().list("licenses")) { contents.append(name).append("\n\n"); try (var input = activity.getAssets().open("licenses/"+name)) { contents.append(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).append("\n\n"); } }
                ScrollView scroll = new ScrollView(activity); TextView text = MaterialUi.text(activity,contents.toString(),false); text.setPadding(MaterialUi.dp(activity,24),MaterialUi.dp(activity,16),MaterialUi.dp(activity,24),MaterialUi.dp(activity,16)); scroll.addView(text); new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(R.string.licenses).setView(scroll).setPositiveButton(R.string.confirm,null).show();
            } catch (Exception e) { throw new IllegalStateException(e); }
        },true);
        button(R.string.project_links, () -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(R.string.project_links).setItems(new String[]{str(R.string.link_project),str(R.string.link_shizuku)},(d,n) -> activity.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(n==0?"https://github.com/leodenglovescode/UltimateIMS":"https://shizuku.rikka.app/")))).show(),true);
    }
    private void wireless(boolean pair) {
        if (Build.VERSION.SDK_INT >= 37 && activity.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setMessage(R.string.local_network_explain).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.confirm,(d,n) -> activity.requestPermissions(new String[]{"android.permission.ACCESS_LOCAL_NETWORK"},303)).show(); return;
        }
        LinearLayout form = MaterialUi.form(activity);
        TextInputEditText port = new TextInputEditText(activity); port.setInputType(InputType.TYPE_CLASS_NUMBER); port.setSaveEnabled(false); MaterialUi.input(form,port,str(pair ? R.string.pair_port : R.string.connection_port));
        TextInputEditText code = new TextInputEditText(activity); code.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD); code.setSaveEnabled(false);
        if (pair) MaterialUi.input(form,code,str(R.string.pair_code));
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(pair ? R.string.pair : R.string.connect_adb).setView(MaterialUi.scrollForm(form)).setNegativeButton(R.string.cancel,(d,n) -> code.setText(""))
                .setPositiveButton(R.string.confirm,(d,n) -> safe(() -> {
                    int number = Integer.parseInt(port.getText().toString()); byte[] secret = new byte[code.length()];
                    for (int i = 0; i < secret.length; i++) secret[i] = (byte) code.getText().charAt(i); code.setText("");
                    io.execute(() -> {
                        try {
                            WirelessAdb adb = new WirelessAdb();
                            if (pair) { adb.pair(number,secret); localLog = "Authenticated local pairing exchange completed; Android device behavior remains to be validated"; }
                            else { adb.startBackend(number,workspace.backend.launchCommand()); localLog = "Private backend launch requested; readiness requires Binder authentication"; }
                        } catch (Exception e) { localLog = "Wireless ADB failed: " + e.getClass().getSimpleName(); }
                        finally { Arrays.fill(secret,(byte)0); activity.runOnUiThread(redraw); }
                    });
                })).show();
    }
    private void persistentVolte() {
        section(R.string.persistent_section);
        Bundle sim = workspace.selected(sub);
        var state = PersistentVolteState.read(workspace.currentValue(sub,ConfigKey.VOIMS_OPT_IN),workspace.currentValue(sub,ConfigKey.ADVANCED_CALLING_USER));
        boolean saved = sim != null && sim.getBoolean("persistentOriginalsSaved");
        boolean recovery = sim == null || sim.getBoolean("recoveryRequired") || sim.getBoolean("recoveryStateUnknown");
        boolean enabled = PersistentVolteState.canToggle(state,workspace.backend.ready(),workspace.busy(),recovery,saved);
        var toggle = MaterialUi.switchRow(body,str(R.string.persistent_switch),state == PersistentVolteState.State.ON,enabled,on -> safe(() -> {
            // 对话框取消或操作尚未核验时，开关继续显示实时值。
            redraw.run();
            confirm(str(on ? R.string.persistent_confirm_enable : R.string.persistent_confirm_restore),() -> workspace.persistentVolte(sub,on));
        }));
        toggle.setStateDescription(str(state == PersistentVolteState.State.UNKNOWN ? R.string.unknown : state == PersistentVolteState.State.ON ? R.string.true_value : R.string.false_value));
        toggle.setTooltipText(str(R.string.persistent_help));
        MaterialUi.divider(body);
        int detail = state == PersistentVolteState.State.UNKNOWN ? R.string.connect_to_read : recovery ? R.string.persistent_recovery_needed : state == PersistentVolteState.State.ON && !saved ? R.string.persistent_no_originals : R.string.persistent_hint;
        MaterialUi.add(body,MaterialUi.row(activity,str(detail),str(R.string.learn_more),() -> {
            if (state != PersistentVolteState.State.UNKNOWN && recovery) { activity.openRecoveryPage(); return; }
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(R.string.persistent_section)
                    .setMessage(R.string.persistent_help).setPositiveButton(R.string.confirm,null).show();
        }));
    }
    void recovery() {
        section(R.string.restore_section);
        button(R.string.restore_originals,this::restoreDialog,writable() && sub >= 0);
        button(R.string.history,this::historyDialog,!workspace.busy());
        section(R.string.maintenance_section); text(str(R.string.maintenance_confirm));
        button(R.string.restart, () -> confirm(str(R.string.maintenance_confirm),() -> workspace.maintenance(sub,false)),writable() && sub >= 0);
        button(R.string.reset, () -> confirm(str(R.string.maintenance_confirm),() -> workspace.maintenance(sub,true)),writable() && sub >= 0);
        section(R.string.report_section);
        button(R.string.export, () -> export(diagnosticReport()),true);
        button(R.string.app_logs, () -> io.execute(() -> { appLogs = LocalLogs.read(); activity.runOnUiThread(redraw); }),true);
        if (!localLog.isBlank() || !appLogs.isBlank()) { section(R.string.raw_output); text(localLog); text(appLogs); }
    }
    private void restoreDialog() {
        try {
            var entry = workspace.backup(sub); if (entry == null) { text(str(R.string.no_backup)); return; }
            List<ConfigKey> fields = entry.originals().keySet().stream().sorted().collect(java.util.stream.Collectors.toList()); boolean[] chosen = new boolean[fields.size()];
            String[] names = fields.stream().map(this::label).toArray(String[]::new);
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(R.string.selective_restore).setMultiChoiceItems(names,chosen,(d,n,yes) -> chosen[n] = yes)
                    .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.confirm,(d,n) -> {
                        Set<ConfigKey> keys = EnumSet.noneOf(ConfigKey.class); for (int i=0;i<fields.size();i++) if (chosen[i]) keys.add(fields.get(i)); if (!keys.isEmpty()) workspace.restore(sub,keys);
                    }).show();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private void historyDialog() {
        try {
            JSONArray history = workspace.history(); if (history.length() == 0) { Toast.makeText(activity,R.string.no_history,Toast.LENGTH_SHORT).show(); return; }
            String[] rows = new String[history.length()];
            for (int i=0;i<rows.length;i++) rows[i] = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.MEDIUM,activity.getResources().getConfiguration().getLocales().get(0)).format(new java.util.Date(history.getJSONObject(i).getLong("time"))) + " · " + UiStatus.historyState(activity,history.getJSONObject(i).getString("state"));
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(R.string.history).setItems(rows,(d,n) -> safe(() -> {
                try { bindDraftIdentity(); draft.clear(); clearProfileDrafts(); draft.putAll(workspace.historyDraft(n)); activity.openImsPage(); }
                catch (Exception e) { throw new IllegalStateException(e); }
            })).show();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    void system() {
        section(R.string.portal_section); text(str(R.string.portal_explain));
        button(R.string.refresh,workspace::readPortal,workspace.backend.ready() && !workspace.busy());
        for (ConfigKey key : List.of(ConfigKey.PORTAL_HTTP,ConfigKey.PORTAL_HTTPS)) {
            View row = MaterialUi.row(activity,label(key),activity.getString(R.string.live_value,value(workspace.portal(key)))+(portalDraft.containsKey(key)?"\n"+activity.getString(R.string.draft_value,value(portalDraft.get(key))):""),() -> safe(() -> edit(key,portalDraft,workspace.portal(key)))); row.setEnabled(!workspace.busy()); MaterialUi.add(body,row);
        }
        button(R.string.portal_presets,() -> {
            String[] hosts = {"Google","V2EX","Xiaomi","vivo","Huawei"};
            String[] urls = {"connectivitycheck.gstatic.com","captive.v2ex.co","connect.rom.miui.com","wifi.vivo.com.cn","connectivitycheck.platform.hicloud.com"};
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(R.string.portal_presets).setItems(hosts,(d,n) -> {
                portalDraft.put(ConfigKey.PORTAL_HTTP,RawValue.string("http://"+urls[n]+"/generate_204"));
                portalDraft.put(ConfigKey.PORTAL_HTTPS,RawValue.string("https://"+(n==0?"www.google.com":urls[n])+"/generate_204")); redraw.run();
            }).show();
        },!workspace.busy());
        button(R.string.portal_apply,() -> confirm(str(R.string.portal_confirm),() -> workspace.apply(List.of(-2),portalDraft,false)),writable() && !portalDraft.isEmpty());
        button(R.string.portal_restore,() -> confirm(str(R.string.selective_restore),() -> workspace.restore(-2,null)),writable());
        text(str(R.string.portal_explain));
    }
    void diagnostics() {
        section(R.string.registration_section); Bundle observed = workspace.selected(sub);
        if (observed != null) {
            text(activity.getString(R.string.default_data,str(observed.getBoolean("defaultData") ? R.string.yes : R.string.no)));
            text(activity.getString(R.string.sim_country,observed.getString("country",str(R.string.unknown))));
            text(activity.getString(R.string.rat,observed.containsKey("dataRat") ? Integer.toString(observed.getInt("dataRat")) : str(R.string.unknown)));
            for (String key : List.of("imsRegistered","imsTransport","imsRegistrationTech","voiceOverNrCapable","voiceOverNrAvailable","voice","video","ut","nrState","nrConnected","psRegistered","psRat","displayBaseRat","displayOverride","dataRat","voiceRat","callState","nrRsrpDbm","nrFrameworkLevel","lteRsrpDbm")) MaterialUi.add(body,MaterialUi.row(activity,diagnosticLabel(key),observed.containsKey(key) && observed.get(key) instanceof Boolean flag ? str(flag ? R.string.yes : R.string.no) : String.valueOf(observed.containsKey(key) ? observed.get(key) : str(R.string.unknown)),null));
            Long user = observed.containsKey("mask_USER") ? observed.getLong("mask_USER") : null;
            Long power = observed.containsKey("mask_POWER") ? observed.getLong("mask_POWER") : null;
            Long carrier = observed.containsKey("mask_CARRIER") ? observed.getLong("mask_CARRIER") : null;
            var radio = RadioObservation.classify(observed.containsKey("psRegistered") ? observed.getBoolean("psRegistered") : null,observed.containsKey("psRat") ? observed.getInt("psRat") : null,observed.containsKey("nrConnected") ? observed.getBoolean("nrConnected") : null);
            text(str(radio == RadioObservation.State.NR_SA ? R.string.nr_sa_observed : radio == RadioObservation.State.NR_NSA ? R.string.nr_nsa_observed : R.string.nr_registration_unknown));
            NetworkAssessment.Finding finding = NetworkAssessment.assess(user,power,carrier);
            text(str(finding == NetworkAssessment.Finding.POWER_EXCLUDES_NR ? R.string.power_nr : finding == NetworkAssessment.Finding.ALL_READ_MASKS_ALLOW_NR ? R.string.masks_allow : R.string.masks_unknown));
        }
        Bundle snapshot = workspace.snapshot();
        if (snapshot != null) { var inactive = snapshot.getParcelableArrayList("inactive",Bundle.class); if (inactive != null) for (Bundle entry : inactive) text(str(R.string.inactive_sim)+" " + entry.getString("carrier","") + " · " + (entry.getBoolean("embedded")?str(R.string.esim):str(R.string.physical))); }
        text(str(R.string.observation_note));
        section(R.string.probe_section); text(str(R.string.probe_consent));
        button(R.string.run_http,this::probeDialog,true);
        button(R.string.export,() -> export(diagnosticReport()),true);
        section(R.string.apn_section);
        button(R.string.apn,() -> activity.startActivity(new Intent(Settings.ACTION_APN_SETTINGS).putExtra("android.provider.extra.SUB_ID",sub)),sub >= 0);
        text(str(R.string.apn_explain)); MaterialUi.add(body,MaterialUi.row(activity,str(R.string.apn_read),str(R.string.technical_details),() -> technicalDetails(workspace.apnStatus())));
        button(R.string.apn_read,() -> workspace.apn(sub,null,false),writable() && sub >= 0);
        button(R.string.apn_profile,() -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(R.string.apn_profile).setItems(new String[]{str(R.string.apn_cmnet_name),str(R.string.apn_tmobile_name)},(d,n) -> confirm(str(R.string.apn_confirm),() -> workspace.apn(sub,n==0?"CMNET":"TMOBILE",false))).show(),writable() && sub >= 0);
        button(R.string.apn_restore,() -> confirm(str(R.string.apn_confirm),() -> workspace.apn(sub,null,true)),writable() && sub >= 0);
        section(R.string.ip_section); text(str(R.string.ip_consent));
        button(R.string.public_ip,this::publicIpDialog,true); if (!publicIpLocal.isBlank() || !localLog.isBlank()) { section(R.string.raw_output); text(publicIpLocal); text(localLog); }
    }
    private void publicIpDialog() {
        LinearLayout form = MaterialUi.form(activity); TextInputEditText endpoint = new TextInputEditText(activity); endpoint.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI); endpoint.setSaveEnabled(false); MaterialUi.input(form,endpoint,str(R.string.endpoint_label)).setPlaceholderText("https://api.ipify.org");
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setMessage(R.string.ip_consent).setView(MaterialUi.scrollForm(form)).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.confirm,(d,n) -> {
            String target = endpoint.getText().toString(); io.execute(() -> { publicIpLocal = ConnectivityProbe.publicIp(activity,target); activity.runOnUiThread(redraw); });
        }).show();
    }
    private void probeDialog() {
        LinearLayout form = MaterialUi.form(activity); TextInputEditText endpoint = new TextInputEditText(activity); endpoint.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI); MaterialUi.input(form,endpoint,str(R.string.endpoint_label)).setPlaceholderText("https://www.google.com/generate_204");
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setMessage(R.string.probe_consent).setView(MaterialUi.scrollForm(form)).setNegativeButton(R.string.cancel,null)
                .setPositiveButton(R.string.confirm,(d,n) -> { String url = endpoint.getText().toString(); io.execute(() -> {
                    localLog = ConnectivityProbe.run(activity,url); activity.runOnUiThread(redraw);
                }); }).show();
    }
    private String diagnosticLabel(String key) { return str(switch (key) {
        case "imsRegistered" -> R.string.diag_ims_registered;
        case "imsTransport" -> R.string.diag_ims_transport;
        case "imsRegistrationTech" -> R.string.diag_ims_tech;
        case "voiceOverNrCapable" -> R.string.diag_nr_capable;
        case "voiceOverNrAvailable" -> R.string.diag_nr_available;
        case "voice" -> R.string.diag_voice;
        case "video" -> R.string.diag_video;
        case "ut" -> R.string.diag_ut;
        case "nrRsrpDbm" -> R.string.diag_nr_rsrp;
        case "nrFrameworkLevel" -> R.string.diag_nr_level;
        case "lteRsrpDbm" -> R.string.diag_lte_rsrp;
        case "nrState" -> R.string.diag_nr_state;
        case "nrConnected" -> R.string.diag_nr_connected;
        case "psRegistered" -> R.string.diag_ps_registered;
        case "psRat" -> R.string.diag_ps_rat;
        case "displayBaseRat" -> R.string.diag_display_rat;
        case "displayOverride" -> R.string.diag_display_override;
        case "dataRat" -> R.string.diag_data_rat;
        case "voiceRat" -> R.string.diag_voice_rat;
        default -> R.string.diag_call_state;
    }); }
    String diagnosticReport() {
        StringBuilder report = new StringBuilder("UltimateIMS local diagnostics\nDevice: ").append(Build.MODEL).append("\nAndroid API: ").append(Build.VERSION.SDK_INT).append("\n");
        int index = 0; for (Bundle sim : workspace.sims()) {
            report.append("SIM ").append(++index).append("\n");
            for (String key : List.of("imsRegistered","imsTransport","imsRegistrationTech","voiceOverNrCapable","voiceOverNrAvailable","voice","video","ut","nrState","nrConnected","psRegistered","psRat","displayBaseRat","displayOverride","dataRat","voiceRat","callState","mask_USER","mask_POWER","mask_CARRIER")) report.append(key).append(": ").append(sim.containsKey(key)?sim.get(key):"unknown").append('\n');
        }
        return report.append(localLog).append("\nNo subscriber identifiers, pairing secrets, IP addresses, cell IDs, or location details exported.\n").toString();
    }
    private void exportDraft(Map<ConfigKey,RawValue> changes) {
        try { JSONObject document = new JSONObject().put("schema",1); JSONObject values = new JSONObject();
            for (var item : changes.entrySet()) values.put(item.getKey().name(),new JSONObject().put("kind",item.getValue().kind().name()).put("value",item.getValue().payload()));
            document.put("draft",values); exportText = document.toString(2); activity.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"UltimateIMS-draft.json"),302);
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
    private void export(String content) { exportText = content; activity.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"UltimateIMS-local-export.txt"),302); }
    boolean fileResult(int request, int result, Intent intent) {
        if (request != 301 && request != 302) return false;
        if (result != Activity.RESULT_OK || intent == null || intent.getData() == null) return true;
        io.execute(() -> {
            try {
                if (request == 302) { if (exportText == null) throw new IllegalStateException("Export content unavailable; destination preserved"); try (var out = activity.getContentResolver().openOutputStream(intent.getData())) { if (out == null) throw new IllegalStateException(); out.write(exportText.getBytes(java.nio.charset.StandardCharsets.UTF_8)); } }
                else {
                    byte[] bytes;
                    try (var input = activity.getContentResolver().openInputStream(intent.getData())) { if (input == null) throw new IllegalStateException(); bytes = input.readNBytes(1048577); }
                    if (bytes.length > 1048576) throw new IllegalArgumentException("Import too large");
                    JSONObject document = new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)); if (document.getInt("schema") != 1) throw new IllegalArgumentException("Unsupported schema");
                    Map<ConfigKey,RawValue> parsed = new EnumMap<>(ConfigKey.class); JSONObject values = document.getJSONObject("draft");
                    for (var names = values.keys(); names.hasNext();) { String name = names.next(); ConfigKey key = ConfigKey.valueOf(name); if (key.scope == ConfigKey.Scope.DEVICE) throw new IllegalArgumentException("SIM draft scope required"); JSONObject value = values.getJSONObject(name); RawValue raw = new RawValue(RawValue.Kind.valueOf(value.getString("kind")),value.getString("value")); key.validate(raw,Build.VERSION.SDK_INT); parsed.put(key,raw); }
                    activity.runOnUiThread(() -> { bindDraftIdentity(); draft.clear(); clearProfileDrafts(); draft.putAll(parsed); redraw.run(); });
                }
                localLog = "Local document operation completed";
            } catch (Exception e) { localLog = "Local document operation failed: " + e.getClass().getSimpleName(); }
            activity.runOnUiThread(redraw);
        }); return true;
    }
}
