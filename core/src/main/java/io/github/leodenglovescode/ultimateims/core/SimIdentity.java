package io.github.leodenglovescode.ultimateims.core;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Objects;

/** 仅已验证的 ICCID 可绑定恢复记录；卡槽、运营商、订阅编号均不是 SIM 身份。 */
public record SimIdentity(String digest) {
    public SimIdentity {
        Objects.requireNonNull(digest);
        if (!digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid SIM identity");
    }
    public static SimIdentity verifiedIccid(String iccid, byte[] installationKey) {
        if (!VerifiedSimIdentity.valid(iccid)) throw new IllegalArgumentException("SIM identity unavailable");
        if (installationKey.length < 32) throw new IllegalArgumentException("Identity key too short");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(installationKey.clone(), "HmacSHA256"));
            byte[] bytes = mac.doFinal(("UltimateIMS:SIM:v1:" + VerifiedSimIdentity.canonical(iccid)).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return new SimIdentity(result.toString());
        } catch (GeneralSecurityException e) { throw new IllegalStateException("Identity hashing unavailable", e); }
    }
    @Override public String toString() { return "SimIdentity[redacted]"; }
}
