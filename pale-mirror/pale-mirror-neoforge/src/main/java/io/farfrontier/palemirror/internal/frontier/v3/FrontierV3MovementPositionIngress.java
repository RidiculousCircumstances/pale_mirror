package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.process.ActorMovementProviders;
import net.minecraft.server.level.ServerLevel;

/** Shared observation ingress. The declared provider supplies dependencies, not a family lookup here. */
final class FrontierV3MovementPositionIngress {
    private FrontierV3MovementPositionIngress() { }
    private static final java.util.Map<FrontierV3ServerRuntime<?, ?>, java.util.Map<io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId, Long>> ATTEMPTS = new java.util.WeakHashMap<>();

    static void observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                        FrontierWorldState state, ActorMovement source, long tick) {
        tick = runtime.canonicalState().orElseThrow().instant().ticks();
        var subjects = ActorMovementProviders.require(source).positionSubjects(state, source);
        if (subjects.size() == 1) return;
        var attempts = ATTEMPTS.computeIfAbsent(runtime, ignored -> new java.util.HashMap<>());
        var active = state.actorMovements();
        attempts.keySet().removeIf(id -> active.get(id.actorId()) == null || !active.get(id.actorId()).executionId().equals(id));
        for (var subject : subjects) {
            state = runtime.decodedState().orElseThrow();
            var movement = state.actorMovements().get(subject);
            if (movement == null || movement.coldTravel().isPresent() || !ActorExecutionCoordinator.coldAvailable(state, subject)
                    || tick <= movement.issuedAtTick()) continue;
            long retryTicks = ActorMovementProviders.require(movement).ticksPerEdge(state, movement);
            if (attempts.getOrDefault(movement.executionId(), Long.MIN_VALUE) > tick - retryTicks) continue;
            attempts.put(movement.executionId(), tick);
            var dependencies = ActorMovementProviders.require(movement).positionSubjects(state, movement);
            var snapshot = FrontierV3ActorPositionView.snapshot(level, state, tick, dependencies);
            if (snapshot.hotPoints().isEmpty()) continue; // Pure COLD continues through the ordinary scheduler.
            var request = new ActorMovementColdRequested(movement.executionId(), movement.order().goalRevision(), snapshot);
            FrontierV3CommandSubmission.submitResult(runtime, "movement-position-ingress", subject.value(), request);
        }
    }
}
