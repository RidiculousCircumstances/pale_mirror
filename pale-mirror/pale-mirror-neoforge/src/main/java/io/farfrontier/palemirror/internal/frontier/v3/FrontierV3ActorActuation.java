package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import net.minecraft.world.entity.Mob;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Captured command authority, not a lookup that reauthorizes yesterday's command. */
record FrontierV3ActorActuation(ActorActuationId id, Supplier<Optional<FrontierWorldState>> currentState) {
    FrontierV3ActorActuation {
        Objects.requireNonNull(id, "captured actuation identity");
        Objects.requireNonNull(currentState, "live read-only authority source");
    }

    static FrontierV3ActorActuation capture(FrontierWorldState basis, Mob body, ActorExecutionId execution,
                                           Supplier<Optional<FrontierWorldState>> currentState) {
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(body).orElseThrow(
                () -> new IllegalArgumentException("actuation requires the body's complete declaration"));
        var actuation = new FrontierV3ActorActuation(new ActorActuationId(
                new ActorBodyId(declaration.actorId(), declaration.epoch()), execution), currentState);
        actuation.require(basis, declaration);
        return actuation;
    }

    /** The already declared body tuple validates identity; activity/scene owner never supplies it. */
    void require(FrontierWorldState state, FrontierV3ActorCarrierComposition.Declaration declaration) {
        ActorBodyAuthority.requireActuation(state, id);
        var actor = state.actorLocations().get(id.body().actorId());
        if (!declaration.actorId().equals(id.body().actorId())
                || declaration.epoch() != id.body().physicalEpoch()
                || declaration.kind() != actor.kind()
                || declaration.representation() != FrontierV3ActorCarrierComposition.Representation.LIVE_BODY
                || !declaration.entityId().equals(ActorBodyId.entityId(state.bootstrap().worldId(), id.body().actorId())))
            throw new IllegalArgumentException("actuation does not address this exact live body incarnation");
    }

    boolean current(Mob body) {
        if (body == null || body.isRemoved() || !body.isAlive()) return false;
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(body);
        return declaration.isPresent() && current(declaration.orElseThrow());
    }

    boolean current(FrontierV3ActorCarrierComposition.Declaration declaration) {
        var state = currentState.get();
        if (state.isEmpty()) return false;
        try {
            require(state.orElseThrow(), declaration);
            return true;
        } catch (IllegalArgumentException stale) {
            return false;
        }
    }
}
