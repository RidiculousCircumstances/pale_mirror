package io.farfrontier.palemirror.frontier.v3.runtime;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldSnapshotHeader;

import java.util.Objects;
import java.util.Optional;

/** Selects one exact installed bootstrap before recovery constructs its engine. */
public final class FrontierWorldRecoveryConfiguration {
    private FrontierWorldRecoveryConfiguration() { }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> select(
            WorldId worldId, long seed, Optional<CheckpointImage> checkpoint
    ) {
        Objects.requireNonNull(worldId, "recovery world"); Objects.requireNonNull(checkpoint, "recovery checkpoint");
        if (checkpoint.isEmpty()) return FrontierWorldRuntimeDefinition.configuration(worldId, seed);
        FrontierWorldSnapshotHeader header = FrontierWorldSnapshotHeader.read(checkpoint.orElseThrow().canonicalState());
        if (!worldId.equals(header.worldId()) || seed != header.seed()) {
            throw new IllegalArgumentException("Frontier v3 recovery header does not match the selected physical world");
        }
        return FrontierWorldRuntimeDefinition.configuration(worldId, seed, header.ruleset());
    }
}
