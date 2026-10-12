package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringApproachSelection;
import java.io.*;
import java.util.Optional;

/** Shared payload grammar, not discovery of an engineering owner from its subject. */
final class EngineeringApproachSelectionCodec {
    private EngineeringApproachSelectionCodec() { }
    static void write(DataOutputStream output, EngineeringApproachSelection selected) throws IOException {
        FrontierWorldStateCodec.writeString(output, selected.actorId().value());
        StationApproachStateCodec.write(output, selected.predecessor());
        TraversalRejoinCodec.write(output, Optional.of(selected.approach()));
    }
    static EngineeringApproachSelection read(DataInputStream input) throws IOException {
        var actor = new SubjectId(FrontierWorldStateCodec.readString(input));
        var predecessor = StationApproachStateCodec.read(input);
        var selected = TraversalRejoinCodec.read(input).orElseThrow(() -> new IllegalArgumentException("engineering approach payload omitted its selected route"));
        return new EngineeringApproachSelection(actor, predecessor, selected);
    }
}
