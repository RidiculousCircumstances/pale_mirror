package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Loaded-chunk-only hand-off coordinator for exact source-graybox actor bodies. */
final class SourceGrayboxActorExecutionRuntime {
    static final String HOLDER = "source-graybox:actor-runtime";
    private static final long DRAIN_HYSTERESIS_TICKS = 200L;
    private static final long CAPTURE_INTERVAL_TICKS = 10L;
    /** A retained foreign collision is rechecked at a bounded stagger, never every executor turn. */
    private static final long OBSTRUCTION_RETRY_TICKS = 200L;
    private static final long OBSTRUCTION_RETRY_SLOTS = OBSTRUCTION_RETRY_TICKS / CAPTURE_INTERVAL_TICKS;

    /**
     * Advances lease admission before a possible immutable-frame projection.
     *
     * <p>The return value deliberately means {@code a new body needs the
     * materializer}, not merely {@code durable execution state changed}. A
     * HOT demand heartbeat and a captured hand-off are persisted so restart
     * remains exact, but neither changes the static source scene. Treating
     * either as a presentation request rebuilt every settlement and label at
     * the actor cadence while a player was nearby.</p>
     */
    boolean prepareForLoadedExecution(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                      Map<String, Entity> admittedEntities) {
        long gameTick = data.actorExecutionGameTime(level.getGameTime());
        SourceGrayboxHotZone zone = SourceGrayboxHotZone.from(level);
        SourceGrayboxPresentationLedger presentation = SourceGrayboxPresentationLedger.get(level);
        recoverLoadedActors(level, data, materializer, admittedEntities, gameTick);
        boolean admissionRequested = false;
        for (ReferenceGrayboxActorExecutionState.ActorState actor : data.actorExecution().actors()) {
            BlockPos position = position(actor);
            switch (actor.mode()) {
                case COLD -> {
                    boolean obstructed = SourceGrayboxActorMaterializer.isActorObstructed(presentation, actor);
                    if (zone.preparing(position) && level.hasChunkAt(position) && allowsPreparation(actor, obstructed, gameTick)) {
                        // Only the COLD -> PREPARING transition needs a broad
                        // claim-checked pass: it is the one transition that
                        // may create a Minecraft body.
                        admissionRequested |= data.prepareActor(actor.id(), HOLDER, gameTick);
                    }
                }
                case PREPARING -> {
                    if (zone.hot(position)) data.touchActorDemand(actor.id(), actor.leaseId(), HOLDER, gameTick);
                    if (!zone.preparing(position) && expired(actor, gameTick)
                            && materializer.actorEntity(level, admittedEntities, actor) == null) {
                        data.cancelActorPreparation(actor.id(), actor.leaseId(), HOLDER, gameTick);
                    }
                }
                case HOT -> {
                    if (zone.hot(position)) data.touchActorDemand(actor.id(), actor.leaseId(), HOLDER, gameTick);
                    if (!zone.hot(position) && expired(actor, gameTick) && zone.safeToDrain(position)) {
                        data.beginActorDrain(actor.id(), actor.leaseId(), HOLDER, gameTick);
                    }
                }
                case DRAINING, RETIRED, RECOVERING -> { /* completed after materialization/loaded observation */ }
            }
        }
        return admissionRequested;
    }

