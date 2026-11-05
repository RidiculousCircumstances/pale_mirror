package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

import java.io.*;
import java.util.*;

/** Exact stored geometry; recovery does not rerun the plot allocator. */
final class SettlementLayoutManifestCodec {
    private static final ImmutableResultView<String, List<byte[]>> ENCODED =
            new ImmutableResultView<>(2);
    private static final BoundedValueMemo<Image, SettlementLayoutPlanner.Result> DECODED =
            new BoundedValueMemo<>(32);

    private static final class Image {
        private final byte[] bytes;
        private final int hash;

        private Image(byte[] bytes) {
            this.bytes = bytes;
            hash = Arrays.hashCode(bytes);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Image image && Arrays.equals(bytes, image.bytes);
        }
    }

    static void write(DataOutputStream out, FrontierBootstrap bootstrap) throws IOException {
        // Layouts change only with an explicit bootstrap/land-allocation replacement, not
        // with jobs or stock. These private immutable bytes carry no recovery authority.
        var images =
                ENCODED.get(
                        List.of(bootstrap),
                        "layouts",
                        () ->
                                bootstrap.settlements().stream()
                                        .map(
                                                home ->
                                                        SettlementLayoutFormat.encode(
                                                                new SettlementLayoutPlanner.Result(
                                                                        home.structures(),
                                                                        home.layout())))
                                        .toList());
        out.writeInt(images.size());
        for (var bytes : images) {
            out.writeInt(bytes.length);
            out.write(bytes);
        }
    }

    static Map<SubjectId, SettlementLayoutPlanner.Result> read(DataInputStream in)
            throws IOException {
        int count = in.readInt();
        if (count < 1 || count > SimulationCapacity.MAX_SETTLEMENTS)
            throw new IllegalArgumentException("invalid settlement layout count");
        var result = new LinkedHashMap<SubjectId, SettlementLayoutPlanner.Result>();
        for (int i = 0; i < count; i++) {
            int size = in.readInt();
            if (size < 1 || size > 2_097_152)
                throw new IllegalArgumentException("invalid layout size");
            var bytes = in.readNBytes(size);
            if (bytes.length != size) throw new EOFException("incomplete settlement layout");
            var plan = DECODED.get(new Image(bytes), () -> SettlementLayoutFormat.decode(bytes));
            if (result.putIfAbsent(plan.layout().settlementId(), plan) != null)
                throw new IllegalArgumentException("duplicate settlement layout");
        }
        return Map.copyOf(result);
    }

    private SettlementLayoutManifestCodec() {}
}
