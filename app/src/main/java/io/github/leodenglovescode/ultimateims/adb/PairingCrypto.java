package io.github.leodenglovescode.ultimateims.adb;

/** 指针生命周期由 AutoCloseable 管理；每个方向只加密/解密一个 peer-info。 */
final class PairingCrypto implements AutoCloseable {
    static { System.loadLibrary("uims_pairing"); }
    private long handle;
    private boolean initialized, encrypted, decrypted;
    PairingCrypto(byte[] password) { this(password,false); }
    PairingCrypto(byte[] password, boolean server) { handle = create(password, server); if (handle == 0) throw new IllegalStateException("SPAKE2 initialization failed"); }
    synchronized byte[] outbound() { return message(handle); }
    synchronized void accept(byte[] peer) {
        if (initialized || handle == 0 || !initialize(handle, peer)) throw new SecurityException("Pairing key exchange failed"); initialized = true;
    }
    synchronized byte[] encrypt(byte[] data) {
        if (!initialized || encrypted || handle == 0) throw new IllegalStateException("Pairing encryption state invalid"); encrypted = true;
        byte[] result = crypt(handle, data, true); if (result == null) throw new SecurityException("Pairing encryption failed"); return result;
    }
    synchronized byte[] decrypt(byte[] data) {
        if (!initialized || decrypted || handle == 0) throw new IllegalStateException("Pairing decryption state invalid"); decrypted = true;
        byte[] result = crypt(handle, data, false); if (result == null) throw new SecurityException("Pairing authentication failed"); return result;
    }
    @Override public synchronized void close() { if (handle != 0) { destroy(handle); handle = 0; } }
    private static native long create(byte[] password, boolean server);
    private static native byte[] message(long handle);
    private static native boolean initialize(long handle, byte[] peer);
    private static native byte[] crypt(long handle, byte[] data, boolean encrypt);
    private static native void destroy(long handle);
}
