package io.farfrontier.palemirror.api;

import java.util.Collection;
import net.minecraft.server.level.ServerLevel;

/** Experimental provider SPI. Implementations discover physical facts; they never mutate canonical state. */
public interface VisualProvider extends IntegrationAdapter {
    Collection<AuthoredRegionSeed> discoverAuthoredRegions(ServerLevel level);
    Collection<ResidentDeathObservation> drainResidentDeaths(ServerLevel level);
    void applyProjection(ServerLevel level, VisualStateProjection projection);
}
