package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState;
import java.util.LinkedHashMap;
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
     * EntityJoinLevelEvent can precede ServerLevel UUID indexing.  This
     * noncanonical, bounded grace record prevents recovery from treating that
     * single hand-off turn as absence, while never permitting an unindexed
     * Java object to become a physical executor.
     */
    private final Map<String, Long> pendingIndexSince = new LinkedHashMap<>();

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
                    // Removal from ServerLevel's UUID manager is finalized on
                    // a later server tick. If a previous exact body was
                    // rejected or rolled back, the first re-admission pass
                    // may correctly refuse to reuse it yet be unable to add
                    // the replacement with that UUID in the same turn. Keep
                    // one bounded actor-cadence retry while the player still
                    // demands the loaded scene; this is a recovery pass, not
                    // a static-world polling loop.
                    if (zone.preparing(position) && level.hasChunkAt(position)
                            && materializer.actorEntity(level, admittedEntities, actor) == null) {
                        admissionRequested = true;
                    }
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
                    } else if (awaitingIndex(level, admittedEntities, actor, gameTick)) {
                        // A just-admitted body may not have reached the UUID
                        // index until the next executor turn.  It is not HOT
                        // until that postcondition is observable.
                    } else if (mustReleaseBlockedPreparation(actor, entity != null,
                            SourceGrayboxActorMaterializer.isActorObstructed(presentation, actor))) {
                        // A physically impossible admission is not a durable
                        // executor. The obstruction claim explains the gap;
                        // the source actor returns to COLD without a body.
                        changed |= data.cancelActorPreparation(actor.id(), actor.leaseId(), HOLDER, gameTick);
                    } else if (!SourceGrayboxActorMaterializer.hasObservedActorBody(level, admittedEntities, actor)
                            && gameTick - actor.changedAtGameTick() >= CAPTURE_INTERVAL_TICKS) {
                        // A canceled or otherwise failed entity join may have
                        // returned a transient Java object to the admission
                        // map.  It must not strand a PREPARING lease or be
                        // mistaken for a live executor.  The later COLD
                        // admission gets a new deterministic lease.
                        boolean settled = data.cancelActorPreparation(actor.id(), actor.leaseId(), HOLDER, gameTick);
                        if (settled) presentation.releaseEntity(SourceGrayboxMaterializer.entityKey(actor.id(), actor.kind()));
                        changed |= settled;
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
                    // Minecraft may serialize a body while its player-demanded
                    // chunk unloads before this coordinator gets another turn.
                    // If the player returns while that exact body is restored,
                    // retain the old lease and resume it rather than discarding
                    // the body then visibly creating a replacement.
                    if (resumeDemandedDrainingBody(data, actor, entity, zone.hot(position), gameTick)) {
                        changed = true;
                        continue;
                    }
                    if (!zone.safeToDrain(position) || !level.hasChunkAt(position)) continue;
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

    /**
     * Records a normal Minecraft chunk-unload hand-off without confusing it
     * with a source death or a missing body. The caller has already proved
     * that the actor is no longer in a player HOT zone and its chunk is gone;
     * the reservation remains until that serialized body is either re-adopted
     * or observed absent from a naturally loaded chunk.
     */
    static boolean beginUnloadedHotDrain(SourceGrayboxSavedData data,
                                         ReferenceGrayboxActorExecutionState.ActorState actor,
                                         Entity body, long gameTick) {
        if (actor.mode() != ReferenceGrayboxActorExecutionState.Mode.HOT) return false;
        if (!data.captureActor(actor.id(), actor.leaseId(), HOLDER, sixteenths(body.getX()), sixteenths(body.getZ()), gameTick)) return false;
        return data.beginActorDrain(actor.id(), actor.leaseId(), HOLDER, gameTick);
    }

    /**
     * Reuses the exact serialized body when player demand returns during the
     * unload hand-off.  No presentation claim is released or reacquired: it
     * is still the same physical incarnation, distinguished by its old lease
     * and UUID.
     */
    static boolean resumeDemandedDrainingBody(SourceGrayboxSavedData data,
                                              ReferenceGrayboxActorExecutionState.ActorState actor,
                                              Entity body, boolean hotDemand, long gameTick) {
        if (!hotDemand || body == null || actor.mode() != ReferenceGrayboxActorExecutionState.Mode.DRAINING) return false;
        if (!data.resumeDrainingActorHot(actor.id(), actor.leaseId(), HOLDER, gameTick)) return false;
        return data.captureActor(actor.id(), actor.leaseId(), HOLDER,
                sixteenths(body.getX()), sixteenths(body.getZ()), gameTick);
    }

    private boolean recoverLoadedActors(ServerLevel level, SourceGrayboxSavedData data, SourceGrayboxMaterializer materializer,
                                        Map<String, Entity> admittedEntities, long gameTick) {
        boolean changed = false;
        SourceGrayboxPresentationLedger presentation = SourceGrayboxPresentationLedger.get(level);
        for (ReferenceGrayboxActorExecutionState.ActorState actor : data.actorExecution().actors()) {
            if (actor.mode() != ReferenceGrayboxActorExecutionState.Mode.RECOVERING) continue;
            BlockPos position = position(actor);
            if (!level.hasChunkAt(position)) continue;
            Entity entity = materializer.actorEntity(level, admittedEntities, actor);
            if (entity != null) {
                pendingIndexSince.remove(SourceGrayboxMaterializer.entityKey(actor.id(), actor.kind()));
                changed |= data.recoverActorHot(actor.id(), actor.leaseId(), HOLDER, gameTick);
                continue;
            }
            if (awaitingIndex(level, admittedEntities, actor, gameTick)) continue;
            boolean settled = data.recoverActorCold(actor.id(), actor.leaseId(), HOLDER, gameTick);
            // A restart may retain the old duplicate-prevention reservation
            // even though the exact body was never saved.  That reservation
            // belongs to the now-settled lease, not to the canonical actor;
            // keeping it would strand the actor in PREPARING forever.  A
            // present but invalid UUID/body remains fail-closed and retains
            // its reservation, so this is never authority to replace an
            // integrity conflict with a fresh executor.
            if (settled && !SourceGrayboxActorMaterializer.hasObservedActorBody(level, admittedEntities, actor)) {
                presentation.releaseEntity(SourceGrayboxMaterializer.entityKey(actor.id(), actor.kind()));
            }
            changed |= settled;
        }
        return changed;
    }

    /**
     * Returns true only for the bounded join-to-index hand-off.  A matching
     * unindexed object is removed after one executor interval so an event
     * cancellation cannot preserve a ghost lease indefinitely.
     */
    private boolean awaitingIndex(ServerLevel level, Map<String, Entity> admittedEntities,
                                  ReferenceGrayboxActorExecutionState.ActorState actor, long gameTick) {
        String key = SourceGrayboxMaterializer.entityKey(actor.id(), actor.kind());
        Entity observed = admittedEntities.get(key);
        if (!pendingExactJoin(level, observed, actor)) {
            pendingIndexSince.remove(key);
            return false;
        }
        long firstSeen = pendingIndexSince.computeIfAbsent(key, ignored -> gameTick);
        if (gameTick - firstSeen < CAPTURE_INTERVAL_TICKS) return true;
        admittedEntities.remove(key, observed);
        pendingIndexSince.remove(key);
        return false;
    }

    private static boolean pendingExactJoin(ServerLevel level, Entity observed,
                                            ReferenceGrayboxActorExecutionState.ActorState actor) {
        if (observed == null || observed.isRemoved()
                || !SourceGrayboxMaterializer.identityMatches(observed, actor.id(), actor.kind().name())) return false;
        String kind = actor.kind().name();
        return observed.getUUID().equals(SourceGrayboxMaterializer.uuid(kind.equals("RESIDENT") ? "resident" : "bioform", actor.id()))
                && (!observed.isAddedToLevel() || level.getEntity(observed.getUUID()) == null);
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
