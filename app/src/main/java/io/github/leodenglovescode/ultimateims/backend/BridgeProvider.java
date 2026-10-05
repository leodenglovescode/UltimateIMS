package io.github.leodenglovescode.ultimateims.backend;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import io.github.leodenglovescode.ultimateims.UltimateApplication;

/** 仅 shell/root 可附着，必须匹配本次用户启动的随机 nonce；不暴露读取或命令。 */
public final class BridgeProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        if ((uid != 0 && uid != 2000) || !"attach".equals(method) || extras == null) throw new SecurityException("Private attachment rejected");
        Bundle result = new Bundle();
        result.putBoolean("accepted", ((UltimateApplication) getContext().getApplicationContext()).backend().attach(arg, extras.getBinder("channel"), uid));
        return result;
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String sort) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
