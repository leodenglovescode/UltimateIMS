package io.github.leodenglovescode.ultimateims.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** 原值只追加；终态和恢复成功都不删除备份。每次状态变更先持久化再执行写入。 */
public final class RecoveryJournal {
    public enum State { PREPARED, APPLYING, VERIFIED, RECOVERY_REQUIRED, RESTORING, RESTORED }
    public record Entry(SimIdentity identity, State state, Map<ConfigKey, RawValue> originals,
                        Map<ConfigKey, RawValue> target) {
        public Entry {
            originals = Map.copyOf(originals); target = Map.copyOf(target);
            if (originals.values().stream().anyMatch(v -> !v.readable())) throw new IllegalArgumentException("Unreadable original");
        }
        public Entry state(State next) { return new Entry(identity, next, originals, target); }
        public boolean requiresRecovery() {
            return state == State.PREPARED || state == State.APPLYING || state == State.RESTORING || state == State.RECOVERY_REQUIRED;
        }
    }
    private final Path directory;
    public RecoveryJournal(Path directory) throws IOException {
        this.directory = directory; Files.createDirectories(directory);
    }
    public synchronized Entry read(SimIdentity sim) throws IOException {
        Path path = directory.resolve(sim.digest() + ".journal");
        if (!Files.exists(path)) return null;
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            if (in.readInt() != 0x55494D53 || in.readInt() != 1) throw new IOException("Unsupported journal format; preserve backup");
            SimIdentity identity = new SimIdentity(in.readUTF());
            if (!identity.equals(sim)) throw new IOException("Journal identity mismatch");
            Entry result = new Entry(identity, State.valueOf(in.readUTF()), readValues(in), readValues(in));
            if (in.read() != -1) throw new IOException("Unexpected journal data");
            return result;
        } catch (IllegalArgumentException e) { throw new IOException("Corrupt journal; preserve backup", e); }
    }
    public synchronized void save(Entry entry) throws IOException {
        Entry old = read(entry.identity());
        if (old != null) for (var item : old.originals().entrySet()) {
            if (!item.getValue().equals(entry.originals().get(item.getKey()))) throw new IOException("Original backup is immutable");
        }
        Path target = directory.resolve(entry.identity().digest() + ".journal");
        Path temporary = directory.resolve(entry.identity().digest() + ".pending");
        try (FileOutputStream file = new FileOutputStream(temporary.toFile()); DataOutputStream out = new DataOutputStream(file)) {
            out.writeInt(0x55494D53); out.writeInt(1);
            out.writeUTF(entry.identity().digest()); out.writeUTF(entry.state().name());
            writeValues(out, entry.originals()); writeValues(out, entry.target());
            out.flush(); file.getFD().sync();
        }
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        // 目录同步失败时向上传播，不得继续执行系统写入。
        try (var channel = java.nio.channels.FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
    }
    private static void writeValues(DataOutputStream out, Map<ConfigKey, RawValue> values) throws IOException {
        out.writeInt(values.size());
        for (var key : ConfigKey.values()) if (values.containsKey(key)) {
            RawValue value = values.get(key);
            out.writeUTF(key.name()); out.writeUTF(value.kind().name()); out.writeUTF(value.payload());
        }
    }
    private static Map<ConfigKey, RawValue> readValues(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > ConfigKey.values().length) throw new IOException("Invalid journal size");
        Map<ConfigKey, RawValue> values = new EnumMap<>(ConfigKey.class);
        for (int i = 0; i < count; i++) {
            ConfigKey key = ConfigKey.valueOf(in.readUTF());
            RawValue value = new RawValue(RawValue.Kind.valueOf(in.readUTF()), in.readUTF());
            if (values.put(key, value) != null) throw new IOException("Duplicate journal key");
        }
        return values;
    }
}
