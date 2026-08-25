package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxBioformObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxObservationOutcome;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxResidentObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxStructureObservation;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        ServerLevel graybox = grayboxLevel();
        long gameTime = graybox.getGameTime();
        int advanced = data.advanceDueDays(gameTime, DAY_INTERVAL_TICKS, MAXIMUM_CATCH_UP_DAYS);
        if (advanced > 0 || gameTime - lastPresentationGameTime >= PRESENTATION_INTERVAL_TICKS) publish(graybox);
        return true;
    }

    /** Explicit activation prevents a legacy campaign clock and source clock from running together. */
    public ReferenceGrayboxSnapshot activate() {
        ServerLevel graybox = grayboxLevel();
        SourceGrayboxWorldBoundary.enforce(graybox);
        data.activate(graybox.getGameTime());
        publish(graybox);
        return data.snapshot();
    }

    public void advance(int days) {
        data.advance(days);
        if (data.activated()) publish(grayboxLevel());
    }

    public ReferenceGrayboxSnapshot snapshot() {
        return data.snapshot();
    }

    public boolean activated() {
        return data.activated();
    }

    public ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxResidentObservation observation) {
        ReferenceGrayboxObservationOutcome outcome = data.observe(observation);
        if (data.activated()) publish(grayboxLevel());
        return outcome;
    }

    public ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxBioformObservation observation) {
        ReferenceGrayboxObservationOutcome outcome = data.observe(observation);
        if (data.activated()) publish(grayboxLevel());
        return outcome;
    }

    public ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxStructureObservation observation) {
        ReferenceGrayboxObservationOutcome outcome = data.observe(observation);
        if (data.activated()) publish(grayboxLevel());
        return outcome;
    }

    /** Reconciles one exact managed entity death in the same server event. */
    public boolean observeEntityDeath(Entity entity, String causationId) {
        if (!data.activated() || entity.level() != grayboxLevel()) return false;
        SourceGrayboxMaterializer.ManagedEntity managed = SourceGrayboxMaterializer.managed(entity);
        if (managed == null) return false;
        String eventId = "source-graybox:physical-death:" + causationId + ":" + entity.getUUID();
        ReferenceGrayboxObservationOutcome outcome = switch (managed.kind()) {
            case "RESIDENT" -> data.observe(ReferenceGrayboxResidentObservation.killed(eventId, managed.revision(), managed.id()));
            case "BIOFORM" -> data.observe(ReferenceGrayboxBioformObservation.killed(eventId, managed.revision(), managed.id()));
            default -> throw new IllegalStateException("unreachable managed entity kind");
        };
        publish(grayboxLevel());
        return outcome.applied();
    }

    /** Turns a declared physical interaction slot into the exact source fact it carries. */
    public boolean observeBlockBreak(ServerLevel level, net.minecraft.core.BlockPos position, String causationId) {
        if (!data.activated() || level != grayboxLevel()) return false;
        SourceGrayboxPresentationLedger.Claim claim = materializer.claimAt(level, position);
        if (claim == null) return false;
        if (claim.interactionKind().isEmpty()) {
            materializer.recordBlockConflict(level, position);
            return true;
        }
        String eventId = "source-graybox:physical-break:" + causationId + ":" + claim.id();
        ReferenceGrayboxStructureObservation.Kind kind = ReferenceGrayboxStructureObservation.Kind.valueOf(
                claim.interactionKind().toUpperCase(Locale.ROOT));
        ReferenceGrayboxObservationOutcome outcome = data.observe(new ReferenceGrayboxStructureObservation(
                ReferenceGrayboxStructureObservation.VERSION, eventId, claim.revision(), kind, claim.subjectId(), claim.interactionWeight()));
        if (outcome.applied()) materializer.consumeBlockClaim(level, position);
        else materializer.recordBlockConflict(level, position);
        publish(grayboxLevel());
        return true;
    }

    /** Explicit operator transport makes the disposable arena discoverable without touching the overworld. */
    public void enter(ServerPlayer player) {
        ServerLevel graybox = grayboxLevel();
        player.teleportTo(graybox, 0.5d, ReferenceGrayboxLayout.GROUND_Y + 1.0d, 0.5d, player.getYRot(), player.getXRot());
    }

    public String status() {
        ReferenceGrayboxSnapshot snapshot = data.snapshot();
        return "profile=" + snapshot.profileId() + ", day=" + snapshot.day() + ", settlements=" + snapshot.settlements().size()
                + ", residents=" + snapshot.residents().size() + ", organs=" + snapshot.hiveOrgans().size()
                + ", bioforms=" + snapshot.bioforms().size() + ", dimension=" + SourceGrayboxWorldBoundary.DIMENSION.location()
                + ", materialization=" + (data.activated() ? "ACTIVE" : "DISABLED");
    }

    private ServerLevel grayboxLevel() {
        return SourceGrayboxWorldBoundary.level(server);
    }

    private void publish(ServerLevel level) {
        materializer.apply(level, data.snapshot());
        lastPresentationGameTime = level.getGameTime();
    }
}
