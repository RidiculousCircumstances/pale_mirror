package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;

/** Explicit scene-participant view of body-owned departure evidence. No physical callbacks. */
final class FrontierV3SceneDepartureObserver {
    private FrontierV3SceneDepartureObserver() { }

    /** The returned snapshot cannot erase evidence against a newer canonical baseline. */
    static boolean resumeReturned(FrontierWorldState state, SceneLease lease, SceneMember member,
                                   FrontierV3ActorCarrierComposition.Declaration live, SceneMemberPosition observed,
                                   FrontierV3AmbientCarrierLedger ledger) {
        return observedDeparture(state, lease, member, ledger)
                .filter(receipt -> live.representation() == FrontierV3ActorCarrierComposition.Representation.LIVE_BODY
                        && live.owner() == receipt.carrier().identity().owner()
                        && live.kind() == receipt.carrier().identity().kind()
                        && live.actorId().equals(receipt.carrier().identity().actorId())
                        && live.authorityRevision() == receipt.carrier().identity().authorityRevision()
                        && live.epoch() == receipt.carrier().identity().epoch()
                        && live.entityId().equals(receipt.carrier().identity().entityId())
                        && receipt.observed().equals(observed))
                .map(ledger::resumeDeparture).orElse(false);
    }

    /** Unresolved final-departure evidence prevents another physical writer, even while HOT. */
    static boolean permitsLiveWork(FrontierV3AmbientCarrierLedger ledger, SceneMember member) {
        return ledger.departure(member.actorId()).isEmpty() && ledger.bodyDeparture(member.actorId()).isEmpty();
    }

    static java.util.Optional<FrontierV3SceneDeparture> validDeparture(FrontierWorldState state, SceneLease lease,
                                                                      SceneMember member, FrontierV3AmbientCarrierLedger ledger) {
        return observedDeparture(state, lease, member, ledger).filter(ledger::savedDeparture);
    }

    /** Unload observation is not yet a release witness until entity storage confirms it. */
    static java.util.Optional<FrontierV3SceneDeparture> observedDeparture(FrontierWorldState state, SceneLease lease,
                                                                      SceneMember member, FrontierV3AmbientCarrierLedger ledger) {
        var actor = state.actorLocations().get(member.actorId());
        var ambient = state.ambientLeases().get(member.actorId());
        if (ledger.hasDepartureConflict(member.actorId()) || actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || (ambient != null && ambient.status() != AmbientLeaseStatus.CLOSED)) return java.util.Optional.empty();
        var physical = ledger.bodyDeparture(member.actorId()).orElse(null);
        java.util.Optional<FrontierV3SceneDeparture> recorded = ledger.departure(member.actorId());
        if (physical != null) {
            if (!physical.current(state) || !physical.identity().entityId().equals(member.entityId())
                    || !lease.members().contains(member)) return java.util.Optional.empty();
            recorded = java.util.Optional.of(new FrontierV3SceneDeparture(
                    new FrontierV3AmbientCarrierLedger.Carrier(physical.identity(), Math.max(1L, lease.revision()),
                        ambient == null ? 0L : ambient.revision()), physical.residenceGeneration(), lease.id(), lease.revision(), physical.observed(),
                    physical.canonicalHealth(), physical.offhand(), physical.mainhand()));
        }
        return recorded.filter(receipt ->
                receipt.leaseId().equals(lease.id()) && receipt.sceneRevision() == lease.revision()
                && receipt.carrier().identity().entityId().equals(member.entityId())
                && (receipt.canonicalHealthAtCapture().equals(actor.condition().health())
                    || physical != null && physical.observed().health().equals(actor.condition().health()))
                && receipt.carrier().ambientRevision() == (ambient == null ? 0L : ambient.revision()))
                .filter(receipt -> declaredActorCurrent(state, receipt));
    }

    private static boolean declaredActorCurrent(FrontierWorldState state, FrontierV3SceneDeparture receipt) {
        var declaration = receipt.carrier().identity();
        try {
            return FrontierV3ActorBodyController.recognizesDeclaration(state,
                    declaration.liveBody(declaration.owner(), 0L, declaration.epoch()));
        } catch (IllegalArgumentException foreignDeclaration) { return false; }
    }

}
