package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityBodyCheckpoint;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;

/** Hive-owned spatial continuation only; common body authority owns position and physical custody. */
final class HiveBodyCheckpoint {
    private HiveBodyCheckpoint() { }
    static ActorActivityBodyCheckpoint.Acknowledgement acknowledge(ActorActivityBodyCheckpoint.Request request, HiveMobilization owner) {
        boolean returning = request.execution().activityKind() == ActorActivityKind.HIVE_TASK_RETURN;
        // Waking and conflicted owners have no advancing route. Their frozen semantic
        // checkpoint is not a second current-pose store and cannot prevent saving a body.
        if (!returning && owner.status() != HiveMobilizationStatus.ASSEMBLING)
            return new ActorActivityBodyCheckpoint.Acknowledgement(request, FrontierWorldStateUpdate.begin());
        var members = returning ? owner.returnAssembly().orElseThrow().members() : owner.assembly().orElseThrow().members();
        var member = members.get(request.execution().actorId());
        var observed = request.observedPosition().supportingSurface();
        if (member.currentSurface().equals(observed))
            return new ActorActivityBodyCheckpoint.Acknowledgement(request, FrontierWorldStateUpdate.begin());
        var state = request.expectedState();
        var path = HiveAssemblyCorridor.rejoin(state, request.execution().actorId(), observed, member, members);
        return new ActorActivityBodyCheckpoint.Acknowledgement(request, FrontierWorldStateUpdate.begin().hiveColony(
                state.hiveColony().checkpointMobilizationMember(owner.id(), request.execution().actorId(), member.withRejoin(new TraversalRejoin(path, 0)))));
    }
}
