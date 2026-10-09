package io.github.leodenglovescode.ultimateims.backend;
import android.os.Bundle;
interface IBrokerChannel {
    void initialize(in byte[] secret);
    void ready(in byte[] secret);
    Bundle execute(in byte[] secret, int operation, in Bundle arguments);
}
