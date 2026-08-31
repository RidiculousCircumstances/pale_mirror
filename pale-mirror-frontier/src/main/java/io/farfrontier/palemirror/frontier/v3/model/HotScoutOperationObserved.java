package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * Durable evidence that one HOT Scout physically saw the exact owned carrier of one HOT route
 * scene.  This is deliberately distinct from the COLD perception planner's derived sighting:
 * replay can therefore retain the physical provenance without querying Minecraft again.
 */
public record HotScoutOperationObserved(SceneLeaseId sceneLeaseId, SubjectId operationId, SubjectId scoutId,
                                        BlockPosition seenCarrierPosition, long observedAt) implements FrontierPayload {
    public HotScoutOperationObserved {
        Objects.requireNonNull(sceneLeaseId, "scene lease id");
        Objects.requireNonNull(operationId, "operation id");
        Objects.requireNonNull(scoutId, "scout id");
        Objects.requireNonNull(seenCarrierPosition, "seen carrier position");
        if (observedAt < 0L) throw new IllegalArgumentException("HOT scout sighting tick must be non-negative");
    }

    @Override public String type() { return "frontier.hot_scout_operation_observed"; }
}
