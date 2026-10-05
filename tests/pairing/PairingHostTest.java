package io.github.leodenglovescode.ultimateims.adb;
import java.util.Arrays;

/** 运行生产 JNI 的故障注入；只验证密码适配器，不声称 Android 配对通过。 */
public final class PairingHostTest {
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    private static void rejected(Runnable action) { try { action.run(); } catch (SecurityException | IllegalStateException expected) { return; } throw new AssertionError("Unsafe pairing accepted"); }
    private static void exchange(PairingCrypto a, PairingCrypto b) { byte[] left = a.outbound(), right = b.outbound(); a.accept(right); b.accept(left); }
    public static void main(String[] args) {
        byte[] password = new byte[70]; Arrays.fill(password,(byte)'7'); byte[] data = new byte[8192]; data[0] = 1;
        try (var a = new PairingCrypto(password,false); var b = new PairingCrypto(password,true)) {
            exchange(a,b); check(Arrays.equals(data,b.decrypt(a.encrypt(data)))); check(Arrays.equals(data,a.decrypt(b.encrypt(data))));
            rejected(() -> a.encrypt(data)); rejected(() -> a.decrypt(data)); rejected(() -> a.accept(b.outbound()));
        }
        byte[] wrong = password.clone(); wrong[0] = '9';
        try (var a = new PairingCrypto(password,false); var b = new PairingCrypto(wrong,true)) { exchange(a,b); byte[] ciphertext = a.encrypt(data); rejected(() -> b.decrypt(ciphertext)); }
        try (var a = new PairingCrypto(password,false); var b = new PairingCrypto(password,true)) { exchange(a,b); byte[] ciphertext = a.encrypt(data); ciphertext[42] ^= 1; rejected(() -> b.decrypt(ciphertext)); }
        try (var a = new PairingCrypto(password,false)) { rejected(() -> a.accept(new byte[500])); rejected(() -> a.encrypt(data)); a.close(); rejected(() -> a.accept(data)); }
        System.out.println("Pairing JNI host checks passed: exchange, wrong code, tampering, malformed exchange, reuse, close. Android flow not tested.");
    }
}
