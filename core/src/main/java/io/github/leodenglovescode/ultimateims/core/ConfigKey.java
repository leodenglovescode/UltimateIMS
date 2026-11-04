package io.github.leodenglovescode.ultimateims.core;

/** 白名单来自 AOSP 定义，不提供任意键名写入接口。原始订阅字段独立于 CarrierConfig。 */
public enum ConfigKey {
    VOLTE("carrier_volte_available_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    VOWIFI("carrier_wfc_ims_available_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    WIFI_ONLY("carrier_wfc_supports_wifi_only_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    WFC_MODE("carrier_default_wfc_ims_mode_int", RawValue.Kind.INTEGER, 33, Scope.CARRIER),
    WFC_ROAMING_MODE("carrier_default_wfc_ims_roaming_mode_int", RawValue.Kind.INTEGER, 33, Scope.CARRIER),
    WFC_ROAMING("carrier_default_wfc_ims_roaming_enabled_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    VT("carrier_vt_available_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    VONR("vonr_enabled_bool", RawValue.Kind.BOOLEAN, 34, Scope.CARRIER),
    CROSS_SIM("carrier_cross_sim_ims_available_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    UT("carrier_supports_ss_over_ut_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    NR_AVAILABILITY("carrier_nr_availabilities_int_array", RawValue.Kind.INTEGER_ARRAY, 33, Scope.CARRIER),
    NR_SIGNAL_METRICS("parameters_use_for_5g_nr_signal_bar_int", RawValue.Kind.INTEGER, 33, Scope.CARRIER),
    NR_NSA_LTE_SIGNAL("signal_strength_nr_nsa_use_lte_as_primary_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    NR_RSRP("5g_nr_ssrsrp_thresholds_int_array", RawValue.Kind.INTEGER_ARRAY, 33, Scope.CARRIER),
    NR_RSRQ("5g_nr_ssrsrq_thresholds_int_array", RawValue.Kind.INTEGER_ARRAY, 33, Scope.CARRIER),
    NR_SINR("5g_nr_sssinr_thresholds_int_array", RawValue.Kind.INTEGER_ARRAY, 33, Scope.CARRIER),
    NR_ICON("5g_icon_configuration_string", RawValue.Kind.STRING, 33, Scope.CARRIER),
    NR_ADVANCED_BANDWIDTH("nr_advanced_threshold_bandwidth_khz_int", RawValue.Kind.INTEGER, 33, Scope.CARRIER),
    NR_ADVANCED_BANDS("additional_nr_advanced_bands_int_array", RawValue.Kind.INTEGER_ARRAY, 33, Scope.CARRIER),
    NR_ADVANCED_PCO("nr_advanced_capable_pco_id_int", RawValue.Kind.INTEGER, 33, Scope.CARRIER),
    NR_ADVANCED_INCLUDE_LTE("include_lte_for_nr_advanced_threshold_bandwidth_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    LTE_PLUS_BANDWIDTH("lte_plus_threshold_bandwidth_khz_int", RawValue.Kind.INTEGER, 33, Scope.CARRIER),
    LTE("lte_enabled_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    HIDE_LTE_PLUS("hide_lte_plus_data_icon_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    SHOW_4G("show_4g_for_lte_data_icon_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    SHOW_4G_LTE("show_4glte_for_lte_data_icon_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    EDITABLE_LTE("editable_enhanced_4g_lte_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    HIDE_LTE_SETTING("hide_enhanced_4g_lte_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    EDITABLE_WFC("editable_wfc_mode_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    EDITABLE_WFC_ROAMING("editable_wfc_roaming_mode_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    CARRIER_NAME("carrier_name_string", RawValue.Kind.STRING, 33, Scope.CARRIER),
    CARRIER_NAME_OVERRIDE("carrier_name_override_bool", RawValue.Kind.BOOLEAN, 33, Scope.CARRIER),
    IMS_USER_AGENT("ims.ims_user_agent_string", RawValue.Kind.STRING, 33, Scope.CARRIER),
    SIM_COUNTRY("sim_country_iso_override_string", RawValue.Kind.STRING, 34, Scope.CARRIER),
    VOIMS_OPT_IN("voims_opt_in_status", RawValue.Kind.INTEGER, 33, Scope.SUBSCRIPTION),
    ADVANCED_CALLING_USER("volte_vt_enabled", RawValue.Kind.INTEGER, 33, Scope.SUBSCRIPTION),
    WFC_USER("wfc_ims_enabled", RawValue.Kind.INTEGER, 33, Scope.SUBSCRIPTION),
    WFC_USER_MODE("wfc_ims_mode", RawValue.Kind.INTEGER, 33, Scope.SUBSCRIPTION),
    WFC_USER_ROAMING_MODE("wfc_ims_roaming_mode", RawValue.Kind.INTEGER, 33, Scope.SUBSCRIPTION),
    WFC_USER_ROAMING("wfc_ims_roaming_enabled", RawValue.Kind.INTEGER, 33, Scope.SUBSCRIPTION),
    VT_USER("vt_ims_enabled", RawValue.Kind.INTEGER, 33, Scope.SUBSCRIPTION),
    CROSS_SIM_USER("cross_sim_calling_enabled", RawValue.Kind.INTEGER, 33, Scope.SUBSCRIPTION),
    VONR_USER("nr_advanced_calling_enabled", RawValue.Kind.INTEGER, 34, Scope.SUBSCRIPTION),
    PORTAL_HTTP("captive_portal_http_url", RawValue.Kind.STRING, 33, Scope.DEVICE),
    PORTAL_HTTPS("captive_portal_https_url", RawValue.Kind.STRING, 33, Scope.DEVICE);

    public enum Scope { CARRIER, SUBSCRIPTION, DEVICE }
    public final String platformName;
    public final RawValue.Kind kind;
    public final int minApi;
    public final Scope scope;
    ConfigKey(String name, RawValue.Kind kind, int minApi, Scope scope) {
        this.platformName = name; this.kind = kind; this.minApi = minApi; this.scope = scope;
    }
    public void validate(RawValue value, int api) {
        if (api < minApi) throw new IllegalArgumentException("Unsupported Android API");
        if (value.kind() == RawValue.Kind.UNSET) return;
        if (value.kind() != kind) throw new IllegalArgumentException("Invalid value type");
        if (this == WFC_MODE || this == WFC_ROAMING_MODE) range(value, 0, 2);
        if (this == WFC_USER_MODE || this == WFC_USER_ROAMING_MODE) range(value, -1, 2);
        else if (scope == Scope.SUBSCRIPTION) range(value, -1, 1);
        if (this == NR_SIGNAL_METRICS) range(value, 0, 7);
        if (this == NR_ADVANCED_BANDWIDTH || this == LTE_PLUS_BANDWIDTH) range(value, 0, 1_000_000);
        if (this == NR_ADVANCED_PCO) range(value, 0, 65535);
        if (this == NR_ADVANCED_BANDS && !value.payload().isEmpty()) {
            java.util.Set<Integer> bands = new java.util.HashSet<>();
            String[] parts = value.payload().split(",",-1);
            if (parts.length > 64) throw new IllegalArgumentException("Too many NR display bands");
            for (String part : parts) {
                int band = Integer.parseInt(part);
                if (band < 1 || band > 1024 || !bands.add(band)) throw new IllegalArgumentException("Invalid NR display band");
            }
        }
        if (this == NR_AVAILABILITY) {
            java.util.Set<String> entries = new java.util.HashSet<>();
            if (!value.payload().isEmpty()) for (String part : value.payload().split(",")) {
                if ((!part.equals("1") && !part.equals("2")) || !entries.add(part)) throw new IllegalArgumentException("Invalid NR availability");
            }
        }
        if (this == NR_RSRP || this == NR_RSRQ || this == NR_SINR) {
            String[] items = value.payload().split(",");
            if (items.length != 4) throw new IllegalArgumentException("Four NR thresholds required");
            int min = this == NR_RSRP ? -140 : this == NR_RSRQ ? -43 : -23;
            int max = this == NR_RSRP ? -44 : this == NR_RSRQ ? 20 : 40;
            int previous = min - 1;
            for (String s : items) {
                int next = Integer.parseInt(s);
                if (next <= previous || next > max) throw new IllegalArgumentException("Invalid NR thresholds");
                previous = next;
            }
        }
        if (this == NR_ICON && !value.payload().isEmpty()) {
            java.util.Set<String> scenarios = java.util.Set.of("connected_mmwave","connected","connected_rrc_idle","not_restricted_rrc_idle","not_restricted_rrc_con","restricted");
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (String pair : value.payload().split(",",-1)) {
                String[] fields = pair.split(":",-1);
                if (fields.length != 2 || !scenarios.contains(fields[0]) || !seen.add(fields[0]) || !java.util.Set.of("5G_Plus","5G","None").contains(fields[1])) throw new IllegalArgumentException("Invalid 5G display mapping");
            }
        }
        if (this == SIM_COUNTRY && !value.payload().matches("[0-9]{3}")) throw new IllegalArgumentException("Three digit compatibility target required");
        if (kind == RawValue.Kind.STRING && (value.payload().length() > 512 || value.payload().chars().anyMatch(c -> c < 32))) throw new IllegalArgumentException("Invalid text");
        if (this == PORTAL_HTTP || this == PORTAL_HTTPS) {
            java.net.URI uri = java.net.URI.create(value.payload());
            String scheme = this == PORTAL_HTTP ? "http" : "https";
            if (!scheme.equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) throw new IllegalArgumentException("Invalid portal endpoint");
        }
    }
    private static void range(RawValue value, int min, int max) {
        int v = Integer.parseInt(value.payload());
        if (v < min || v > max) throw new IllegalArgumentException("Value out of range");
    }
}
