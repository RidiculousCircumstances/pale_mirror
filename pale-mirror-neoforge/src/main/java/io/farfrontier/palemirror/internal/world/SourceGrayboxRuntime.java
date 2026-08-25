package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxBioformObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxObservationOutcome;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxResidentObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Server-thread scheduler and SavedData owner for the source-parity graybox. */
public final class SourceGrayboxRuntime {
    private static final long DAY_INTERVAL_TICKS = 1_200L;
    private static final int MAXIMUM_CATCH_UP_DAYS = 24;
    private static final long PRESENTATION_INTERVAL_TICKS = 20L;
    private static final Map<MinecraftServer, SourceGrayboxRuntime> INSTANCES = new IdentityHashMap<>();
    private final MinecraftServer server;
    private final SourceGrayboxSavedData data;
    private final SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
    private long lastPresentationGameTime = Long.MIN_VALUE;

    private SourceGrayboxRuntime(MinecraftServer server) {
        this.server = server;
        data = SourceGrayboxSavedData.get(server.overworld());
    }

    public static SourceGrayboxRuntime forServer(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, SourceGrayboxRuntime::new);
    }

    public static void stop(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    /** Returns true only after the source graybox has become the active campaign clock. */
    public boolean tick() {
        if (!data.activated()) return false;
        ServerLevel overworld = server.overworld();
        long gameTime = overworld.getGameTime();
        int advanced = data.advanceDueDays(gameTime, DAY_INTERVAL_TICKS, MAXIMUM_CATCH_UP_DAYS);
        if (advanced > 0 || gameTime - lastPresentationGameTime >= PRESENTATION_INTERVAL_TICKS) publish(overworld);
        return true;
    }

    /** Explicit activation prevents a legacy campaign clock and source clock from running together. */
    public ReferenceGrayboxSnapshot activate() {
        ServerLevel overworld = server.overworld();
        SourceGrayboxWorldBoundary.enforce(overworld);
        data.activate(overworld.getGameTime());
        publish(overworld);
        return data.snapshot();
    }

    public void advance(int days) {
        data.advance(days);
        if (data.activated()) publish(server.overworld());
    }

    public ReferenceGrayboxSnapshot snapshot() {
        return data.snapshot();
    }

    public boolean activated() {
        return data.activated();
    }

    public ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxResidentObservation observation) {
        ReferenceGrayboxObservationOutcome outcome = data.observe(observation);
        if (data.activated()) publish(server.overworld());
        return outcome;
    }

    public ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxBioformObservation observation) {
        ReferenceGrayboxObservationOutcome outcome = data.observe(observation);
        if (data.activated()) publish(server.overworld());
        return outcome;
    }

    /** Reconciles one exact managed entity death in the same server event. */
    public boolean observeEntityDeath(Entity entity, String causationId) {
        SourceGrayboxMaterializer.ManagedEntity managed = SourceGrayboxMaterializer.managed(entity);
        if (managed == null) return false;
        String eventId = "source-graybox:physical-death:" + causationId + ":" + entity.getUUID();
        ReferenceGrayboxObservationOutcome outcome = switch (managed.kind()) {
            case "RESIDENT" -> data.observe(ReferenceGrayboxResidentObservation.killed(eventId, managed.revision(), managed.id()));
            case "BIOFORM" -> data.observe(ReferenceGrayboxBioformObservation.killed(eventId, managed.revision(), managed.id()));
            default -> throw new IllegalStateException("unreachable managed entity kind");
        };
        if (data.activated()) publish(server.overworld());
        return outcome.applied();
    }

    /** A structural change is retained as an explicit conflict until its typed source fact exists. */
    public boolean observeBlockBreak(ServerLevel level, net.minecraft.core.BlockPos position) {
        SourceGrayboxPresentationLedger.Claim claim = materializer.claimAt(level, position);
        if (claim == null) return false;
        materializer.recordBlockConflict(level, position);
        return true;
    }

    public String status() {
        ReferenceGrayboxSnapshot snapshot = data.snapshot();
        return "profile=" + snapshot.profileId() + ", day=" + snapshot.day() + ", settlements=" + snapshot.settlements().size()
                + ", residents=" + snapshot.residents().size() + ", organs=" + snapshot.hiveOrgans().size()
                + ", bioforms=" + snapshot.bioforms().size() + ", materialization=" + (data.activated() ? "ACTIVE" : "DISABLED");
    }

    private void publish(ServerLevel level) {
        materializer.apply(level, data.snapshot());
        lastPresentationGameTime = level.getGameTime();
    }
}
