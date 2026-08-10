package io.farfrontier.palemirror.internal;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import io.farfrontier.palemirror.domain.LivingRegionState;
import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
import io.farfrontier.palemirror.domain.DomainServices;
import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.domain.Narrator;
import io.farfrontier.palemirror.domain.ScenarioDefinitionRef;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.RecognitionState;
import io.farfrontier.palemirror.domain.ScenarioArchetype;
import io.farfrontier.palemirror.internal.materialization.MaterializationScheduler;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.adapter.LogisticsRouteContract;
import io.farfrontier.palemirror.internal.adapter.ActorDamageResult;
import io.farfrontier.palemirror.internal.combat.PmProjectileRuntime;
import io.farfrontier.palemirror.internal.content.ScenarioDefinition;
import io.farfrontier.palemirror.internal.content.ScenarioDefinitions;
import io.farfrontier.palemirror.internal.content.EncounterDefinitions;
import io.farfrontier.palemirror.internal.content.ThreatTierDefinitions;
import io.farfrontier.palemirror.internal.world.CampaignRegionBootstrapper;
import io.farfrontier.palemirror.internal.world.CampaignRegionRecord;
import io.farfrontier.palemirror.internal.world.RegionalLogisticsRuntime;
import io.farfrontier.palemirror.internal.world.SettlementObservationRuntime;
import io.farfrontier.palemirror.internal.presentation.RegionalJournal;
import io.farfrontier.palemirror.internal.economy.ResourceTransferRuntime;
import io.farfrontier.palemirror.internal.economy.SettlementDepotRuntime;
import io.farfrontier.palemirror.internal.settlement.RefugeeCampRuntime;
import io.farfrontier.palemirror.internal.settlement.SettlementDevelopmentRuntime;
import io.farfrontier.palemirror.domain.SourceGateStatus;
import io.farfrontier.palemirror.internal.observation.Observation;
import io.farfrontier.palemirror.internal.observation.ObservationReconciler;
import io.farfrontier.palemirror.internal.observation.PlayerEnteredFacilityBounds;
import io.farfrontier.palemirror.internal.observation.ThreatControllerDestroyed;
import io.farfrontier.palemirror.internal.observation.EncounterActorDestroyed;
import io.farfrontier.palemirror.internal.observation.GatePartDestroyed;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
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
    private final DomainCommandProcessor commands = domainServices.commands();
    private final Narrator narrator = domainServices.narrator();
    private final ObservationReconciler reconciler = new ObservationReconciler(commands);
    private final MaterializationScheduler materializationScheduler = new MaterializationScheduler();

    private PaleMirrorRuntime(MinecraftServer server) {
        this.server = server;
        this.data = PaleMirrorSavedData.get(server.overworld());
        if (data.effectLeases().recoverAfterRestart(server.overworld().getGameTime())) data.setDirty();
        if (data.threatCombat().recoverAfterRestart(server.overworld().getGameTime())) data.setDirty();
        PmProjectileRuntime.discardUnknownAfterRestart(server, data);
        AdapterRegistry.onServerStarted(server);
    }

    public static PaleMirrorRuntime forServer(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, PaleMirrorRuntime::new);
    }

    public static void stop(MinecraftServer server) { INSTANCES.remove(server); }
    public void tick() {
        domainServices.setThreatTierPolicy(ThreatTierDefinitions.current());
        if (server.overworld().getGameTime() % SETTLEMENT_OBSERVATION_INTERVAL_TICKS == 0
                && SettlementObservationRuntime.observeNearPlayers(server, data, commands)) data.setDirty();
        CampaignRegionBootstrapper.tick(server, data, commands);
        if (SettlementDepotRuntime.tick(server, data)) data.setDirty();
        if (ResourceTransferRuntime.tick(server, data, commands)) data.setDirty();
        if (RefugeeCampRuntime.tick(server, data)) data.setDirty();
        if (SettlementDevelopmentRuntime.tick(server, data, commands)) data.setDirty();
        if (server.overworld().getGameTime() % LOGISTICS_OBSERVATION_INTERVAL_TICKS == 0) {
            handleDomainEvents(RegionalLogisticsRuntime.observe(server, data, commands, LOGISTICS_PROOF_WINDOW_STEPS));
        }
        if (server.overworld().getGameTime() % SIMULATION_INTERVAL_TICKS == 0) {
            advanceSimulation(1);
            triggerDueRegionCrises();
        }
        reconcilePendingGates();
        observePlayers();
        reconcileScenarioCapabilities();
        reconcileMaterialization();
        long gameTick = server.overworld().getGameTime();
        if (data.effectLeases().expireDue(gameTick)) data.setDirty();
        if (gameTick % 1200L == 0L && data.effectLeases().compact(gameTick)) data.setDirty();
        if (data.threatCombat().expireAndCompact(gameTick)) data.setDirty();
        AdapterRegistry.tickRuntime(server, data);
    }

    /**
     * A facility is bound to exactly one canonical infection source at
     * creation.  Source composition is deliberately rejected by construction
     * until a future policy defines conflict and cleanup semantics.
     */
    public TestMineRecord registerThreatSite(ServerPlayer player, WorldObjectId id, InfectionSourceId source) {
        if (data.testMines().containsKey(id)) throw new IllegalStateException("PM threat site already exists: " + id.value());
        ServerLevel level = player.serverLevel();
        TestMineRecord mine = TestMineTemplate.place(level, player.blockPosition().above(2), id, audienceFor(player));
        data.registerTestMine(mine);
        data.worldState().putFacility(new FacilityState(id, source, 80, 10, 10));
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
            boolean changed = !commands.execute(data.worldState(), new DomainCommand.AcceptScenario(scenarioId)).isEmpty();
            if (changed) data.setDirty();
            return changed;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public boolean beginSettlementEvacuation(String communityId, StoryAudienceId audience, String causationId) {
        try {
            List<DomainEvent> events = commands.execute(data.worldState(), new DomainCommand.BeginSettlementEvacuation(
                    new WorldObjectId(communityId), audience, causationId));
            if (!events.isEmpty()) data.setDirty();
            return !events.isEmpty();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public List<io.farfrontier.palemirror.domain.ScenarioInstance> offered(StoryAudienceId audience) {
        return narrator.offeredFor(data.worldState(), audience);
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
                + ", jobs=" + data.testMines().values().stream().filter(value -> value.job() != null).count()
                + ", effectLeases=" + data.effectLeases().leases().size() + ", combatActors=" + data.threatCombat().actors().size()
                + ", projectiles=" + data.threatCombat().projectiles().size() + ", quarantine=" + data.quarantine().records().size()
                + ", resourceTransfers=" + data.resourceTransfers().transfers().size()
                + ", depots=" + data.settlementDepots().size();
    }

    /** Admin-facing causal state, deliberately derived from canonical state rather than the physical presentation. */
    public String explainSettlement(String settlementId) {
        return RegionalJournal.explainSettlement(data, settlementId);
    }

    public String timeline(String objectId) {
        return RegionalJournal.timeline(data, objectId);
    }

    public String logisticsStatus() { return RegionalLogisticsRuntime.describe(data); }

    public void recordSettlementDeath(Entity entity, DamageSource source) {
        if (SettlementObservationRuntime.observeDeath(data, commands, entity, source)) data.setDirty();
    }

    /** Returns true only for a registered observed-settlement marker, never for an arbitrary vanilla lectern. */
    public boolean presentSettlementJournal(ServerPlayer player, net.minecraft.core.BlockPos position) {
        return RegionalJournal.present(data, player, position, this::audienceFor);
    }

    public ResourceTransferRuntime.InteractionResult interactWithSupplyDepot(ServerPlayer player,
                                                                              net.minecraft.core.BlockPos position) {
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
        TestMineRecord mine = data.testMines().get(new WorldObjectId(objectId));
        if (mine == null) return "Unknown PM world object " + objectId;
        String job = mine.job() == null ? "none" : mine.job().jobId() + ":" + mine.job().state()
                + ":op=" + mine.job().nextOperationIndex();
        String source = data.worldState().facility(mine.id()).map(value -> value.infectionSource().value()).orElse("missing");
        return "object=" + mine.id().value() + ", source=" + source + ", lifecycle=" + mine.object().lifecycle()
                + ", anchor=" + (mine.anchorId() == null ? "none" : mine.anchorId())
                + ", encounter=" + mine.encounter().state() + ":" + mine.encounter().profileId()
                + (mine.encounter().diagnostic().isBlank() ? "" : " (" + mine.encounter().diagnostic() + ")")
                + ", job=" + job;
    }

    public void threatDestroyed(String objectId, String causationId) {
        WorldObjectId id = new WorldObjectId(objectId);
        if (data.testMines().containsKey(id)) publish(new ThreatControllerDestroyed(
                "controller-destroyed:" + causationId, id, causationId));
    }

    /** The event layer asks this before allowing damage to the PM controller. */
    public boolean controllerVulnerable(String objectId) {
        try {
            return data.worldState().facility(new WorldObjectId(objectId))
                    .map(FacilityState::controllerVulnerable).orElse(false);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /** Reports an exact source-owned gate carrier death; stale identities are ignored. */
    public void gateEntityDestroyed(String objectId, String slotId, UUID entityId) {
        try {
            WorldObjectId id = new WorldObjectId(objectId);
            TestMineRecord mine = data.testMines().get(id);
            FacilityState facility = data.worldState().facility(id).orElse(null);
            if (mine == null || facility == null || mine.gate().part(slotId)
                    .filter(part -> entityId.equals(part.entityId())).isEmpty()) return;
            publish(new GatePartDestroyed("gate-part:" + id.value() + ":" + slotId + ":" + entityId,
                    id, slotId, "entity:" + entityId));
        } catch (IllegalArgumentException ignored) {
            // Entity data is not trusted provenance until it matches a registered PM reference.
        }
    }

    /** Routes a block observation to the facility's source adapter without naming the physical representation. */
    public void gateBlockDestroyed(ServerLevel level, net.minecraft.core.BlockPos position) {
        for (TestMineRecord mine : data.testMines().values()) {
            if (!mine.dimensionId().equals(level.dimension().location().toString())) continue;
            FacilityState facility = data.worldState().facility(mine.id()).orElse(null);
            if (facility == null) continue;
            AdapterRegistry.sourceAdapter(facility.infectionSource()).gatePartAt(level, mine, position).ifPresent(slot -> publish(
                            new GatePartDestroyed("gate-block:" + mine.id().value() + ":" + slot + ":"
                                    + data.worldState().facility(mine.id()).map(FacilityState::desiredRevision).orElse(0L),
                                    mine.id(), slot, "block:" + position.asLong())));
        }
    }

    public void settlementBlockDamaged(ServerLevel level, net.minecraft.core.BlockPos position, UUID playerId) {
        if (SettlementObservationRuntime.observePlayerBlockDamage(data, commands, level, position, playerId)) data.setDirty();
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
        var gateClaimant = AdapterRegistry.sourceAdapters().stream().filter(adapter -> adapter.matchesGatePart(entity)).findFirst().orElse(null);
        if (gateClaimant != null) return receiveSourceGateDamage(gateClaimant, entity, source, amount);
        var claimant = AdapterRegistry.sourceAdapters().stream().filter(adapter -> adapter.matchesActor(entity)).findFirst().orElse(null);
        if (claimant == null) return ActorDamageResult.passThrough();
        if (!(entity.level() instanceof ServerLevel level)) return ActorDamageResult.blocked("PM actor is not in a server level");
        String objectId = entity.getPersistentData().getString(io.farfrontier.palemirror.internal.adapter.VanillaAnchorAdapter.OBJECT_ID_KEY);
        String slotId = entity.getPersistentData().getString("pale_mirror_encounter_slot");
        if (objectId.isBlank() || slotId.isBlank()) return ActorDamageResult.blocked("PM actor lacks persisted provenance");
        try {
            WorldObjectId facilityId = new WorldObjectId(objectId);
            TestMineRecord mine = data.testMines().get(facilityId);
            FacilityState facility = data.worldState().facility(facilityId).orElse(null);
            if (mine == null || facility == null || !mine.dimensionId().equals(level.dimension().location().toString())) {
                return ActorDamageResult.blocked("PM actor is not attached to its registered threat site");
            }
            var reference = mine.encounter().actor(slotId).orElse(null);
            if (!claimant.source().equals(facility.infectionSource()) || reference == null
                    || !claimant.matchesOwnedActor(entity, mine, slotId)) {
                return ActorDamageResult.blocked("PM actor provenance does not match its canonical source and slot");
            }
            ActorDamageResult result = claimant.receiveDamage(level, mine, entity, reference, source, amount);
            if (!result.intercepts()) return result;
            data.setDirty();
            if (result.disposition() == ActorDamageResult.Disposition.DEFEATED) {
                String causationId = "combat:" + entity.getUUID();
                publish(new EncounterActorDestroyed("encounter-actor-destroyed:" + claimant.source().value() + ":" + causationId,
                        facilityId, claimant.source(), slotId, entity.getUUID()));
            }
            return result;
        } catch (IllegalArgumentException ignored) {
            return ActorDamageResult.blocked("PM actor contains an invalid persisted world object id");
        }
    }

    private ActorDamageResult receiveSourceGateDamage(io.farfrontier.palemirror.internal.adapter.SourceThreatAdapter claimant,
                                                      Entity entity, DamageSource source, float amount) {
        if (!(entity.level() instanceof ServerLevel level)) return ActorDamageResult.blocked("PM gate actor is not in a server level");
        String objectId = entity.getPersistentData().getString(io.farfrontier.palemirror.internal.adapter.VanillaAnchorAdapter.OBJECT_ID_KEY);
        String slotId = entity.getPersistentData().getString("pale_mirror_encounter_slot");
        try {
            WorldObjectId facilityId = new WorldObjectId(objectId);
            TestMineRecord mine = data.testMines().get(facilityId);
            if (mine == null || !mine.dimensionId().equals(level.dimension().location().toString())) {
                return ActorDamageResult.blocked("PM gate actor is not attached to its registered site");
            }
            FacilityState facility = data.worldState().facility(facilityId).orElse(null);
            SourceGatePartRef part = mine.gate().part(slotId).orElse(null);
            if (facility == null || !claimant.source().equals(facility.infectionSource()) || part == null
                    || !entity.getUUID().equals(part.entityId()) || !claimant.matchesOwnedGatePart(entity, mine, slotId)) {
                return ActorDamageResult.blocked("PM gate actor identity is stale");
            }
            ActorDamageResult result = claimant.receiveGateDamage(level, mine, entity, part, source, amount);
            if (result.intercepts()) data.setDirty();
            if (result.disposition() == ActorDamageResult.Disposition.DEFEATED) gateEntityDestroyed(objectId, slotId, entity.getUUID());
            return result;
        } catch (IllegalArgumentException ignored) {
            return ActorDamageResult.blocked("PM gate actor contains invalid provenance");
        }
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
        data.worldState().livingRegions().forEach(region -> {
            if (region.recognition() != RecognitionState.DISCOVERED) return;
            data.worldRegistry().find(region.placeId()).ifPresent(settlement -> {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.serverLevel().dimension().location().toString().equals(settlement.dimensionId())
                            && settlement.contains(player.blockPosition())) {
                        List<DomainEvent> events = commands.execute(data.worldState(),
                                new DomainCommand.DiscoverLivingRegion(region.id(), audienceFor(player),
                                        "player:" + player.getUUID()));
                        if (!events.isEmpty()) data.setDirty();
                    }
                }
            });
        });
    }

    private void reconcileMaterialization() {
        materializationScheduler.schedule(server, data).forEach(this::publish);
    }

    private void handleDomainEvents(List<DomainEvent> events) {
        events.forEach(event -> {
            if (event.type() == io.farfrontier.palemirror.domain.DomainEventType.MINE_INFECTED) {
                boolean regionPrimary = data.worldState().livingRegions().stream()
                        .anyMatch(region -> region.primaryFacilityId().equals(event.subject()));
                if (!regionPrimary) {
                    TestMineRecord mine = data.testMines().get(event.subject());
                    if (mine != null) offerInvestigationScenario(event, mine.primaryAudience());
                }
            } else if (event.type() == io.farfrontier.palemirror.domain.DomainEventType.SETTLEMENT_CRISIS_DETECTED) {
                data.worldState().livingRegions().stream()
                        .filter(region -> region.communityId().equals(event.subject()) && region.primaryAudience() != null)
                        .findFirst().ifPresent(region -> offerSettlementCrisis(event, region.primaryAudience(), region.primaryFacilityId()));
            }
        });
    }

    private void offerInvestigationScenario(DomainEvent event, StoryAudienceId audience) {
        InfectionSourceId source = data.worldState().facility(event.subject()).map(FacilityState::infectionSource).orElse(null);
        if (source == null) return;
        ScenarioDefinition definition = ScenarioDefinitions.forSourceAndArchetype(source, ScenarioArchetype.INVESTIGATION_RECOVERY)
                .stream().findFirst().orElse(null);
        offerScenario(event, audience, source, definition);
    }

    private void offerSettlementCrisis(DomainEvent event, StoryAudienceId audience, WorldObjectId primaryFacilityId) {
        InfectionSourceId source = data.worldState().facility(primaryFacilityId).map(FacilityState::infectionSource).orElse(null);
        if (source == null) return;
        ScenarioDefinition definition = ScenarioDefinitions.forSourceAndArchetype(source, ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS)
                .stream().findFirst().orElse(null);
        offerScenario(event, audience, source, definition);
    }

    private void offerScenario(DomainEvent event, StoryAudienceId audience, InfectionSourceId source, ScenarioDefinition definition) {
        if (definition == null) {
            commands.execute(data.worldState(), new DomainCommand.NoScenario(event, audience, "definition unavailable"));
            return;
        }
        if (!AdapterRegistry.supports(source, definition.capabilities())) {
            commands.execute(data.worldState(), new DomainCommand.NoScenario(event, audience, "required capability unavailable"));
            return;
        }
        String profileId = definition.encounterProfileId();
        String profileVersion = profileId.isBlank() ? "" : EncounterDefinitions.current()
                .get(net.minecraft.resources.ResourceLocation.parse(profileId)) == null ? "unavailable"
                : Integer.toString(EncounterDefinitions.current().get(net.minecraft.resources.ResourceLocation.parse(profileId)).version());
        ScenarioDefinitionRef pinned = new ScenarioDefinitionRef(definition.id().toString(), Integer.toString(definition.version()),
                definition.stages(), definition.capabilities().stream().map(Enum::name).sorted().toList(), definition.cooldownSteps(),
                profileId, profileVersion, definition.archetype());
        commands.execute(data.worldState(), new DomainCommand.OfferScenario(event, audience, pinned));
    }

    private void reconcileScenarioCapabilities() {
        data.worldState().scenarios().forEach(scenario -> {
            java.util.Set<io.farfrontier.palemirror.api.Capability> requirements = scenario.requiredCapabilities().stream()
                    .map(io.farfrontier.palemirror.api.Capability::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
            FacilityState facility = data.worldState().facility(scenario.target()).orElseGet(() ->
                    scenario.archetype() == ScenarioArchetype.SETTLEMENT_SUPPLY_CRISIS
                            ? data.worldState().livingRegions().stream().filter(region -> region.communityId().equals(scenario.target()))
                            .findFirst().flatMap(region -> data.worldState().facility(region.primaryFacilityId())).orElse(null)
                            : null);
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
        data.worldState().livingRegions().stream().filter(region -> region.incidentDue(data.worldState().simulationStep()))
                .forEach(region -> {
                    List<DomainEvent> events = commands.execute(data.worldState(), new DomainCommand.TriggerFacilityInfection(
                            region.primaryFacilityId(), "region-crisis:" + region.id()));
                    if (!events.isEmpty()) {
                        handleDomainEvents(events);
                        data.setDirty();
                    }
                });
    }
}
