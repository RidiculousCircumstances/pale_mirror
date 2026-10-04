package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.StationApproachState;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;

/** Fresh schema; absence of historical spatial bytes is never an initial-state default. */
final class StationApproachStateCodec {
    private StationApproachStateCodec() { }
    static void write(DataOutputStream output, StationApproachState state) throws IOException {
        output.writeLong(state.revision()); TraversalRejoinCodec.write(output, state.approach());
        output.writeBoolean(state.waitingOrigin().isPresent());
        if (state.waitingOrigin().isPresent())
            FrontierWorldStateCodec.writePosition(output, state.waitingOrigin().orElseThrow().support());
    }
    static StationApproachState read(DataInputStream input) throws IOException {
        long revision = input.readLong(); var approach = TraversalRejoinCodec.read(input);
        Optional<SurfaceAnchor> waiting = input.readBoolean()
                ? Optional.of(new SurfaceAnchor(FrontierWorldStateCodec.readPosition(input))) : Optional.empty();
        return new StationApproachState(revision, approach, waiting);
    }
}
