package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorDied;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorObserved;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseRestartAbsenceObserved;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind;
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMigrationJourney;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMigrationStatus;
import io.farfrontier.palemirror.frontier.v3.model.ResidentTransitAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.ScoutPatrolAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.ScoutPatrolLeaseRecovered;
import io.farfrontier.palemirror.frontier.v3.model.HumanTacticalFunctionProjection;
import io.farfrontier.palemirror.frontier.v3.model.HivePhysiologySupport;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilization;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationAssemblyAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflicted;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationConflictReason;
import io.farfrontier.palemirror.frontier.v3.model.HiveMobilizationStatus;
import io.farfrontier.palemirror.frontier.v3.model.HiveTaskAssembly;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringToolCustody;
import io.farfrontier.palemirror.frontier.v3.process.HiveScoutPatrolProcess;
import io.farfrontier.palemirror.frontier.v3.model.OperationAssembly;
import io.farfrontier.palemirror.frontier.v3.model.OperationAssemblyDeferral;
import io.farfrontier.palemirror.frontier.v3.model.OperationAssemblyAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.OperationAssemblyDeferred;
import io.farfrontier.palemirror.frontier.v3.model.OperationStage;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkAssembly;
import io.farfrontier.palemirror.frontier.v3.model.EngineeringWorkOrder;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstruction;
import io.farfrontier.palemirror.frontier.v3.model.RouteConstructionAssemblyAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenance;
import io.farfrontier.palemirror.frontier.v3.model.RouteMaintenanceAssemblyAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;
import io.farfrontier.palemirror.frontier.v3.model.Settlement;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAccessPort;
import io.farfrontier.palemirror.frontier.v3.model.SettlementStructure;
import io.farfrontier.palemirror.frontier.v3.model.StructureKind;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
/**
 * Materializes exact ordinary residents and hive bioforms through persisted per-actor HOT leases.
 *
 * <p>It owns no canonical mutation and never interprets an unloaded/missing body as death. A
 * deterministic UUID prevents a second body after ordinary unload/reload. A durable COLD hand-off
 * discards the body before its chunk can serialize it; an untagged body with that UUID is a visible
 * non-owning conflict and remains untouched.</p>
 */
