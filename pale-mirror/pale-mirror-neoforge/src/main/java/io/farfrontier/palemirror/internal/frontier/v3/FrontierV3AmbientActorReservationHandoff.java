package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Optional;

/** Bounded exact ambient-to-scene hand-off, separate from ordinary ambient body behavior. */
final class FrontierV3AmbientActorReservationHandoff {
    private FrontierV3AmbientActorReservationHandoff() { }

    static void run(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState initialState,
                    FrontierV3AmbientAdmissionPolicy.Session admissionPolicy) {
        admissionPolicy.scan(initialState.actorLocations().keySet(), () -> runtime.decodedState().orElse(null),
                selection -> execute(level, runtime, selection));
    }

    static FrontierV3AmbientAdmissionPolicy.Decision runOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                             FrontierWorldState state, SubjectId actorId,
                                                             FrontierV3AmbientAdmissionPolicy.Session admissionPolicy) {
        return admissionPolicy.decide(actorId, state, selection -> execute(level, runtime, selection));
    }

    private static Optional<FrontierWorldState> execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                         FrontierV3AmbientAdmissionPolicy.Selection selection) {
        Entity body = level.getEntity(FrontierV3AmbientActorExecutor.entityId(selection.state(), selection.actorId()));
        boolean applied = switch (selection.effect()) {
            case DRAIN_HOT -> body instanceof Mob mob && FrontierV3AmbientActorExecutor.owned(mob, selection.actorId(),
                    FrontierV3AmbientActorExecutor.bioform(selection.state(), selection.actorId()))
                    && FrontierV3AmbientActorExecutor.drain(runtime, mob);
            case ABANDON_PREPARED -> {
                var lease = selection.state().ambientLeases().get(selection.actorId());
                yield lease != null && FrontierV3AmbientActorExecutor.abandonPreparedForReservation(
                        level, runtime, selection.state(), selection.actorId(), lease);
            }
        };
        return applied ? runtime.decodedState() : Optional.empty();
    }
}
