package io.github.leodenglovescode.ultimateims.core;

import java.util.*;

/** 同一应用必须只持有一个协调器。超时、身份变化和无法回读均阻止后续写入。 */
public final class OperationCoordinator {
    public interface Backend {
        BackendSession.Mode mode();
        SimIdentity verifiedIdentity(int subscriptionId) throws Exception;
        RawValue readRaw(int subscriptionId, ConfigKey key) throws Exception;
        void writeRaw(int subscriptionId, ConfigKey key, RawValue value) throws Exception;
    }
    public record Request(int callerUid, BackendSession.Mode selectedMode, byte[] token,
                          SimIdentity sim, int subscriptionId, int api, Map<ConfigKey, RawValue> target,
                          boolean countryOverrideConfirmed) {
        public Request { token = token.clone(); target = Map.copyOf(target); }
        @Override public byte[] token() { return token.clone(); }
    }
    private final RecoveryJournal journal;
    private final Backend backend;
    private final BackendSession session;
    public OperationCoordinator(RecoveryJournal journal, Backend backend, BackendSession session) {
        this.journal = journal; this.backend = backend; this.session = session;
    }
    private void authorize(Request request) throws Exception {
        session.authenticate(request.callerUid(), request.selectedMode(), request.token());
        if (backend.mode() != request.selectedMode()) throw new SecurityException("Selected backend mismatch");
        if (request.subscriptionId() < 0 || !request.sim().equals(backend.verifiedIdentity(request.subscriptionId()))) throw new SecurityException("SIM identity changed or unavailable");
    }
    public synchronized RecoveryJournal.State apply(Request request) throws Exception {
        authorize(request);
        if (request.target().isEmpty()) throw new IllegalArgumentException("No changes selected");
        for (var item : request.target().entrySet()) {
            if (item.getKey().scope == ConfigKey.Scope.DEVICE) throw new IllegalArgumentException("Device settings require separate scope");
            item.getKey().validate(item.getValue(), request.api());
            if (item.getKey() == ConfigKey.SIM_COUNTRY && !request.countryOverrideConfirmed()) throw new SecurityException("Explicit SIM region confirmation required");
        }
        RecoveryJournal.Entry old = journal.read(request.sim());
        if (old != null && old.requiresRecovery()) throw new IllegalStateException("Recovery required before new operation");
        Map<ConfigKey, RawValue> originals = new EnumMap<>(ConfigKey.class);
        if (old != null) originals.putAll(old.originals());
        for (var key : request.target().keySet()) {
            authorize(request);
            RawValue current = backend.readRaw(request.subscriptionId(), key);
            if (current == null || !current.readable()) throw new IllegalStateException("Original value unavailable");
            originals.putIfAbsent(key, current);
        }
        var entry = new RecoveryJournal.Entry(request.sim(), RecoveryJournal.State.PREPARED, originals, request.target());
        journal.save(entry);
        return execute(request, entry, false);
    }
    public synchronized RecoveryJournal.State restore(Request request) throws Exception {
        authorize(request);
        var entry = journal.read(request.sim());
        if (entry == null) throw new IllegalStateException("No original backup available");
        // 恢复严格使用原值，不验证为新配置，也不把未设置值转换为 false。
        return execute(request, entry, true);
    }
    private RecoveryJournal.State execute(Request request, RecoveryJournal.Entry entry, boolean restore) throws Exception {
        journal.save(entry.state(restore ? RecoveryJournal.State.RESTORING : RecoveryJournal.State.APPLYING));
        Map<ConfigKey, RawValue> changes = restore ? entry.originals() : entry.target();
        try {
            for (var key : ConfigKey.values()) if (changes.containsKey(key)) {
                authorize(request);
                backend.writeRaw(request.subscriptionId(), key, changes.get(key));
                authorize(request);
                if (!changes.get(key).equals(backend.readRaw(request.subscriptionId(), key))) throw new IllegalStateException("Write verification failed");
            }
            // 验证完整集合，避免后写入改变前一字段时误报成功。
            for (var item : changes.entrySet()) {
                authorize(request);
                if (!item.getValue().equals(backend.readRaw(request.subscriptionId(), item.getKey()))) throw new IllegalStateException("Final verification failed");
            }
            var state = restore ? RecoveryJournal.State.RESTORED : RecoveryJournal.State.VERIFIED;
            journal.save(entry.state(state)); return state;
        } catch (Exception failure) {
            try { journal.save(entry.state(RecoveryJournal.State.RECOVERY_REQUIRED)); }
            catch (Exception storageFailure) { failure.addSuppressed(storageFailure); }
            throw failure;
        }
    }
}
