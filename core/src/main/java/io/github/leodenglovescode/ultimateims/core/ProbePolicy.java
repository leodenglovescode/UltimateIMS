package io.github.leodenglovescode.ultimateims.core;
import java.util.Set;

/** 明文诊断只允许已列出的门户候选域；任意自选其他端点必须使用 HTTPS。 */
public final class ProbePolicy {
    public static final Set<String> HTTP_HOSTS = Set.of("connectivitycheck.gstatic.com","captive.v2ex.co","connect.rom.miui.com","wifi.vivo.com.cn","connectivitycheck.platform.hicloud.com");
    public static boolean allowsHttp(String host) { return host != null && HTTP_HOSTS.contains(host.toLowerCase(java.util.Locale.ROOT)); }
    private ProbePolicy() { }
}
