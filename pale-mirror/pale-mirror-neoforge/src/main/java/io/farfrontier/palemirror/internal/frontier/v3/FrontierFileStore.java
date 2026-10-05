package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierPersistenceCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** NeoForge-owned filesystem host for the pure v3 store contract. No legacy SavedData is touched. */
public final class FrontierFileStore implements FrontierStore, AutoCloseable {
    private static final Pattern SNAPSHOT = Pattern.compile("snapshot-(\\d{20})\\.bin");
    private static final Pattern WAL = Pattern.compile("wal-segment-(\\d{20})\\.bin");
    /** One shared segment force, never a deferred force per old transaction file. */
    private static final int MAX_BATCHABLE_WAL_RECORDS = 64;
    private static final byte[] FORMAT = "PM-FRONTIER-STORE-2".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private final Map<WorldId, FrontierWalSegment> activeSegments = new HashMap<>();
    private final java.util.Set<Path> formattedDirectories = new java.util.HashSet<>();
    private int turnDepth;
    private final Path baseDirectory;
    private final PayloadCodecs payloadCodecs;
    /**
     * The server owns one store on one thread.  Retaining its already-verified image avoids a
     * directory scan and full snapshot decode at every periodic checkpoint; a new store created
     * for restart still performs the complete on-disk recovery below.
     */
    private final Map<WorldId, RecoveryImage> recoveredImages = new HashMap<>();
    /**
     * Per-store ordered WAL head, populated by complete disk recovery.  It separately records
     * the last fsynced WAL sequence so BATCHABLE progression has a real bounded batch boundary,
     * while a physical effect can flush every preceding canonical fact before it is released.
     */
    private final Map<WorldId, DurableHead> durableHeads = new HashMap<>();

    public FrontierFileStore(Path baseDirectory, PayloadCodecs payloadCodecs) {
        this.baseDirectory = Objects.requireNonNull(baseDirectory, "base directory");
        this.payloadCodecs = Objects.requireNonNull(payloadCodecs, "payload codecs");
    }

    /** Package seam for the bounded server-turn durability contract. */
    synchronized long flushedSequence(WorldId world) { return head(world).flushedSequence(); }

    @Override public synchronized RecoveryImage recover(WorldId worldId) {
        Path directory = worldDirectory(worldId);
        if (!Files.exists(directory)) {
            RecoveryImage empty = new RecoveryImage(worldId, java.util.Optional.empty(), List.of());
            durableHeads.put(worldId, DurableHead.from(empty)); recoveredImages.put(worldId, empty);
            return empty;
        }
        try {
            verifyFormat(directory);
            List<NumberedPath> snapshots = entries(directory, SNAPSHOT);
            SnapshotRecord snapshot = snapshots.isEmpty() ? null : FrontierPersistenceCodec.decodeSnapshot(Files.readAllBytes(snapshots.getLast().path()));
            if (snapshot != null && !worldId.equals(snapshot.checkpoint().worldId())) throw new IllegalStateException("v3 snapshot world mismatch");
            if (snapshot != null && snapshot.coveredWalSequence() != snapshots.getLast().sequence())
                throw new IllegalStateException("v3 snapshot filename sequence mismatch");
            long covered = snapshot == null ? 0L : snapshot.coveredWalSequence();
            List<NumberedPath> wal = entries(directory, WAL);
            long expectedSequence = covered + 1L;
            Revision expectedRevision = snapshot == null ? Revision.ZERO : snapshot.checkpoint().revision();
            java.util.ArrayList<TransactionRecord> tail = new java.util.ArrayList<>();
            for (int index = 0; index < wal.size(); index++) {
                NumberedPath entry = wal.get(index);
                var segment = FrontierWalSegment.read(entry.path(), entry.sequence(), index == wal.size() - 1);
                for (var frame : segment.frames()) {
                if (frame.sequence() <= covered) continue;
                if (frame.sequence() != expectedSequence++) throw new IllegalStateException("v3 WAL sequence gap");
                TransactionRecord transaction = FrontierPersistenceCodec.decodeWal(frame.payload(), payloadCodecs);
                if (!worldId.equals(transaction.worldId()) || !transaction.revision().equals(expectedRevision.next())) {
                    throw new IllegalStateException("v3 WAL transaction sequence mismatch");
                }
                expectedRevision = transaction.revision();
                tail.add(transaction);
                }
            }
            RecoveryImage image = new RecoveryImage(worldId, java.util.Optional.ofNullable(snapshot), tail);
            DurableHead recoveredHead = DurableHead.from(image);
            DurableHead previousHead = durableHeads.get(worldId);
            // A diagnostic caller may request a full read while this server still owns an
            // unflushed BATCHABLE tail.  Reading valid bytes does not upgrade their flush
            // boundary; retain that in-process fact so the next physical effect still flushes
            // the complete prefix.
            if (previousHead != null) recoveredHead = recoveredHead.retainingKnownFlush(previousHead);
            durableHeads.put(worldId, recoveredHead); recoveredImages.put(worldId, image);
            return image;
        } catch (IOException error) { throw new IllegalStateException("unable to recover Frontier v3 store", error); }
    }