final class FrontierV3AmbientActorExecutor {
    static final String ACTOR_KEY = "pale_mirror_frontier_v3_ambient_actor";
    static final String KIND_KEY = "pale_mirror_frontier_v3_ambient_kind";
    private static final int MAX_ACTORS_PER_TICK = 16;
    private static final int DRAIN_SAFE_RADIUS_BLOCKS = 64;
    private static final long DRAIN_HYSTERESIS_TICKS = 200L;
    private static final int MAX_PENDING_ADMISSIONS = 4_096;
    private static final int GRAYBOX_BIOFORM_FIRE_RESISTANCE_TICKS = Integer.MAX_VALUE;
    /**
     * Noncanonical, bounded bridge across {@code EntityJoinLevelEvent} and the global UUID
     * index.  {@link Entity#isAddedToLevel()} becomes true before that index is necessarily
     * published, so it is deliberately not completion evidence.  A live candidate stays here
     * until the index names that exact Java object (or it is removed); failing closed is safer
     * than recreating its deterministic UUID.
     */
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<UUID, PendingAdmission>> PENDING_ADMISSIONS = new IdentityHashMap<>();
    /**
     * Loaded-world observation only: the durable lease remains the source of truth.  A body is
     * never allowed to fall out of a chunk and serialize after its lease has become COLD.
     */
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SubjectId, Long>> COLD_DEMAND_SINCE = new IdentityHashMap<>();
    private FrontierV3AmbientActorExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        cleanPending(level, runtime);
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return;
        // A successor scene cannot begin until the outgoing ambient authority has closed.  This
        // is a correctness hand-off, not ordinary ambient work: it must not be starved behind
        // a previously sorted resident pursuing a local goal.  Process every currently
        // reserved exact identity first, then let the bounded ambient pass handle voluntary
        // movement and new admissions.  Each transfer remains durable and independently
        // validates its exact body before discard.
        handOffReservedActors(level, runtime, state);
        state = runtime.decodedState().orElse(null);
        if (state == null) return;
        int admitted = 0;
        // Commands submitted below synchronously install a new immutable checkpoint.  Preserve
        // the deterministic actor order, but never let a later actor make another physical
        // decision from the predecessor's stale snapshot.
        for (SubjectId actorId : state.actorLocations().keySet().stream().sorted().toList()) {
            if (admitted >= MAX_ACTORS_PER_TICK) return;
            state = runtime.decodedState().orElse(null);
            if (state == null) return;
            var location = state.actorLocations().get(actorId);
            if (location == null || location.condition().status() != ActorLifeStatus.ALIVE) {
                forgetColdDemand(runtime, actorId);
                FrontierV3AmbientActorCaches.forgetObserved(runtime, actorId);
                continue;
            }
            if (!HivePhysiologySupport.permitsAmbientLease(state, actorId)) {
                // Cocoon custody is materially represented by the owned block, never by a
                // second dormant Zombie. A lifecycle transition must durably release the
                // cocoon before this executor may admit a body again.
                forgetColdDemand(runtime, actorId);
                FrontierV3AmbientActorCaches.forgetObserved(runtime, actorId);
                continue;
            }
            var lease = state.ambientLeases().get(actorId);
            boolean successorSceneOwnsActor = state.sceneLeases().values().stream()
                    // A conflicted scene remains the exact physical claimant until its owner
                    // records recovery.  Releasing its former ambient body here would permit a
                    // second lease/body to appear after a visible conflict.
                    .filter(scene -> scene.status() != SceneLeaseStatus.CLOSED)
                    .anyMatch(scene -> scene.members().stream().anyMatch(member -> member.actorId().equals(actorId)));
            if (lease != null && lease.status() == AmbientLeaseStatus.CLOSED && !successorSceneOwnsActor) {
                Entity stale = level.getEntity(entityId(state, actorId));
                if (stale != null && owned(stale, actorId, bioform(state, actorId))) stale.discard();
                FrontierV3AmbientActorCaches.forgetObserved(runtime, actorId);
            }
            // An exact ambient-to-scene hand-off transfers the existing body under its same
            // UUID.  While its successor scene is PREPARED, leave that body untouched for the
            // scene executor to claim; discarding it here would turn a hand-off into a respawn
            // at an old canonical floor.  All other closed ambient bodies remain stale.
            if (successorSceneOwnsActor) {
                forgetColdDemand(runtime, actorId);
                FrontierV3AmbientActorCaches.forgetObserved(runtime, actorId);
                continue;
            }
            if (reservedActors(runtime, state).contains(actorId)) {
                // Reservation alone is not a hand-off: the successor scene is forbidden to
                // overlap this HOT authority.  Capture this exact observed body first, close
                // the ambient lease durably, and only then let the next scene turn materialize
                // the same canonical identity.  This is deliberately general rather than a
                // harvest special case; every pre-lease candidate uses the same ownership law.
                if (lease != null && lease.status() == AmbientLeaseStatus.HOT) {
                    Entity body = level.getEntity(entityId(state, actorId));
                    if (body instanceof Mob mob && owned(mob, actorId, bioform(state, actorId)) && drain(runtime, mob)) admitted++;
                } else if (lease != null && lease.status() == AmbientLeaseStatus.PREPARED
                        && abandonPreparedForReservation(level, runtime, state, actorId, lease)) {
                    admitted++;
                }
                forgetColdDemand(runtime, actorId);
                FrontierV3AmbientActorCaches.forgetObserved(runtime, actorId);
                continue;
            }
            // A future field-work scene owns the actor's next purpose but not yet its body.
            // Keep a naturally loaded ambient body stationary for the typed scene hand-off;
            // do not let a generic local goal move, drain or replace the named worker.
            FrontierSceneAdmission.GenericAmbientAdmission genericAdmission = genericAmbientAdmission(runtime, state);
            if (genericAdmission.reserves(actorId)) {
                // A typed process may require one inert physical body purely to transfer its
                // stable identity into a HOT lease.  The behavior registry supplies the
                // standing rule; this generic ambient executor never branches on harvest (or
                // any other process) semantics.
                Optional<FrontierV3SceneBehaviorRegistry.StandingPositionProvider> preLeaseStanding = genericAdmission.preLeaseSceneCause(actorId)
                        .map(FrontierV3SceneBehaviorRegistry::preLeaseStandingPositionProvider);
                if (FrontierV3PreLeaseDemandGate.holdsForTypedHandoff(preLeaseStanding.isPresent(),
                        demand(level, location.supportingSurface().support()))) {
                    if (lease == null || lease.status() == AmbientLeaseStatus.CLOSED) {
                        submit(runtime, "ambient-pre-lease-prepare", actorId.value(),
                                new AmbientLeasePrepared(AmbientActorProcess.nextLease(state, actorId,
                                        runtime.canonicalState().orElseThrow().instant())));
                        admitted++;
                    } else if (lease.status() == AmbientLeaseStatus.PREPARED) {
                        Result result = materialize(level, runtime, state, actorId, lease.handoffBody(), preLeaseStanding.orElseThrow());
                        if (result == Result.APPLIED || result == Result.CURRENT || result == Result.PENDING) {
                            if (result != Result.PENDING) submit(runtime, "ambient-pre-lease-hot", actorId.value(),
                                    new AmbientLeaseTransition(actorId, AmbientLeaseStatus.HOT));
                            admitted++;
                        }
                    }
                } else if (lease != null && lease.status() == AmbientLeaseStatus.HOT) {
                    Entity body = level.getEntity(entityId(state, actorId));
                    if (body instanceof Mob mob && owned(mob, actorId, bioform(state, actorId)) && drain(runtime, mob)) admitted++;
                } else if (lease != null && lease.status() == AmbientLeaseStatus.PREPARED
                        && abandonPreparedForReservation(level, runtime, state, actorId, lease)) {
                    admitted++;
                }
                forgetColdDemand(runtime, actorId);
                FrontierV3AmbientActorCaches.forgetObserved(runtime, actorId);
                continue;
            }
            boolean demanded = demand(level, location.supportingSurface().support());
            if (!demanded) {
                if (lease != null && lease.status() == AmbientLeaseStatus.HOT) {
                    Entity body = level.getEntity(entityId(state, actorId));
                    if (body instanceof Mob mob && owned(mob, actorId, bioform(state, actorId))) {
                        FrontierV3AmbientActorCaches.rememberObserved(runtime, actorId, mob, MAX_PENDING_ADMISSIONS);
                        if (FrontierV3AmbientActorLocalTargets.directedGoal(lease)) {
                            if (pursueLocalGoal(level, runtime, state, actorId, mob, lease)) return;
                            if (observeDirectedArrival(level, runtime, state, actorId, mob, lease)) admitted++;
                        }
                        if (drainAfterDemandHysteresis(level, runtime, actorId, mob)) admitted++;
                    } else if (drainObservedAfterDemandHysteresis(level, runtime, actorId)) {
                        admitted++;
                    }
                } else {
                    forgetColdDemand(runtime, actorId);
                }
                continue;
            }
            forgetColdDemand(runtime, actorId);
            if (lease == null || lease.status() == AmbientLeaseStatus.CLOSED) {
                submit(runtime, "ambient-prepare", actorId.value(), new AmbientLeasePrepared(AmbientActorProcess.nextLease(state, actorId, runtime.canonicalState().orElseThrow().instant())));
                admitted++;
                continue;
            }
            if (lease.status() == AmbientLeaseStatus.PREPARED) {
                Result result = materialize(level, runtime, state, actorId, lease.handoffBody());
                if (result == Result.APPLIED || result == Result.CURRENT || result == Result.PENDING) {
                    if (result != Result.PENDING) submit(runtime, "ambient-hot", actorId.value(), new AmbientLeaseTransition(actorId, AmbientLeaseStatus.HOT));
                    admitted++;
                }
                continue;
            }
            Entity body = level.getEntity(entityId(state, actorId));
            if (lease.status() == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART) {
                if (body != null && owned(body, actorId, bioform(state, actorId))) {
                    submit(runtime, "ambient-recovered", actorId.value(), new AmbientLeaseTransition(actorId, AmbientLeaseStatus.HOT));
                    admitted++;
                } else if (body == null && restartAbsenceIsObserved(level, runtime, state, actorId, lease)) {
                    // This is a loaded-world negative postcondition, not a desired-state
                    // overwrite.  A normal player death is already observed before its body
                    // disappears; after restart an absent exact body therefore closes only
                    // the failed HOT hand-off, retaining the canonical living actor for the
                    // ordinary next PREPARED -> HOT admission.
                    submit(runtime, "ambient-restart-absence", actorId.value(),
                            new AmbientLeaseRestartAbsenceObserved(actorId, lease.handoffBody()));
                    admitted++;
                }
                continue;
            }
            if (lease.status() == AmbientLeaseStatus.HOT && body instanceof Mob mob && owned(body, actorId, bioform(state, actorId))) {
                FrontierV3AmbientActorCaches.rememberObserved(runtime, actorId, mob, MAX_PENDING_ADMISSIONS);
                FrontierV3ScenePresentation.applyAmbientActorPresentation(mob, state, actorId, bioform(state, actorId));
                if (FrontierV3HotScoutObservation.observe(level, runtime, state, actorId, mob, lease)) {
                    admitted++;
                    continue;
                }
                if (pursueLocalGoal(level, runtime, state, actorId, mob, lease)) return;
                if (observeDirectedArrival(level, runtime, state, actorId, mob, lease)) admitted++;
            }
        }
    }

    /**
     * Gives a successor scene's exact pre-lease reservation priority over ordinary ambient
     * goals.  The deterministic full scan is bounded by successful durable hand-offs rather
     * than by the first arbitrary actor in lexical order: actors without a live predecessor
     * cost only a read.  A conflict deliberately remains unresolved for the normal visible
     * recovery path; this method never replaces or guesses a body.
     */
    private static void handOffReservedActors(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                              FrontierWorldState initialState) {
        int transferred = 0;
        for (SubjectId actorId : initialState.actorLocations().keySet().stream().sorted().toList()) {
            if (transferred >= MAX_ACTORS_PER_TICK) return;
            FrontierWorldState state = runtime.decodedState().orElse(null);
            if (state == null || !FrontierSceneAdmission.reserved(state, actorId)) continue;
            var location = state.actorLocations().get(actorId);
            if (location == null || location.condition().status() != ActorLifeStatus.ALIVE
                    || !HivePhysiologySupport.permitsAmbientLease(state, actorId)) continue;
            AmbientActorLease lease = state.ambientLeases().get(actorId);
            if (lease == null || lease.status() == AmbientLeaseStatus.CLOSED) continue;
            if (lease.status() == AmbientLeaseStatus.HOT) {
                Entity body = level.getEntity(entityId(state, actorId));
                if (body instanceof Mob mob && owned(mob, actorId, bioform(state, actorId)) && drain(runtime, mob)) transferred++;
                continue;
            }
            if (lease.status() == AmbientLeaseStatus.PREPARED
                    && abandonPreparedForReservation(level, runtime, state, actorId, lease)) transferred++;
        }
    }
    static Result materialize(ServerLevel level, FrontierWorldState state, SubjectId actorId, BodyPosition canonicalBody) {
        return materialize(level, state, actorId, canonicalBody, FrontierV3StandingPosition::aboveExactFloor);
    }

    private static Result materialize(ServerLevel level, FrontierWorldState state, SubjectId actorId, BodyPosition canonicalBody,
                                      FrontierV3SceneBehaviorRegistry.StandingPositionProvider standingPositionProvider) {
        if (!state.actorLocations().containsKey(actorId)) return Result.CONFLICT;
        UUID entityId = entityId(state, actorId); Entity existing = level.getEntity(entityId); boolean bioform = bioform(state, actorId);
        if (existing != null) {
            if (!owned(existing, actorId, bioform)) return Result.CONFLICT;
            if (existing instanceof Zombie zombie) configureBioform(zombie, bioformProfile(state, actorId));
            // A recovered PREPARED body has not yet crossed the canonical HOT boundary.
            // Keep it inert until that durable transition is accepted.
            if (existing instanceof Mob body) {
                body.getNavigation().stop();
                body.setNoAi(true);
            }
            return Result.CURRENT;
        }
        BlockPos position = standingPositionProvider.resolve(level, minecraftFloor(canonicalBody.supportingSurface().support()));
        if (position == null || !position.equals(minecraftBody(canonicalBody)) || !level.hasChunkAt(position)) return Result.DEFERRED;
        Mob body = bioform ? EntityType.ZOMBIE.create(level) : EntityType.VILLAGER.create(level);
        if (body == null) throw new IllegalStateException("Minecraft could not create a Frontier v3 ambient actor");
        body.setUUID(entityId); body.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D); body.setPersistenceRequired();
        // The body exists before the durable PREPARED -> HOT acknowledgement. Do not let
        // vanilla AI move it across that crash window.
        body.setNoAi(true);
        if (body instanceof Zombie zombie) configureBioform(zombie, bioformProfile(state, actorId));
        hydrateExactHeldEquipment(body, state, actorId);
        FrontierV3ScenePresentation.applyAmbientActorPresentation(body, state, actorId, bioform);
        body.getPersistentData().putString(ACTOR_KEY, actorId.value()); body.getPersistentData().putString(KIND_KEY, bioform ? "BIOFORM" : "RESIDENT");
        return level.addFreshEntity(body) ? Result.APPLIED : Result.CONFLICT;
    }

    /**
     * A newly created exact body must reflect already canonical actor custody after ordinary
     * COLD/restart materialization. Existing loaded bodies are never overwritten here: player
     * changes on those bodies remain observation input, not desired-state repair.
     */
    private static void hydrateExactHeldEquipment(Mob body, FrontierWorldState state, SubjectId actorId) {
        if (!body.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return;
        // The main hand is one exact physical projection.  A fresh COLD/restart body must
        // re-materialize either supported current main-hand capability from canonical actor
        // custody; otherwise an engineering pickaxe disappears between the issue and return
        // operations even though the canonical item still has the same actor owner.
        state.inventory().actorItems(actorId).stream().filter(item -> HumanTacticalFunctionProjection.isGrayboxWeaponKind(item.itemKind())
                        || EngineeringToolCustody.isTool(item.itemKind()))
                .sorted(Comparator.comparing(io.farfrontier.palemirror.frontier.v3.model.ExactItemStack::id)).findFirst()
                .ifPresent(item -> body.setItemSlot(EquipmentSlot.MAINHAND, FrontierV3CargoHandoffExecutor.materializedStack(item)));
    }

    static Result materialize(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                              FrontierWorldState state, SubjectId actorId, BodyPosition canonicalBody) {
        return materialize(level, runtime, state, actorId, canonicalBody, FrontierV3StandingPosition::aboveExactFloor);
    }

    private static Result materialize(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                      FrontierWorldState state, SubjectId actorId, BodyPosition canonicalBody,
                                      FrontierV3SceneBehaviorRegistry.StandingPositionProvider standingPositionProvider) {
        PendingAdmission pending = pending(runtime, entityId(state, actorId));
        if (pending != null && owned(pending.entity(), actorId, bioform(state, actorId))) return Result.PENDING;
        // A chunk can expose blocks before PersistentEntitySectionManager has finished restoring
        // saved entities. Creating our deterministic UUID in that interval races the ordinary
        // saved-body join and can briefly surface a duplicate to players. This read-only proof
        // neither loads a chunk nor interprets absence as death. Production callers must use this
        // runtime overload; the state-only overload is retained for isolated GameTest fixtures.
        BlockPos position = standingPositionProvider.resolve(level, minecraftFloor(canonicalBody.supportingSurface().support()));
        if (position == null || !position.equals(minecraftBody(canonicalBody))
                || !mayCreateFreshBody(level.hasChunkAt(position), level.areEntitiesLoaded(ChunkPos.asLong(position)), true)) return Result.DEFERRED;
        return materialize(level, state, actorId, canonicalBody, standingPositionProvider);
    }

    static UUID entityId(FrontierWorldState state, SubjectId actorId) { return io.farfrontier.palemirror.frontier.v3.model.SceneLease.deterministicEntityId(state.bootstrap().worldId(), actorId); }

    /** Pure gate retained for the negative admission regression. */
    static boolean mayCreateFreshBody(boolean chunkLoaded, boolean entitiesLoaded, boolean exactHeadroom) {
        return chunkLoaded && entitiesLoaded && exactHeadroom;
    }

    /**
     * The only legal absence proof is the exact canonical hand-off column in a naturally
     * loaded chunk.  An unloaded chunk stays UNKNOWN; a mismatched loaded entity stays a
     * conflict path and is never replaced here.
     */
    static boolean restartAbsenceIsObserved(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime, FrontierWorldState state,
                                            SubjectId actorId, AmbientActorLease lease) {
        return restartAbsenceIsObserved(level, state, actorId, lease) && pending(runtime, entityId(state, actorId)) == null;
    }

    /** Package-visible pure loaded-world absence proof used by the isolated recovery fixture. */
    static boolean restartAbsenceIsObserved(ServerLevel level, FrontierWorldState state, SubjectId actorId, AmbientActorLease lease) {
        var location = state.actorLocations().get(actorId);
        return location != null && location.condition().status() == ActorLifeStatus.ALIVE
                && lease.status() == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART
                && location.body().equals(lease.handoffBody())
                && level.hasChunkAt(minecraftBody(lease.handoffBody()))
                && level.getEntity(entityId(state, actorId)) == null;
    }

    /**
     * Read-only loaded-world admission evidence for one canonical ambient actor.  This must not
     * load a chunk or alter a lease: it exists so an operator can distinguish a legitimate
     * unloaded/blocked deferral from a UUID ownership conflict while investigating a visible
     * PREPARED lease.
     */
    static FrontierV3AmbientAdmissionDiagnostic admissionDiagnostic(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                     FrontierWorldState state, SubjectId actorId) {
        var location = state.actorLocations().get(actorId);
        if (location == null) return FrontierV3AmbientAdmissionDiagnostic.notCanonical();
        UUID expectedId = entityId(state, actorId);
        // A dead canonical actor can still have a short-lived Minecraft death animation or
        // removal callback carrying its historical UUID. That is terminal physical evidence,
        // not an attempted ambient admission and never a foreign-body conflict.
        if (location.condition().status() != ActorLifeStatus.ALIVE) return FrontierV3AmbientAdmissionDiagnostic.terminal(expectedId);
        Entity existing = level.getEntity(expectedId);
        if (existing != null) {
            BlockPosition observedPosition = new BlockPosition(existing.getBlockX(), existing.getBlockY(), existing.getBlockZ());
            ObservedPosition observedExact = new ObservedPosition(existing.getX(), existing.getY(), existing.getZ());
            if (owned(existing, actorId, bioform(state, actorId))) {
                return FrontierV3AmbientAdmissionDiagnostic.indexed(expectedId, pending(runtime, expectedId) != null, observedPosition, observedExact);
            }
            // A physical UUID belongs to the canonical actor rather than to a lease. During a
            // legal ambient-to-scene hand-off the same body has already exchanged its ambient
            // tags for an active scene lease, so this is not a duplicate or foreign body.
            if (FrontierV3SceneExecutor.recognizes(runtime, existing)) {
                return FrontierV3AmbientAdmissionDiagnostic.sceneOwned(expectedId, observedPosition, observedExact);
            }
            return FrontierV3AmbientAdmissionDiagnostic.conflict(expectedId);
        }
        PendingAdmission pending = pending(runtime, expectedId);
        if (pending != null) return FrontierV3AmbientAdmissionDiagnostic.pendingUnindexed(expectedId,
                new BlockPosition(pending.entity().getBlockX(), pending.entity().getBlockY(), pending.entity().getBlockZ()),
                new ObservedPosition(pending.entity().getX(), pending.entity().getY(), pending.entity().getZ()));
        BlockPos anchor = minecraftBody(location.body());
        if (!level.hasChunkAt(anchor)) return FrontierV3AmbientAdmissionDiagnostic.unloaded(expectedId);
        if (!level.areEntitiesLoaded(ChunkPos.asLong(anchor))) return FrontierV3AmbientAdmissionDiagnostic.entityStoragePending(expectedId,
                new BlockPosition(location.body().x(), location.body().y(), location.body().z()));
        if (!FrontierV3StandingPosition.hasExactStandingColumn(level, location.supportingSurface().support())) return FrontierV3AmbientAdmissionDiagnostic.blocked(expectedId, location.supportingSurface().support());
        return FrontierV3AmbientAdmissionDiagnostic.ready(expectedId, new BlockPosition(location.body().x(), location.body().y(), location.body().z()));
    }

    /** Retains only an exact expected body during the short join-to-index hand-off. */
    static JoinDisposition observeJoin(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null || !recognizes(runtime, entity)) return JoinDisposition.NOT_MANAGED;
        String rawActorId = entity.getPersistentData().getString(ACTOR_KEY);
        if (rawActorId.isBlank()) return JoinDisposition.NOT_MANAGED;
        SubjectId actorId;
        try { actorId = new SubjectId(rawActorId); } catch (IllegalArgumentException invalid) { return JoinDisposition.NOT_MANAGED; }
        if (!state.actorLocations().containsKey(actorId) || state.actorLocations().get(actorId).condition().status() != ActorLifeStatus.ALIVE
                || !entityId(state, actorId).equals(entity.getUUID()) || !owned(entity, actorId, bioform(state, actorId))) return JoinDisposition.NOT_MANAGED;
        Map<UUID, PendingAdmission> pending = PENDING_ADMISSIONS.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        if (pending.size() >= MAX_PENDING_ADMISSIONS && !pending.containsKey(entity.getUUID())) return JoinDisposition.NOT_MANAGED;
        PendingAdmission present = pending.get(entity.getUUID());
        if (present != null && present.entity() != entity && !present.entity().isRemoved()) {
            PaleMirrorMod.LOGGER.warn("Frontier v3 rejects a duplicate unindexed managed body uuid={} for actor={}",
                    entity.getUUID(), actorId.value());
            return JoinDisposition.DUPLICATE_UNINDEXED;
        }
        pending.put(entity.getUUID(), new PendingAdmission(entity));
        if (state.ambientLeases().get(actorId) != null && state.ambientLeases().get(actorId).status() == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART) {
            submit(runtime, "ambient-recovered", actorId.value(), new AmbientLeaseTransition(actorId, AmbientLeaseStatus.HOT));
        }
        return JoinDisposition.RETAINED;
    }

    /**
     * Strict non-mutating admission proof for the shared graybox entity boundary.
     * A tag alone is never sufficient: this verifies the live canonical actor, exact UUID,
     * actor kind and an extant ambient lease before a V3 body may enter the physical world.
     */
    static boolean recognizes(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        String rawActorId = entity.getPersistentData().getString(ACTOR_KEY);
        if (rawActorId.isBlank()) return false;
        SubjectId actorId;
        try { actorId = new SubjectId(rawActorId); } catch (IllegalArgumentException invalid) { return false; }
        var location = state.actorLocations().get(actorId);
        var lease = state.ambientLeases().get(actorId);
        return location != null && location.condition().status() == ActorLifeStatus.ALIVE
                && lease != null && lease.status() != AmbientLeaseStatus.CLOSED
                && !FrontierSceneAdmission.reserved(state, actorId)
                && entityId(state, actorId).equals(entity.getUUID())
                && owned(entity, actorId, bioform(state, actorId));
    }
    /** Accepts only a real loaded-world death for the exact HOT ambient body. */
    static boolean observeDeath(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity, Entity source) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        String rawActorId = entity.getPersistentData().getString(ACTOR_KEY);
        if (rawActorId.isBlank()) return false;
        SubjectId actorId;
        try { actorId = new SubjectId(rawActorId); } catch (IllegalArgumentException invalid) { return false; }
        if (!state.actorLocations().containsKey(actorId) || state.actorLocations().get(actorId).condition().status() != ActorLifeStatus.ALIVE
                || state.sceneLeases().values().stream().anyMatch(lease -> lease.status() != io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus.CLOSED
                && lease.members().stream().anyMatch(member -> member.actorId().equals(actorId)))
                || state.ambientLeases().get(actorId) == null || state.ambientLeases().get(actorId).status() != AmbientLeaseStatus.HOT
                || !entityId(state, actorId).equals(entity.getUUID()) || !owned(entity, actorId, bioform(state, actorId))) return false;
        String cause = source == null ? "environment" : "entity:" + source.getUUID();
        submit(runtime, "ambient-death", actorId.value(),
                new AmbientActorDied(actorId, observedBody(entity), cause));
        return true;
    }
    /**
     * A late entity-leave callback never changes a HOT lease. Minecraft may already have serialized
     * the body, so closing here could admit a second deterministic UUID on the next player demand.
     */
    static boolean observeLeave(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity, boolean serverStopping) {
        if (serverStopping) return false;
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null || !(entity instanceof Mob body)) return false;
        String rawActorId = entity.getPersistentData().getString(ACTOR_KEY);
        if (rawActorId.isBlank()) return false;
        SubjectId actorId;
        try { actorId = new SubjectId(rawActorId); } catch (IllegalArgumentException invalid) { return false; }
        var current = state.actorLocations().get(actorId);
        if (current == null || current.condition().status() != ActorLifeStatus.ALIVE || !entityId(state, actorId).equals(entity.getUUID())
                || !owned(entity, actorId, bioform(state, actorId)) || body.getHealth() <= 0.0F
                || state.ambientLeases().get(actorId) == null || state.ambientLeases().get(actorId).status() != AmbientLeaseStatus.HOT) return false;
        // EntityLeaveLevelEvent can be emitted only after chunk serialization has begun.  A
        // transition to CLOSED here would permit a fresh body on the next demand while the old
        // UUID is still in chunk NBT.  The regular tick drains, durably closes and discards the
        // body while it is unquestionably loaded.  An unexpected leave therefore fails closed
        // as HOT and is reclaimed by the same UUID if Minecraft restores it later.
        return false;
    }
    private static boolean demand(ServerLevel level, BlockPosition position) {
        // Ambient custody has one lease per exact actor, but observer presence still has the
        // same one aggregate meaning as a work scene.  Keep player scans and radius policy in
        // the shared read-only input rather than allowing this executor to drift into an
        // independent admission rule.
        return FrontierV3SceneExecutor.demandExists(level, position);
    }
    static boolean bioform(FrontierWorldState state, SubjectId actorId) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .map(Bioform::id).anyMatch(actorId::equals);
    }
    static Bioform bioformProfile(FrontierWorldState state, SubjectId actorId) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(bioform -> bioform.id().equals(actorId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("not a canonical Frontier v3 bioform: " + actorId));
    }
    static boolean owned(Entity entity, SubjectId actorId, boolean bioform) {
        return !entity.isRemoved() && actorId.value().equals(entity.getPersistentData().getString(ACTOR_KEY))
                && (bioform ? entity instanceof Zombie : entity instanceof Villager)
                && (bioform ? "BIOFORM" : "RESIDENT").equals(entity.getPersistentData().getString(KIND_KEY));
    }
    /** The graybox Zombie is a hive creature, not a vanilla undead exposed to daylight. */
    static void configureBioform(Zombie body, Bioform bioform) {
        if (!body.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            body.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, GRAYBOX_BIOFORM_FIRE_RESISTANCE_TICKS, 0, true, false));
        }
        // Fire resistance prevents daylight damage but vanilla Zombies still render as burning.
        // The non-damageable role marker suppresses only vanilla sunlight ignition; real fire and
        // explosion effects remain visible and flow through their normal physical observation path.
        body.setCanPickUpLoot(false);
        if (body.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            body.setItemSlot(EquipmentSlot.HEAD, new ItemStack(bioform.isExplosiveAssaulter() ? Items.RED_WOOL
                    : bioform.isScout() ? Items.CYAN_WOOL : bioform.isDefender() ? Items.PURPLE_WOOL : Items.LIME_WOOL));
            body.setDropChance(EquipmentSlot.HEAD, 0.0F);
        }
    }
    /**
     * Executes one bounded ambient local brain.  The durable lease owns the purpose and the
     * individual hand-off slot is the spatial anchor; using a settlement/nest centroid would
     * route a valid exterior body through owned structure geometry.
     * exact actor identity supplies a stable phase.  Minecraft's vanilla goal AI intentionally
     * remains disabled because ambient combat, target acquisition and inventory use would evade
     * the v3 physical-intent ledger.
     */
    /** @return true when a canonical command was accepted and this tick must not use its old snapshot again. */
    static boolean pursueLocalGoal(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SubjectId actorId, Mob body, AmbientActorLease lease) {
        if (lease.goal() == AmbientGoalKind.TRANSIT) {
            ResidentMigrationJourney journey = state.humanPopulation().migration(actorId);
            if (journey == null || journey.status() != ResidentMigrationStatus.EN_ROUTE || journey.arriving()
                    || !lease.goalBody().equals(BodyPosition.above(new SurfaceAnchor(journey.nextColdPosition())))) {
                body.getNavigation().stop();
                return false;
            }
        }
        if (lease.goal() == AmbientGoalKind.SCOUT_PATROL && !lease.goalBody().equals(state.actorLocations().get(actorId).body())) {
            // The command reducer owns the cursor transition; movement may only approach the
            // exact next lease target, never choose a local substitute after COLD hand-off.
            BlockPos physicalTarget = minecraftBody(lease.goalBody());
            if (!level.hasChunkAt(physicalTarget) || !FrontierV3StandingPosition.hasExactStandingColumn(level, lease.goalBody().supportingSurface().support())) {
                body.getNavigation().stop();
                return false;
            }
            FrontierV3ControlledMobMotion.moveToward(level, body, new Vec3(physicalTarget.getX() + 0.5D,
                    physicalTarget.getY(), physicalTarget.getZ() + 0.5D));
            return false;
        }
        if (lease.goal() == AmbientGoalKind.HIVE_TASK_ASSEMBLY) {
            HiveTaskAssembly.Member member = hiveAssemblyMember(state, actorId, lease);
            HiveMobilization mobilization = assemblingMobilization(state, actorId);
            if (mobilization == null || member == null || member.arrived()) {
                body.getNavigation().stop();
                return false;
            }
            HiveTaskAssembly assembly = mobilization.assembly().orElseThrow();
            if (!assembly.safeAdvances().contains(actorId)) {
                body.getNavigation().stop();
                return false;
            }
            BlockPos physicalTarget = minecraftBody(lease.goalBody());
            if (!level.hasChunkAt(physicalTarget)) {
                body.getNavigation().stop();
                return false;
            }
            if (!FrontierV3StandingPosition.hasExactStandingColumn(level, lease.goalBody().supportingSurface().support())) {
                io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-hive-assembly-blocked",
                        actorId.value(), new HiveMobilizationConflicted(mobilization.id(), HiveMobilizationConflictReason.ASSEMBLY_PATH_BLOCKED,
                                java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.model.HiveAssemblyBlockage(actorId,
                                        member.cursor(), member.nextSurface()))));
                FrontierV3DiagnosticTrace.record(level.getServer(), "hive-assembly:" + mobilization.id().value(),
                        "hive_assembly_path_blocked", actorId, result);
                body.getNavigation().stop();
                return result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted;
            }
            FrontierV3ControlledMobMotion.moveToward(level, body, new Vec3(physicalTarget.getX() + 0.5D,
                    physicalTarget.getY(), physicalTarget.getZ() + 0.5D));
            return false;
        }
        if (lease.goal() == AmbientGoalKind.OPERATION_ASSEMBLY) {
            OperationAssembly.Member member = assemblyMember(state, actorId, lease);
            if (member == null) {
                body.getNavigation().stop();
                return false;
            }
            RouteOperation operation = assemblingOperation(state, actorId);
            OperationAssembly assembly = operation.activeAssembly().orElseThrow();
            // Assembly is one public operation, not two independent pedestrians.  A recorded
            // block therefore holds every other member at its current exact cursor.  Only the
            // named blocked actor may inspect the restored same target and clear the fact by a
            // normal observed arrival; no command clears it speculatively.
            if (assembly.deferral().isPresent() && !assembly.deferral().orElseThrow().actorId().equals(actorId)) {
                body.getNavigation().stop();
                return false;
            }
            // A physical follower must not walk into a retained colleague's current cell just
            // because that colleague may move later. The same immediate occupancy rule owns
            // COLD and HOT assembly; the actor waits for the leading observed cursor instead.
            if (!assembly.safeAdvances().contains(actorId)) {
                body.getNavigation().stop();
                return false;
            }
            BlockPosition obstruction = assemblyObstruction(level, state, operation, lease.goalBody().supportingSurface().support());
            if (obstruction != null) {
                OperationAssemblyDeferral deferral = new OperationAssemblyDeferral(actorId, lease.goalBody().supportingSurface(), new SurfaceAnchor(obstruction),
                        OperationAssemblyDeferral.Reason.LOADED_WORLD_OBSTRUCTION);
                if (!assembly.deferral().filter(deferral::equals).isPresent()) {
                    io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-operation-assembly-deferred", actorId.value(),
                            new OperationAssemblyDeferred(operation.id(), deferral));
                    FrontierV3DiagnosticTrace.record(level.getServer(), "operation-assembly:" + operation.id().value(), "operation_assembly_deferred", actorId, result);
                    body.getNavigation().stop();
                    return true;
                }
                body.getNavigation().stop();
                return false;
            }
            BlockPos physicalTarget = minecraftBody(lease.goalBody());
            if (!level.hasChunkAt(physicalTarget) || !FrontierV3StandingPosition.hasExactStandingColumn(level, lease.goalBody().supportingSurface().support())) {
                body.getNavigation().stop();
                return false;
            }
            FrontierV3ControlledMobMotion.moveToward(level, body, new Vec3(physicalTarget.getX() + 0.5D,
                    physicalTarget.getY(), physicalTarget.getZ() + 0.5D));
            return false;
        }
        if (lease.goal() == AmbientGoalKind.ENGINEERING_ASSEMBLY) {
            EngineeringWorkAssembly.Member member = engineeringAssemblyMember(state, actorId, lease);
            if (member == null || member.arrived()) {
                body.getNavigation().stop();
                return false;
            }
            EngineeringWorkAssembly assembly = engineeringProject(state, actorId).assembly().orElseThrow();
            // This is the same retained queue that COLD advances.  A physical body waits for
            // the exact leading cursor instead of walking around a colleague or inventing an
            // alternate lane; arrival will atomically advance this one cursor in the domain.
            if (!assembly.safeAdvances().contains(actorId)) {
                body.getNavigation().stop();
                return false;
            }
            BlockPos physicalTarget = minecraftBody(lease.goalBody());
            if (!level.hasChunkAt(physicalTarget) || !FrontierV3StandingPosition.hasExactStandingColumn(level, lease.goalBody().supportingSurface().support())) {
                body.getNavigation().stop();
                return false;
            }
            FrontierV3ControlledMobMotion.moveToward(level, body, new Vec3(physicalTarget.getX() + 0.5D,
                    physicalTarget.getY(), physicalTarget.getZ() + 0.5D));
            return false;
        }
        FrontierV3ControlledMobMotion.followContinuously(level, body,
                FrontierV3AmbientActorLocalTargets.localTarget(state, actorId, lease, level.getGameTime()));
        return false;
    }
    /** Durably captures then removes a loaded HOT body; the return value proves no serialized duplicate remains. */
    static boolean drain(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null) return false;
        String rawActorId = body.getPersistentData().getString(ACTOR_KEY);
        if (rawActorId.isBlank()) return false;
        SubjectId actorId;
        try { actorId = new SubjectId(rawActorId); } catch (IllegalArgumentException invalid) { return false; }
        var current = state.actorLocations().get(actorId);
        if (current == null || current.condition().status() != ActorLifeStatus.ALIVE || !entityId(state, actorId).equals(body.getUUID())
                || !owned(body, actorId, bioform(state, actorId)) || body.getHealth() <= 0.0F
                || state.ambientLeases().get(actorId) == null || state.ambientLeases().get(actorId).status() != AmbientLeaseStatus.HOT) return false;
        BodyPosition position = observedBody(body);
        ResidentMigrationJourney journey = state.humanPopulation().migration(actorId);
        if (state.ambientLeases().get(actorId).goal() == AmbientGoalKind.TRANSIT) {
            if (journey == null) return false;
            BodyPosition cursor = BodyPosition.above(new SurfaceAnchor(journey.currentPosition()));
            if (!position.equals(cursor)) return false;
            position = cursor;
        }
        if (state.ambientLeases().get(actorId).goal() == AmbientGoalKind.OPERATION_ASSEMBLY) {
            OperationAssembly.Member member = assemblyMember(state, actorId, state.ambientLeases().get(actorId));
            BodyPosition cursor = member == null ? null : member.currentSurface().standingBody();
            if (cursor == null || !position.equals(cursor)) return false;
            position = cursor;
        }
        if (state.ambientLeases().get(actorId).goal() == AmbientGoalKind.ENGINEERING_ASSEMBLY) {
            EngineeringWorkAssembly.Member member = engineeringAssemblyMember(state, actorId, state.ambientLeases().get(actorId));
            BodyPosition cursor = member == null ? null : BodyPosition.above(new SurfaceAnchor(member.currentPosition()));
            if (cursor == null || !position.equals(cursor)) return false;
            position = cursor;
        }
        if (state.ambientLeases().get(actorId).goal() == AmbientGoalKind.HIVE_TASK_ASSEMBLY) {
            HiveTaskAssembly.Member member = hiveAssemblyMember(state, actorId, state.ambientLeases().get(actorId));
            BodyPosition cursor = member == null ? null : member.currentSurface().standingBody();
            if (cursor == null || !position.equals(cursor)) return false;
            position = cursor;
        }
        if (state.ambientLeases().get(actorId).goal() == AmbientGoalKind.SCOUT_PATROL) {
            if (!position.equals(current.body())) return false;
            position = current.body();
        }
        FixedScalar health = new FixedScalar(Math.round((double) body.getHealth() * FixedScalar.SCALE));
        submit(runtime, "ambient-draining", actorId.value(), new AmbientLeaseTransition(actorId, AmbientLeaseStatus.DRAINING));
        submit(runtime, "ambient-release", actorId.value(), new AmbientLeaseReleased(actorId, position, health));
        body.discard();
        return true;
    }

    /**
     * Cancels the pre-HOT half of an ambient admission for a durable successor reservation.
     * No Minecraft decision has occurred yet: a PREPARED body is inert by contract.  Its
     * canonical hand-off body and health are therefore the only admissible release evidence;
     * a mismatched physical UUID/body remains a conflict and blocks the successor.
     */
    private static boolean abandonPreparedForReservation(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                         FrontierWorldState state, SubjectId actorId, AmbientActorLease lease) {
        Entity body = level.getEntity(entityId(state, actorId));
        if (body != null && (!(body instanceof Mob mob) || !owned(mob, actorId, bioform(state, actorId))
                || !observedBody(mob).equals(lease.handoffBody()))) return false;
        if (!(submit(runtime, "ambient-reserved-draining", actorId.value(),
                new AmbientLeaseTransition(actorId, AmbientLeaseStatus.DRAINING)) instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)) {
            return false;
        }
        FrontierWorldState drained = runtime.decodedState().orElse(null);
        if (drained == null || drained.ambientLeases().get(actorId) == null
                || drained.ambientLeases().get(actorId).status() != AmbientLeaseStatus.DRAINING) return false;
        var condition = drained.actorLocations().get(actorId);
        if (condition == null || condition.condition().status() != ActorLifeStatus.ALIVE) return false;
        boolean released = submit(runtime, "ambient-reserved-release", actorId.value(),
                new AmbientLeaseReleased(actorId, lease.handoffBody(), condition.condition().health()))
                instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted;
        if (released && body != null) body.discard();
        return released;
    }
    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        PENDING_ADMISSIONS.remove(runtime);
        COLD_DEMAND_SINCE.remove(runtime);
        FrontierV3AmbientActorCaches.forget(runtime);
    }

    private static java.util.Set<SubjectId> reservedActors(FrontierV3ServerRuntime<?, ?> runtime, FrontierWorldState state) {
        return FrontierV3AmbientActorCaches.reservedActors(runtime, state);
    }
    private static FrontierSceneAdmission.GenericAmbientAdmission genericAmbientAdmission(FrontierV3ServerRuntime<?, ?> runtime,
                                                                                           FrontierWorldState state) {
        return FrontierV3AmbientActorCaches.genericAmbientAdmission(runtime, state);
    }
    private static PendingAdmission pending(FrontierV3ServerRuntime<?, ?> runtime, UUID entityId) {
        Map<UUID, PendingAdmission> pending = PENDING_ADMISSIONS.get(runtime);
        return pending == null ? null : pending.get(entityId);
    }
    private static void cleanPending(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime) {
        Map<UUID, PendingAdmission> pending = PENDING_ADMISSIONS.get(runtime);
        if (pending == null) return;
        pending.entrySet().removeIf(entry -> {
            Entity candidate = entry.getValue().entity();
            if (candidate.isRemoved()) return true;
            // EntityJoinLevelEvent can run after isAddedToLevel() but before the global UUID
            // index has published the exact body.  Never substitute a second deterministic
            // identity during that interval; an unrelated indexed body is a visible conflict,
            // not permission to forget the candidate.
            return level.getEntity(entry.getKey()) == candidate;
        });
        if (pending.isEmpty()) PENDING_ADMISSIONS.remove(runtime);
    }
    private static boolean drainAfterDemandHysteresis(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                      SubjectId actorId, Mob body) {
        Map<SubjectId, Long> absentSince = COLD_DEMAND_SINCE.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        if (absentSince.size() >= MAX_PENDING_ADMISSIONS && !absentSince.containsKey(actorId)) return false;
        long started = absentSince.computeIfAbsent(actorId, ignored -> level.getGameTime());
        if (level.getGameTime() - started < DRAIN_HYSTERESIS_TICKS || playerWithin(level, body.blockPosition(), DRAIN_SAFE_RADIUS_BLOCKS)) return false;
        boolean drained = drain(runtime, body);
        if (drained) absentSince.remove(actorId);
        if (absentSince.isEmpty()) COLD_DEMAND_SINCE.remove(runtime);
        return drained;
    }
    private static boolean drainObservedAfterDemandHysteresis(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                               SubjectId actorId) {
        Map<SubjectId, Long> absentSince = COLD_DEMAND_SINCE.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        if (absentSince.size() >= MAX_PENDING_ADMISSIONS && !absentSince.containsKey(actorId)) return false;
        long started = absentSince.computeIfAbsent(actorId, ignored -> level.getGameTime());
        var observed = FrontierV3AmbientActorCaches.lastObserved(runtime, actorId);
        if (observed == null || level.getGameTime() - started < DRAIN_HYSTERESIS_TICKS
                || playerWithin(level, minecraftBody(observed.body()), DRAIN_SAFE_RADIUS_BLOCKS)) return false;
        // The state reducer independently proves cursor identity for Transit, operation and
        // engineering movement. A stale cached body is therefore rejected rather than changing
        // the canonical actor's position.
        io.farfrontier.palemirror.frontier.v3.api.CommandResult draining = submit(runtime, "ambient-draining-unloaded", actorId.value(),
                new AmbientLeaseTransition(actorId, AmbientLeaseStatus.DRAINING));
        if (!(draining instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted)) return false;
        io.farfrontier.palemirror.frontier.v3.api.CommandResult released = submit(runtime, "ambient-release-unloaded", actorId.value(),
                new AmbientLeaseReleased(actorId, observed.body(), observed.health()));
        if (released instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted) {
            FrontierV3AmbientActorCaches.forgetObserved(runtime, actorId); absentSince.remove(actorId);
            if (absentSince.isEmpty()) COLD_DEMAND_SINCE.remove(runtime);
            return true;
        }
        return false;
    }
    private static boolean observeDirectedArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                                   SubjectId actorId, Mob body, AmbientActorLease lease) {
        if (lease.goal() == AmbientGoalKind.OPERATION_ASSEMBLY) return observeAssemblyArrival(level, runtime, state, actorId, body, lease);
        if (lease.goal() == AmbientGoalKind.ENGINEERING_ASSEMBLY) return observeEngineeringAssemblyArrival(level, runtime, state, actorId, body, lease);
        if (lease.goal() == AmbientGoalKind.HIVE_TASK_ASSEMBLY) return observeHiveAssemblyArrival(level, runtime, state, actorId, body, lease);
        if (lease.goal() == AmbientGoalKind.SCOUT_PATROL) return observeScoutPatrolArrival(level, runtime, state, actorId, body, lease);
        if (lease.goal() != AmbientGoalKind.TRANSIT) return false;
        ResidentMigrationJourney journey = state.humanPopulation().migration(actorId);
        if (journey == null || journey.status() != ResidentMigrationStatus.EN_ROUTE || journey.arriving()
                || !lease.goalBody().equals(BodyPosition.above(new SurfaceAnchor(journey.nextColdPosition())))) return false;
        if (!observedBody(body).equals(lease.goalBody())) return false;
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-transit", actorId.value(),
                new ResidentTransitAdvanced(actorId, journey.nextRouteIndex()));
        FrontierV3DiagnosticTrace.record(level.getServer(), "resident-transit:" + actorId.value(), "resident_transit_advanced", actorId, result);
        return true;
    }
    private static boolean observeAssemblyArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                                   SubjectId actorId, Mob body, AmbientActorLease lease) {
        OperationAssembly.Member member = assemblyMember(state, actorId, lease);
        if (member == null || member.arrived() || !observedBody(body).equals(lease.goalBody())) return false;
        RouteOperation operation = assemblingOperation(state, actorId); OperationAssembly assembly = operation.activeAssembly().orElseThrow();
        if (!assembly.safeAdvances().contains(actorId)) {
            body.getNavigation().stop();
            return false;
        }
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-operation-assembly", actorId.value(),
                new OperationAssemblyAdvanced(operation.id(), assembly.advance(actorId)));
        FrontierV3DiagnosticTrace.record(level.getServer(), "operation-assembly:" + operation.id().value(), "operation_assembly_advanced", actorId, result);
        return true;
    }
    private static boolean observeEngineeringAssemblyArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                              FrontierWorldState state, SubjectId actorId, Mob body, AmbientActorLease lease) {
        EngineeringWorkAssembly.Member member = engineeringAssemblyMember(state, actorId, lease);
        EngineeringWorkOrder project = engineeringProject(state, actorId);
        if (project == null || member == null || member.arrived()
                || !observedBody(body).equals(lease.goalBody())) return false;
        EngineeringWorkAssembly assembly = project.assembly().orElseThrow();
        if (!assembly.safeAdvances().contains(actorId)) return false;
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-engineering-assembly", actorId.value(),
                assemblyAdvanced(project, assembly.advance(actorId)));
        FrontierV3DiagnosticTrace.record(level.getServer(), "engineering:" + project.id().value(),
                "engineering_assembly_advanced", actorId, result);
        return true;
    }
    private static boolean observeHiveAssemblyArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                       FrontierWorldState state, SubjectId actorId, Mob body, AmbientActorLease lease) {
        HiveMobilization mobilization = assemblingMobilization(state, actorId);
        HiveTaskAssembly.Member member = hiveAssemblyMember(state, actorId, lease);
        if (mobilization == null || member == null || member.arrived() || !observedBody(body).equals(lease.goalBody())) return false;
        HiveTaskAssembly assembly = mobilization.assembly().orElseThrow();
        if (!assembly.safeAdvances().contains(actorId)) {
            body.getNavigation().stop();
            return false;
        }
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-hive-assembly", actorId.value(),
                new HiveMobilizationAssemblyAdvanced(mobilization.id(), actorId, member.cursor()));
        FrontierV3DiagnosticTrace.record(level.getServer(), "hive-assembly:" + mobilization.id().value(),
                "hive_assembly_advanced", actorId, result);
        return true;
    }
    private static boolean observeScoutPatrolArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                                      SubjectId actorId, Mob body, AmbientActorLease lease) {
        BlockPosition current = state.actorLocations().get(actorId).supportingSurface().support();
        if (!observedBody(body).equals(lease.goalBody())) return false;
        BlockPosition expected = HiveScoutPatrolProcess.nextPosition(state, actorId, current);
        if (!lease.goalBody().supportingSurface().support().equals(expected)) {
            // A persisted pre-cursor lease may still name an old target.  The body has reached
            // that exact owned target, so record the observed hand-off and establish the only
            // following cursor; do not forge an ordinary advance to a non-next position.
            io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-scout-patrol-recover", actorId.value(),
                    new ScoutPatrolLeaseRecovered(actorId, current, lease.goalBody().supportingSurface().support(),
                            HiveScoutPatrolProcess.nextPosition(state, actorId, lease.goalBody().supportingSurface().support())));
            FrontierV3DiagnosticTrace.record(level.getServer(), "scout-patrol:" + actorId.value(), "scout_patrol_lease_recovered", actorId, result);
            return true;
        }
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-scout-patrol", actorId.value(),
                new ScoutPatrolAdvanced(actorId, runtime.canonicalState().orElseThrow().instant().ticks(), lease.goalBody().supportingSurface().support(), java.util.Optional.of(current)));
        FrontierV3DiagnosticTrace.record(level.getServer(), "scout-patrol:" + actorId.value(), "scout_patrol_advanced", actorId, result);
        return true;
    }
    private static RouteOperation assemblingOperation(FrontierWorldState state, SubjectId actorId) {
        return state.operations().values().stream().filter(operation -> operation.stage() == OperationStage.ASSEMBLING)
                .filter(operation -> operation.activeAssembly().map(assembly -> assembly.members().containsKey(actorId)).orElse(false))
                .findFirst().orElse(null);
    }
    /** Returns only one authored port floor that is presently unsafe, never an inferred nearby route. */
    private static BlockPosition assemblyObstruction(ServerLevel level, FrontierWorldState state, RouteOperation operation, BlockPosition target) {
        if (!FrontierV3StandingPosition.hasExactStandingColumn(level, target)) return target;
        Settlement settlement = state.bootstrap().settlements().stream().filter(value -> value.id().equals(operation.settlementId())).findFirst().orElse(null);
        SettlementStructure hall = settlement == null ? null : settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL).findFirst().orElse(null);
        BlockPosition throat = hall == null ? null : SettlementAccessPort.forHall(hall).throatSurface().support();
        return throat != null && !FrontierV3StandingPosition.hasExactStandingColumn(level, throat) ? throat : null;
    }
    private static OperationAssembly.Member assemblyMember(FrontierWorldState state, SubjectId actorId, AmbientActorLease lease) {
        RouteOperation operation = assemblingOperation(state, actorId);
        if (operation == null || lease.goal() != AmbientGoalKind.OPERATION_ASSEMBLY) return null;
        OperationAssembly.Member member = operation.activeAssembly().orElseThrow().members().get(actorId);
        SurfaceAnchor expected = member.arrived() ? member.currentSurface() : member.nextSurface();
        return lease.goalBody().equals(expected.standingBody()) ? member : null;
    }
    private static EngineeringWorkOrder engineeringProject(FrontierWorldState state, SubjectId actorId) {
        return java.util.stream.Stream.concat(state.routeConstructions().values().stream(), state.routeMaintenances().values().stream())
                .filter(project -> project.assembly().map(assembly -> assembly.members().containsKey(actorId)).orElse(false))
                .reduce((left, right) -> { throw new IllegalStateException("engineering assembly owner is ambiguous"); }).orElse(null);
    }
    private static EngineeringWorkAssembly.Member engineeringAssemblyMember(FrontierWorldState state, SubjectId actorId, AmbientActorLease lease) {
        EngineeringWorkOrder project = engineeringProject(state, actorId);
        if (project == null || lease.goal() != AmbientGoalKind.ENGINEERING_ASSEMBLY) return null;
        EngineeringWorkAssembly.Member member = project.assembly().orElseThrow().members().get(actorId);
        BlockPosition expected = member.arrived() ? member.currentPosition() : member.corridor().get(member.cursor() + 1);
        return lease.goalBody().equals(BodyPosition.above(new SurfaceAnchor(expected))) ? member : null;
    }
    private static HiveMobilization assemblingMobilization(FrontierWorldState state, SubjectId actorId) {
        return state.hiveColony().mobilizations().values().stream()
                .filter(mobilization -> mobilization.status() == HiveMobilizationStatus.ASSEMBLING)
                .filter(mobilization -> mobilization.assembly().map(assembly -> assembly.members().containsKey(actorId)).orElse(false))
                .reduce((left, right) -> { throw new IllegalStateException("ambient bioform belongs to more than one hive assembly"); })
                .orElse(null);
    }
    private static HiveTaskAssembly.Member hiveAssemblyMember(FrontierWorldState state, SubjectId actorId, AmbientActorLease lease) {
        HiveMobilization mobilization = assemblingMobilization(state, actorId);
        if (mobilization == null || lease.goal() != AmbientGoalKind.HIVE_TASK_ASSEMBLY) return null;
        HiveTaskAssembly.Member member = mobilization.assembly().orElseThrow().members().get(actorId);
        SurfaceAnchor expected = member.arrived() ? member.currentSurface() : member.nextSurface();
        return lease.goalBody().equals(expected.standingBody()) ? member : null;
    }
    private static FrontierPayload assemblyAdvanced(EngineeringWorkOrder project, EngineeringWorkAssembly assembly) {
        return switch (project) {
            case RouteConstruction construction -> new RouteConstructionAssemblyAdvanced(construction.id(), assembly);
            case RouteMaintenance maintenance -> new RouteMaintenanceAssemblyAdvanced(maintenance.id(), assembly);
        };
    }
    private static BlockPos minecraftFloor(BlockPosition floor) { return new BlockPos(floor.x(), floor.y(), floor.z()); }
    private static BlockPos minecraftBody(BodyPosition body) { return new BlockPos(body.x(), body.y(), body.z()); }
    private static BodyPosition observedBody(Entity entity) { return new BodyPosition(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ()); }
    private static void forgetColdDemand(FrontierV3ServerRuntime<?, ?> runtime, SubjectId actorId) {
        Map<SubjectId, Long> absentSince = COLD_DEMAND_SINCE.get(runtime); if (absentSince == null) return;
        absentSince.remove(actorId); if (absentSince.isEmpty()) COLD_DEMAND_SINCE.remove(runtime);
    }
    private static boolean playerWithin(ServerLevel level, BlockPos position, int radius) {
        return FrontierV3SceneDemand.observerWithin(level, List.of(position), radius);
    }
    private static io.farfrontier.palemirror.frontier.v3.api.CommandResult submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                                    String phase, String id, FrontierPayload payload) {
        return FrontierV3CommandSubmission.submit(runtime, phase, id, payload);
    }
    private record PendingAdmission(Entity entity) { } record ObservedPosition(double x, double y, double z) { }
    enum Result { APPLIED, CURRENT, PENDING, DEFERRED, CONFLICT } enum JoinDisposition { NOT_MANAGED, RETAINED, DUPLICATE_UNINDEXED }
}
