package io.github.leodenglovescode.ultimateims.core;

import java.security.MessageDigest;
import java.util.Arrays;

/** 单次会话必须同时通过后端 UID、随机密钥和 instrumentation 就绪握手。 */
public final class BrokerHandshake {
    private byte[] secret;
    private int backendUid = -1;
    private boolean prepared;
    public synchronized void initialize(int caller, byte[] token) {
        if ((caller != 0 && caller != 2000) || secret != null || token == null || token.length != 32) throw new SecurityException("Broker initialization rejected");
        backendUid = caller; secret = token.clone();
    }
    private void authenticate(int caller, byte[] token) {
        if (secret == null || caller != backendUid || token == null || !MessageDigest.isEqual(secret, token)) throw new SecurityException("Broker authentication failed");
    }
    public synchronized void ready(int caller, byte[] token) {
        authenticate(caller, token);
        if (prepared) throw new SecurityException("Broker readiness already established");
        prepared = true;
    }
    public synchronized void requireReady(int caller, byte[] token) {
        authenticate(caller, token);
        if (!prepared) throw new SecurityException("Broker instrumentation handshake incomplete");
    }
    public synchronized void close(int caller, byte[] token) {
        requireReady(caller, token); revoke();
    }
    public synchronized void revoke() {
        prepared = false;
        if (secret != null) Arrays.fill(secret, (byte) 0);
        secret = null; backendUid = -1;
    }
}
