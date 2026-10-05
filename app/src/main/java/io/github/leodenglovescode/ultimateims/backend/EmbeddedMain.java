package io.github.leodenglovescode.ultimateims.backend;

import android.os.*;

/** APK 自带独立 app_process 入口，只向本应用授权中的 provider 回传固定协议 Binder。 */
public final class EmbeddedMain {
    public static void main(String[] args) {
        try {
            if ((android.os.Process.myUid() != 2000 && android.os.Process.myUid() != 0) || args.length != 2 || !args[0].matches("[0-9a-f-]{36}") || !args[1].matches("[0-9]{1,5}")) return;
            Looper.prepareMainLooper(); PlatformMethods.enableAccess();
            Bundle bundle = new Bundle(); bundle.putBinder("channel", new PrivilegeService());
            Bundle result = PlatformMethods.attachPrivateBridge(args[0],bundle,Integer.parseInt(args[1]));
            if (result == null || !result.getBoolean("accepted")) return;
            Looper.loop();
        } catch (Exception e) { android.util.Log.e("UltimateIMS", "Private backend startup failed: " + e.getClass().getSimpleName()); }
    }
}