    /**
     * Owning server-thread checkpoint path.  Public {@link #recover(WorldId)} remains a full
     * disk verification API for restart and corruption tests; only the single owner that has
     * already established this store's durable head may reuse its verified image.
     */
    synchronized RecoveryImage recoverOwned(WorldId worldId) {
        RecoveryImage cached = recoveredImages.get(worldId);
        return cached == null ? recover(worldId) : cached;
    }

    @Override public synchronized AppendReceipt append(TransactionRecord transaction, Durability durability) {
        Objects.requireNonNull(transaction, "transaction"); Objects.requireNonNull(durability, "durability");
        DurableHead head = head(transaction.worldId());
        if (!transaction.revision().equals(head.revision().next())) throw new IllegalStateException("WAL append revision is not next");
        long sequence = Math.addExact(head.lastSequence(), 1L);
        WorldId worldId = transaction.worldId();
        appendSegment(worldId, sequence, FrontierPersistenceCodec.encodeWal(transaction, payloadCodecs));
        DurableHead appended = head.appended(sequence, transaction.revision());
        if (durability == Durability.DURABLE_BEFORE_EFFECT) {
            // A durable physical boundary depends on the complete preceding canonical history,
            // not merely its own record.  Flush the bounded BATCHABLE tail before returning the
            // receipt that permits an executor to observe or invoke an external effect.
            flushWalThrough(worldId, appended.flushedSequence(), sequence);
            appended = appended.flushedThrough(sequence);
        } else if (turnDepth == 0 || sequence - appended.flushedSequence() >= MAX_BATCHABLE_WAL_RECORDS) {
            flushWalThrough(worldId, appended.flushedSequence(), sequence);
            appended = appended.flushedThrough(sequence);
        }
        durableHeads.put(worldId, appended);
        RecoveryImage image = Objects.requireNonNull(recoveredImages.get(transaction.worldId()), "recovered store image");
        java.util.ArrayList<TransactionRecord> tail = new java.util.ArrayList<>(image.walTail()); tail.add(transaction);
        recoveredImages.put(transaction.worldId(), new RecoveryImage(transaction.worldId(), image.checkpoint(), List.copyOf(tail)));
        return new AppendReceipt(transaction.id(), transaction.revision(), durability, sequence);
    }

    @Override public synchronized SnapshotReceipt installSnapshot(SnapshotRecord snapshot) {
        WorldId worldId = snapshot.checkpoint().worldId(); DurableHead head = head(worldId);
        if (snapshot.coveredWalSequence() != head.lastSequence() || !snapshot.checkpoint().revision().equals(head.revision())) {
            throw new IllegalStateException("snapshot does not cover current WAL state");
        }
        try { ensureFormat(worldDirectory(worldId)); }
        catch (IOException failure) { throw new IllegalStateException("unable to initialize snapshot store", failure); }
        writeAtomically(worldDirectory(worldId).resolve(fileName("snapshot", head.lastSequence())), FrontierPersistenceCodec.encodeSnapshot(snapshot), true);
        closeSegment(worldId);
        durableHeads.put(worldId, head.withSnapshot(head.lastSequence(), head.revision()));
        recoveredImages.put(worldId, new RecoveryImage(worldId, java.util.Optional.of(snapshot), List.of()));
        return new SnapshotReceipt(snapshot.checkpoint().revision(), head.lastSequence());
    }

    @Override public synchronized CompactionReceipt compact(WorldId worldId, Revision coveredRevision) {
        DurableHead head = head(worldId);
        if (head.snapshotSequence() != head.lastSequence() || !head.snapshotRevision().equals(coveredRevision)
                || !head.revision().equals(coveredRevision)) throw new IllegalStateException("snapshot does not cover all retained WAL");
        try {
            var segments = entries(worldDirectory(worldId), WAL);
            for (int index = 0; index < segments.size(); index++) {
                var entry = segments.get(index);
                var image = FrontierWalSegment.read(entry.path(), entry.sequence(), index == segments.size() - 1);
                if (image.frames().isEmpty() || image.frames().getLast().sequence() <= head.snapshotSequence()) Files.delete(entry.path());
            }
            for (var entry : entries(worldDirectory(worldId), SNAPSHOT))
                if (entry.sequence() < head.snapshotSequence()) Files.delete(entry.path());
            forceDirectory(worldDirectory(worldId));
            return new CompactionReceipt(coveredRevision, 0L);
        } catch (IOException error) { throw new IllegalStateException("unable to compact Frontier v3 WAL", error); }
    }

