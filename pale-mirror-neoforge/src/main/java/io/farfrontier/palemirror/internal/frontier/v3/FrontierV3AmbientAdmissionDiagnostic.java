package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;

import java.util.UUID;

/** Bounded loaded-world evidence for one ambient-body admission; it has no canonical authority. */
record FrontierV3AmbientAdmissionDiagnostic(String status, UUID entityId, boolean pending, BlockPosition placement,
                                            BlockPosition observedPosition, FrontierV3AmbientActorExecutor.ObservedPosition observedExact) {
    static FrontierV3AmbientAdmissionDiagnostic notCanonical() { return new FrontierV3AmbientAdmissionDiagnostic("NOT_CANONICAL", null, false, null, null, null); }
    static FrontierV3AmbientAdmissionDiagnostic terminal(UUID entityId) { return new FrontierV3AmbientAdmissionDiagnostic("TERMINAL", entityId, false, null, null, null); }
    static FrontierV3AmbientAdmissionDiagnostic indexed(UUID entityId, boolean pending, BlockPosition observedPosition, FrontierV3AmbientActorExecutor.ObservedPosition observedExact) {
        return new FrontierV3AmbientAdmissionDiagnostic("INDEXED", entityId, pending, null, observedPosition, observedExact);
    }
    static FrontierV3AmbientAdmissionDiagnostic sceneOwned(UUID entityId, BlockPosition observedPosition, FrontierV3AmbientActorExecutor.ObservedPosition observedExact) {
        return new FrontierV3AmbientAdmissionDiagnostic("SCENE_OWNED", entityId, false, null, observedPosition, observedExact);
    }
    static FrontierV3AmbientAdmissionDiagnostic conflict(UUID entityId) { return new FrontierV3AmbientAdmissionDiagnostic("UUID_CONFLICT", entityId, false, null, null, null); }
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
