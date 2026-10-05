package io.github.leodenglovescode.ultimateims;

import android.content.Context;
import android.net.*;
import android.os.SystemClock;
import java.net.*;

/** 仅用户确认后运行；不读取响应正文、IP、位置或 SIM 标识符到报告。 */
final class ConnectivityProbe {
    static String run(Context context, String input) {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(input);
            if ((!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) throw new IllegalArgumentException("Invalid diagnostic endpoint");
            if ("http".equals(uri.getScheme()) && !io.github.leodenglovescode.ultimateims.core.ProbePolicy.allowsHttp(uri.getHost())) return "HTTP probes are limited to the five portal preset hosts; use HTTPS for other endpoints";
            Network network = context.getSystemService(ConnectivityManager.class).getActiveNetwork();
            if (network == null) return "No active default network; no probe sent";
            long started = SystemClock.elapsedRealtime(); int count = network.getAllByName(uri.getHost()).length; long dns = SystemClock.elapsedRealtime() - started;
            connection = (HttpURLConnection) network.openConnection(uri.toURL()); connection.setConnectTimeout(7000); connection.setReadTimeout(7000); connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET"); int status = connection.getResponseCode();
            return "User-triggered default-network probe: DNS answers=" + count + ", DNS time=" + dns + "ms, HTTP=" + status + ", total=" + (SystemClock.elapsedRealtime()-started) + "ms. No response body retained.";
        } catch (Exception e) { return "Connectivity probe failed: " + e.getClass().getSimpleName() + ". No endpoint, IP, or credentials logged."; }
        finally { if (connection != null) connection.disconnect(); }
    }
    static String publicIp(Context context, String input) {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(input);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) throw new IllegalArgumentException("HTTPS IP endpoint required");
            Network network = context.getSystemService(ConnectivityManager.class).getActiveNetwork(); if (network == null) return "No active network";
            connection = (HttpURLConnection)network.openConnection(uri.toURL()); connection.setConnectTimeout(7000); connection.setReadTimeout(7000); connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200) return "IP service did not return HTTP 200";
            byte[] body; try (var stream = connection.getInputStream()) { body = stream.readNBytes(257); }
            if (body.length > 256) throw new IllegalArgumentException("Response too large");
            String candidate = new String(body,java.nio.charset.StandardCharsets.US_ASCII).trim();
            boolean literal = android.util.Patterns.IP_ADDRESS.matcher(candidate).matches();
            if (!literal && candidate.contains(":") && candidate.matches("[0-9a-fA-F:.]{2,64}")) { InetAddress.getByName("["+candidate+"]"); literal = true; }
            if (!literal) throw new IllegalArgumentException("Endpoint must return one plain-text IP address");
            return "Public IP (local display only): " + candidate;
        } catch (Exception e) { return "Public IP lookup failed: " + e.getClass().getSimpleName(); }
        finally { if (connection != null) connection.disconnect(); }
    }
    private ConnectivityProbe() { }
}
