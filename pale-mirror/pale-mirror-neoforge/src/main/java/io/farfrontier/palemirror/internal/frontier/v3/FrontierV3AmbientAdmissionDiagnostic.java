package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;

import java.util.UUID;

/** Bounded loaded-world evidence for one ambient-body admission; it has no canonical authority. */
record FrontierV3AmbientAdmissionDiagnostic(String status, UUID entityId, boolean pending, BlockPosition placement,
                                            BlockPosition observedPosition, FrontierV3AmbientActorExecutor.ObservedPosition observedExact,
                                            int trackerCalls, int trackerImpulseCalls,
                                            FrontierV3ControlledMobMotion.MotionObservation motion) {
    FrontierV3AmbientAdmissionDiagnostic {
        if (motion == null) motion = FrontierV3ControlledMobMotion.MotionObservation.idle();
    }
    /** Backward-compatible read-only diagnostic construction for tests and non-motion scopes. */
    FrontierV3AmbientAdmissionDiagnostic(String status, UUID entityId, boolean pending, BlockPosition placement,
                                         BlockPosition observedPosition, FrontierV3AmbientActorExecutor.ObservedPosition observedExact) {
        this(status, entityId, pending, placement, observedPosition, observedExact, 0, 0,
                FrontierV3ControlledMobMotion.MotionObservation.idle());
    }
    static FrontierV3AmbientAdmissionDiagnostic notCanonical() { return new FrontierV3AmbientAdmissionDiagnostic("NOT_CANONICAL", null, false, null, null, null); }
    static FrontierV3AmbientAdmissionDiagnostic terminal(UUID entityId) { return new FrontierV3AmbientAdmissionDiagnostic("TERMINAL", entityId, false, null, null, null); }
    static FrontierV3AmbientAdmissionDiagnostic indexed(UUID entityId, boolean pending, BlockPosition observedPosition, FrontierV3AmbientActorExecutor.ObservedPosition observedExact,
                                                         FrontierV3ControlledMobMotion.TrackerObservation tracker) {
        return indexed(entityId, pending, observedPosition, observedExact, tracker, FrontierV3ControlledMobMotion.MotionObservation.idle());
    }
    static FrontierV3AmbientAdmissionDiagnostic indexed(UUID entityId, boolean pending, BlockPosition observedPosition, FrontierV3AmbientActorExecutor.ObservedPosition observedExact,
                                                         FrontierV3ControlledMobMotion.TrackerObservation tracker,
                                                         FrontierV3ControlledMobMotion.MotionObservation motion) {
        return new FrontierV3AmbientAdmissionDiagnostic("INDEXED", entityId, pending, null, observedPosition, observedExact, tracker.calls(), tracker.impulseCalls(), motion);
    }
    static FrontierV3AmbientAdmissionDiagnostic sceneOwned(UUID entityId, BlockPosition observedPosition, FrontierV3AmbientActorExecutor.ObservedPosition observedExact,
                                                            FrontierV3ControlledMobMotion.TrackerObservation tracker) {
        return sceneOwned(entityId, observedPosition, observedExact, tracker, FrontierV3ControlledMobMotion.MotionObservation.idle());
    }
    static FrontierV3AmbientAdmissionDiagnostic sceneOwned(UUID entityId, BlockPosition observedPosition, FrontierV3AmbientActorExecutor.ObservedPosition observedExact,
                                                            FrontierV3ControlledMobMotion.TrackerObservation tracker,
                                                            FrontierV3ControlledMobMotion.MotionObservation motion) {
        return new FrontierV3AmbientAdmissionDiagnostic("SCENE_OWNED", entityId, false, null, observedPosition, observedExact, tracker.calls(), tracker.impulseCalls(), motion);
    }
    static FrontierV3AmbientAdmissionDiagnostic conflict(UUID entityId) { return new FrontierV3AmbientAdmissionDiagnostic("UUID_CONFLICT", entityId, false, null, null, null); }
    static FrontierV3AmbientAdmissionDiagnostic carrierAmbiguity(UUID entityId, String reason) {
        return new FrontierV3AmbientAdmissionDiagnostic("CARRIER_" + reason, entityId, false, null, null, null);
    }
    /** Read-only history explanation; never authorization to create or discard a body. */
    static java.util.Optional<String> unresolvedCreationReason(FrontierV3AmbientCarrierLedger ledger,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        if (ledger.pendingHandoff(actor).isPresent()) return java.util.Optional.of("HANDOFF_SAVE_PENDING");
        if (ledger.pendingAdoption(actor).isPresent()) return java.util.Optional.of("ADOPTION_SAVE_PENDING");
        return ledger.firstAdmission(actor)
                .filter(first -> first.phase() == FrontierV3ActorFirstAdmission.Phase.PENDING)
                .map(first -> "FIRST_CREATION_PENDING");
    }
    static FrontierV3AmbientAdmissionDiagnostic pendingUnindexed(UUID entityId, BlockPosition observedPosition, FrontierV3AmbientActorExecutor.ObservedPosition observedExact) {
        return new FrontierV3AmbientAdmissionDiagnostic("PENDING_UNINDEXED", entityId, true, null, observedPosition, observedExact);
    }
    static FrontierV3AmbientAdmissionDiagnostic unloaded(UUID entityId) { return new FrontierV3AmbientAdmissionDiagnostic("UNLOADED", entityId, false, null, null, null); }
    static FrontierV3AmbientAdmissionDiagnostic entityStoragePending(UUID entityId, BlockPosition placement) {
        return new FrontierV3AmbientAdmissionDiagnostic("ENTITY_STORAGE_PENDING", entityId, false, placement, null, null);
    }
    static FrontierV3AmbientAdmissionDiagnostic blocked(UUID entityId, BlockPosition placement) { return new FrontierV3AmbientAdmissionDiagnostic("BLOCKED", entityId, false, placement, null, null); }
    static FrontierV3AmbientAdmissionDiagnostic ready(UUID entityId, BlockPosition placement) { return new FrontierV3AmbientAdmissionDiagnostic("READY", entityId, false, placement, null, null); }
}
