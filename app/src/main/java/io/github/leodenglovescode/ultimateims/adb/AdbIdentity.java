package io.github.leodenglovescode.ultimateims.adb;

import android.security.keystore.*;
import java.security.*;
import java.security.cert.*;
import java.security.interfaces.RSAPublicKey;
import java.math.BigInteger;
import java.nio.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import org.conscrypt.Conscrypt;

/** ADB 主机密钥仅在 AndroidKeyStore；配对码与私钥不写入偏好或日志。 */
final class AdbIdentity {
    private static final String ALIAS = "ultimateims.adb.host.v1";
    final PrivateKey privateKey;
    final X509Certificate certificate;
    AdbIdentity() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1, KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setCertificateSubject(new X500Principal("CN=UltimateIMS ADB"))
                    .setCertificateSerialNumber(BigInteger.ONE)
                    .setCertificateNotBefore(new java.util.Date(System.currentTimeMillis() - 86400000L))
                    .setCertificateNotAfter(new java.util.Date(System.currentTimeMillis() + 315360000000L)).build());
            generator.generateKeyPair();
        }
        privateKey = (PrivateKey) store.getKey(ALIAS, null); certificate = (X509Certificate) store.getCertificate(ALIAS);
    }
    byte[] publicAdbKey() {
        RSAPublicKey key = (RSAPublicKey) certificate.getPublicKey(); BigInteger n = key.getModulus();
        if (n.bitLength() != 2048 || key.getPublicExponent().intValue() != 65537) throw new IllegalStateException("Unsupported ADB host RSA key");
        ByteBuffer encoded = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        encoded.putInt(64); encoded.putInt(n.modInverse(BigInteger.ONE.shiftLeft(32)).negate().intValue());
        little(encoded, n, 256); little(encoded, BigInteger.ONE.shiftLeft(4096).mod(n), 256); encoded.putInt(65537);
        String publicText = android.util.Base64.encodeToString(encoded.array(), android.util.Base64.NO_WRAP) + " UltimateIMS@localhost\0";
        return publicText.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
    private static void little(ByteBuffer target, BigInteger number, int count) {
        byte[] bytes = number.toByteArray(); for (int i = 0; i < count; i++) target.put(i < bytes.length ? bytes[bytes.length - 1 - i] : 0);
    }
    SSLSocket tls(java.net.Socket transport, int port) throws Exception {
        SSLContext ssl = SSLContext.getInstance("TLS", Conscrypt.newProvider());
        X509ExtendedKeyManager keys = new X509ExtendedKeyManager() {
            @Override public String[] getClientAliases(String t, Principal[] p) { return new String[]{ALIAS}; }
            @Override public String chooseClientAlias(String[] t, Principal[] p, java.net.Socket s) { return ALIAS; }
            @Override public String[] getServerAliases(String t, Principal[] p) { return null; }
            @Override public String chooseServerAlias(String t, Principal[] p, java.net.Socket s) { return null; }
            @Override public X509Certificate[] getCertificateChain(String a) { return new X509Certificate[]{certificate}; }
            @Override public PrivateKey getPrivateKey(String a) { return privateKey; }
            @Override public String chooseEngineClientAlias(String[] t, Principal[] p, SSLEngine e) { return ALIAS; }
        };
        // ADB 使用本机共享码/客户端授权而非公有 CA；仅本机端口，后续 Binder 再校验 shell UID。
        X509TrustManager localAdbTrust = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] c, String a) throws CertificateException { if (c.length == 0) throw new CertificateException("No certificate"); }
            @Override public void checkServerTrusted(X509Certificate[] c, String a) throws CertificateException { if (c.length == 0) throw new CertificateException("No local ADB certificate"); }
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        ssl.init(new KeyManager[]{keys}, new TrustManager[]{localAdbTrust}, new SecureRandom());
        SSLSocket socket = (SSLSocket) ssl.getSocketFactory().createSocket(transport, "127.0.0.1", port, true);
        socket.setEnabledProtocols(new String[]{"TLSv1.3"}); socket.setSoTimeout(12000); socket.startHandshake(); return socket;
    }
}
