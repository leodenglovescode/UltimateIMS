package io.github.leodenglovescode.ultimateims.backend;

import android.app.Instrumentation;
import android.os.Bundle;

/** 只驻留独立 broker 进程，不重启界面进程，不自行获取权限。 */
public final class BrokerInstrumentation extends Instrumentation {
    static volatile BrokerInstrumentation active;
    static volatile Object operationLock = new Object();
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        if (arguments == null || arguments.getBinder("channel") == null || arguments.getByteArray("secret") == null) { finish(0, new Bundle()); return; }
        try {
            PlatformMethods.enableAccess();
            IPrivilegedChannel channel = IPrivilegedChannel.Stub.asInterface(arguments.getBinder("channel"));
            arguments.getBinder("channel").linkToDeath(() -> new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                synchronized(operationLock) { if (active == this) { active = null; finish(0,new Bundle()); } }
            }),0);
            active = this;
            channel.ready(arguments.getByteArray("secret"));
        } catch (Exception e) { active = null; finish(0, new Bundle()); }
    }
}
