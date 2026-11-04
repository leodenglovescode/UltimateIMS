package io.github.leodenglovescode.ultimateims.core;

/** 回调仅在认证 broker 操作期间开放，并限定为该操作的目标卡。 */
public final class IdentityReadScope {
    private boolean open, all;
    private int subscription;
    public synchronized void begin(int subscription, boolean snapshot) {
        if (open) throw new IllegalStateException("Identity read scope already active");
        this.subscription = subscription; all = snapshot; open = true;
    }
    public synchronized void require(int requested) {
        if (!open || requested < 0 || (!all && requested != subscription)) throw new SecurityException("Identity read outside authenticated operation");
    }
    public synchronized void end() { open = false; all = false; subscription = -1; }
}
