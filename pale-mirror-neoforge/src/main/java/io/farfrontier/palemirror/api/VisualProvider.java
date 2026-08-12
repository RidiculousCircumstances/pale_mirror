package io.farfrontier.palemirror.api;

import java.util.Collection;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Experimental provider SPI. Implementations discover physical facts; they never mutate canonical state. */
public interface VisualProvider extends IntegrationAdapter {
    Collection<AuthoredRegionSeed> discoverAuthoredRegions(ServerLevel level);
    /** True only after genesis has durably completed this exact authored module. */
    default boolean authoredModuleReady(ServerLevel level, AuthoredRegionSeed region,
                                        VisualModulePlacement module) { return false; }
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
