package io.github.leodenglovescode.ultimateims;

import android.content.Context;
import android.security.keystore.*;
import java.security.*;
import javax.crypto.*;
import io.github.leodenglovescode.ultimateims.core.SimIdentity;
import io.github.leodenglovescode.ultimateims.core.VerifiedSimIdentity;

/** 安装私有 HMAC 密钥不可导出；密钥丢失且备份存在时拒绝新建，避免伪绑定恢复。 */
final class IdentityStore {
    private static final String ALIAS = "ultimateims.sim.identity.v1";
    private final KeyStore store;
    IdentityStore(Context context) throws Exception {
        store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            java.io.File directory = new java.io.File(context.getNoBackupFilesDir(), "recovery");
            String[] existing = directory.list((d, n) -> n.endsWith(".journal"));
            String[] apn = context.getNoBackupFilesDir().list((d,n) -> n.endsWith(".apn.json"));
            if ((existing != null && existing.length > 0) || (apn != null && apn.length > 0)) throw new IllegalStateException("Identity key missing; preserve recovery backups");
            KeyGenerator generator = KeyGenerator.getInstance("HmacSHA256", "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).build()); generator.generateKey();
        }
    }
    synchronized SimIdentity sim(String iccid) throws Exception {
        // 旧格式仍是同一前缀和大写字符串，已保存的 HMAC/原值备份不重新命名。
        return digest("UltimateIMS:SIM:v1:" + VerifiedSimIdentity.canonical(iccid));
    }
    synchronized SimIdentity device() throws Exception { return digest("UltimateIMS:DEVICE:v1"); }
    private SimIdentity digest(String text) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(store.getKey(ALIAS, null));
        StringBuilder result = new StringBuilder();
        for (byte b : mac.doFinal(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return new SimIdentity(result.toString());
    }
}
