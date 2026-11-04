package io.github.leodenglovescode.ultimateims.backend;
import android.os.Bundle;
import io.github.leodenglovescode.ultimateims.backend.IPrivilegedChannel;
interface IBrokerChannel {
    void initialize(in byte[] secret, IPrivilegedChannel identitySource);
    void ready(in byte[] secret);
    Bundle execute(in byte[] secret, int operation, in Bundle arguments);
}
