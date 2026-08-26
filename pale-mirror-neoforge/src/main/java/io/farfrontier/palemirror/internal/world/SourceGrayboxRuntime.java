package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxObservationOutcome;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxBioformObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxResidentObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxStructureObservation;
import java.util.LinkedHashMap;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Server-thread scheduler and SavedData owner for the source-parity graybox. */
public final class SourceGrayboxRuntime {
    private static final int MAXIMUM_CATCH_UP_DAYS = 24;
    private static final long PRESENTATION_INTERVAL_TICKS = 20L;
    private static final long ACTOR_EXECUTION_INTERVAL_TICKS = 10L;
    private static final Map<MinecraftServer, SourceGrayboxRuntime> INSTANCES = new IdentityHashMap<>();
    private final MinecraftServer server;
    private final SourceGrayboxSavedData data;
    private final SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();
    private final SourceGrayboxActorExecutionRuntime actorExecution = new SourceGrayboxActorExecutionRuntime();
    private final SourceGrayboxActorBehaviorRuntime actorBehavior = new SourceGrayboxActorBehaviorRuntime();
    private final SourceGrayboxActorCombatRuntime actorCombat = new SourceGrayboxActorCombatRuntime();
    private final SourceGrayboxWarehouseRuntime warehouses = new SourceGrayboxWarehouseRuntime();
    private final SourceGrayboxCargoRuntime cargoes = new SourceGrayboxCargoRuntime();
    private final SourceGrayboxOperationCargoCarrierRuntime operationCarriers = new SourceGrayboxOperationCargoCarrierRuntime();
    private final SourceGrayboxEffectRuntime effects = new SourceGrayboxEffectRuntime();
    private final Map<String, Entity> admittedEntities = new LinkedHashMap<>();
    private long lastPresentationGameTime = Long.MIN_VALUE;

    private SourceGrayboxRuntime(MinecraftServer server) {
        this.server = server;
        data = SourceGrayboxSavedData.get(server.overworld());
        // A saved HOT/PREPARING actor is unknown after JVM restart.  Do not let
        // a later materializer blindly create a second body for that lease.
        long recoveryTick = data.actorExecutionGameTime(server.overworld().getGameTime());
        data.enterActorRecovery(recoveryTick);
        data.recoverEffectLeases(recoveryTick);
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
        SourceGrayboxPresentationLedger.assertCompatibleData(worldRoot);
    }

    /** Returns true only after the source graybox has become the active campaign clock. */
    public boolean tick() {
        if (!data.activated()) return false;
        ServerLevel graybox = grayboxLevel();
        long gameTime = graybox.getGameTime();
        if (gameTime % 5L == 0L) data.maintainEffectLeases(data.actorExecutionGameTime(gameTime));
        boolean warehouseChanged = warehouses.reconcileInbound(graybox, data, materializer);
        boolean carrierChanged = operationCarriers.tick(graybox, data, materializer);
        boolean cargoChanged = cargoes.reconcileInbound(graybox, data, materializer);
        boolean actorDue = gameTime % ACTOR_EXECUTION_INTERVAL_TICKS == 0L;
        boolean executionChanged = actorDue && actorExecution.beforePublication(graybox, data, materializer, admittedEntities);
        List<ReferenceGrayboxSnapshot> dueBoundaries = data.advanceDueDaySnapshots(gameTime, MAXIMUM_CATCH_UP_DAYS);
        int advanced = dueBoundaries.size();
        if (advanced > 0 || executionChanged || warehouseChanged || cargoChanged || carrierChanged || gameTime - lastPresentationGameTime >= PRESENTATION_INTERVAL_TICKS) {
            publish(graybox);
            if (actorDue || materializer.actorRecoveries().hasAny()) actorExecution.afterPublication(graybox, data, materializer, admittedEntities);
        }
        // The just-published plan is the observed world against which a real
        // source-day consequence lands.  The executor decides HOT/COLD only
        // once at this boundary, so an off-screen event cannot explode late
        // merely because a player later returns to its chunk.
        if (advanced > 0) {
            boolean effectsSettled;
            if (dueBoundaries.size() == 1) {
                effectsSettled = effects.settleSourceDay(graybox, data, materializer, dueBoundaries.getFirst(), effect -> {
                    BlockPos target = new BlockPos(effect.position().x(), ReferenceGrayboxLayout.GROUND_Y + 3, effect.position().z());
                    return SourceGrayboxHotZone.from(graybox).hot(target) && graybox.hasChunkAt(target);
                });
            } else {
                // A capped catch-up compressed several source boundaries into
                // one server tick. They did not have individual live Minecraft
                // frames, so conservatively retain each as COLD rather than
                // showing a burst of late explosions in the final frame.
                effectsSettled = false;
                for (ReferenceGrayboxSnapshot frame : dueBoundaries) {
                    effectsSettled |= effects.settleSourceDay(graybox, data, materializer, frame, ignored -> false);
                }
            }
            if (effectsSettled) publish(graybox);
        }
        actorBehavior.tick(graybox, data.snapshot(), data.actorExecution(), materializer, admittedEntities, gameTime);
        actorCombat.tick(graybox, data, materializer, admittedEntities, gameTime);
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
        if (data.activated()) {
            ServerLevel graybox = grayboxLevel();
            publish(graybox);
            // Explicit multi-day fast-forward has no physically elapsed day
            // boundary.  Settle its final source receipt cold rather than
            // fabricating a delayed detonation in the player's current scene.
            if (effects.settleSourceDay(graybox, data, materializer, data.snapshot(), ignored -> false)) publish(graybox);
        }
    }

    public ReferenceGrayboxSnapshot snapshot() {
        return data.snapshot();
    }

    public boolean activated() {
        return data.activated();
    }

