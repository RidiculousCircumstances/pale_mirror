package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Typed canonical reason that owns a coupled HOT/COLD scene.
 *
 * <p>A lease is an execution mechanism, not an operation category. Each cause must be
 * validated by its owning domain before it may be prepared.</p>
 */
public sealed interface SceneCause permits LogisticsSceneCause, SettlementAssaultSceneCause, EngineeringWorkSceneCause, MedicalTreatmentSceneCause {
    SceneCauseKind kind();
}
