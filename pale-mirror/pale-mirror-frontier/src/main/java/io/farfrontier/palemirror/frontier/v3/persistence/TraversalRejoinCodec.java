package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

/** Current-schema only; unknown/absent historical fields do not imply a rejoin. */
final class TraversalRejoinCodec {
    private TraversalRejoinCodec() { }
    static void write(DataOutputStream output, Optional<TraversalRejoin> value) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isEmpty()) return;
        var approach = value.orElseThrow();
        FrontierWorldStateCodec.writeCount(output, approach.path().size());
        for (var surface : approach.path()) FrontierWorldStateCodec.writePosition(output, surface.support());
        FrontierWorldStateCodec.writeCount(output, approach.cursor());
    }
    static Optional<TraversalRejoin> read(DataInputStream input) throws IOException {
        if (!input.readBoolean()) return Optional.empty();
        int size = FrontierWorldStateCodec.readCount(input);
        if (size < 1 || size > TraversalRejoin.MAX_SURFACES)
            throw new IllegalArgumentException("recovered rejoin exceeds its bounded path");
        var path = new ArrayList<SurfaceAnchor>(size);
        for (int i = 0; i < size; i++) path.add(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(input)));
        return Optional.of(new TraversalRejoin(path, FrontierWorldStateCodec.readCount(input)));
    }
}