    /**
     * Captures and drains already-loaded bodies at the executor cadence.
     *
     * <p>This runs after a frame publication when admission was requested, or
     * directly against an already materialized HOT body. It never asks the
     * static materializer to replay an unchanged source snapshot merely to
     * retain an exact physical hand-off.</p>
     */
    boolean reconcileLoadedActors(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                  Map<String, Entity> admittedEntities) {
        long gameTick = data.actorExecutionGameTime(level.getGameTime());
        SourceGrayboxHotZone zone = SourceGrayboxHotZone.from(level);
        SourceGrayboxPresentationLedger presentation = SourceGrayboxPresentationLedger.get(level);
        boolean changed = false;
        for (ReferenceGrayboxActorExecutionState.ActorState actor : data.actorExecution().actors()) {
            BlockPos position = position(actor);
            Entity entity = materializer.actorEntity(level, admittedEntities, actor);
            boolean recoveredThisPublication = materializer.actorRecoveries().consume(actor.id(), actor.kind().name());
            switch (actor.mode()) {
                case PREPARING -> {
                    if (entity != null && data.activateActor(actor.id(), actor.leaseId(), HOLDER, gameTick)) {
                        // The spawn resolver may have selected a deterministic
                        // legal neighbour of a blocked source point. Persist
                        // that physical hand-off in the same admission turn;
                        // it is not a transient NeoForge-only fallback.
                        changed = true;
                        changed |= data.captureActor(actor.id(), actor.leaseId(), HOLDER, sixteenths(entity.getX()),
                                sixteenths(entity.getZ()), gameTick);
                    } else if (mustReleaseBlockedPreparation(actor, entity != null,
                            SourceGrayboxActorMaterializer.isActorObstructed(presentation, actor))) {
                        // A physically impossible admission is not a durable
                        // executor. The obstruction claim explains the gap;
                        // the source actor returns to COLD without a body.
                        changed |= data.cancelActorPreparation(actor.id(), actor.leaseId(), HOLDER, gameTick);
                    }
                }
                case HOT -> {
                    String key = SourceGrayboxMaterializer.entityKey(actor.id(), actor.kind());
                    if (entity == null && SourceGrayboxActorMaterializer.isActorObstructed(presentation, actor)) {
                        // The materializer removed the body in the same server
                        // turn in which it found no collision-free recovery
                        // cell.  Release this lease explicitly; the retained
                        // obstruction is the only reason a later HOT demand
                        // may retry, never a duplicate body.
                        changed |= data.deferBlockedHotActor(actor.id(), actor.leaseId(), HOLDER, gameTick);
                    } else if (entity == null && level.hasChunkAt(position)
                            && !SourceGrayboxActorMaterializer.hasObservedActorBody(level, admittedEntities, actor)) {
                        // A loaded scene has no exact body at all. This is not
                        // a death (that reaches the source through the typed
                        // observation boundary), nor a foreign/invalid body
                        // (which must remain fail-closed). Release precisely
                        // this phantom HOT lease so a later admission can use
                        // a new UUID-safe lease rather than preserve an
                        // invisible actor forever.
                        boolean settled = data.settleMissingHotActor(actor.id(), actor.leaseId(), HOLDER, gameTick);
                        if (settled) presentation.releaseEntity(key);
                        changed |= settled;
                    } else if (entity != null && (recoveredThisPublication || gameTick % CAPTURE_INTERVAL_TICKS == 0L)) {
                        changed |= data.captureActor(actor.id(), actor.leaseId(), HOLDER, sixteenths(entity.getX()),
                                sixteenths(entity.getZ()), gameTick);
                    }
                    if (!zone.hot(position) && expired(actor, gameTick) && zone.safeToDrain(position)) {
                        changed |= data.beginActorDrain(actor.id(), actor.leaseId(), HOLDER, gameTick);
                    }
                }
                case DRAINING -> {
                    if (!level.hasChunkAt(position) || !zone.safeToDrain(position)) continue;
                    if (entity != null) {
                        data.captureActor(actor.id(), actor.leaseId(), HOLDER, sixteenths(entity.getX()), sixteenths(entity.getZ()), gameTick);
                        entity.discard();
                        admittedEntities.remove(SourceGrayboxMaterializer.entityKey(actor.id(), actor.kind()));
                    }
                    boolean settled = data.settleActorCold(actor.id(), actor.leaseId(), HOLDER, gameTick);
                    if (settled) presentation.releaseEntity(SourceGrayboxMaterializer.entityKey(actor.id(), actor.kind()));
                    changed |= settled;
                }
                case RETIRED -> {
                    if (!level.hasChunkAt(position) || !zone.safeToDrain(position)) continue;
                    if (entity != null) {
                        entity.discard();
                        admittedEntities.remove(SourceGrayboxMaterializer.entityKey(actor.id(), actor.kind()));
                    }
                    boolean acknowledged = data.acknowledgeRetiredActor(actor.id(), actor.leaseId(), HOLDER, gameTick);
                    if (acknowledged) presentation.releaseEntity(SourceGrayboxMaterializer.entityKey(actor.id(), actor.kind()));
                    changed |= acknowledged;
                }
                case COLD, RECOVERING -> { /* handled before publication */ }
            }
        }
        return changed;
    }

    private static boolean recoverLoadedActors(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                               Map<String, Entity> admittedEntities, long gameTick) {
        boolean changed = false;
        for (ReferenceGrayboxActorExecutionState.ActorState actor : data.actorExecution().actors()) {
            if (actor.mode() != ReferenceGrayboxActorExecutionState.Mode.RECOVERING) continue;
            BlockPos position = position(actor);
            if (!level.hasChunkAt(position)) continue;
            Entity entity = materializer.actorEntity(level, admittedEntities, actor);
            changed |= entity == null
                    ? data.recoverActorCold(actor.id(), actor.leaseId(), HOLDER, gameTick)
                    : data.recoverActorHot(actor.id(), actor.leaseId(), HOLDER, gameTick);
        }
        return changed;
    }

    private static boolean expired(ReferenceGrayboxActorExecutionState.ActorState actor, long gameTick) {
        return gameTick - actor.demandedAtGameTick() >= DRAIN_HYSTERESIS_TICKS;
    }

    /** Package-visible pure policy proof for the bounded COLD re-admission path. */
    static boolean allowsPreparation(ReferenceGrayboxActorExecutionState.ActorState actor, boolean obstructed, long gameTick) {
        if (actor.mode() != ReferenceGrayboxActorExecutionState.Mode.COLD || gameTick < actor.changedAtGameTick()) return false;
        if (!obstructed) return true;
        if (gameTick - actor.changedAtGameTick() < OBSTRUCTION_RETRY_TICKS) return false;
        long slot = Math.floorMod(actor.id().hashCode(), (int) OBSTRUCTION_RETRY_SLOTS) * CAPTURE_INTERVAL_TICKS;
        return Math.floorMod(gameTick, OBSTRUCTION_RETRY_TICKS) == slot;
    }

    /** A PREPARING lease with no body and a retained collision fact must not remain live. */
    static boolean mustReleaseBlockedPreparation(ReferenceGrayboxActorExecutionState.ActorState actor,
                                                 boolean hasPhysicalBody, boolean obstructed) {
        return actor.mode() == ReferenceGrayboxActorExecutionState.Mode.PREPARING && !hasPhysicalBody && obstructed;
    }

    private static BlockPos position(ReferenceGrayboxActorExecutionState.ActorState actor) {
        return new BlockPos(Math.floorDiv(actor.actualXSixteenths(), ReferenceGrayboxActorExecutionState.POSITION_SCALE),
                io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout.GROUND_Y + 1,
                Math.floorDiv(actor.actualZSixteenths(), ReferenceGrayboxActorExecutionState.POSITION_SCALE));
    }

    private static int sixteenths(double coordinate) {
        if (!Double.isFinite(coordinate)) throw new IllegalStateException("Minecraft actor position is not finite");
        return Math.toIntExact(Math.round(coordinate * ReferenceGrayboxActorExecutionState.POSITION_SCALE));
    }
}
