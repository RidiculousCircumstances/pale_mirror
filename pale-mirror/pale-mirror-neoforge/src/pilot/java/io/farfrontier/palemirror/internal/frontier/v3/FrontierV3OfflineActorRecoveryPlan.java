package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess;
import java.util.List;

/** Offline plan only: the publisher must retain the world lock and durable absence receipt. */
record FrontierV3OfflineActorRecoveryPlan(SubjectId actor, long cancelledRevision,
                                         FrontierWorldState before, FrontierWorldState draining,
                                         FrontierWorldState closed,
                                         FrontierV3AmbientCarrierLedger.Carrier recoveryCarrier,
                                         List<FrontierPayload> commands) {
    FrontierV3OfflineActorRecoveryPlan { commands = List.copyOf(commands); }

    /**
     * In-memory composition for the offline publisher. Persist the plan/absence receipt
     * before calling, and durably publish the fenced ledger before permitting startup.
     * A retry accepts only this plan's exact carrier and canonical intermediate states.
     */
    void apply(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
               FrontierV3AmbientCarrierLedger ledger) {
        var state = runtime.decodedState().orElseThrow();
        if (!state.equals(before) && !state.equals(draining) && !state.equals(closed))
            throw new IllegalStateException("offline recovery canonical head changed");
        var carrier = recoveryCarrier;
        if (ledger.firstAdmission(actor).isPresent()
                || ledger.departure(actor).isPresent() || ledger.ambientDeparture(actor).isPresent()
                || ledger.hasDepartureConflict(actor) || ledger.pendingAdoption(actor).isPresent() || ledger.pendingHandoff(actor).isPresent()
                || ledger.hasCarrier(actor) && !ledger.matchesCarrier(carrier.identity(),
                    carrier.physicalRevision(), carrier.ambientRevision()))
            throw new IllegalStateException("offline recovery custody changed");
        if (!ledger.hasCarrier(actor) && !ledger.fence(carrier.identity(),
                carrier.physicalRevision(), carrier.ambientRevision()))
            throw new IllegalStateException("offline recovery carrier could not be retained");
        applyCanonical(runtime);
    }

    /** Resumable only across this plan's exact states; no simulated time is advanced. */
    void applyCanonical(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var state = runtime.decodedState().orElseThrow();
        if (state.equals(before)) {
            FrontierV3CommandSubmission.submit(runtime, "offline-absent-actor-draining", actor.value(), commands.getFirst());
            state = runtime.decodedState().orElseThrow();
        }
        if (state.equals(draining)) {
            FrontierV3CommandSubmission.submit(runtime, "offline-absent-actor-release", actor.value(), commands.getLast());
            state = runtime.decodedState().orElseThrow();
        }
        if (!state.equals(closed)) throw new IllegalStateException("offline recovery state diverged from the exact cancellation plan");
    }

    static FrontierV3OfflineActorRecoveryPlan create(FrontierWorldState state, SubjectId actor,
                                                     FrontierV3OfflineActorAbsence.Proof absence,
                                                     FrontierV3AmbientCarrierLedger ledger) {
        var lease = state.ambientLeases().get(actor);
        var location = state.actorLocations().get(actor);
        var uuid = SceneLease.deterministicEntityId(state.bootstrap().worldId(), actor);
        if (!uuid.equals(absence.actorUuid()) || lease == null || lease.status() != AmbientLeaseStatus.PREPARED
                || location == null || location.condition().status() != ActorLifeStatus.ALIVE
                || !location.body().equals(lease.handoffBody()) || ledger.hasCarrier(actor) || ledger.pendingAdoption(actor).isPresent()
                || ledger.pendingHandoff(actor).isPresent() || ledger.firstAdmission(actor).isPresent()
                || ledger.departure(actor).isPresent() || ledger.ambientDeparture(actor).isPresent()
                || ledger.hasDepartureConflict(actor)
                || state.sceneLeases().values().stream().anyMatch(scene -> scene.status() != SceneLeaseStatus.CLOSED
                    && scene.members().stream().anyMatch(member -> member.actorId().equals(actor))))
            throw new IllegalArgumentException("offline recovery does not name an absent unacknowledged sole ambient authority");
        var resident = state.humanPopulation().resident(actor);
        // Recovery is deliberately limited to the reported resident lifecycle. Bioform
        // physiology and held combat custody require their own explicit recovery contract.
        if (resident == null) throw new IllegalArgumentException("offline resident recovery requires a resident");
        var transition = new AmbientLeaseTransition(actor, AmbientLeaseStatus.DRAINING);
        var release = new AmbientLeaseReleased(actor, location.body(), location.condition().health());
        var draining = AmbientLeaseStateProcess.transition(state, actor, AmbientLeaseStatus.DRAINING);
        var closed = AmbientLeaseStateProcess.release(draining, release);
        if (!state.equals(closed.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(state.ambientLeases()))))
            throw new IllegalStateException("offline cancellation would change actor, economy or work beyond the named lease");
        // This is a NEW inactive recovery carrier for the cancelled generation. It does not
        // assert that an old physical body/epoch was observed, nor restore historical health.
        var declaration = FrontierV3ActorCarrierComposition.fromCanonical(closed, actor,
                ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE,
                uuid, FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER, lease.revision(), 1L);
        var carrier = new FrontierV3AmbientCarrierLedger.Carrier(declaration, lease.revision(), lease.revision());
        return new FrontierV3OfflineActorRecoveryPlan(actor, lease.revision(), state, draining, closed, carrier, List.of(transition, release));
    }
}
