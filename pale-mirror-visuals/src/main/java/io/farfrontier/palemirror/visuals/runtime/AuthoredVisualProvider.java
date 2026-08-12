package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.api.VisualProvider;
import io.farfrontier.palemirror.api.VisualStateProjection;
import io.farfrontier.palemirror.api.ResidentDeathObservation;
import io.farfrontier.palemirror.visuals.threat.ThreatHeartRuntime;
import java.util.Collection;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

/** Runtime bridge. World-generation markers are the only accepted discovery facts. */
public final class AuthoredVisualProvider implements VisualProvider {
    public static final AuthoredVisualProvider INSTANCE = new AuthoredVisualProvider();
    private final AuthoredRegionMarkerIndex markers = new AuthoredRegionMarkerIndex();
    private final ThreatHeartRuntime threatHearts = new ThreatHeartRuntime();
    private final SettlementStateCueRuntime settlementCues = new SettlementStateCueRuntime();
    private final java.util.LinkedHashMap<String, ResidentDeathObservation> deaths = new java.util.LinkedHashMap<>();

    private AuthoredVisualProvider() { }

    @Override public String id() { return "pale_mirror_visuals:authored_regions"; }

    @Override public AdapterHealth health() {
        return new AdapterHealth(AdapterHealth.Status.AVAILABLE, "fresh-world authored genesis active",
                Set.of(Capability.AUTHORED_REGION_GENESIS, Capability.MANAGED_SETTLEMENT_RESIDENTS,
                        Capability.DYNAMIC_WORLD_PRESENTATION));
    }

    @Override public Collection<AuthoredRegionSeed> discoverAuthoredRegions(ServerLevel level) {
        return markers.discovered(level.dimension().location().toString());
    }

    @Override public void applyProjection(ServerLevel level, VisualStateProjection projection) {
        var regions = markers.discovered(level.dimension().location().toString());
        threatHearts.reconcile(level, projection, regions);
        settlementCues.reconcile(level, projection, regions);
    }

    @Override public synchronized Collection<ResidentDeathObservation> drainResidentDeaths(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        var result = deaths.values().stream().filter(value -> value.dimensionId().equals(dimension))
                .toList();
        result.forEach(value -> deaths.remove(value.observationId()));
        return result;
    }

    public synchronized void observeDeath(ResidentDeathObservation observation) {
        deaths.putIfAbsent(observation.observationId(), observation);
        while (deaths.size() > 128) deaths.remove(deaths.firstEntry().getKey());
    }

    public AuthoredRegionMarkerIndex markers() { return markers; }

    public synchronized void resetRuntime() {
        deaths.clear();
        markers.clear();
    }
}
