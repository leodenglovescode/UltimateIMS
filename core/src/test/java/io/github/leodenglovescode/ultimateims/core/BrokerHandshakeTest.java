package io.github.leodenglovescode.ultimateims.core;

import org.junit.Test;
import static org.junit.Assert.*;

/** 验证真实 broker 所用握手状态，覆盖未完成启动、伪造身份和撤销。 */
public class BrokerHandshakeTest {
    private final byte[] token = new byte[32];
    @Test public void initializationDoesNotAuthorizeOperations() {
        BrokerHandshake session = new BrokerHandshake(); session.initialize(2000, token);
        assertThrows(SecurityException.class, () -> session.requireReady(2000, token));
        session.ready(2000, token); session.requireReady(2000, token);
    }
    @Test public void onlySelectedBackendCanCompleteHandshake() {
        BrokerHandshake session = new BrokerHandshake(); session.initialize(2000, token);
        assertThrows(SecurityException.class, () -> session.ready(0, token));
        assertThrows(SecurityException.class, () -> session.ready(10001, token));
        byte[] wrong = token.clone(); wrong[0] = 1;
        assertThrows(SecurityException.class, () -> session.ready(2000, wrong));
        assertThrows(SecurityException.class, () -> session.requireReady(2000, token));
    }
    @Test public void replayAndReinitializationAreRejected() {
        BrokerHandshake session = new BrokerHandshake(); session.initialize(0, token); session.ready(0, token);
        assertThrows(SecurityException.class, () -> session.ready(0, token));
        assertThrows(SecurityException.class, () -> session.initialize(0, token));
    }
    @Test public void closeRevokesOldAuthorization() {
        BrokerHandshake session = new BrokerHandshake(); session.initialize(2000, token); session.ready(2000, token);
        assertThrows(SecurityException.class, () -> session.close(10001, token));
        session.requireReady(2000, token); session.close(2000, token);
        assertThrows(SecurityException.class, () -> session.requireReady(2000, token));
        assertThrows(SecurityException.class, () -> session.ready(2000, token));
    }
    @Test public void unbindRevokesInterruptedPreparation() {
        BrokerHandshake session = new BrokerHandshake(); session.initialize(2000, token); session.revoke();
        assertThrows(SecurityException.class, () -> session.ready(2000, token));
    }
    @Test public void rejectsInvalidInitialIdentityAndToken() {
        BrokerHandshake session = new BrokerHandshake();
        assertThrows(SecurityException.class, () -> session.initialize(10001, token));
        assertThrows(SecurityException.class, () -> session.initialize(2000, new byte[31]));
        assertThrows(SecurityException.class, () -> session.initialize(2000, null));
    }
    @Test public void initializerCannotMutateStoredToken() {
        BrokerHandshake session = new BrokerHandshake(); byte[] supplied = token.clone(); session.initialize(2000, supplied); supplied[0] = 1;
        session.ready(2000, token); session.requireReady(2000, token);
    }
}
