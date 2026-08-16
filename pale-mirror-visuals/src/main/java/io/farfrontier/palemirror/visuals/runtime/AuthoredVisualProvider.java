package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.api.VisualProvider;
import io.farfrontier.palemirror.api.VisualStateProjection;
import io.farfrontier.palemirror.api.ResidentDeathObservation;
import io.farfrontier.palemirror.api.JourneyObservation;
import io.farfrontier.palemirror.api.JourneyProjection;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import io.farfrontier.palemirror.visuals.resident.JourneyProjectionRuntime;
import io.farfrontier.palemirror.visuals.threat.ThreatHeartRuntime;
import io.farfrontier.palemirror.visuals.genesis.AuthoredAssetCatalog;
import io.farfrontier.palemirror.visuals.genesis.AuthoredModuleCompiler;
import java.util.Collection;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

/** Runtime bridge. World-generation markers are the only accepted discovery facts. */
public final class AuthoredVisualProvider implements VisualProvider {
    public static final AuthoredVisualProvider INSTANCE = new AuthoredVisualProvider();
    private final AuthoredRegionMarkerIndex markers = new AuthoredRegionMarkerIndex();
    private final ThreatHeartRuntime threatHearts = new ThreatHeartRuntime();
    private final SettlementStateCueRuntime settlementCues = new SettlementStateCueRuntime();
    private final VisualProjectionLedger projections = new VisualProjectionLedger();
    private final JourneyProjectionRuntime journeys = new JourneyProjectionRuntime();
    private final io.farfrontier.palemirror.visuals.foundry.FoundryAuditEngine foundry =
            new io.farfrontier.palemirror.visuals.foundry.FoundryAuditEngine();
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

    @Override public Collection<io.farfrontier.palemirror.api.VisualAuditView> visualAuditViews(
            ServerLevel level, String regionId) {
        java.util.List<io.farfrontier.palemirror.api.VisualAuditView> views = new java.util.ArrayList<>(
                markers.discovered(level.dimension().location().toString()).stream()
                .filter(seed -> seed.planId().equals(regionId)).findFirst()
                .map(io.farfrontier.palemirror.visuals.genesis.VisualAuditPlanner::views)
                .orElse(java.util.List.of()));
        foundryAudit(level, regionId, io.farfrontier.palemirror.api.FoundryAuditPhase.SETTLED)
                .map(io.farfrontier.palemirror.visuals.foundry.FoundryAuditCameras::views)
                .ifPresent(views::addAll);
        return java.util.List.copyOf(views);
    }

    @Override public java.util.Optional<io.farfrontier.palemirror.api.FoundryAuditReport> foundryAudit(
            ServerLevel level, String regionId, io.farfrontier.palemirror.api.FoundryAuditPhase phase) {
        var catalog = FrontierGenesisRuntime.compiledCatalog();
        if (catalog == null || catalog.manifests().stream().noneMatch(value -> value.planId().equals(regionId))) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(foundry.audit(catalog, regionId, level, phase));
        } catch (RuntimeException failure) {
            PaleMirrorVisualsMod.LOGGER.error("Foundry audit failed for {} at {}", regionId, phase, failure);
            return java.util.Optional.empty();
        }
    }

    @Override public java.util.Optional<io.farfrontier.palemirror.api.FoundryAuditExport> exportFoundryAudit(
            ServerLevel level, String regionId, io.farfrontier.palemirror.api.FoundryAuditPhase phase) {
        return foundryAudit(level, regionId, phase).flatMap(report -> {
            try {
                return java.util.Optional.of(new io.farfrontier.palemirror.visuals.foundry.FoundryReportExporter()
                        .export(level, report));
            } catch (java.io.IOException failure) {
                PaleMirrorVisualsMod.LOGGER.error("Cannot export Foundry report for {}", regionId, failure);
                return java.util.Optional.empty();
            }
        });
    }

    @Override public java.util.Optional<io.farfrontier.palemirror.api.FoundryBlockInspection> inspectFoundryBlock(
            ServerLevel level, String regionId, io.farfrontier.palemirror.api.VisualPoint position) {
        var catalog = FrontierGenesisRuntime.compiledCatalog();
        if (catalog == null) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(foundry.inspect(catalog, regionId, level, position));
        } catch (IllegalArgumentException unavailable) {
            return java.util.Optional.empty();
        }
    }

    @Override public java.util.Optional<io.farfrontier.palemirror.api.VisualModuleSnapshot> compileAuthoredModule(
            io.farfrontier.palemirror.api.StagedVisualModule module) {
        try {
            return java.util.Optional.of(AuthoredModuleCompiler.compile(module.module()));
        } catch (RuntimeException invalid) {
            return java.util.Optional.empty();
        }
    }

    @Override public java.util.Optional<io.farfrontier.palemirror.api.VisualModuleSnapshot> compileAuthoredModuleState(
            io.farfrontier.palemirror.api.VisualModulePlacement module, String state) {
        try {
            return java.util.Optional.of(AuthoredModuleCompiler.compileState(module, state));
        } catch (RuntimeException invalid) {
            PaleMirrorVisualsMod.LOGGER.error("Cannot compile authored state {} for {}", state, module.instanceId(), invalid);
            return java.util.Optional.empty();
        }
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

    @Override public boolean authoredMineSiteReady(ServerLevel level, AuthoredRegionSeed region,
                                                    io.farfrontier.palemirror.api.AuthoredMineSitePlan mine) {
        java.util.Set<Long> chunks = new java.util.LinkedHashSet<>();
        mine.initialModules().forEach(module -> addChunks(chunks, module.footprint()));
        mine.foundations().forEach(foundation -> addChunks(chunks, foundation.footprint()));
        mine.semanticVolumes().forEach(volume -> addChunks(chunks, volume.bounds()));
        for (long chunk : chunks) {
            var expected = FrontierGenesisRuntime.compiledChunk(chunk);
            if (expected == null || !expected.stamp().equals(VisualGenesisSavedData.get(level).observedStamp(chunk))) {
                return false;
            }
        }
        return true;
    }

    private static void addChunks(java.util.Set<Long> chunks, io.farfrontier.palemirror.api.VisualBounds bounds) {
        int minX = bounds.min().x() >> 4; int maxX = bounds.max().x() >> 4;
        int minZ = bounds.min().z() >> 4; int maxZ = bounds.max().z() >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            chunks.add(net.minecraft.world.level.ChunkPos.asLong(x, z));
        }
    }

    @Override public void applyProjection(ServerLevel level, VisualStateProjection projection) {
        projections.accept(level.dimension().location().toString(), projection);
    }

    /** Applies the retained latest desired state to loaded presentation only. */
    public void tickVisuals(ServerLevel level) {
        tickVisuals(level, level.getServer().getTickCount());
    }

    void tickVisuals(ServerLevel level, long cueTick) {
        String dimension = level.dimension().location().toString();
        var regions = markers.discovered(dimension);
        for (VisualStateProjection projection : projections.projections(dimension)) {
            threatHearts.reconcile(level, projection, regions, cueTick);
            settlementCues.reconcile(level, projection, regions, cueTick);
        }
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
        projections.clear();
        threatHearts.clear();
    }
}
