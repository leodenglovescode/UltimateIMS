package io.github.leodenglovescode.ultimateims.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/** 按实际框架签名解析；只有方法缺失才选择旧签名，调用失败绝不换签名重试。 */
public final class DelegationMethods {
    private final Object target;
    private final Method start, read, stop;
    private final boolean uidScopedStop, uidScopedRead;

    public DelegationMethods(Object target) throws NoSuchMethodException {
        this.target = target;
        Class<?> type = target.getClass();
        start = require(type, "startDelegateShellPermissionIdentity", void.class, int.class, String[].class);
        Method readCandidate;
        boolean scopedRead;
        try { readCandidate = type.getMethod("getDelegatedShellPermissions", int.class); scopedRead = true; }
        catch (NoSuchMethodException missingUidRead) { readCandidate = type.getMethod("getDelegatedShellPermissions"); scopedRead = false; }
        if (readCandidate.getReturnType() != List.class) throw new NoSuchMethodException("Framework return type mismatch: getDelegatedShellPermissions");
        readCandidate.setAccessible(true); read = readCandidate; uidScopedRead = scopedRead;
        Method candidate;
        boolean scoped;
        try {
            candidate = type.getMethod("stopDelegateShellPermissionIdentity", int.class);
            scoped = true;
        } catch (NoSuchMethodException missingUidSignature) {
            candidate = type.getMethod("stopDelegateShellPermissionIdentity");
            scoped = false;
        }
        // 已存在但返回类型不匹配时拒绝，不把接口变化当成旧版本。
        if (candidate.getReturnType() != void.class) throw new NoSuchMethodException("Framework return type mismatch: stopDelegateShellPermissionIdentity");
        candidate.setAccessible(true);
        stop = candidate; uidScopedStop = scoped;
    }
    private static Method require(Class<?> type, String name, Class<?> result, Class<?>... parameters) throws NoSuchMethodException {
        Method method = type.getMethod(name, parameters);
        if (method.getReturnType() != result) throw new NoSuchMethodException("Framework return type mismatch: " + name);
        method.setAccessible(true);
        return method;
    }
    private Object call(Method method, Object... arguments) throws Exception {
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error fatal) throw fatal;
            throw new IllegalStateException("Framework invocation failed");
        }
    }
    public List<?> permissions(int uid) throws Exception {
        if (uid < 10000) throw new IllegalArgumentException("Invalid delegation UID");
        return (List<?>) (uidScopedRead ? call(read, uid) : call(read));
    }
    public void start(int uid, String[] permissions) throws Exception {
        if (uid < 10000 || permissions == null || permissions.length == 0) throw new IllegalArgumentException("Invalid delegation request");
        call(start, uid, permissions.clone());
    }
    public void stop(int uid) throws Exception {
        if (uid < 10000) throw new IllegalArgumentException("Invalid delegation UID");
        if (uidScopedStop) call(stop, uid); else call(stop);
        // 释放调用返回不能证明释放完成；不可读或仍有委托时保留失败状态。
        List<?> remaining = permissions(uid);
        if (remaining == null || !remaining.isEmpty()) throw new IllegalStateException("Permission delegation release unverified");
    }
    public boolean uidScopedRead() { return uidScopedRead; }
    public boolean uidScopedStop() { return uidScopedStop; }
}
