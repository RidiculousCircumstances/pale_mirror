package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Captures final unload only. Tracking loss and periodic observations grant no custody. */
final class FrontierV3AmbientDepartureObserver {
    private FrontierV3AmbientDepartureObserver() { }

    static boolean observeLeave(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        if (!(entity instanceof Mob body) || entity.getRemovalReason() != Entity.RemovalReason.UNLOADED_TO_CHUNK
                || body.getHealth() <= 0.0F) return false;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        var live = declaration(state, entity);
        if (live == null || !FrontierV3ActorCarrierComposition.ownsUnloading(entity, live)) return false;
        var location = state.actorLocations().get(live.actorId());
        var inactive = new FrontierV3ActorCarrierComposition.Declaration(live.actorId(), live.kind(), live.owner(), live.entityId(),
                FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, live.authorityRevision(), live.epoch());
        var receipt = new FrontierV3AmbientDeparture(new FrontierV3AmbientCarrierLedger.Carrier(inactive,
                live.authorityRevision(), live.authorityRevision()), observation(body, live.actorId()),
                location.body(), location.condition().health());
        return receipt.current(state) && FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()).recordAmbientDeparture(receipt);
    }

    static void observeJoin(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        if (!(entity instanceof Mob body) || !body.isAlive()) return;
        var state = runtime.decodedState().orElse(null);
        if (state == null) return;
        var live = declaration(state, entity);
        if (live == null || !FrontierV3ActorCarrierComposition.owns(entity, live)) return;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        ledger.ambientDeparture(live.actorId()).filter(receipt -> receipt.current(state)
                && receipt.carrier().identity().epoch() == live.epoch()
                && receipt.observed().equals(observation(body, live.actorId())))
                .ifPresent(ledger::resumeAmbientDeparture);
    }

    private static SceneMemberPosition observation(Mob body, SubjectId actor) {
        return new SceneMemberPosition(actor, FrontierV3BodyObservation.position(body),
                new FixedScalar(Math.round((double) body.getHealth() * FixedScalar.SCALE)));
    }

    private static FrontierV3ActorCarrierComposition.Declaration declaration(FrontierWorldState state, Entity entity) {
        try {
            var tag = entity.getPersistentData();
            var actor = new SubjectId(tag.getString(FrontierV3AmbientActorExecutor.ACTOR_KEY));
            var lease = state.ambientLeases().get(actor);
            var location = state.actorLocations().get(actor);
            if (lease == null || location == null || location.condition().status() != ActorLifeStatus.ALIVE
                    || (lease.status() != AmbientLeaseStatus.HOT && lease.status() != AmbientLeaseStatus.DRAINING
                        && lease.status() != AmbientLeaseStatus.UNKNOWN_AFTER_RESTART)) return null;
            return FrontierV3ActorCarrierComposition.fromCanonical(state, actor,
                    FrontierV3ActorCarrierComposition.ActorKind.valueOf(tag.getString(FrontierV3ActorCarrierComposition.KIND_KEY)),
                    FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, entity.getUUID(),
                    FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, lease.revision(),
                    tag.getLong(FrontierV3ActorCarrierComposition.EPOCH_KEY));
        } catch (IllegalArgumentException invalid) { return null; }
    }
}
