package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.internal.frontier.v3.ExperimentSealedWalReader;
import java.nio.file.*;
import java.util.*;

/** Bounded offline replay. No state/notification escapes before the complete
 * normal kernel replay and its final encoding/validation have succeeded. */
final class CommittedTradeCorpus {
    static final int LIMIT = 512;
    record Frame(long revision, long tick, FrontierWorldState state) { }
    record Corpus(List<Frame> frames, List<TransactionRecord> transactions, Path snapshot,
                  long covered, int towns, long replayNanos) { }
    static Corpus read(Path directory) throws Exception {
        var files = new ArrayList<Path>();
        try (var entries = Files.list(directory)) { entries.sorted().forEach(files::add); }
        var snapshotPath = files.stream().filter(p -> p.getFileName().toString().startsWith("snapshot-")).findFirst().orElseThrow();
        if (files.stream().filter(p -> p.getFileName().toString().startsWith("snapshot-")).count() != 1)
            throw new IllegalArgumentException("exactly one captured snapshot required");
        long start = System.nanoTime();
        var snapshot = FrontierPersistenceCodec.decodeSnapshot(Files.readAllBytes(snapshotPath));
        var checkpoint = snapshot.checkpoint(); var codec = new FrontierWorldStateCodec();
        var state = codec.decode(checkpoint.canonicalState());
        var transactions = new ArrayList<TransactionRecord>(); long expected = snapshot.coveredWalSequence() + 1;
        for (var path : files) {
            var name = path.getFileName().toString();
            if (!name.startsWith("wal-segment-")) continue;
            long first = Long.parseLong(name.substring(12, name.length() - 4));
            for (var frame : ExperimentSealedWalReader.read(path, first)) {
                if (frame.sequence() <= snapshot.coveredWalSequence() || transactions.size() == LIMIT) continue;
                if (frame.sequence() != expected++) throw new IllegalArgumentException("non-contiguous captured WAL");
                transactions.add(FrontierPersistenceCodec.decodeWal(frame.payload(), FrontierWorldRuntimeDefinition.payloadCodecs()));
            }
        }
        if (transactions.size() != LIMIT) throw new IllegalArgumentException("insufficient captured transactions");
        // Private provisional images. Each canonical transaction can have several
        // events; retain only its final reducer image. Schedule-only records reuse
        // the preceding image. Nothing is observable until replay returns below.
        var provisional = new HashMap<Long, FrontierWorldState>();
        var replay = TransactionReplayer.replayFrom(checkpoint.worldId(), state, checkpoint.revision(), checkpoint.instant(),
                checkpoint.schedules(), transactions, (before, event) -> {
                    var after = FrontierWorldRuntimeDefinition.reduce(before, event);
                    provisional.put(event.revision().value(), after); return after;
                }, codec, FrontierWorldStateTransitionValidator.INSTANCE);
        var frames = new ArrayList<Frame>();
        for (var tx : transactions) {
            state = provisional.getOrDefault(tx.revision().value(), state);
            frames.add(new Frame(tx.revision().value(), tx.instant().ticks(), state));
        }
        if (frames.getLast().state() != replay.state()) throw new AssertionError("replay capture lost final image");
        int towns = state.companies().goodsTrade().participants().participants().size();
        return new Corpus(List.copyOf(frames), List.copyOf(transactions), snapshotPath,
                snapshot.coveredWalSequence(), towns, System.nanoTime() - start);
    }
}
