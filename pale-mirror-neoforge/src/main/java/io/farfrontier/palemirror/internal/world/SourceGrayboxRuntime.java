package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxObservationOutcome;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxBioformObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxResidentObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxStructureObservation;
import java.util.LinkedHashMap;
import java.nio.file.Path;
import java.util.IdentityHashMap;
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
    private final Map<String, Entity> admittedEntities = new LinkedHashMap<>();
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

    /** Avoid constructing source SavedData for ordinary entity admissions in unrelated dimensions. */
    public static boolean recognizesManagedEntity(Entity entity) {
        return SourceGrayboxMaterializer.recognizesManagedEntity(entity);
    }

    /** Verify durable source state before Minecraft can substitute a fresh SavedData instance. */
    public static void assertCompatibleData(Path worldRoot) {
        SourceGrayboxSavedData.assertCompatibleData(worldRoot);
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

    /**
     * Retains a restored PM entity before the level UUID index is guaranteed to expose it.
     * The entry is transient; durable duplicate prevention is the presentation ledger claim.
     */
    public void observeEntityJoin(ServerLevel level, Entity entity) {
        if (!level.dimension().equals(SourceGrayboxWorldBoundary.DIMENSION)) return;
        SourceGrayboxMaterializer.rememberAdmittedEntity(admittedEntities, entity);
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
        boolean applied = SourceGrayboxEntityObservation.observe(data, entity, causationId);
        publish(grayboxLevel());
        return applied;
    }

    /** Turns a declared physical interaction slot into the exact source fact it carries. */
    public boolean observeBlockBreak(ServerLevel level, net.minecraft.core.BlockPos position, String causationId) {
        if (!data.activated() || level != grayboxLevel()) return false;
        boolean handled = SourceGrayboxBlockObservation.observe(data, materializer, level, position, causationId);
        publish(grayboxLevel());
        return handled;
    }

    /** Explicit operator transport makes the disposable arena discoverable without touching the overworld. */
    public void enter(ServerPlayer player) {
        ServerLevel graybox = grayboxLevel();
        net.minecraft.core.BlockPos entry = SourceGrayboxWorldBoundary.preparedEntry(graybox);
        player.teleportTo(graybox, entry.getX() + 0.5d, entry.getY(), entry.getZ() + 0.5d, player.getYRot(), player.getXRot());
        player.fallDistance = 0.0f;
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
        materializer.apply(level, data.snapshot(), admittedEntities);
        lastPresentationGameTime = level.getGameTime();
    }
}
