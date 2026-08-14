package io.farfrontier.palemirror.api;

import java.util.Collection;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
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
