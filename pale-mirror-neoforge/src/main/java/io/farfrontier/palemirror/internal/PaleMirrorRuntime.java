package io.farfrontier.palemirror.internal;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import io.farfrontier.palemirror.domain.DomainEngine;
import io.farfrontier.palemirror.domain.DomainEvent;
import io.farfrontier.palemirror.domain.FacilityState;
import io.farfrontier.palemirror.domain.FacilityStatus;
import io.farfrontier.palemirror.domain.Narrator;
import io.farfrontier.palemirror.domain.ScenarioRuntime;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.materialization.TestMineMaterializer;
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
    private final DomainEngine engine = new DomainEngine();
    private final Narrator narrator = new Narrator();
    private final ScenarioRuntime scenarios = new ScenarioRuntime();
    private final TestMineMaterializer materializer = new TestMineMaterializer();

    private PaleMirrorRuntime(MinecraftServer server) {
        this.server = server;
        this.data = PaleMirrorSavedData.get(server.overworld());
    }

    public static PaleMirrorRuntime forServer(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, PaleMirrorRuntime::new);
    }

    public static void stop(MinecraftServer server) { INSTANCES.remove(server); }

    public void tick() {
        if (server.overworld().getGameTime() % SIMULATION_INTERVAL_TICKS == 0) advanceSimulation(1);
        observePlayers();
        reconcileMaterialization();
    }

    public TestMineRecord createTestMine(ServerPlayer player) {
        WorldObjectId id = new WorldObjectId("pale_mirror:test_mine");
        if (data.testMines().containsKey(id)) throw new IllegalStateException("Test mine already exists");
        ServerLevel level = player.serverLevel();
        TestMineRecord mine = TestMineTemplate.place(level, player.blockPosition().above(2), id, audienceFor(player));
        data.testMines().put(id, mine);
        data.worldState().putFacility(new FacilityState(id, 80, 10, 10));
        data.setDirty();
        return mine;
    }

    public List<DomainEvent> advanceSimulation(int steps) {
        List<DomainEvent> events = engine.advanceSimulation(data.worldState(), steps);
        events.forEach(event -> {
            TestMineRecord mine = data.testMines().get(event.subject());
            if (mine != null) narrator.offerFor(data.worldState(), event, mine.primaryAudience());
        });
        if (!events.isEmpty()) data.setDirty();
        return events;
    }

    public boolean accept(String scenarioId, StoryAudienceId audience) {
        try {
            if (!data.worldState().scenario(scenarioId).map(value -> value.audience().equals(audience)).orElse(false)) return false;
            boolean changed = !scenarios.accept(data.worldState(), scenarioId).isEmpty();
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
                + ", scenarios=" + data.worldState().scenarios().size() + ", jobs=" + data.testMines().values().stream().filter(value -> value.job() != null).count();
    }

    public void threatDestroyed(String objectId, String causationId) {
        WorldObjectId id = new WorldObjectId(objectId);
        if (data.testMines().containsKey(id)) {
            List<DomainEvent> events = engine.controllerDestroyed(data.worldState(), id, causationId);
            if (!events.isEmpty()) {
                scenarios.reconcileRecovery(data.worldState(), id);
                data.testMines().get(id).setControllerId(null);
                data.setDirty();
            }
        }
    }

    private void observePlayers() {
        for (TestMineRecord mine : data.testMines().values()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.serverLevel().dimension().location().toString().equals(mine.dimensionId()) && mine.contains(player.blockPosition())) {
                    if (!scenarios.playerEntered(data.worldState(), audienceFor(player), mine.id()).isEmpty()) data.setDirty();
                }
            }
        }
    }

    private void reconcileMaterialization() {
        for (TestMineRecord mine : data.testMines().values()) {
            FacilityState facility = data.worldState().facility(mine.id()).orElse(null);
            if (facility == null) continue;
            if (facility.status() == FacilityStatus.INFECTED && data.worldState().scenarios().stream().noneMatch(scenario ->
                    scenario.target().equals(mine.id()) && scenario.status() == io.farfrontier.palemirror.domain.ScenarioStatus.RECOVER)) continue;
            for (ServerLevel level : server.getAllLevels()) {
                if (level.dimension().location().toString().equals(mine.dimensionId())) {
                    materializer.reconcile(level, facility, mine);
                    data.setDirty();
                }
            }
        }
    }
}
