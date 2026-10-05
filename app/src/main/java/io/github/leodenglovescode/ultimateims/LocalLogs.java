package io.github.leodenglovescode.ultimateims;

import java.util.concurrent.TimeUnit;

/** 固定的非特权 logcat 请求，仅本应用可读 UID 日志与固定标签；原始内容不进分享报告。 */
final class LocalLogs {
    static String read() {
        java.lang.Process process = null;
        try {
            process = new ProcessBuilder("logcat","-d","-t","200","-v","brief","UltimateIMS:I","*:S").redirectErrorStream(true).start();
            if (!process.waitFor(3,TimeUnit.SECONDS)) { process.destroyForcibly(); return "Local log read timed out"; }
            byte[] bytes; try (var input = process.getInputStream()) { bytes = input.readNBytes(65537); }
            if (bytes.length > 65536) return "Local log bound exceeded";
            String output = new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
            return output.isBlank() ? "No readable application log entries" : output;
        } catch (Exception e) { return "Local logs unavailable: " + e.getClass().getSimpleName(); }
        finally { if (process != null) process.destroy(); }
    }
    private LocalLogs() { }
}
