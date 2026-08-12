package io.farfrontier.palemirror.internal;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandExecutor;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
import io.farfrontier.palemirror.domain.DomainServices;
import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.ScenarioArchetype;
import io.farfrontier.palemirror.internal.materialization.MaterializationScheduler;
import io.farfrontier.palemirror.internal.domain.DomainTransaction;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.adapter.ActorDamageResult;
import io.farfrontier.palemirror.internal.combat.PmProjectileRuntime;
import io.farfrontier.palemirror.internal.combat.RuntimeCombatFacade;
import io.farfrontier.palemirror.internal.content.ThreatTierDefinitions;
import io.farfrontier.palemirror.internal.debug.RuntimeDebugController;
import io.farfrontier.palemirror.internal.world.CampaignRegionBootstrapper;
import io.farfrontier.palemirror.internal.world.RegionalLogisticsRuntime;
import io.farfrontier.palemirror.internal.world.SettlementObservationRuntime;
import io.farfrontier.palemirror.internal.presentation.CampaignPresentationRuntime;
import io.farfrontier.palemirror.internal.presentation.RegionalJournal;
import io.farfrontier.palemirror.internal.presentation.RegionalMarkerRuntime;
import io.farfrontier.palemirror.internal.presentation.PlaytestMetrics;
import io.farfrontier.palemirror.internal.presentation.atlas.RegionalAtlasProjection;
import io.farfrontier.palemirror.internal.network.AtlasSnapshotPayload;
import io.farfrontier.palemirror.internal.economy.ResourceTransferRuntime;
import io.farfrontier.palemirror.internal.economy.SettlementDepotRuntime;
import io.farfrontier.palemirror.internal.settlement.RefugeeCampRuntime;
import io.farfrontier.palemirror.internal.settlement.PreparedEvacuationRuntime;
import io.farfrontier.palemirror.internal.settlement.SettlementDevelopmentRuntime;
import io.farfrontier.palemirror.domain.SourceGateStatus;
import io.farfrontier.palemirror.internal.observation.Observation;
import io.farfrontier.palemirror.internal.observation.ObservationReconciler;
import io.farfrontier.palemirror.internal.observation.PlayerEnteredFacilityBounds;
import io.farfrontier.palemirror.internal.observation.ThreatControllerDestroyed;
import io.farfrontier.palemirror.internal.observation.EncounterActorDestroyed;
import io.farfrontier.palemirror.internal.observation.GatePartDestroyed;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.RegionalDiscoveryRuntime;
import io.farfrontier.palemirror.internal.world.ManagedRailwayRuntime;
import io.farfrontier.palemirror.internal.world.VanillaMinecartRouteRuntime;
import io.farfrontier.palemirror.internal.world.SourceGatePartRef;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import io.farfrontier.palemirror.internal.world.TestMineTemplate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
/** Server-thread coordinator. It is intentionally the only bridge between domain and Minecraft layers. */
public final class PaleMirrorRuntime {
    private static final Map<MinecraftServer, PaleMirrorRuntime> INSTANCES = new IdentityHashMap<>();
    private static final int SIMULATION_INTERVAL_TICKS = 1200;
    private static final int LOGISTICS_OBSERVATION_INTERVAL_TICKS = 40;
    private static final int SETTLEMENT_OBSERVATION_INTERVAL_TICKS = 200;
    private static final int LOGISTICS_PROOF_WINDOW_STEPS = 8;
    private final MinecraftServer server;
    private final PaleMirrorSavedData data;
    private final DomainServices domainServices = new DomainServices();
    private final DomainCommandProcessor commandProcessor = domainServices.commands();
    private final DomainCommandExecutor commands;
    private final ObservationReconciler reconciler;
    private final MaterializationScheduler materializationScheduler = new MaterializationScheduler();
    private final PreparedEvacuationRuntime evacuation;
    private final NarrativeCandidateRuntime narrative;
    private final RuntimeDebugController debug;
    private final PlaytestMetrics playtestMetrics;
    private final RuntimeCombatFacade combat;
    private PaleMirrorRuntime(MinecraftServer server) {
        this.server = server;
        this.data = PaleMirrorSavedData.get(server.overworld());
        this.commands = new DomainTransaction(data, commandProcessor);
        this.reconciler = new ObservationReconciler(commands);
        this.narrative = new NarrativeCandidateRuntime(server, data, commands, this::audienceFor);
        this.evacuation = new PreparedEvacuationRuntime(data, commands, this::audienceFor, this::handleDomainEvents);
        this.debug = new RuntimeDebugController(server, data, commands, this::handleDomainEvents);
        this.playtestMetrics = new PlaytestMetrics(server);
        this.combat = new RuntimeCombatFacade(data, observation -> publish(observation));
        if (data.effectLeases().recoverAfterRestart(server.overworld().getGameTime())) data.setDirty();
        if (data.threatCombat().recoverAfterRestart(server.overworld().getGameTime())) data.setDirty();
        PmProjectileRuntime.discardUnknownAfterRestart(server, data);
        AdapterRegistry.onServerStarted(server);
        ManagedRailwayRuntime.installPlacementAuthority(data);
    }
    public static PaleMirrorRuntime forServer(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, PaleMirrorRuntime::new);
    }
    public static void stop(MinecraftServer server) {
        CampaignRegionBootstrapper.stop(server);
        ManagedRailwayRuntime.stop(server);
        INSTANCES.remove(server);
    }
    public void tick() {
        domainServices.setThreatTierPolicy(ThreatTierDefinitions.current());
        if (server.overworld().getGameTime() % SETTLEMENT_OBSERVATION_INTERVAL_TICKS == 0
                && SettlementObservationRuntime.observeNearPlayers(server, data, commands)) data.setDirty();
        boolean authoredProvider = io.farfrontier.palemirror.api.PaleMirrorVisuals.provider().isPresent();
        if (authoredProvider && io.farfrontier.palemirror.internal.world.AuthoredRegionRegistrar.discover(server, data, commands)) {
            data.setDirty();
        }
        handleDomainEvents(io.farfrontier.palemirror.internal.world.AuthoredResidentReconciler.reconcile(server, data, commands));
        CampaignRegionBootstrapper.tick(server, data, commands,
                !authoredProvider && debug.automaticBindingEnabled());
        if (VanillaMinecartRouteRuntime.tick(server, data, commands)) data.setDirty();
        if (ManagedRailwayRuntime.tick(server, data, commands)) data.setDirty();
        if (SettlementDepotRuntime.tick(server, data, commands)) data.setDirty();
        if (ResourceTransferRuntime.tick(server, data, commands)) data.setDirty();
        if (RefugeeCampRuntime.tick(server, data, commands)) data.setDirty();
        if (SettlementDevelopmentRuntime.tick(server, data, commands)) data.setDirty();
        if (server.overworld().getGameTime() % LOGISTICS_OBSERVATION_INTERVAL_TICKS == 0) {
            handleDomainEvents(RegionalLogisticsRuntime.observe(server, data, commands, LOGISTICS_PROOF_WINDOW_STEPS));
        }
        if (server.overworld().getGameTime() % SIMULATION_INTERVAL_TICKS == 0) {
            RegionalDiscoveryRuntime.observeAudienceAccess(server, data, commands, this::audienceFor, this::handleDomainEvents);
            advanceSimulation(1);
            triggerDueRegionCrises();
            offerPendingNarrativeOpportunities();
            if (data.refugeeAnchorPermits().compact(data.worldState().simulationStep())) data.setDirty();
        }
        reconcilePendingGates();
        observePlayers();
        reconcileScenarioCapabilities();
        reconcileMaterialization();
        long gameTick = server.overworld().getGameTime();
        if (data.effectLeases().expireDue(gameTick)) data.setDirty();
        if (gameTick % 1200L == 0L && data.effectLeases().compact(gameTick)) data.setDirty();
        if (gameTick % 1200L == 0L && data.materializationJobs().compactTerminalJobs()) data.setDirty();
        if (data.threatCombat().expireAndCompact(gameTick)) data.setDirty();
        AdapterRegistry.tickRuntime(server, data);
        io.farfrontier.palemirror.internal.world.VisualProjectionPublisher.publish(server, data);
        RegionalMarkerRuntime.tick(server, data);
        debug.renderZoneMarkers();
    }
    public TestMineRecord registerThreatSite(ServerPlayer player, WorldObjectId id, InfectionSourceId source) {
        if (data.testMines().containsKey(id)) throw new IllegalStateException("PM threat site already exists: " + id.value());
        ServerLevel level = player.serverLevel();
        TestMineRecord mine = TestMineTemplate.place(level, player.blockPosition().above(2), id, audienceFor(player));
        data.registerTestMine(mine);
        commands.execute(data.worldState(), new DomainCommand.RegisterFacility(
                new FacilityState(id, source, 80, 10, 10), "test-mine:" + id.value()));
        data.setDirty();
        return mine;
    }
    public List<DomainEvent> advanceSimulation(int steps) {
        domainServices.setThreatTierPolicy(ThreatTierDefinitions.current());
        List<DomainEvent> events = commands.execute(data.worldState(), new DomainCommand.AdvanceSimulation(steps));
        handleDomainEvents(events);
        if (!events.isEmpty()) data.setDirty();
        return events;
    }
    public boolean accept(String scenarioId, StoryAudienceId audience) {
        try {
            if (!data.worldState().scenario(scenarioId).map(value -> value.audience().equals(audience)).orElse(false)) return false;
            List<DomainEvent> events = commands.execute(data.worldState(), new DomainCommand.AcceptScenario(scenarioId));
            boolean changed = !events.isEmpty();
            handleDomainEvents(events);
            if (changed) data.setDirty();
            return changed;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
    public boolean decline(String scenarioId, StoryAudienceId audience) {
        try {
            if (!data.worldState().scenario(scenarioId).map(value -> value.audience().equals(audience)).orElse(false)) return false;
            List<DomainEvent> events = commands.execute(data.worldState(), new DomainCommand.DeclineScenario(scenarioId));
            boolean changed = !events.isEmpty();
            handleDomainEvents(events);
            if (changed) data.setDirty();
            return changed;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
    public boolean beginSettlementEvacuation(String communityId, StoryAudienceId audience, String causationId) {
        return evacuation.begin(communityId, audience, causationId);
    }
    public boolean issueRefugeeAnchor(ServerPlayer player, String communityId) {
        return evacuation.issue(player, communityId);
    }

    public boolean placeRefugeeAnchor(ServerPlayer player, net.minecraft.core.BlockPos anchor, ItemStack stack) {
        return evacuation.place(player, anchor, stack);
    }
    public List<io.farfrontier.palemirror.domain.ScenarioInstance> offered(StoryAudienceId audience) {
        return data.worldState().scenarios().stream()
                .filter(value -> value.audience().equals(audience)
                        && value.status() == io.farfrontier.palemirror.domain.ScenarioStatus.OFFERED)
                .toList();
    }

    public StoryAudienceId audienceFor(ServerPlayer player) {
        String binding = player.getTeam() == null ? "player:" + player.getUUID() : "team:" + player.getTeam().getName();
        StoryAudienceId audience = data.audienceMappings().computeIfAbsent(binding, key ->
                new StoryAudienceId("pm:audience:" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8))));
        data.setDirty();
        return audience;
    }

    public StoryAudienceId defaultAudience() { return StoryAudienceId.globalTestAudience(); }
    public String status() {
        return "step=" + data.worldState().simulationStep() + ", facilities=" + data.worldState().facilities().size()
                + ", communities=" + data.worldState().communities().size() + ", places=" + data.worldState().places().size()
                + ", routeContracts=" + data.worldState().routeContracts().size()
                + ", regions=" + data.worldState().livingRegions().size() + ", scenarios=" + data.worldState().scenarios().size()
                + ", jobs=" + data.materializationJobs().jobs().size()
                + ", effectLeases=" + data.effectLeases().leases().size() + ", combatActors=" + data.threatCombat().actors().size()
                + ", projectiles=" + data.threatCombat().projectiles().size() + ", quarantine=" + data.quarantine().records().size()
                + ", resourceTransfers=" + data.resourceTransfers().transfers().size()
                + ", depots=" + data.settlementDepots().size() + ", vanillaMinecartRoutes=" + data.vanillaMinecartRoutes().size();
    }

    /** Admin-facing causal state, deliberately derived from canonical state rather than the physical presentation. */
    public String explainSettlement(String settlementId) {
        return RegionalJournal.explainSettlement(data, settlementId);
    }

    public String timeline(String objectId) {
        return RegionalJournal.timeline(data, objectId);
    }

    public String logisticsStatus() { return RegionalLogisticsRuntime.describe(data); }

    public io.farfrontier.palemirror.internal.materialization.MaterializationJob materializationJob(WorldObjectId id) {
        return data.materializationJobs().activeFor(id.value(), "threat").orElse(null);
    }

    public ManagedRailwayRuntime.CommissioningResult commissionRedValleyExercise(String regionId) {
        return ManagedRailwayRuntime.commissionRedValleyExercise(server, data, regionId);
    }

    public RuntimeDebugController debug() { return debug; }

    public void recordSettlementDeath(Entity entity, DamageSource source) {
        if (SettlementObservationRuntime.observeDeath(data, commands, entity, source)) data.setDirty();
    }

    /** Returns true only for a registered observed-settlement marker, never for an arbitrary vanilla lectern. */
    public boolean presentSettlementJournal(ServerPlayer player, net.minecraft.core.BlockPos position) {
        return CampaignPresentationRuntime.presentJournal(data, player, position, this::audienceFor);
    }

    public void refreshRegionalLedger(ServerPlayer player, ItemStack stack) {
        CampaignPresentationRuntime.refreshLedger(data, player, stack, audienceFor(player));
    }

    /** Produces a bounded read-only projection; the client never reads SavedData directly. */
    public AtlasSnapshotPayload atlasSnapshot(ServerPlayer player, String notice) {
        return atlasSnapshot(player, notice, true);
    }

    public AtlasSnapshotPayload atlasSnapshot(ServerPlayer player, String notice, boolean openScreen) {
        AtlasSnapshotPayload snapshot = RegionalAtlasProjection.snapshot(data, audienceFor(player), notice, openScreen);
        if (openScreen) playtestMetrics.atlasOpened(player.getUUID(), data.worldState().simulationStep(),
                snapshot.snapshot().getList("regions", net.minecraft.nbt.Tag.TAG_COMPOUND).size());
        return snapshot;
    }

    public ResourceTransferRuntime.InteractionResult interactWithSupplyDepot(ServerPlayer player,
                                                                              net.minecraft.core.BlockPos position) {
        RegionalDiscoveryRuntime.discoverDepot(data, commands, player, audienceFor(player), position, this::handleDomainEvents);
        ResourceTransferRuntime.InteractionResult result = ResourceTransferRuntime.prepare(data, commands, player,
                position, audienceFor(player));
        if (result.handled() && result.success()) data.setDirty();
        return result;
    }

    public boolean isReservedTransferItem(ItemStack stack) { return ResourceTransferRuntime.isReserved(stack); }

    /** Records a legacy source stack without granting it PM authority or deleting player data. */
    public void quarantineLegacyItem(ServerPlayer player, String sourceId, String fingerprint, String reason) {
        data.quarantine().observe(sourceId, io.farfrontier.palemirror.internal.quarantine.QuarantineKind.ITEM_STACK,
                fingerprint, player.getUUID(), server.overworld().getGameTime(), reason);
        data.setDirty();
    }

    public String inspectObject(String objectId) {
        return io.farfrontier.palemirror.internal.debug.ThreatSiteDiagnostics.inspect(data, objectId);
    }

    public void threatDestroyed(String objectId, String causationId) {
        combat.threatDestroyed(objectId, causationId);
    }

    /** The event layer asks this before allowing damage to the PM controller. */
    public boolean controllerVulnerable(String objectId) {
        return combat.controllerVulnerable(objectId);
    }

    /** All product-controller damage is consumed into the persisted PM combat ledger. */
    public ActorDamageResult receiveThreatControllerDamage(Entity entity, DamageSource source, float amount) {
        return combat.receiveThreatControllerDamage(entity, source, amount);
    }

    /** Reports an exact source-owned gate carrier death; stale identities are ignored. */
    public void gateEntityDestroyed(String objectId, String slotId, UUID entityId) {
        combat.gateEntityDestroyed(objectId, slotId, entityId);
    }

    /** Routes a block observation to the facility's source adapter without naming the physical representation. */
    public void gateBlockDestroyed(ServerLevel level, net.minecraft.core.BlockPos position) {
        combat.gateBlockDestroyed(level, position);
    }

    public void settlementBlockDamaged(ServerLevel level, net.minecraft.core.BlockPos position, UUID playerId) {
        if (SettlementObservationRuntime.observePlayerBlockDamage(data, commands, level, position, playerId)) data.setDirty();
    }

    /** Records only local physical evidence; unloaded chunks retain their last observed graph revision. */
    public void railTopologyChanged(ServerLevel level, net.minecraft.core.BlockPos position) {
        if (VanillaMinecartRouteRuntime.observeBlockChange(data, level, position)) data.setDirty();
    }

    public List<DomainEvent> publish(Observation observation) {
        List<DomainEvent> events = reconciler.reconcile(data, observation);
        if (observation instanceof ThreatControllerDestroyed destroyed && !events.isEmpty()) {
            TestMineRecord mine = data.testMines().get(destroyed.facilityId());
            if (mine != null) mine.setAnchorId(null);
        }
        return events;
    }

    /**
     * Resolves incoming damage only for an exact persisted PM actor reference.
     * This remains outside domain state: encounter combat is optional physical
     * presentation, while an adapter can never directly alter a facility.
     */
    public ActorDamageResult receiveSourceActorDamage(Entity entity, DamageSource source, float amount) {
        return combat.receiveSourceActorDamage(entity, source, amount);
    }

    private void observePlayers() {
        for (TestMineRecord mine : data.testMines().values()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.serverLevel().dimension().location().toString().equals(mine.dimensionId()) && mine.contains(player.blockPosition())) {
                    FacilityState facility = data.worldState().facility(mine.id()).orElse(null);
                    if (facility != null) publish(new PlayerEnteredFacilityBounds(
                            "player-entered:" + player.getUUID() + ":" + mine.id().value() + ":" + facility.desiredRevision(),
                            audienceFor(player), mine.id()));
                }
            }
        }
        RegionalDiscoveryRuntime.observePlayers(server, data, commands, this::audienceFor, this::handleDomainEvents);
    }

    private void reconcileMaterialization() {
        materializationScheduler.schedule(server, data).forEach(this::publish);
    }

    private void handleDomainEvents(List<DomainEvent> events) {
        playtestMetrics.domainEvents(events);
        narrative.handleDomainEvents(events);
    }

    private void reconcileScenarioCapabilities() {
        data.worldState().scenarios().forEach(scenario -> {
            java.util.Set<io.farfrontier.palemirror.api.Capability> requirements = scenario.requiredCapabilities().stream()
                    .map(io.farfrontier.palemirror.api.Capability::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
            FacilityState facility = data.worldState().facility(scenario.target()).orElseGet(() ->
                    data.worldState().livingRegions().stream().filter(region -> region.communityId().equals(scenario.target()))
                            .findFirst().flatMap(region -> data.worldState().facility(region.primaryFacilityId())).orElse(null));
            boolean gateNeedsSource = facility != null && facility.gate().status().protectsController();
            boolean available = facility != null && AdapterRegistry.supports(facility.infectionSource(), requirements)
                    && (!gateNeedsSource || AdapterRegistry.sourceAdapter(facility.infectionSource()).health().status()
                    == io.farfrontier.palemirror.api.AdapterHealth.Status.AVAILABLE);
            if (!available || scenario.status() == io.farfrontier.palemirror.domain.ScenarioStatus.BLOCKED) {
                List<DomainEvent> events = commands.execute(data.worldState(),
                        new DomainCommand.SetScenarioBlocked(scenario.id(), !available,
                                available ? "capabilities restored" : gateNeedsSource
                                        ? "source gate capability unavailable" : "required capability unavailable"));
                if (!events.isEmpty()) data.setDirty();
            }
        });
    }
    private void reconcilePendingGates() {
        data.worldState().facilities().forEach(facility -> {
            if (facility.gate().status() != SourceGateStatus.PENDING) return;
            var plan = AdapterRegistry.sourceAdapter(facility.infectionSource()).gatePlan(facility, server.overworld().getSeed());
            List<DomainEvent> events = plan.map(value -> commands.execute(data.worldState(),
                    new DomainCommand.ActivateGate(facility.id(), value, "pm:source-gate-activate"))).orElseGet(() ->
                    commands.execute(data.worldState(), new DomainCommand.BypassGate(facility.id(), "pm:source-no-gate:" + facility.infectionSource().value())));
            if (!events.isEmpty()) data.setDirty();
        });
    }
    private void triggerDueRegionCrises() {
        List<DomainEvent> produced = new ArrayList<>();
        data.worldState().livingRegions().stream().filter(region -> region.incidentDue(data.worldState().simulationStep(),
                        region.primaryAudience() == null ? null : data.worldState()
                                .regionKnowledge(region.primaryAudience(), region.id()).orElse(null)))
                .filter(this::baselineInfrastructureReady)
                .forEach(region -> {
                    List<DomainEvent> events = commands.execute(data.worldState(), new DomainCommand.TriggerFacilityInfection(
                            region.primaryFacilityId(), "region-crisis:" + region.id()));
                    if (!events.isEmpty()) {
                        produced.addAll(events);
                        data.setDirty();
                    }
                });
        // Narrator sees all simultaneous regional incidents before it chooses
        // one story for an audience, rather than taking HashMap/tick order as
        // an accidental pacing policy.
        if (!produced.isEmpty()) handleDomainEvents(produced);
    }

    /**
     * Opportunities survive a pacing cooldown. They are re-derived from the
     * canonical history rather than stored as a second mutable queue, so a
     * restart cannot lose or duplicate the pending continuation.
     */
    private void offerPendingNarrativeOpportunities() {
        narrative.offerPendingOpportunities();
    }
    private boolean baselineInfrastructureReady(io.farfrontier.palemirror.domain.LivingRegionState region) {
        var vanilla = data.vanillaMinecartRoutes().get(region.id());
        if (vanilla != null) return vanilla.status() == io.farfrontier.palemirror.internal.world.VanillaMinecartRouteStatus.ACTIVE;
        return java.util.Optional.ofNullable(data.campaignCommissioning().get(region.id()))
                .filter(record -> record.status() == io.farfrontier.palemirror.internal.world.CampaignCommissioningStatus.ACTIVE
                        && data.worldState().simulationStep() >= record.infectionEligibleAtStep()).isPresent();
    }
}
