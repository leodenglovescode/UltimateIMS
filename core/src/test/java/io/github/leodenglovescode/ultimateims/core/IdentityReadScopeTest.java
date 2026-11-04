package io.github.leodenglovescode.ultimateims.core;

import org.junit.Test;
import static org.junit.Assert.*;

/** 认证另由后端校验；此处验证回调的时间和目标范围，不能扩展成任意读取通道。 */
public class IdentityReadScopeTest {
    @Test public void closedScopeRejectsReads() { assertThrows(SecurityException.class,() -> new IdentityReadScope().require(3)); }
    @Test public void singleSimOperationCannotQueryOtherSubscription() {
        IdentityReadScope scope = new IdentityReadScope(); scope.begin(3,false); scope.require(3);
        assertThrows(SecurityException.class,() -> scope.require(4));
    }
    @Test public void snapshotCanReadMultipleNonnegativeSubscriptions() {
        IdentityReadScope scope = new IdentityReadScope(); scope.begin(-1,true); scope.require(3); scope.require(4);
        assertThrows(SecurityException.class,() -> scope.require(-2));
    }
    @Test public void completionRevokesCallbackScope() {
        IdentityReadScope scope = new IdentityReadScope(); scope.begin(3,false); scope.end();
        assertThrows(SecurityException.class,() -> scope.require(3));
    }
    @Test public void overlappingScopeIsRejected() {
        IdentityReadScope scope = new IdentityReadScope(); scope.begin(3,false);
        assertThrows(IllegalStateException.class,() -> scope.begin(4,true)); scope.require(3);
    }
    @Test public void nextOperationDoesNotInheritPreviousTarget() {
        IdentityReadScope scope = new IdentityReadScope(); scope.begin(-1,true); scope.end(); scope.begin(4,false); scope.require(4);
        assertThrows(SecurityException.class,() -> scope.require(3));
    }
}