    private DurableHead head(WorldId worldId) {
        DurableHead head = durableHeads.get(worldId);
        if (head != null) return head;
        recover(worldId);
        return Objects.requireNonNull(durableHeads.get(worldId), "recovered durable head");
    }

    private Path worldDirectory(WorldId worldId) { return baseDirectory.resolve("frontier-v3").resolve(worldId.value().replace(':', '_')); }
    private static String fileName(String prefix, long sequence) { return "%s-%020d.bin".formatted(prefix, sequence); }
    private void flushWalThrough(WorldId worldId, long alreadyFlushed, long throughSequence) {
        try {
            if (throughSequence > alreadyFlushed) Objects.requireNonNull(activeSegments.get(worldId), "active WAL prefix").force();
        } catch (IOException error) {
            throw new IllegalStateException("unable to flush Frontier v3 WAL batch", error);
        }
    }

    /** BATCHABLE records share one force at turn exit; irreversible effects flush immediately. */
    synchronized void beginTurn() { turnDepth = Math.addExact(turnDepth, 1); }
    synchronized void endTurn() {
        if (turnDepth < 1) throw new IllegalStateException("unbalanced persistence turn");
        if (--turnDepth != 0) return;
        for (var world : List.copyOf(durableHeads.keySet())) {
            var head = durableHeads.get(world);
            flushWalThrough(world, head.flushedSequence(), head.lastSequence());
            durableHeads.put(world, head.flushedThrough(head.lastSequence()));
        }
    }

    private void appendSegment(WorldId world, long sequence, byte[] bytes) {
        try {
            if (bytes.length > FrontierWalSegment.MAX_RECORD_BYTES) throw new IllegalArgumentException("oversized WAL record");
            Path directory = worldDirectory(world); ensureFormat(directory);
            var active = activeSegments.get(world);
            if (active == null) {
                var paths = entries(directory, WAL);
                if (!paths.isEmpty()) {
                    var last = paths.getLast();
                    var image = FrontierWalSegment.read(last.path(), last.sequence(), true);
                    long end = last.sequence() + image.frames().size();
                    if (end == sequence) active = FrontierWalSegment.resume(last.path(), image);
                }
            }
            if (active != null && !active.fits(bytes.length)) { active.force(); active.close(); active = null; }
            if (active == null) {
                active = FrontierWalSegment.create(directory.resolve(fileName("wal-segment", sequence)), sequence);
                forceDirectory(directory);
            }
            activeSegments.put(world, active);
            active.append(sequence, bytes);
        } catch (IOException failure) { throw new IllegalStateException("unable to append WAL segment", failure); }
    }

    private void closeSegment(WorldId world) {
        var active = activeSegments.remove(world);
        if (active != null) try { active.close(); }
        catch (IOException failure) { throw new IllegalStateException("unable to close WAL segment", failure); }
    }

