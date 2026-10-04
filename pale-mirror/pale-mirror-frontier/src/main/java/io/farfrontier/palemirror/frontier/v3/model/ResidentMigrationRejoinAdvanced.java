package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;

/** COLD progress along the exact known approach, distinct from original route progress. */
public record ResidentMigrationRejoinAdvanced(SubjectId residentId, long routeRevision, int nextRejoinCursor,
                                              ActorExecutionId executionId) implements FrontierPayload {
    public ResidentMigrationRejoinAdvanced {
        java.util.Objects.requireNonNull(residentId, "rejoining resident");
        TransitActivityCapability.requireDeclared(residentId, executionId);
        if (routeRevision < 1 || nextRejoinCursor < 0 || nextRejoinCursor >= TraversalRejoin.MAX_SURFACES)
            throw new IllegalArgumentException("migration rejoin requires its exact revision and bounded approach cursor");
    }
    @Override public String type() { return "frontier.resident_migration_rejoin_advanced"; }
}
