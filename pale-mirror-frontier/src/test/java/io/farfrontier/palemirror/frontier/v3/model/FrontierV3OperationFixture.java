package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small exact-state builder for operation transition tests; it never starts Minecraft. */
final class FrontierV3OperationFixture {
    private FrontierV3OperationFixture() { }

    static RouteOperation routeSceneReturnOperation() {
        FrontierWorldState state = FrontierV3FixtureCatalog
                .routeSceneReturnConfiguration(new WorldId("frontier:operation-fixture"), 91L).initialState();
        return state.operations().get(new SubjectId("operation:supply-1-2"));
    }

    static OperationTravel advanceCold(OperationTravel prior) {
        return advance(prior, prior.nextColdCursor());
    }

    static OperationTravel advance(OperationTravel prior, int cursor) {
        BlockPosition from = prior.currentPosition();
        BlockPosition destination = prior.corridor().get(cursor);
        int deltaX = destination.x() - from.x();
        int deltaZ = destination.z() - from.z();
        Map<SubjectId, BlockPosition> formation = new LinkedHashMap<>();
        prior.formation().forEach((actor, position) -> formation.put(actor, position.offset(deltaX, 0, deltaZ)));
        return prior.advance(cursor, formation, prior.cargoAnchor().offset(deltaX, 0, deltaZ));
    }
}
