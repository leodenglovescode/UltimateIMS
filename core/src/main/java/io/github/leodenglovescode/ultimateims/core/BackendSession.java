package io.github.leodenglovescode.ultimateims.core;

import java.security.MessageDigest;
import java.security.SecureRandom;

/** 会话令牌只能在已认证的传输建立后签发；本类不代替 Binder 调用方身份校验。 */
public final class BackendSession {
    public enum Mode { SHIZUKU, EMBEDDED_ADB, EXISTING_ROOT }
    private final byte[] secret = new byte[32];
    private final int clientUid;
    private final Mode mode;
    private boolean revoked;
    public BackendSession(int clientUid, Mode mode) {
        if (clientUid < 0 || mode == null) throw new IllegalArgumentException("Invalid session");
        this.clientUid = clientUid; this.mode = mode; new SecureRandom().nextBytes(secret);
    }
    public synchronized byte[] token() { if (revoked) throw new SecurityException("Session revoked"); return secret.clone(); }
    public synchronized void authenticate(int callingUid, Mode selectedMode, byte[] token) {
        if (revoked || clientUid != callingUid || mode != selectedMode || token == null || !MessageDigest.isEqual(secret, token)) throw new SecurityException("Backend authentication failed");
    }
    public synchronized void revoke() { revoked = true; java.util.Arrays.fill(secret, (byte) 0); }
    @Override public String toString() { return "BackendSession[redacted]"; }
}
