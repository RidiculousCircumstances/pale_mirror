package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test-fixture-only bounded WAL forensic reader for a retained disposable world.
 *
 * <p>It has no store, engine, world or mutation capability. The native failure runner invokes
 * it only after a failed run to expose the last attributable durable transactions as JSONL.</p>
 */
public final class FrontierV3WalTailDiagnostic {
    private static final Pattern WAL = Pattern.compile("wal-(\\d{20})\\.bin");
    private static final int MAX_RECORDS = 16;

    private FrontierV3WalTailDiagnostic() { }

    public static List<Entry> decode(Path directory) {
        return decode(directory, FrontierWorldRuntimeDefinition.payloadCodecs(), MAX_RECORDS);
    }

    static List<Entry> decode(Path directory, PayloadCodecs codecs, int maximum) {
        if (maximum < 1 || maximum > MAX_RECORDS) throw new IllegalArgumentException("bounded WAL diagnostic record count");
        try {
            if (!Files.isDirectory(directory)) return List.of();
            try (var paths = Files.list(directory)) {
                List<NumberedPath> records = paths.map(FrontierV3WalTailDiagnostic::numbered)
                        .filter(java.util.Objects::nonNull).sorted(Comparator.comparingLong(NumberedPath::sequence)).toList();
                int first = Math.max(0, records.size() - maximum);
                return records.subList(first, records.size()).stream().map(record -> entry(record, codecs)).toList();
            }
        } catch (IOException failure) {
            throw new IllegalStateException("unable to read bounded Frontier v3 WAL tail", failure);
        }
    }

    public static void main(String[] arguments) {
        if (arguments.length != 1) throw new IllegalArgumentException("usage: FrontierV3WalTailDiagnostic <frontier-v3-world-directory>");
        for (Entry entry : decode(Path.of(arguments[0]))) System.out.println("PMV3_WAL_TAIL " + entry.json());
    }

    private static Entry entry(NumberedPath record, PayloadCodecs codecs) {
        try {
            TransactionRecord transaction = FrontierPersistenceCodec.decodeWal(Files.readAllBytes(record.path()), codecs);
            List<String> payloadTypes = transaction.events().stream().map(event -> event.payload().type()).toList();
            return new Entry(record.sequence(), transaction.revision().value(), transaction.id().value(), transaction.worldId().value(), payloadTypes);
        } catch (IOException failure) {
            throw new IllegalStateException("unable to read Frontier v3 WAL record " + record.path().getFileName(), failure);
        }
    }

    public record Entry(long sequence, long revision, String transactionId, String worldId, List<String> payloadTypes) {
        public Entry {
            if (sequence < 1 || revision < 1 || transactionId == null || worldId == null) throw new IllegalArgumentException("decoded WAL entry");
            payloadTypes = List.copyOf(payloadTypes);
        }

        String json() {
            return "{\"sequence\":" + sequence + ",\"revision\":" + revision + ",\"transactionId\":\"" + escape(transactionId)
                    + "\",\"worldId\":\"" + escape(worldId) + "\",\"payloadTypes\":["
                    + payloadTypes.stream().map(value -> "\"" + escape(value) + "\"").collect(java.util.stream.Collectors.joining(",")) + "]}";
        }
    }

    private static NumberedPath numbered(Path path) {
        Matcher match = WAL.matcher(path.getFileName().toString());
        return match.matches() ? new NumberedPath(Long.parseLong(match.group(1)), path) : null;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record NumberedPath(long sequence, Path path) { }
}
