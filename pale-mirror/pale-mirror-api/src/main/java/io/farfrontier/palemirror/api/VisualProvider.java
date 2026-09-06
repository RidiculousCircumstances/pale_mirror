package io.farfrontier.palemirror.api;

import java.util.Collection;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/** Experimental provider SPI. Implementations discover physical facts; they never mutate canonical state. */
public interface VisualProvider extends IntegrationAdapter {
    default GenesisReadiness genesisReadiness() { return GenesisReadiness.failed("Genesis readiness is unavailable"); }
    default String performanceSummary() { return "unavailable"; }
    Collection<AuthoredRegionSeed> discoverAuthoredRegions(ServerLevel level);
    /** Semantic, repeatable camera poses; production state is never mutated by this diagnostic projection. */
    default Collection<VisualAuditView> visualAuditViews(ServerLevel level, String regionId) {
        return java.util.List.of();
    }
    /** Bounded read-only comparison of an authored plan with already-loaded physical chunks. */
    default Optional<FoundryAuditReport> foundryAudit(ServerLevel level, String regionId,
                                                       FoundryAuditPhase phase) { return Optional.empty(); }
    /** Explicit diagnostic export. Artifacts are non-canonical and never affect reconciliation. */
    default Optional<FoundryAuditExport> exportFoundryAudit(ServerLevel level, String regionId,
                                                             FoundryAuditPhase phase) { return Optional.empty(); }
    /** Looks up plan ownership and physical state without loading the containing chunk. */
    default Optional<FoundryBlockInspection> inspectFoundryBlock(ServerLevel level, String regionId,
                                                                  VisualPoint position) { return Optional.empty(); }
    /** True only after genesis has durably completed this exact authored module. */
    default boolean authoredModuleReady(ServerLevel level, AuthoredRegionSeed region,
                                        VisualModulePlacement module) { return false; }
    /** True only after every initial blueprint slice and semantic volume for this MineSite was observed. */
    default boolean authoredMineSiteReady(ServerLevel level, AuthoredRegionSeed region,
                                          AuthoredMineSitePlan mine) { return false; }
    /** Compiles an inert blueprint. Core remains the sole owner of later world mutation. */
    default Optional<VisualModuleSnapshot> compileAuthoredModule(StagedVisualModule module) { return Optional.empty(); }
    /** Compiles a bounded state overlay; returned cells are mutations, not a second source of truth. */
    default Optional<VisualModuleSnapshot> compileAuthoredModuleState(VisualModulePlacement module, String state) {
        return Optional.empty();
    }
    Collection<ResidentDeathObservation> drainResidentDeaths(ServerLevel level);
    void applyProjection(ServerLevel level, VisualStateProjection projection);
    /** Latest desired autonomous-world state. Delivery never grants canonical mutation authority. */
    default void applyFrontierProjection(ServerLevel level, FrontierProjection projection) { }
    /** Read-only physical facts are queued here; Core drains and validates them in the accepting server event. */
    default Collection<FrontierPhysicalObservation> drainFrontierObservations(ServerLevel level) { return java.util.List.of(); }
    /** Returns true only when this is a current managed Frontier entity. */
    default boolean observeFrontierEntityDeath(ServerLevel level, Entity entity, String causationId) { return false; }
    /** Returns true only when the destroyed block is a current managed Frontier facility or organ. */
    default boolean observeFrontierBlockBreak(ServerLevel level, BlockPos position, String causationId) { return false; }
    default void applyJourneyProjection(ServerLevel level, JourneyProjection projection) { }
    default Collection<JourneyObservation> drainJourneyObservations(ServerLevel level) { return java.util.List.of(); }
    default ThreatControllerResult ensureThreatController(ServerLevel level, ThreatControllerProjection projection) {
        return ThreatControllerResult.blocked("Visual provider does not support a PM threat controller");
    }
    default ThreatControllerResult removeThreatController(ServerLevel level, String objectId) {
        return ThreatControllerResult.absent();
    }
    default Optional<String> threatControllerObjectId(Entity entity) { return Optional.empty(); }
    default void presentThreatControllerDamage(Entity entity, boolean defeated) { }
}
