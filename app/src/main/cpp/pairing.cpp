#include <jni.h>
#include <openssl/curve25519.h>
#include <openssl/hkdf.h>
#include <openssl/aead.h>
#include <openssl/mem.h>
#include <openssl/digest.h>
#include <array>
#include <vector>
#include <cstring>

// 自写 JNI 适配只调用官方 BoringSSL 密码 API，不重新实现 SPAKE2、HKDF 或 AES。
struct Session {
    SPAKE2_CTX* spake = nullptr;
    EVP_AEAD_CTX* cipher = nullptr;
    std::vector<uint8_t> message;
    ~Session() { SPAKE2_CTX_free(spake); EVP_AEAD_CTX_free(cipher); }
};
static jbyteArray array(JNIEnv* env, const uint8_t* bytes, size_t size) {
    auto output = env->NewByteArray(static_cast<jsize>(size));
    if (output) env->SetByteArrayRegion(output, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte*>(bytes));
    return output;
}
extern "C" JNIEXPORT jlong JNICALL Java_io_github_leodenglovescode_ultimateims_adb_PairingCrypto_create(JNIEnv* env, jclass, jbyteArray password, jboolean server) {
    auto* s = new Session();
    const uint8_t client[] = "adb pair client", device[] = "adb pair server";
    s->spake = SPAKE2_CTX_new(server ? spake2_role_bob : spake2_role_alice,
                            server ? device : client, server ? sizeof(device) : sizeof(client),
                            server ? client : device, server ? sizeof(client) : sizeof(device));
    std::vector<uint8_t> secret(env->GetArrayLength(password));
    env->GetByteArrayRegion(password, 0, secret.size(), reinterpret_cast<jbyte*>(secret.data()));
    std::array<uint8_t, SPAKE2_MAX_MSG_SIZE> message{}; size_t size = 0;
    int valid = s->spake && SPAKE2_generate_msg(s->spake, message.data(), &size, message.size(), secret.data(), secret.size());
    OPENSSL_cleanse(secret.data(), secret.size());
    if (!valid) { delete s; return 0; }
    s->message.assign(message.begin(), message.begin() + size); return reinterpret_cast<jlong>(s);
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_io_github_leodenglovescode_ultimateims_adb_PairingCrypto_message(JNIEnv* env, jclass, jlong handle) {
    auto* s = reinterpret_cast<Session*>(handle); return s ? array(env, s->message.data(), s->message.size()) : nullptr;
}
extern "C" JNIEXPORT jboolean JNICALL Java_io_github_leodenglovescode_ultimateims_adb_PairingCrypto_initialize(JNIEnv* env, jclass, jlong handle, jbyteArray peer) {
    auto* s = reinterpret_cast<Session*>(handle); if (!s || s->cipher || env->GetArrayLength(peer) > SPAKE2_MAX_MSG_SIZE) return false;
    std::vector<uint8_t> input(env->GetArrayLength(peer)); env->GetByteArrayRegion(peer, 0, input.size(), reinterpret_cast<jbyte*>(input.data()));
    std::array<uint8_t, SPAKE2_MAX_KEY_SIZE> shared{}; size_t size = 0;
    if (!SPAKE2_process_msg(s->spake, shared.data(), &size, shared.size(), input.data(), input.size())) return false;
    uint8_t key[16]; const uint8_t info[] = "adb pairing_auth aes-128-gcm key";
    int valid = HKDF(key, sizeof(key), EVP_sha256(), shared.data(), size, nullptr, 0, info, sizeof(info) - 1);
    if (valid) s->cipher = EVP_AEAD_CTX_new(EVP_aead_aes_128_gcm(), key, sizeof(key), EVP_AEAD_DEFAULT_TAG_LENGTH);
    OPENSSL_cleanse(shared.data(), shared.size()); OPENSSL_cleanse(key, sizeof(key)); return valid && s->cipher;
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_io_github_leodenglovescode_ultimateims_adb_PairingCrypto_crypt(JNIEnv* env, jclass, jlong handle, jbyteArray data, jboolean encrypt) {
    auto* s = reinterpret_cast<Session*>(handle); if (!s || !s->cipher || env->GetArrayLength(data) > 16384) return nullptr;
    std::vector<uint8_t> input(env->GetArrayLength(data)), output(input.size() + 16);
    env->GetByteArrayRegion(data, 0, input.size(), reinterpret_cast<jbyte*>(input.data()));
    // 本协议只交换一个 peer-info 包；双方独立方向序号均从 0 开始。
    uint8_t nonce[12] = {}; size_t size = 0;
    int valid = encrypt ? EVP_AEAD_CTX_seal(s->cipher, output.data(), &size, output.size(), nonce, sizeof(nonce), input.data(), input.size(), nullptr, 0)
                        : EVP_AEAD_CTX_open(s->cipher, output.data(), &size, output.size(), nonce, sizeof(nonce), input.data(), input.size(), nullptr, 0);
    if (!valid) return nullptr; return array(env, output.data(), size);
}
extern "C" JNIEXPORT void JNICALL Java_io_github_leodenglovescode_ultimateims_adb_PairingCrypto_destroy(JNIEnv*, jclass, jlong handle) { delete reinterpret_cast<Session*>(handle); }
