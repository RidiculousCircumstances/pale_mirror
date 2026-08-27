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
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** NeoForge-owned filesystem host for the pure v3 store contract. No legacy SavedData is touched. */
public final class FrontierFileStore implements FrontierStore {
    private static final Pattern SNAPSHOT = Pattern.compile("snapshot-(\\d{20})\\.bin");
    private static final Pattern WAL = Pattern.compile("wal-(\\d{20})\\.bin");
    private final Path baseDirectory;
    private final PayloadCodecs payloadCodecs;

    public FrontierFileStore(Path baseDirectory, PayloadCodecs payloadCodecs) {
        this.baseDirectory = Objects.requireNonNull(baseDirectory, "base directory");
        this.payloadCodecs = Objects.requireNonNull(payloadCodecs, "payload codecs");
    }

    @Override public synchronized RecoveryImage recover(WorldId worldId) {
        Path directory = worldDirectory(worldId);
        if (!Files.exists(directory)) return new RecoveryImage(worldId, java.util.Optional.empty(), List.of());
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
            return new RecoveryImage(worldId, java.util.Optional.ofNullable(snapshot), tail);
        } catch (IOException error) { throw new IllegalStateException("unable to recover Frontier v3 store", error); }
    }

    @Override public synchronized AppendReceipt append(TransactionRecord transaction, Durability durability) {
        Objects.requireNonNull(transaction, "transaction"); Objects.requireNonNull(durability, "durability");
        RecoveryImage recovered = recover(transaction.worldId());
        Revision current = recovered.walTail().isEmpty()
                ? recovered.checkpoint().map(value -> value.checkpoint().revision()).orElse(Revision.ZERO)
                : recovered.walTail().getLast().revision();
        if (!transaction.revision().equals(current.next())) throw new IllegalStateException("WAL append revision is not next");
        long sequence = recovered.checkpoint().map(SnapshotRecord::coveredWalSequence).orElse(0L) + recovered.walTail().size() + 1L;
        writeAtomically(worldDirectory(transaction.worldId()).resolve(fileName("wal", sequence)), FrontierPersistenceCodec.encodeWal(transaction, payloadCodecs));
        return new AppendReceipt(transaction.id(), transaction.revision(), durability, sequence);
    }

    @Override public synchronized SnapshotReceipt installSnapshot(SnapshotRecord snapshot) {
        RecoveryImage recovered = recover(snapshot.checkpoint().worldId());
        long lastSequence = recovered.checkpoint().map(SnapshotRecord::coveredWalSequence).orElse(0L) + recovered.walTail().size();
        Revision current = recovered.walTail().isEmpty() ? recovered.checkpoint().map(value -> value.checkpoint().revision()).orElse(Revision.ZERO) : recovered.walTail().getLast().revision();
        if (snapshot.coveredWalSequence() != lastSequence || !snapshot.checkpoint().revision().equals(current)) throw new IllegalStateException("snapshot does not cover current WAL state");
        writeAtomically(worldDirectory(snapshot.checkpoint().worldId()).resolve(fileName("snapshot", lastSequence)), FrontierPersistenceCodec.encodeSnapshot(snapshot));
        return new SnapshotReceipt(snapshot.checkpoint().revision(), lastSequence);
    }

    @Override public synchronized CompactionReceipt compact(WorldId worldId, Revision coveredRevision) {
        RecoveryImage recovered = recover(worldId);
        SnapshotRecord snapshot = recovered.checkpoint().orElseThrow(() -> new IllegalStateException("cannot compact without snapshot"));
        if (!snapshot.checkpoint().revision().equals(coveredRevision) || !recovered.walTail().isEmpty()) throw new IllegalStateException("snapshot does not cover all retained WAL");
        try {
            for (NumberedPath entry : entries(worldDirectory(worldId), WAL)) if (entry.sequence() <= snapshot.coveredWalSequence()) Files.delete(entry.path());
            return new CompactionReceipt(coveredRevision, 0L);
        } catch (IOException error) { throw new IllegalStateException("unable to compact Frontier v3 WAL", error); }
    }

    private Path worldDirectory(WorldId worldId) { return baseDirectory.resolve("frontier-v3").resolve(worldId.value().replace(':', '_')); }
    private static String fileName(String prefix, long sequence) { return "%s-%020d.bin".formatted(prefix, sequence); }
    private static List<NumberedPath> entries(Path directory, Pattern pattern) throws IOException {
        if (!Files.exists(directory)) return List.of();
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.map(path -> numbered(path, pattern)).filter(Objects::nonNull).sorted(Comparator.comparingLong(NumberedPath::sequence)).toList();
        }
    }
    private static NumberedPath numbered(Path path, Pattern pattern) { Matcher match = pattern.matcher(path.getFileName().toString()); return match.matches() ? new NumberedPath(Long.parseLong(match.group(1)), path) : null; }
    private static void writeAtomically(Path target, byte[] bytes) {
        try {
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            // A prior crash can leave only this uncommitted candidate. It is never considered by
            // recovery, so removing it before creating a new candidate cannot discard a fact.
            Files.deleteIfExists(temporary);
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { throw new IllegalStateException("atomic filesystem move is required for Frontier v3", unsupported); }
        } catch (IOException error) { throw new IllegalStateException("unable to atomically write Frontier v3 record", error); }
    }
    private record NumberedPath(long sequence, Path path) {}
}
