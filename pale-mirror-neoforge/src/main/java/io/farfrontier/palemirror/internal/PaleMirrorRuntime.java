package io.farfrontier.palemirror.internal;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
import io.farfrontier.palemirror.domain.DomainServices;
import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.Narrator;
import io.farfrontier.palemirror.domain.ScenarioDefinitionRef;
import io.farfrontier.palemirror.domain.SettlementState;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.materialization.MaterializationScheduler;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import io.farfrontier.palemirror.internal.content.ScenarioDefinition;
import io.farfrontier.palemirror.internal.content.ScenarioDefinitions;
import io.farfrontier.palemirror.internal.content.EncounterDefinitions;
import io.farfrontier.palemirror.internal.content.ThreatTierDefinitions;
import io.farfrontier.palemirror.internal.observation.Observation;
import io.farfrontier.palemirror.internal.observation.ObservationReconciler;
import io.farfrontier.palemirror.internal.observation.PlayerEnteredFacilityBounds;
import io.farfrontier.palemirror.internal.observation.ThreatControllerDestroyed;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import io.farfrontier.palemirror.internal.world.TestMineTemplate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Server-thread coordinator. It is intentionally the only bridge between domain and Minecraft layers. */
public final class PaleMirrorRuntime {
    private static final Map<MinecraftServer, PaleMirrorRuntime> INSTANCES = new IdentityHashMap<>();
    private static final int SIMULATION_INTERVAL_TICKS = 1200;

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
        AdapterRegistry.crimson().verifySandbox(server);
    }

    public static PaleMirrorRuntime forServer(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, PaleMirrorRuntime::new);
    }

    public static void stop(MinecraftServer server) { INSTANCES.remove(server); }

    public void tick() {
        domainServices.setThreatTierPolicy(ThreatTierDefinitions.current());
        if (server.overworld().getGameTime() % SIMULATION_INTERVAL_TICKS == 0) advanceSimulation(1);
        observePlayers();
        reconcileScenarioCapabilities();
        reconcileMaterialization();
        AdapterRegistry.crimson().tickRuntime(server, data);
    }

    public TestMineRecord createTestMine(ServerPlayer player) {
        WorldObjectId id = new WorldObjectId("pale_mirror:test_mine");
        TestMineRecord mine = registerThreatSite(player, id);
        data.worldState().putSettlement(new SettlementState(new WorldObjectId("pale_mirror:test_settlement"), id, 80, 40));
        data.setDirty();
        return mine;
    }

    /** Registers a PM-owned, bounded threat site in any loaded player dimension; it never uses worldgen. */
    public TestMineRecord registerThreatSite(ServerPlayer player, WorldObjectId id) {
        if (data.testMines().containsKey(id)) throw new IllegalStateException("PM threat site already exists: " + id.value());
        ServerLevel level = player.serverLevel();
        TestMineRecord mine = TestMineTemplate.place(level, player.blockPosition().above(2), id, audienceFor(player));
        data.registerTestMine(mine);
        data.worldState().putFacility(new FacilityState(id, 80, 10, 10));
        data.setDirty();
        return mine;
    }

    public List<DomainEvent> advanceSimulation(int steps) {
        domainServices.setThreatTierPolicy(ThreatTierDefinitions.current());
        List<DomainEvent> events = commands.execute(data.worldState(), new DomainCommand.AdvanceSimulation(steps));
        events.forEach(event -> {
            TestMineRecord mine = data.testMines().get(event.subject());
            if (mine != null) offerScenario(event, mine.primaryAudience());
        });
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
                + ", settlements=" + data.worldState().settlements().size() + ", scenarios=" + data.worldState().scenarios().size()
                + ", jobs=" + data.testMines().values().stream().filter(value -> value.job() != null).count();
    }

    public String inspectObject(String objectId) {
        TestMineRecord mine = data.testMines().get(new WorldObjectId(objectId));
        if (mine == null) return "Unknown PM world object " + objectId;
        String job = mine.job() == null ? "none" : mine.job().jobId() + ":" + mine.job().state()
                + ":op=" + mine.job().nextOperationIndex();
        return "object=" + mine.id().value() + ", lifecycle=" + mine.object().lifecycle()
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

    public List<DomainEvent> publish(Observation observation) {
        List<DomainEvent> events = reconciler.reconcile(data, observation);
        if (observation instanceof ThreatControllerDestroyed destroyed) {
            TestMineRecord mine = data.testMines().get(destroyed.facilityId());
            if (mine != null) mine.setAnchorId(null);
        }
        return events;
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
    }

    private void reconcileMaterialization() {
        materializationScheduler.schedule(server, data).forEach(this::publish);
    }

    private void offerScenario(DomainEvent event, StoryAudienceId audience) {
        ScenarioDefinition definition = ScenarioDefinitions.current().get(
                net.minecraft.resources.ResourceLocation.parse("pale_mirror:investigation_recovery"));
        if (definition == null) {
            commands.execute(data.worldState(), new DomainCommand.NoScenario(event, audience, "definition unavailable"));
            return;
        }
        if (!AdapterRegistry.supports(definition.capabilities())) {
            commands.execute(data.worldState(), new DomainCommand.NoScenario(event, audience, "required capability unavailable"));
            return;
        }
        String profileId = definition.encounterProfileId();
        String profileVersion = profileId.isBlank() ? "" : EncounterDefinitions.current()
                .get(net.minecraft.resources.ResourceLocation.parse(profileId)) == null ? "unavailable"
                : Integer.toString(EncounterDefinitions.current().get(net.minecraft.resources.ResourceLocation.parse(profileId)).version());
        ScenarioDefinitionRef pinned = new ScenarioDefinitionRef(definition.id().toString(), Integer.toString(definition.version()),
                definition.stages(), definition.capabilities().stream().map(Enum::name).sorted().toList(), definition.cooldownSteps(),
                profileId, profileVersion);
        commands.execute(data.worldState(), new DomainCommand.OfferScenario(event, audience, pinned));
    }

    private void reconcileScenarioCapabilities() {
        data.worldState().scenarios().forEach(scenario -> {
            java.util.Set<io.farfrontier.palemirror.api.Capability> requirements = scenario.requiredCapabilities().stream()
                    .map(io.farfrontier.palemirror.api.Capability::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
            boolean available = AdapterRegistry.supports(requirements);
            if (!available || scenario.status() == io.farfrontier.palemirror.domain.ScenarioStatus.BLOCKED) {
                List<DomainEvent> events = commands.execute(data.worldState(),
                        new DomainCommand.SetScenarioBlocked(scenario.id(), !available,
                                available ? "capabilities restored" : "required capability unavailable"));
                if (!events.isEmpty()) data.setDirty();
            }
        });
    }
}
