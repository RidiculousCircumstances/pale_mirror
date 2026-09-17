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
public final class FrontierFileStore implements FrontierStore {
    private static final Pattern SNAPSHOT = Pattern.compile("snapshot-(\\d{20})\\.bin");
    private static final Pattern WAL = Pattern.compile("wal-(\\d{20})\\.bin");
    /**
     * A canonical transaction is one server-turn boundary.  This store uses one immutable WAL
     * file per transaction, so a larger "batch" does not coalesce a single fsync: the eventual
     * flush has to force every preceding file on whichever ordinary turn reaches the threshold.
     * That deferred 64-file flush was the measured multi-hundred-millisecond server-thread
     * tail during COLD continuation.  Keep the durable tail to one record instead: it preserves
     * the same ordered WAL/recovery contract and bounds one turn to one file force rather than
     * transferring accumulated I/O to an unrelated exact worker or physical release.
     */
    private static final int MAX_BATCHABLE_WAL_RECORDS = 1;
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
    static int maxBatchableWalRecords() { return MAX_BATCHABLE_WAL_RECORDS; }

    @Override public synchronized RecoveryImage recover(WorldId worldId) {
        Path directory = worldDirectory(worldId);
        if (!Files.exists(directory)) {
            RecoveryImage empty = new RecoveryImage(worldId, java.util.Optional.empty(), List.of());
            durableHeads.put(worldId, DurableHead.from(empty)); recoveredImages.put(worldId, empty);
            return empty;
        }
        try {
            List<NumberedPath> snapshots = entries(directory, SNAPSHOT);
            SnapshotRecord snapshot = snapshots.isEmpty() ? null : FrontierPersistenceCodec.decodeSnapshot(Files.readAllBytes(snapshots.getLast().path()));
            if (snapshot != null && !worldId.equals(snapshot.checkpoint().worldId())) throw new IllegalStateException("v3 snapshot world mismatch");
            long covered = snapshot == null ? 0L : snapshot.coveredWalSequence();
            List<NumberedPath> wal = entries(directory, WAL);
            long expectedSequence = covered + 1L;
            Revision expectedRevision = snapshot == null ? Revision.ZERO : snapshot.checkpoint().revision();
            java.util.ArrayList<TransactionRecord> tail = new java.util.ArrayList<>();
            for (NumberedPath entry : wal) {
                if (entry.sequence() <= covered) continue;
                if (entry.sequence() != expectedSequence++) throw new IllegalStateException("v3 WAL sequence gap");
                TransactionRecord transaction = FrontierPersistenceCodec.decodeWal(Files.readAllBytes(entry.path()), payloadCodecs);
                if (!worldId.equals(transaction.worldId()) || !transaction.revision().equals(expectedRevision.next())) {
                    throw new IllegalStateException("v3 WAL transaction sequence mismatch");
                }
                expectedRevision = transaction.revision();
                tail.add(transaction);
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
        writeAtomically(worldDirectory(worldId).resolve(fileName("wal", sequence)), FrontierPersistenceCodec.encodeWal(transaction, payloadCodecs), false);
        DurableHead appended = head.appended(sequence, transaction.revision());
        if (durability == Durability.DURABLE_BEFORE_EFFECT) {
            // A durable physical boundary depends on the complete preceding canonical history,
            // not merely its own record.  Flush the bounded BATCHABLE tail before returning the
            // receipt that permits an executor to observe or invoke an external effect.
            flushWalThrough(worldId, appended.flushedSequence(), sequence);
            appended = appended.flushedThrough(sequence);
        } else if (sequence - appended.flushedSequence() >= MAX_BATCHABLE_WAL_RECORDS) {
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
        writeAtomically(worldDirectory(worldId).resolve(fileName("snapshot", head.lastSequence())), FrontierPersistenceCodec.encodeSnapshot(snapshot), true);
        durableHeads.put(worldId, head.withSnapshot(head.lastSequence(), head.revision()));
        recoveredImages.put(worldId, new RecoveryImage(worldId, java.util.Optional.of(snapshot), List.of()));
        return new SnapshotReceipt(snapshot.checkpoint().revision(), head.lastSequence());
    }

    @Override public synchronized CompactionReceipt compact(WorldId worldId, Revision coveredRevision) {
        DurableHead head = head(worldId);
        if (head.snapshotSequence() != head.lastSequence() || !head.snapshotRevision().equals(coveredRevision)
                || !head.revision().equals(coveredRevision)) throw new IllegalStateException("snapshot does not cover all retained WAL");
        try {
            for (NumberedPath entry : entries(worldDirectory(worldId), WAL)) if (entry.sequence() <= head.snapshotSequence()) Files.delete(entry.path());
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
            for (long sequence = Math.addExact(alreadyFlushed, 1L); sequence <= throughSequence; sequence++) {
                Path path = worldDirectory(worldId).resolve(fileName("wal", sequence));
                try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
            }
        } catch (IOException error) {
            throw new IllegalStateException("unable to flush Frontier v3 WAL batch", error);
        }
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
