package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.api.VisualProvider;
import io.farfrontier.palemirror.api.VisualStateProjection;
import io.farfrontier.palemirror.api.ResidentDeathObservation;
import io.farfrontier.palemirror.api.JourneyObservation;
import io.farfrontier.palemirror.api.JourneyProjection;
import io.farfrontier.palemirror.visuals.resident.JourneyProjectionRuntime;
import io.farfrontier.palemirror.visuals.threat.ThreatHeartRuntime;
import io.farfrontier.palemirror.visuals.genesis.AuthoredAssetCatalog;
import java.util.Collection;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

/** Runtime bridge. World-generation markers are the only accepted discovery facts. */
public final class AuthoredVisualProvider implements VisualProvider {
    public static final AuthoredVisualProvider INSTANCE = new AuthoredVisualProvider();
    private final AuthoredRegionMarkerIndex markers = new AuthoredRegionMarkerIndex();
    private final ThreatHeartRuntime threatHearts = new ThreatHeartRuntime();
    private final SettlementStateCueRuntime settlementCues = new SettlementStateCueRuntime();
    private final JourneyProjectionRuntime journeys = new JourneyProjectionRuntime();
    private final java.util.LinkedHashMap<String, ResidentDeathObservation> deaths = new java.util.LinkedHashMap<>();

    private AuthoredVisualProvider() { }

    @Override public String id() { return "pale_mirror_visuals:authored_regions"; }

    @Override public io.farfrontier.palemirror.api.GenesisReadiness genesisReadiness() {
        return FrontierGenesisRuntime.readiness();
    }

    @Override public String performanceSummary() { return FrontierGenesisRuntime.performanceSummary(); }

    @Override public AdapterHealth health() {
        String failure = AuthoredAssetCatalog.verify();
        if (failure != null) return new AdapterHealth(AdapterHealth.Status.BLOCKED, failure, Set.of());
        return new AdapterHealth(AdapterHealth.Status.AVAILABLE, "fresh-world authored genesis active",
                Set.of(Capability.AUTHORED_REGION_GENESIS, Capability.MANAGED_SETTLEMENT_RESIDENTS,
                        Capability.DYNAMIC_WORLD_PRESENTATION, Capability.VISIBLE_PM_THREAT_CONTROLLER));
    }

    @Override public Collection<AuthoredRegionSeed> discoverAuthoredRegions(ServerLevel level) {
        return markers.discovered(level.dimension().location().toString());
    }

    @Override public boolean authoredModuleReady(ServerLevel level, AuthoredRegionSeed region,
                                                  io.farfrontier.palemirror.api.VisualModulePlacement module) {
        int minX = module.footprint().min().x() >> 4; int maxX = module.footprint().max().x() >> 4;
        int minZ = module.footprint().min().z() >> 4; int maxZ = module.footprint().max().z() >> 4;
        VisualGenesisSavedData ledger = VisualGenesisSavedData.get(level);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            var expected = FrontierGenesisRuntime.compiledChunk(net.minecraft.world.level.ChunkPos.asLong(x, z));
            if (expected == null || !expected.stamp().equals(ledger.observedStamp(
                    net.minecraft.world.level.ChunkPos.asLong(x, z)))) return false;
        }
        return true;
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

    @Override public void applyJourneyProjection(ServerLevel level, JourneyProjection projection) {
        markers.discovered(level.dimension().location().toString()).stream()
                .filter(seed -> seed.planId().equals(projection.regionId())).findFirst()
                .ifPresent(seed -> journeys.reconcile(level, projection, seed));
    }

    @Override public Collection<JourneyObservation> drainJourneyObservations(ServerLevel level) {
        return journeys.drain();
    }

    @Override public io.farfrontier.palemirror.api.ThreatControllerResult ensureThreatController(
            ServerLevel level, io.farfrontier.palemirror.api.ThreatControllerProjection projection) {
        return threatHearts.ensure(level, projection);
    }

    @Override public io.farfrontier.palemirror.api.ThreatControllerResult removeThreatController(
            ServerLevel level, String objectId) { return threatHearts.remove(level, objectId); }

    @Override public java.util.Optional<String> threatControllerObjectId(net.minecraft.world.entity.Entity entity) {
        return entity instanceof io.farfrontier.palemirror.visuals.threat.ThreatHeartEntity heart
                && !heart.facilityId().isBlank() ? java.util.Optional.of(heart.facilityId()) : java.util.Optional.empty();
    }

    @Override public void presentThreatControllerDamage(net.minecraft.world.entity.Entity entity, boolean defeated) {
        if (!(entity instanceof io.farfrontier.palemirror.visuals.threat.ThreatHeartEntity heart)
                || !(entity.level() instanceof ServerLevel level)) return;
        level.sendParticles(defeated ? net.minecraft.core.particles.ParticleTypes.EXPLOSION
                        : net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR,
                heart.getX(), heart.getY() + 0.8, heart.getZ(), defeated ? 8 : 3, 0.5, 0.5, 0.5, 0.05);
        level.playSound(null, heart.blockPosition(), defeated ? net.minecraft.sounds.SoundEvents.SCULK_SHRIEKER_SHRIEK
                        : net.minecraft.sounds.SoundEvents.SCULK_BLOCK_HIT,
                net.minecraft.sounds.SoundSource.HOSTILE, defeated ? 1.2F : 0.7F, defeated ? 0.7F : 1.0F);
    }

    public synchronized void observeDeath(ResidentDeathObservation observation) {
        deaths.putIfAbsent(observation.observationId(), observation);
        while (deaths.size() > 128) deaths.remove(deaths.firstEntry().getKey());
    }

    public AuthoredRegionMarkerIndex markers() { return markers; }
    public JourneyProjectionRuntime journeys() { return journeys; }

    public synchronized void resetRuntime() {
        deaths.clear();
        markers.clear();
        journeys.clear();
        threatHearts.clear();
    }
}
