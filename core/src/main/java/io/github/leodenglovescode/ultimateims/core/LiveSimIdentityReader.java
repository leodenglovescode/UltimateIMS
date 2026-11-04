package io.github.leodenglovescode.ultimateims.core;

/** 使用后端实时读源；活动状态和标识都重复核对，不能缓存 subId 到 ICCID 的映射。 */
public final class LiveSimIdentityReader {
    public interface Source {
        boolean active(int subscription) throws Exception;
        String serial(int subscription) throws Exception;
    }
    public static String read(Source source, int subscription) throws Exception {
        if (subscription < 0) throw new SecurityException("Subscription inactive or unreadable");
        if (!source.active(subscription)) throw new SecurityException("Subscription inactive or unreadable");
        String first = VerifiedSimIdentity.choose(null,source.serial(subscription));
        if (!source.active(subscription)) throw new SecurityException("SIM identity changed or unavailable");
        String second = VerifiedSimIdentity.choose(null,source.serial(subscription));
        return VerifiedSimIdentity.choose(first,second);
    }
    private LiveSimIdentityReader() { }
}