    /** Explicit operator-controlled pacing transition; it never changes source state itself. */
    public boolean changeClockProfile(String profileId) {
        SourceGrayboxClockProfile profile = SourceGrayboxClockProfile.fromId(profileId);
        return data.changeClockProfile(profile, grayboxLevel().getGameTime());
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

    /**
     * Applies a managed death and returns a transient player receipt only when
     * the canonical source accepted it. The receipt is derived before/after
     * immutable snapshots and is never retained as a second event log.
     */
    public Optional<String> observeEntityDeathWithReceipt(Entity entity, String causationId) {
        if (!data.activated() || entity.level() != grayboxLevel()) return Optional.empty();
        ReferenceGrayboxSnapshot before = data.snapshot();
        SourceGrayboxEntityObservation.Result result = SourceGrayboxEntityObservation.observeDetailed(data, entity, causationId);
        publish(grayboxLevel());
        if (!result.applied()) return Optional.empty();
        return Optional.of(SourceGrayboxPlayerBriefing.acceptedEntityReceipt(before, data.snapshot(), result.entity()));
    }

    /** Turns a declared physical interaction slot into the exact source fact it carries. */
    public boolean observeBlockBreak(ServerLevel level, net.minecraft.core.BlockPos position, String causationId) {
        if (!data.activated() || level != grayboxLevel()) return false;
        boolean handled = SourceGrayboxBlockObservation.observe(data, materializer, level, position, causationId);
        publish(grayboxLevel());
        return handled;
    }

    /** Applies one declared slot and supplies a receipt only for an accepted canonical change. */
    public Optional<String> observeBlockBreakWithReceipt(ServerLevel level, BlockPos position, String causationId) {
        if (!data.activated() || level != grayboxLevel()) return Optional.empty();
        ReferenceGrayboxSnapshot before = data.snapshot();
        SourceGrayboxBlockObservation.Result result = SourceGrayboxBlockObservation.observeDetailed(data, materializer, level, position, causationId);
        publish(grayboxLevel());
        if (!result.handled()) return Optional.empty();
        if (!result.applied()) {
            return Optional.of(result.claim().interactionKind().isEmpty()
                    ? "World unchanged: this block only describes a source object. Break a marked ACTION block to make a real intervention."
                    : "World unchanged: this action was stale, already used, or rejected by the source world.");
        }
        ReferenceGrayboxSnapshot.Interaction interaction = before.interactions().stream()
                .filter(value -> value.id().equals(result.claim().id().substring("interaction:".length(), result.claim().id().lastIndexOf(':'))))
                .findFirst().orElse(null);
        return interaction == null ? Optional.empty()
                : Optional.of(SourceGrayboxPlayerBriefing.acceptedReceipt(before, data.snapshot(), interaction));
    }

    /** Presents a source-derived briefing for a physical source object without changing any source or world state. */
    public boolean presentBriefing(ServerPlayer player, BlockPos position) {
        if (!data.activated() || player.level() != grayboxLevel()) return false;
        ReferenceGrayboxSnapshot snapshot = data.snapshot();
        SourceGrayboxPresentationLedger.Claim claim = materializer.claimAt(grayboxLevel(), position);
        Optional<String> briefing = claim != null && !claim.interactionKind().isEmpty()
                ? SourceGrayboxPlayerBriefing.forIdentity(snapshot, interactionLabelId(claim), SourceGrayboxMaterializer.LABEL_KIND)
                : SourceGrayboxPlayerBriefing.at(snapshot, position.getX(), position.getZ());
        return briefing.map(text -> {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(text));
            return true;
        }).orElse(false);
    }

    /** Presents a source-derived briefing when a player clicks a board, resident or bioform. */
    public boolean presentBriefing(ServerPlayer player, Entity entity) {
        if (!data.activated() || entity.level() != grayboxLevel()) return false;
        String id = entity.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_ID);
        String kind = entity.getPersistentData().getString(SourceGrayboxMaterializer.ENTITY_KIND);
        if (id.isBlank() || kind.isBlank()) return false;
        return SourceGrayboxPlayerBriefing.forIdentity(data.snapshot(), id, kind).map(text -> {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(text));
            return true;
        }).orElse(false);
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
        return "profile=" + snapshot.profileId() + ", clock=" + data.clockProfile().id() + " (" + data.dayIntervalTicks() + " ticks/day), day=" + snapshot.day() + ", settlements=" + snapshot.settlements().size()
                + ", residents=" + snapshot.residents().size() + ", organs=" + snapshot.hiveOrgans().size()
                + ", bioforms=" + snapshot.bioforms().size() + ", dimension=" + SourceGrayboxWorldBoundary.DIMENSION.location()
                + ", materialization=" + (data.activated() ? "ACTIVE" : "DISABLED");
    }

    /** Read-only exact source detail for the materialized location under an operator. */
    public String inspect(int x, int z) { return SourceGrayboxInspector.at(data.snapshot(), x, z); }

    private ServerLevel grayboxLevel() {
        return SourceGrayboxWorldBoundary.level(server);
    }

    private static String interactionLabelId(SourceGrayboxPresentationLedger.Claim claim) {
        String id = claim.id();
        String prefix = "interaction:";
        if (!id.startsWith(prefix) || id.lastIndexOf(':') <= prefix.length()) {
            throw new IllegalStateException("interaction claim has no deterministic slot id: " + id);
        }
        return prefix + id.substring(prefix.length(), id.lastIndexOf(':'));
    }

    private void publish(ServerLevel level) {
        materializer.apply(level, data.snapshot(), data.actorExecution(), admittedEntities);
        warehouses.materialize(level, data, materializer);
        cargoes.materialize(level, data, materializer);
        lastPresentationGameTime = level.getGameTime();
    }
}