    @Override public synchronized void close() {
        RuntimeException failure = null;
        for (var world : List.copyOf(activeSegments.keySet())) {
            var segment = activeSegments.remove(world);
            try { segment.force(); }
            catch (IOException error) {
                if (failure == null) failure = new IllegalStateException("unable to flush closing WAL segment", error);
                else failure.addSuppressed(error);
            }
            finally {
                try { segment.close(); }
                catch (IOException error) {
                    if (failure == null) failure = new IllegalStateException("unable to close WAL segment", error);
                    else failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) throw failure;
    }

    private static void verifyFormat(Path directory) throws IOException {
        Path marker = directory.resolve("store-format.bin");
        if (Files.exists(marker)) {
            if (Files.size(marker) != FORMAT.length || !java.util.Arrays.equals(Files.readAllBytes(marker), FORMAT))
                throw new IllegalStateException("unsupported Frontier store format");
        } else try (var paths = Files.list(directory)) {
            if (paths.anyMatch(path -> path.getFileName().toString().endsWith(".bin")))
                throw new IllegalStateException("old Frontier store requires a fresh world");
        }
    }

    private void ensureFormat(Path directory) throws IOException {
        if (formattedDirectories.contains(directory)) return;
        var missing = new java.util.ArrayList<Path>();
        for (Path ancestor = directory.toAbsolutePath(); !Files.exists(ancestor); ancestor = ancestor.getParent()) missing.add(ancestor);
        Files.createDirectories(directory);
        // A forced record is useless if the new world-store directory itself vanishes on crash.
        // Publish newly created directory links from their existing ancestor downward once.
        for (int index = missing.size() - 1; index >= 0; index--) forceDirectory(missing.get(index).getParent());
        verifyFormat(directory);
        Path marker = directory.resolve("store-format.bin");
        if (!Files.exists(marker)) writeAtomically(marker, FORMAT, true);
        formattedDirectories.add(directory);
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
    }
    private static List<NumberedPath> entries(Path directory, Pattern pattern) throws IOException {
        if (!Files.exists(directory)) return List.of();
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.map(path -> numbered(path, pattern)).filter(Objects::nonNull).sorted(Comparator.comparingLong(NumberedPath::sequence)).toList();
        }
    }
    private static NumberedPath numbered(Path path, Pattern pattern) { Matcher match = pattern.matcher(path.getFileName().toString()); return match.matches() ? new NumberedPath(Long.parseLong(match.group(1)), path) : null; }
    private static void writeAtomically(Path target, byte[] bytes, boolean force) {
        try {
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            // A prior crash can leave only this uncommitted candidate. It is never considered by
            // recovery, so removing it before creating a new candidate cannot discard a fact.
            Files.deleteIfExists(temporary);
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            if (force) try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            // A snapshot may advance canonical time without a new WAL record. Replacing the
            // same covered sequence is safe only as one atomic publication: recovery sees either
            // the old verified checkpoint or the complete new one, never an absent checkpoint.
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { throw new IllegalStateException("atomic filesystem move is required for Frontier v3", unsupported); }
            if (force) forceDirectory(target.getParent());
        } catch (IOException error) { throw new IllegalStateException("unable to atomically write Frontier v3 record", error); }
    }
    private record DurableHead(long lastSequence, Revision revision, long snapshotSequence, Revision snapshotRevision, long flushedSequence) {
        DurableHead {
            if (lastSequence < 0L || snapshotSequence < 0L || snapshotSequence > lastSequence
                    || flushedSequence < snapshotSequence || flushedSequence > lastSequence) {
                throw new IllegalArgumentException("invalid Frontier durable head sequence");
            }
            Objects.requireNonNull(revision, "revision"); Objects.requireNonNull(snapshotRevision, "snapshot revision");
        }
        static DurableHead from(RecoveryImage image) {
            SnapshotRecord snapshot = image.checkpoint().orElse(null);
            long covered = snapshot == null ? 0L : snapshot.coveredWalSequence();
            Revision snapshotRevision = snapshot == null ? Revision.ZERO : snapshot.checkpoint().revision();
            Revision revision = image.walTail().isEmpty() ? snapshotRevision : image.walTail().getLast().revision();
            long lastSequence = Math.addExact(covered, image.walTail().size());
            return new DurableHead(lastSequence, revision, covered, snapshotRevision, lastSequence);
        }
        DurableHead appended(long sequence, Revision nextRevision) {
            if (sequence != Math.addExact(lastSequence, 1L) || !nextRevision.equals(revision.next())) {
                throw new IllegalArgumentException("non-contiguous Frontier durable append");
            }
            return new DurableHead(sequence, nextRevision, snapshotSequence, snapshotRevision, flushedSequence);
        }
        DurableHead flushedThrough(long sequence) {
            if (sequence < flushedSequence || sequence > lastSequence) throw new IllegalArgumentException("invalid Frontier WAL flush sequence");
            return new DurableHead(lastSequence, revision, snapshotSequence, snapshotRevision, sequence);
        }
        DurableHead withSnapshot(long sequence, Revision nextSnapshotRevision) {
            if (sequence != lastSequence || !nextSnapshotRevision.equals(revision)) {
                throw new IllegalArgumentException("Frontier snapshot does not cover durable head");
            }
            return new DurableHead(lastSequence, revision, sequence, nextSnapshotRevision, sequence);
        }
        DurableHead retainingKnownFlush(DurableHead previous) {
            if (lastSequence != previous.lastSequence || !revision.equals(previous.revision)
                    || snapshotSequence != previous.snapshotSequence || !snapshotRevision.equals(previous.snapshotRevision)) {
                return this;
            }
            return new DurableHead(lastSequence, revision, snapshotSequence, snapshotRevision,
                    Math.min(flushedSequence, previous.flushedSequence));
        }
    }
    private record NumberedPath(long sequence, Path path) {}
}
