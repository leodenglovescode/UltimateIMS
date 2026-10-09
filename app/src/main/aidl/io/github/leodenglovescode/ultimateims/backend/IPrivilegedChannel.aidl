package io.github.leodenglovescode.ultimateims.backend;
import android.os.Bundle;
import android.os.IBinder;
import io.github.leodenglovescode.ultimateims.backend.IBrokerChannel;
import io.github.leodenglovescode.ultimateims.backend.IInstrumentationSession;
interface IPrivilegedChannel {
    void initialize(int appUid, int mode, in byte[] secret, IBrokerChannel broker, IBinder clientLife) = 0;
    Bundle invoke(in byte[] secret, int operation, in Bundle arguments) = 1;
    void ready(in byte[] secret, IInstrumentationSession session) = 2;
    void destroy() = 16777114;
}
