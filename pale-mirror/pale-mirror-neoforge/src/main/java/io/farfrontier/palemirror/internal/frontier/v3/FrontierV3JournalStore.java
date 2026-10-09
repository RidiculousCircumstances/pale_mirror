package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.CRC32C;

/** Opaque keyed images: one ordered durable append lane, immutable background checkpoints.
 * No actor, activity, Minecraft-world or resource policy belongs to this storage owner.
 */
final class FrontierV3JournalStore {
    private static final int VERSION = 1, MAGIC = 0x504d4a31;
    private static final int MAX_ROWS = 4_096, MAX_FRAME = 16 * 1024 * 1024;
    private static final long MAX_IMAGE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_RETAINED_BYTES = 64L * 1024 * 1024;
    private static final int SEGMENT_RECORDS = 64, CHECKPOINT_RECORDS = 128;
    private static final ExecutorService CHECKPOINTS = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(4), task -> {
                var thread = new Thread(task, "PM physical checkpoint"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private static final ExecutorService WRITES = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), task -> {
                var thread = new Thread(task, "PM physical journal"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final Executor writer;
    private static final Map<Path, java.lang.ref.WeakReference<Object>> FILE_LOCKS = new HashMap<>();
    private final Object fileLock;
    private final ArrayDeque<Pending> queue = new ArrayDeque<>();
    private long queuedBytes;
    private boolean writerScheduled;
    private volatile IOException appendFailure;
    private final Path checkpoint, journal;
    private final SortedMap<String, byte[]> rows = new TreeMap<>();
    private volatile long sequence, checkpointSequence;
    private long imageBytes;
    private volatile long forcedGroups, appendedBytes;
    private final java.util.concurrent.atomic.AtomicLong retainedBytes = new java.util.concurrent.atomic.AtomicLong();
    private CompletableFuture<Void> pendingCheckpoint;
    private volatile IOException checkpointFailure;
    private Path tornSegment;
    private long tornLength;

    private FrontierV3JournalStore(Path checkpoint, Executor writer) {
        this.writer = Objects.requireNonNull(writer);
        this.checkpoint = checkpoint.toAbsolutePath().normalize();
        synchronized (FILE_LOCKS) {
            FILE_LOCKS.values().removeIf(value -> value.get() == null);
            var previous = FILE_LOCKS.get(this.checkpoint);
            Object retained = previous == null ? null : previous.get();
            fileLock = retained == null ? new Object() : retained;
            FILE_LOCKS.put(this.checkpoint, new java.lang.ref.WeakReference<>(fileLock));
        }
        journal = this.checkpoint.resolveSibling(this.checkpoint.getFileName() + ".journal");
    }

    static FrontierV3JournalStore open(Path checkpoint) throws IOException {
        return open(checkpoint, WRITES);
    }
    static FrontierV3JournalStore open(Path checkpoint, Executor writer) throws IOException {
        var store = new FrontierV3JournalStore(checkpoint, writer);
        synchronized (store.fileLock) {
        if (Files.exists(store.checkpoint)) {
            var root = NbtIo.readCompressed(store.checkpoint, NbtAccounter.create(MAX_RETAINED_BYTES));
            var data = root.getCompound("data");
            if (data.getInt("journalVersion") != VERSION || !data.contains("sequence", Tag.TAG_LONG)
                    || !(data.get("rows") instanceof ListTag values) || values.size() > MAX_ROWS)
                throw new IOException("incompatible physical journal checkpoint; fresh world required");
            store.sequence = data.getLong("sequence");
            if (store.sequence < 0) throw new IOException("negative physical checkpoint sequence");
            for (var raw : values) {
                if (!(raw instanceof CompoundTag row) || !row.contains("key", Tag.TAG_STRING)
                        || !row.contains("value", Tag.TAG_BYTE_ARRAY)
                        || row.getString("key").isEmpty() || row.getString("key").length() > 256
                        || row.getByteArray("value").length > MAX_FRAME
                        || store.rows.putIfAbsent(row.getString("key"), row.getByteArray("value")) != null)
                    throw new IOException("invalid or duplicate physical checkpoint row");
                store.imageBytes += rowBytes(row.getString("key"), row.getByteArray("value"));
                if (store.imageBytes > MAX_IMAGE_BYTES) throw new IOException("physical journal image capacity");
            }
            store.checkpointSequence = store.sequence;
        } else if (Files.exists(store.journal)) {
            throw new IOException("physical journal exists without its checkpoint");
        }
        if (Files.exists(store.journal)) store.replay();
        }
        return store;
    }

    synchronized Map<String, byte[]> image() {
        var copy = new TreeMap<String, byte[]>(); rows.forEach((key, value) -> copy.put(key, value.clone()));
        return Collections.unmodifiableMap(copy);
    }
    record Pressure(long durableSequence, long checkpointSequence, int queuedWrites, long queuedBytes,
                    long retainedBytes, long forcedGroups, long appendedBytes) { }
    synchronized Pressure pressure() {
        return new Pressure(sequence, checkpointSequence, queue.size(), queuedBytes,
                retainedBytes.get(), forcedGroups, appendedBytes);
    }

    /** Required synchronous callers wait for their own ordered receipt, not a background snapshot. */
    long append(Map<String, byte[]> replacements) throws IOException {
        try { return appendAsync(replacements).join(); }
        catch (CompletionException failure) { throw new IOException("physical journal append failed", failure.getCause()); }
    }
    synchronized CompletableFuture<Long> appendAsync(Map<String, byte[]> replacements) throws IOException {
        checkHealthy();
        var next = new TreeMap<String, byte[]>();
        replacements.forEach((key, value) -> {
            if (Objects.requireNonNull(key).isEmpty() || key.length() > 256)
                throw new IllegalArgumentException("invalid physical journal key");
            next.put(key, value == null ? null : value.clone());
        });
        if (next.size() > MAX_ROWS) throw new IOException("physical journal row capacity");
        long bytes = encode(0, next).length + 12L;
        if (bytes > MAX_FRAME || queue.size() >= 64 || queuedBytes + bytes > MAX_FRAME)
            throw new IOException("physical journal admission capacity");
        var pending = new Pending(next, bytes, new CompletableFuture<>());
        queue.add(pending); queuedBytes += bytes;
        if (!writerScheduled) {
            writerScheduled = true;
            try { writer.execute(this::drainWrites); }
            catch (RejectedExecutionException busy) {
                queue.remove(pending); queuedBytes -= bytes; writerScheduled = false;
                throw new IOException("physical journal writer unavailable", busy);
            }
        }
        return pending.receipt();
    }
    void checkHealthy() throws IOException {
        if (appendFailure != null) throw new IOException("physical journal writer failed", appendFailure);
        if (checkpointFailure != null) throw new IOException("physical checkpoint writer failed", checkpointFailure);
    }
    private record Pending(Map<String, byte[]> changes, long bytes, CompletableFuture<Long> receipt) { }
    private void drainWrites() {
        while (true) {
            List<Pending> batch = new ArrayList<>();
            synchronized (this) {
                int room = SEGMENT_RECORDS - (int) (sequence % SEGMENT_RECORDS);
                while (!queue.isEmpty() && batch.size() < Math.min(16, room)) {
                    var next = queue.remove(); queuedBytes -= next.bytes(); batch.add(next);
                }
                if (batch.isEmpty()) { writerScheduled = false; return; }
            }
            try {
                var ids = appendBatch(batch);
                for (int i = 0; i < batch.size(); i++) batch.get(i).receipt().complete(ids.get(i));
            } catch (IOException | RuntimeException failure) {
                var error = failure instanceof IOException io ? io : new IOException("physical journal writer failed", failure);
                synchronized (this) {
                    appendFailure = error;
                    batch.forEach(pending -> pending.receipt().completeExceptionally(error));
                    queue.forEach(pending -> pending.receipt().completeExceptionally(error));
                    queue.clear(); queuedBytes = 0; writerScheduled = false;
                }
                return;
            }
        }
    }
    /** One force covers every retained record in this bounded same-segment group. */
    private List<Long> appendBatch(List<Pending> batch) throws IOException {
        synchronized (fileLock) { return appendLocked(batch); }
    }
    private List<Long> appendLocked(List<Pending> batch) throws IOException {
        checkHealthy();
        var buffers = new ByteArrayOutputStream();
        var ids = new ArrayList<Long>();
        var sizes = new HashMap<String, Long>();
        int count = rows.size(); long id = sequence, bytes = imageBytes;
        for (var pending : batch) {
            for (var entry : pending.changes().entrySet()) {
                long previous = sizes.getOrDefault(entry.getKey(), rowBytes(entry.getKey(), rows.get(entry.getKey())));
                long next = rowBytes(entry.getKey(), entry.getValue());
                if ((previous == 0) != (next == 0)) count += next == 0 ? -1 : 1;
                bytes += next - previous; sizes.put(entry.getKey(), next);
            }
            if (count > MAX_ROWS) throw new IOException("physical journal row capacity");
            if (bytes > MAX_IMAGE_BYTES) throw new IOException("physical journal image capacity");
            if (pending.changes().isEmpty()) { ids.add(id); continue; }
            byte[] payload = encode(++id, pending.changes()); ids.add(id);
            var crc = new CRC32C(); crc.update(payload);
            try (var output = new DataOutputStream(buffers)) {
                output.writeInt(MAGIC); output.writeInt(payload.length); output.writeInt((int) crc.getValue()); output.write(payload);
            }
        }
        if (Files.exists(checkpoint)) {
            if (!Files.isRegularFile(checkpoint)) throw new IOException("physical checkpoint is not a regular file");
        } else {
            if (sequence != 0) throw new IOException("physical checkpoint disappeared from active writer");
            writeCheckpoint(0L, Map.of());
        }
        if (id == sequence) return ids;
        if (retainedBytes.get() + buffers.size() > MAX_RETAINED_BYTES)
            throw new IOException("physical journal checkpoint backpressure exceeded bounded retention");
        boolean newDirectory = !Files.exists(journal);
        Files.createDirectories(journal);
        if (newDirectory) forceDirectory(journal.getParent());
        Path segment = segment(sequence + 1);
        // A torn frame may follow a completely filled segment. Repair that exact
        // unacknowledged tail before opening the successor, not only the next file.
        if (tornSegment != null) {
            try (var channel = FileChannel.open(tornSegment, StandardOpenOption.WRITE)) {
                long tornBytes = channel.size() - tornLength;
                channel.truncate(tornLength); channel.force(true);
                retainedBytes.addAndGet(-tornBytes); tornSegment = null;
            }
        }
        boolean newFile = !Files.exists(segment);
        var frame = ByteBuffer.wrap(buffers.toByteArray());
        try (var channel = FileChannel.open(segment, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            long previous = channel.size(); channel.position(previous);
            try {
                while (frame.hasRemaining()) channel.write(frame);
                channel.force(true);
                if (newFile) forceDirectory(journal);
            } catch (IOException failure) {
                try { channel.truncate(previous); channel.force(true); }
                catch (IOException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
        synchronized (this) {
            for (var pending : batch) pending.changes().forEach((key, value) -> {
                if (value == null) rows.remove(key); else rows.put(key, value);
            });
            sequence = id;
            imageBytes = bytes;
        }
        retainedBytes.addAndGet(buffers.size());
        forcedGroups++; appendedBytes += buffers.size();
        scheduleCheckpoint();
        return ids;
    }

    private synchronized void scheduleCheckpoint() {
        if (sequence - checkpointSequence < CHECKPOINT_RECORDS || pendingCheckpoint != null) return;
        long captured = sequence;
        var immutable = Collections.unmodifiableMap(new TreeMap<>(rows)); // Values are never mutated after publication.
        var completion = new CompletableFuture<Void>(); pendingCheckpoint = completion;
        try {
            CHECKPOINTS.execute(() -> {
                try {
                    // Compression can overlap appends; publication/deletion cannot
                    // race a recovery reader's inventory of the same files.
                    Path staged = stageCheckpoint(captured, immutable);
                    try { synchronized (fileLock) {
                        publishCheckpoint(staged);
                        checkpointSequence = captured;
                        // Never remove the segment receiving concurrent new appends.
                        for (Path path : segments()) {
                            long start = segmentStart(path);
                            if (start + SEGMENT_RECORDS - 1 <= captured) {
                                long bytes = Files.size(path); Files.delete(path); retainedBytes.addAndGet(-bytes);
                            }
                        }
                        forceDirectory(journal);
                    } } finally { Files.deleteIfExists(staged); }
                    completion.complete(null);
                } catch (IOException failure) {
                    checkpointFailure = failure; completion.completeExceptionally(failure);
                } finally { synchronized (this) { pendingCheckpoint = null; } }
            });
        } catch (RejectedExecutionException busy) {
            pendingCheckpoint = null; // Existing forced journal remains authoritative; retry next append.
        }
    }

    /** Orderly shutdown/offline publication can wait; ordinary ticks never wait for checkpoints. */
    void awaitCheckpoint() throws IOException {
        CompletableFuture<Void> pending;
        synchronized (this) { pending = pendingCheckpoint; }
        if (pending != null) {
            try { pending.join(); }
            catch (CompletionException failure) { throw new IOException("physical checkpoint failed", failure.getCause()); }
        }
        if (checkpointFailure != null) throw new IOException("physical checkpoint failed", checkpointFailure);
    }

    private void writeCheckpoint(long captured, Map<String, byte[]> image) throws IOException {
        Path staged = stageCheckpoint(captured, image);
        try { synchronized (fileLock) { publishCheckpoint(staged); } }
        finally { Files.deleteIfExists(staged); }
    }
    private Path stageCheckpoint(long captured, Map<String, byte[]> image) throws IOException {
        Files.createDirectories(checkpoint.getParent());
        var root = new CompoundTag(); var data = new CompoundTag(); var values = new ListTag();
        data.putInt("journalVersion", VERSION); data.putLong("sequence", captured);
        image.forEach((key, value) -> {
            var row = new CompoundTag(); row.putString("key", key); row.putByteArray("value", value); values.add(row);
        });
        data.put("rows", values); root.put("data", data); NbtUtils.addCurrentDataVersion(root);
        Path staged = Files.createTempFile(checkpoint.getParent(), ".physical-checkpoint-", ".tmp");
        try {
            NbtIo.writeCompressed(root, staged);
            try (var channel = FileChannel.open(staged, StandardOpenOption.WRITE)) { channel.force(true); }
            return staged;
        } catch (IOException | RuntimeException failure) { Files.deleteIfExists(staged); throw failure; }
    }
    private void publishCheckpoint(Path staged) throws IOException {
        Files.move(staged, checkpoint, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        forceDirectory(checkpoint.getParent());
    }

    private Path segment(long id) { return journal.resolve(String.format(Locale.ROOT, "%020d.wal", ((id - 1) / SEGMENT_RECORDS) * SEGMENT_RECORDS + 1)); }
    private List<Path> segments() throws IOException {
        try (var paths = Files.list(journal)) {
            var all = paths.limit(65_537).sorted().toList();
            if (all.size() > 65_536) throw new IOException("physical journal segment capacity");
            for (Path path : all) if (!Files.isRegularFile(path) || !path.getFileName().toString().matches("[0-9]{20}\\.wal"))
                throw new IOException("unexpected physical journal entry: " + path);
            return all;
        }
    }
    private static long segmentStart(Path path) throws IOException {
        try { return Long.parseLong(path.getFileName().toString().substring(0, 20)); }
        catch (RuntimeException invalid) { throw new IOException("invalid physical journal segment", invalid); }
    }
    private void replay() throws IOException {
        var files = segments();
        for (int index = 0; index < files.size(); index++) {
            Path path = files.get(index); boolean last = index == files.size() - 1;
            long start = segmentStart(path);
            if (start < 1 || (start - 1) % SEGMENT_RECORDS != 0) throw new IOException("invalid physical journal segment sequence");
            if (start + SEGMENT_RECORDS - 1 <= checkpointSequence) continue;
            try (var channel = FileChannel.open(path, StandardOpenOption.READ)) {
                long valid = 0; int records = 0;
                while (channel.position() < channel.size()) {
                    var header = ByteBuffer.allocate(12);
                    if (!readFully(channel, header)) { retainTornTail(path, valid, last); break; }
                    header.flip();
                    if (header.getInt() != MAGIC) throw new IOException("physical journal frame magic mismatch");
                    int length = header.getInt(), checksum = header.getInt();
                    if (length < 12 || length > MAX_FRAME) throw new IOException("physical journal frame length");
                    var content = ByteBuffer.allocate(length);
                    if (!readFully(channel, content)) { retainTornTail(path, valid, last); break; }
                    var crc = new CRC32C(); crc.update(content.array());
                    if ((int) crc.getValue() != checksum) throw new IOException("physical journal checksum mismatch");
                    try (var input = new DataInputStream(new ByteArrayInputStream(content.array()))) {
                        long id = input.readLong();
                        if (id != start + records++ || records > SEGMENT_RECORDS) throw new IOException("physical journal segment order");
                        var changes = decode(input);
                        if (id > sequence) {
                            if (id != sequence + 1) throw new IOException("physical journal sequence gap");
                            changes.forEach((key, value) -> {
                                imageBytes += rowBytes(key, value) - rowBytes(key, rows.get(key));
                                if (value == null) rows.remove(key); else rows.put(key, value);
                            });
                            if (rows.size() > MAX_ROWS) throw new IOException("physical journal row capacity");
                            if (imageBytes > MAX_IMAGE_BYTES) throw new IOException("physical journal image capacity");
                            sequence = id;
                        }
                    }
                    valid = channel.position();
                }
                if (!last && records != SEGMENT_RECORDS) throw new IOException("incomplete nonterminal physical journal segment");
                retainedBytes.addAndGet(channel.size());
                if (retainedBytes.get() > MAX_RETAINED_BYTES) throw new IOException("physical journal retention capacity");
            }
        }
    }
    private static boolean readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) if (channel.read(buffer) < 0) return false;
        return true;
    }
    private void retainTornTail(Path path, long valid, boolean last) throws IOException {
        if (!last) throw new IOException("torn nonterminal physical journal frame");
        tornSegment = path; tornLength = valid; // Inspection is read-only; the next authorized append repairs its own tail.
    }
    private static byte[] encode(long id, Map<String, byte[]> changes) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeLong(id); output.writeInt(changes.size());
            for (var entry : changes.entrySet()) {
                output.writeUTF(entry.getKey()); byte[] value = entry.getValue();
                output.writeInt(value == null ? -1 : value.length); if (value != null) output.write(value);
            }
        }
        return bytes.toByteArray();
    }
    private static Map<String, byte[]> decode(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 1 || count > MAX_ROWS) throw new IOException("physical journal change count");
        var changes = new TreeMap<String, byte[]>();
        for (int i = 0; i < count; i++) {
            String key = input.readUTF(); int length = input.readInt();
            if (key.isEmpty() || key.length() > 256 || changes.containsKey(key) || length < -1 || length > MAX_FRAME)
                throw new IOException("invalid physical journal change");
            byte[] value = length == -1 ? null : input.readNBytes(length);
            if (value != null && value.length != length) throw new EOFException("truncated physical journal row");
            changes.put(key, value);
        }
        if (input.read() != -1) throw new IOException("physical journal trailing payload");
        return changes;
    }
    private static void forceDirectory(Path directory) throws IOException {
        try (var channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
    }
    private static long rowBytes(String key, byte[] value) {
        return value == null ? 0L : value.length + 3L * key.length() + 64L;
    }
}
