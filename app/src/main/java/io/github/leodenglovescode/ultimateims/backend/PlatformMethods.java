package io.github.leodenglovescode.ultimateims.backend;

import android.os.IBinder;
import java.lang.reflect.*;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

/** 集中按实际字段、参数和返回类型验证，不硬编码 Binder 事务编号。 */
public final class PlatformMethods {
    public static void enableAccess() {
        if (!HiddenApiBypass.addHiddenApiExemptions("Landroid/", "Lcom/android/internal/telephony/")) throw new IllegalStateException("Hidden API access unavailable");
    }
    public static Object invoke(Object target, String name, Class<?> result, Class<?>[] signature, Object... args) throws Exception {
        Method method = target.getClass().getMethod(name, signature);
        if (method.getReturnType() != result) throw new NoSuchMethodException("Framework return type mismatch: " + name);
        return call(method, target, args);
    }
    public static Object statik(String type, String name, Class<?> result, Class<?>[] signature, Object... args) throws Exception {
        Method method = Class.forName(type).getDeclaredMethod(name, signature);
        if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != result) throw new NoSuchMethodException("Framework static signature mismatch: " + name);
        return call(method, null, args);
    }
    private static Object call(Method method, Object target, Object[] args) throws Exception {
        method.setAccessible(true);
        try { return method.invoke(target, args); }
        catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception error) throw error;
            throw new IllegalStateException("Framework invocation failed");
        }
    }
    public static Object service(String name, String interfaceName) throws Exception {
        IBinder binder = (IBinder) statik("android.os.ServiceManager", "getService", IBinder.class, new Class<?>[]{String.class}, name);
        if (binder == null) throw new IllegalStateException("Framework service unavailable: " + name);
        Class<?> type = Class.forName(interfaceName);
        return statik(interfaceName + "$Stub", "asInterface", type, new Class<?>[]{IBinder.class}, binder);
    }
    public static void setSubscriptionProperty(Object service, int sub, String key, String value) throws Exception {
        Method method = service.getClass().getMethod("setSubscriptionProperty",int.class,String.class,String.class);
        if (method.getReturnType() != void.class && method.getReturnType() != int.class) throw new NoSuchMethodException("Unsupported subscription setter return type");
        Object result = call(method,service,new Object[]{sub,key,value});
        if (result instanceof Integer count && count < 0) throw new IllegalStateException("Subscription update rejected");
        // Android 13 的 int 与 Android 16 的 void 都不能替代真实原始值回读。
    }
    public static String failure(Exception error) {
        if (error instanceof NoSuchMethodException || error instanceof NoSuchFieldException || error instanceof ClassNotFoundException) {
            String name = error.getMessage(); return "Unsupported framework signature: " + (name != null && name.matches("[A-Za-z0-9_.$():, \\[\\]<>-]{1,256}") ? name : error.getClass().getSimpleName());
        }
        if (error instanceof SecurityException) return "Permission denied or permission delegation occupied";
        if (error instanceof UnsupportedOperationException) return "Framework operation is unsupported on this build";
        return "Framework operation failed: " + error.getClass().getSimpleName();
    }
    public static android.os.Bundle attachPrivateBridge(String nonce, android.os.Bundle arguments, int user) throws Exception {
        int uid = android.os.Process.myUid();
        if ((uid != 0 && uid != 2000) || user < 0 || user > 99999) throw new SecurityException("Private bridge identity invalid");
        Object am = activityManager(); android.os.IBinder token = new android.os.Binder();
        String authority = Protocol.PACKAGE + ".bridge"; Object provider = null;
        try {
            Object holder = invoke(am,"getContentProviderExternal",Class.forName("android.app.ContentProviderHolder"),new Class<?>[]{String.class,int.class,android.os.IBinder.class,String.class},authority,user,token,"UltimateIMS private attachment");
            if (holder == null) throw new IllegalStateException("Private bridge unavailable");
            Field field = holder.getClass().getField("provider");
            if (field.getType() != Class.forName("android.content.IContentProvider")) throw new NoSuchFieldException("ContentProviderHolder.provider signature");
            provider = field.get(holder); if (provider == null) throw new IllegalStateException("Private bridge provider unavailable");
            android.content.AttributionSource source = new android.content.AttributionSource.Builder(uid).setPackageName(uid == 0 ? "root" : "com.android.shell").build();
            return (android.os.Bundle)invoke(provider,"call",android.os.Bundle.class,new Class<?>[]{android.content.AttributionSource.class,String.class,String.class,String.class,android.os.Bundle.class},source,authority,"attach",nonce,arguments);
        } finally {
            if (provider != null) invoke(am,"removeContentProviderExternalAsUser",void.class,new Class<?>[]{String.class,android.os.IBinder.class,int.class},authority,token,user);
        }
    }
    public static Object activityManager() throws Exception { return service("activity", "android.app.IActivityManager"); }
    public static int intField(String type, String name) throws Exception {
        Field field = Class.forName(type).getDeclaredField(name);
        if (field.getType() != int.class || !Modifier.isStatic(field.getModifiers())) throw new NoSuchFieldException(name);
        field.setAccessible(true); return field.getInt(null);
    }
    private PlatformMethods() { }
}
