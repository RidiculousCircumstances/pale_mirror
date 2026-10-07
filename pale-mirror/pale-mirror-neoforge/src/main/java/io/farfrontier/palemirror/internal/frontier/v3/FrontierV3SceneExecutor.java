package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;
import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.process.FrontierDurationProcessDriverRegistry;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.ActorDirective;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import io.farfrontier.palemirror.frontier.v3.model.ResidentRole;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseRecoveryUnresolved;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseHandoff;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentPrepared;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneStrikeObservation;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssault;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultCauseIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
/**
 * Loaded-chunk HOT executor for exact route participants.
 *
 * <p>A persisted lease is the authority boundary. PREPARED may safely finish materialization of
 * its deterministic bodies after a restart; restart recovery and a live loaded-world conflict
 * remain separate durable states. This executor neither loads chunks nor writes world blocks.</p>
 */
final class FrontierV3SceneExecutor {
    static final String LEASE_KEY = "pale_mirror_frontier_v3_scene_lease";
    static final String ACTOR_KEY = FrontierV3ActorCarrierComposition.ACTOR_KEY;
    static final String REVISION_KEY = "pale_mirror_frontier_v3_scene_revision";
    private static final int DRAIN_SAFE_RADIUS_BLOCKS = 64;
    private static final long DRAIN_HYSTERESIS_TICKS = 200L;
    /**
     * A chunk becoming naturally loaded is not proof that its saved entity UUID index has
     * finished publishing.  Recovery observes a complete loaded scene for this bounded window
     * before absence becomes durable evidence; it never creates, loads or moves an entity.
     */
    private static final long RESTART_RECOVERY_OBSERVATION_TICKS = 20L;
    private static final int MAX_PENDING_DRAINS = 4_096;
    /**
     * Short-lived loaded-world observation.  The durable scene lease remains the only canonical
     * authority; this just prevents a player crossing the demand boundary for one tick from
     * making bodies disappear before their chunk can be safely released.
     */
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SceneLeaseId, Long>> COLD_DEMAND_SINCE = new IdentityHashMap<>();
    /**
     * Volatile first-complete-load ticks for UNKNOWN_AFTER_RESTART leases.  This is deliberately
     * not canonical state: durable evidence begins only after the bounded observation barrier.
     */
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SceneLeaseId, Long>> RESTART_RECOVERY_SINCE = new IdentityHashMap<>();
    /** Admission outcome; release observations live in the durable departure ledger. */
    enum BodyMaterialization { COMPLETE, DEFERRED, CONFLICT }
    private FrontierV3SceneExecutor() { }
    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierV3SceneBehaviorRegistry.tick(level, runtime);
    }
    /** One registered family is checked before every physical executor turn. */
    static void requireRegisteredSceneTurn(FrontierWorldState state, SceneLease lease) {
        FrontierDurationProcessDriverRegistry.requireSceneTick(lease, state.actorLocations(), 1);
    }
    private static Map<SubjectId, BodyPosition> positions(FrontierWorldState state, List<SceneMember> members) {
        Map<SubjectId, BodyPosition> positions = new LinkedHashMap<>();
        for (SceneMember member : members) positions.put(member.actorId(), state.actorLocations().get(member.actorId()).body());
        return Map.copyOf(positions);
    }
    /** Transfers already-loaded exact ambient bodies without despawning, cloning or teleporting them. */
    /** Resumes explicitly unstarted admission, then reclaims only a complete observed body set. */
    static void reclaim(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        if (!demandExists(level, lease.handoffPosition())) {
            forgetRestartRecoveryObservation(runtime, lease.id());
            // With no audience, first reclaim a complete loaded set or prove the entire
            // saved set from disk before release. Elapsed reconnect grace alone never
            // means that a missing UUID is a lost body; cargo includes the exact cart.
            if (FrontierV3RestartDemandGrace.expired(runtime, lease.id(), level.getGameTime())) {
                // A persistently loaded exact set is physical evidence too. Reclaim its
                // original UUIDs first; the ordinary HOT turn then drains without demand.
                // A partial set cannot take this path or be mistaken for stored absence.
                if (completeLoadedSceneSet(level, state, lease)
                        && reclaimObservedBodies(level, runtime, state, lease)) return;
                FrontierV3SceneStoredRecovery.progress(level, runtime, state, lease);
            }
            return;
        }
        FrontierV3SceneStoredRecovery.forget(runtime, lease.id());
        if (!allRestartRecoveryColumnsLoaded(level, state, lease)
                || FrontierV3SceneReleaseReadiness.awaitingEntityStorage(level, state, lease)) {
            forgetRestartRecoveryObservation(runtime, lease.id());
            return;
        }
        long completeLoadSince = restartRecoveryObservationSince(runtime, lease.id(), level.getGameTime());
        if (!restartRecoveryObservationReady(completeLoadSince, level.getGameTime())) return;
        // A restart may precede the very first body insertion. UNKNOWN is not evidence
        // that a body once existed: the retained, unconsumed first-admission permit is
        // authoritative here. Reuse the ordinary durable-before-insertion boundary;
        // never reset a pending attempt or infer creation permission from absence.
        if (FrontierSceneBehaviors.recoveredStatus(state, lease) != SceneLeaseStatus.DRAINING
                && canResumeUnstartedBodyAdmissions(level, state, lease)) {
            FrontierV3SceneExecutor.materializeBodies(
                    level, runtime, state, lease, FrontierV3ActorCarrierComposition.InventoryEntry.SCENE_BODY);
        }
        java.util.Set<SubjectId> missing = lease.members().stream()
                .filter(member -> state.actorLocations().get(member.actorId()).condition().status() == io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.ALIVE)
                .filter(member -> !owned(level.getEntity(member.entityId()), state, lease, member))
                .map(SceneMember::actorId).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!missing.isEmpty()) {
            forgetRestartRecoveryObservation(runtime, lease.id());
            if (lease.recoveryEvidence().isEmpty()) recoveryUnresolved(runtime, lease, missing);
            return;
        }
        if (reclaimObservedBodies(level, runtime, state, lease)) forgetRestartRecoveryObservation(runtime, lease.id());
    }
    static boolean completeLoadedSceneSet(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        boolean observed = false;
        for (SceneMember member : lease.members()) {
            var actor = state.actorLocations().get(member.actorId());
            if (actor == null) return false;
            if (actor.condition().status() == io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.DEAD) continue;
            if (level.getEntity(member.entityId()) == null) return false;
            observed = true;
        }
        return observed;
    }
    /** Whole-set preflight: no partial admission when another member has unknown physical history. */
    static boolean canResumeUnstartedBodyAdmissions(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        if (lease.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        boolean unstarted = false;
        for (SceneMember member : lease.members()) {
            var actor = state.actorLocations().get(member.actorId());
            if (actor == null || actor.condition().status() != io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.ALIVE)
                return false;
            Entity body = level.getEntity(member.entityId());
            if (body != null) {
                if (!owned(body, state, lease, member) || !(body instanceof Mob mob) || mob.getHealth() <= 0.0F) return false;
                continue;
            }
            if (lease.ambientHandoffActorIds().contains(member.actorId())) return false;
            var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state, member.actorId(), member.entityId(),
                    FrontierV3ActorCarrierComposition.Representation.LIVE_BODY,
                    io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, member.actorId()).physicalEpoch());
            if (!FrontierV3AmbientActorExecutor.hasUnusedFirstAdmission(ledger, declaration)
                    || ledger.pendingAdoption(member.actorId()).isPresent()

                    || ledger.reconciliation(declaration) != FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER)
                return false;
            unstarted = true;
        }
        // This permission concerns actor bodies only. Cargo still requires its own
        // exact existing carrier in reclaim; this path never materializes cargo.
        return unstarted;
    }
    /** Package-visible recovery policy hook. The interval is inclusive at its final tick. */
    static boolean restartRecoveryObservationReady(long completeLoadSince, long gameTime) {
        return gameTime - completeLoadSince >= RESTART_RECOVERY_OBSERVATION_TICKS;
    }
    private static boolean allRestartRecoveryColumnsLoaded(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        for (SceneMember member : lease.members()) {
            BodyPosition position = lease.memberBody(state.actorLocations(), member.actorId());
            if (!level.hasChunkAt(new BlockPos(position.x(), position.y() - 1, position.z()))) return false;
        }
        return true;
    }
    private static long restartRecoveryObservationSince(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId, long gameTime) {
        Map<SceneLeaseId, Long> observations = RESTART_RECOVERY_SINCE.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        if (observations.size() >= MAX_PENDING_DRAINS && !observations.containsKey(leaseId)) return gameTime;
        return observations.computeIfAbsent(leaseId, ignored -> gameTime);
    }
    /** Accepts only the already observed exact body set; player demand remains the caller's responsibility. */
    static boolean reclaimObservedBodies(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        for (SceneMember member : lease.members()) {
            if (state.actorLocations().get(member.actorId()).condition().status() == io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.DEAD) continue;
            Entity entity = level.getEntity(member.entityId());
            if (!owned(entity, state, lease, member) || !(entity instanceof Mob body) || body.getHealth() <= 0.0F) return false;
        }
        SceneLeaseStatus recoveredStatus = FrontierSceneBehaviors.recoveredStatus(state, lease);
        return submit(runtime, recoveredStatus == SceneLeaseStatus.DRAINING ? "scene-recovery-draining" : "scene-reclaimed",
                lease.id().value(), new SceneLeaseTransition(lease.id(), recoveredStatus))
                instanceof CommandResult.Accepted;
    }
    static BodyMaterialization materializeBodies(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                 FrontierWorldState state, SceneLease lease,
                                                 FrontierV3ActorCarrierComposition.InventoryEntry adopter) {
        FrontierV3ActorCarrierComposition.requireRole(adopter, FrontierV3ActorCarrierComposition.Role.ADOPTER);
        // Blocks become available before Minecraft has necessarily restored the saved entity
        // columns. A deterministic body UUID must never be admitted into that interval: a saved
        // scene member could still join with the same identity. This only reads readiness; it
        // neither force-loads nor treats absence as death.
        if (lease.members().stream().map(member -> lease.memberBody(state.actorLocations(), member.actorId()))
                .anyMatch(body -> !entityStorageReady(level, new BlockPos(body.x(), body.y() - 1, body.z())))) {
            return BodyMaterialization.DEFERRED;
        }
        var expected = lease.members().stream().map(member ->
                io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, member.actorId())).toList();
        var result = materializeBodiesInReadyColumns(level, state, lease,
                FrontierV3SceneBehaviorRegistry.standingPositionProvider(lease), lease.memberBodies(state.actorLocations()));
        if (result != BodyMaterialization.COMPLETE) return result;
        // Join observation can commit during insertion; inspect the current canonical state,
        // not the older placement snapshot. Missing confirmation is still PREPARED, not HOT.
        return runtime.decodedState().filter(current ->
                FrontierV3ActorBodyController.readyForExecution(level, current, expected)).isPresent()
                ? BodyMaterialization.COMPLETE : BodyMaterialization.DEFERRED;
    }
    /** Isolated GameTest fixture entry point; production code must use the inventory-bound overload. */
    static BodyMaterialization materializeBodiesForFixture(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        return materializeBodiesForFixture(level, state, lease, lease.memberBodies(state.actorLocations()));
    }
    /** Ephemeral template translation; no production caller may supply a second position store. */
    static BodyMaterialization materializeBodiesForFixture(ServerLevel level, FrontierWorldState state, SceneLease lease,
                                                           Map<SubjectId, BodyPosition> fixtureBodies) {
        if (!fixtureBodies.keySet().equals(lease.members().stream().map(SceneMember::actorId)
                .collect(java.util.stream.Collectors.toSet()))) throw new IllegalArgumentException("fixture must project exactly its participants");
        return materializeBodiesInReadyColumns(level, state, lease, FrontierV3StandingPosition::aboveExactFloor, fixtureBodies);
    }
    static boolean entityStorageReady(ServerLevel level, BlockPos position) {
        return entityStorageReady(level.hasChunkAt(position), level.areEntitiesLoaded(ChunkPos.asLong(position)));
    }
    /** Pure admission gate retained for the storage-restoration negative regression. */
    static boolean entityStorageReady(boolean chunkLoaded, boolean entitiesLoaded) {
        return chunkLoaded && entitiesLoaded;
    }
    private static BodyMaterialization materializeBodiesInReadyColumns(ServerLevel level, FrontierWorldState state, SceneLease lease,
                                                                        FrontierV3SceneBehaviorRegistry.StandingPositionProvider standingPosition,
                                                                        Map<SubjectId, BodyPosition> presentedBodies) {
        for (int index = 0; index < lease.members().size(); index++) {
            SceneMember member = lease.members().get(index);
            Entity existing = level.getEntity(member.entityId());
            if (existing != null) {
                if (!(existing instanceof Mob body) || body.getHealth() <= 0.0F) return BodyMaterialization.CONFLICT;
                // A fenced inactive carrier and any indexed body would be concurrent custody.
                // In particular, a crash after fencing a closed resource body must not let a
                // successor scene silently reclaim that old Java object.
                if (FrontierV3AmbientActorExecutor.hasInactiveCarrier(level, state, member.actorId())) return BodyMaterialization.CONFLICT;
                if (owned(existing, state, lease, member)) {
                    if (body instanceof Zombie zombie) FrontierV3AmbientActorExecutor.configureBioform(zombie,
                            FrontierV3AmbientActorExecutor.bioformProfile(state, member.actorId()));
                    continue;
                }
                return BodyMaterialization.CONFLICT;
            }
            if (lease.ambientHandoffActorIds().contains(member.actorId())) return BodyMaterialization.DEFERRED;
            // The scene owns its participant list and placement constraints, not
            // the decision to create/reconstruct or physically admit an actor.
            BodyPosition canonical = presentedBodies.get(member.actorId());
            boolean bioform = bioform(state, member.actorId());
            long custodyEpoch = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, member.actorId()).physicalEpoch();
            var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state, member.actorId(), member.entityId(), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, custodyEpoch);
            var surfaces = List.of(canonical.supportingSurface());
            var result = FrontierV3ActorBodyController.materialize(level, state,
                    new FrontierV3ActorBodyController.BirthRequest(FrontierV3ActorCarrierComposition.InventoryEntry.SCENE_BODY,
                            FrontierV3ActorOwnerBinding.body(declaration),
                            surfaces, new FrontierV3NavigationScope.Restricted(LocalNavigationEnvelope.along(surfaces, surfaces)),
                            standingPosition::resolve, body -> {
                                if (body instanceof Zombie zombie) FrontierV3AmbientActorExecutor.configureBioform(zombie,
                                        FrontierV3AmbientActorExecutor.bioformProfile(state, member.actorId()));
                                FrontierV3ScenePresentation.applyAmbientActorPresentation(body, state, member.actorId(), bioform);
                                return FrontierV3BakeryHandProjection.prepareNew(state, lease, member, body);
                            }));
            if (result == FrontierV3ActorBodyController.Result.DEFERRED) return BodyMaterialization.DEFERRED;
            if (result == FrontierV3ActorBodyController.Result.CONFLICT) return BodyMaterialization.CONFLICT;
        }
        return BodyMaterialization.COMPLETE;
    }
    /**
     * Bounded local motion for one loaded HOT lease. This intentionally owns no strategic
     * decision and cannot inflict damage: a later durable SCENE_STRIKE executor is the sole
     * effect boundary. Native AI stays disabled so neither a Zombie nor a Villager can invent
     * an unaccounted target, attack, breeding decision, or path outside the canonical scene.
     */
    /** Commits one reached physical grid step with the operation and its current HOT lease together. */
    /** Executes one durable effect phase; HOT scheduling supplies the twenty-tick cadence. */
    static void executeStrike(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                              FrontierWorldState state, SceneLease lease, PhysicalIntentLifecycleOwner lifecycleOwner) {
        FrontierV3SceneStrikeExecutor.executeStrike(level, runtime, state, lease, lifecycleOwner);
    }
    static FixedPosition position(Entity entity) {
        return new FixedPosition(new FixedScalar(Math.round(entity.getX() * FixedScalar.SCALE)),
                new FixedScalar(Math.round(entity.getY() * FixedScalar.SCALE)), new FixedScalar(Math.round(entity.getZ() * FixedScalar.SCALE)));
    }
    private static BlockPosition supportPosition(BlockPos position) { return new BlockPosition(position.getX(), position.getY(), position.getZ()); }
    private static FixedPosition position(BlockPos position) {
        return new FixedPosition(FixedScalar.whole(position.getX()), FixedScalar.whole(position.getY()), FixedScalar.whole(position.getZ()));
    }
    static FixedScalar fixed(float health) { return new FixedScalar(Math.max(0L, Math.round(health * FixedScalar.SCALE))); }
    static Optional<Body> body(ServerLevel level, FrontierWorldState state, SceneLease lease, SceneMember member) {
        Entity entity = level.getEntity(member.entityId());
        return owned(entity, state, lease, member) && entity instanceof Mob mob && mob.isAlive() ? Optional.of(new Body(member, mob, bioform(state, member.actorId()))) : Optional.empty();
    }
    static long confirmedStrikeCount(FrontierWorldState state, SubjectId sceneCause) {
        return state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.SCENE_STRIKE
                && intent.causeSubjectId().equals(sceneCause) && intent.status() == PhysicalIntentStatus.CONFIRMED).count();
    }
    /** Exact feet target for the next retained edge; package-visible for the terrain regression. */
    static boolean residentGuard(FrontierWorldState state, SubjectId actorId) {
        return state.bootstrap().settlements().stream().flatMap(settlement -> settlement.residents().stream())
                .anyMatch(resident -> resident.id().equals(actorId) && resident.role() == ResidentRole.GUARD);
    }
    /**
     * Releases a draining scene from the latest durable state, not the tick's earlier projection.
     * A real death listener may have committed one or more member deaths between the initial scene
     * selection and this release pass; those dead bodies are no longer release candidates.
     */
    static void release(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease selectedLease) {
        FrontierV3SceneReleaseExecutor.release(level, runtime, selectedLease);
    }
    static void release(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                        SceneLease selectedLease,
                        java.util.Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction> binding) {
        FrontierV3SceneReleaseExecutor.release(level, runtime, selectedLease, binding);
    }
    static void releaseCustodyConflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                       FrontierWorldState state, SceneLease lease, String reason) {
        FrontierV3SceneReleaseExecutor.releaseCustodyConflict(level, runtime, state, lease, reason);
    }
    /**
     * Removes only stale closed-scene projections before any behavior receives its turn.
     *
     * <p>This is deliberately shared registry lifecycle work, rather than logistics work:
     * every typed scene can close, and a higher-priority active behavior must not leave a
     * former medical/engineering/assault body permanently tagged by its old lease.</p>
     */
    static FrontierV3SceneDemand.Snapshot demandSnapshot(ServerLevel level, BlockPosition anchor) {
        return FrontierV3SceneDemand.observe(level, anchor);
    }
    static boolean demandExists(ServerLevel level, BlockPosition anchor) {
        return FrontierV3ServerLifecycle.sceneEligible(level, anchor) && demandSnapshot(level, anchor).active();
    }
    /** Package-visible deterministic policy hook for the negative hand-off GameTest. */
    static boolean drainAfterDemandHysteresis(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId, long gameTime,
                                              FrontierV3SceneDemand.Snapshot demand, boolean playerWithinSafeRadius) {
        Objects.requireNonNull(demand, "scene aggregate demand");
        if (demand.active()) {
            forgetColdDemand(runtime, leaseId);
            return false;
        }
        Map<SceneLeaseId, Long> absentSince = COLD_DEMAND_SINCE.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        if (absentSince.size() >= MAX_PENDING_DRAINS && !absentSince.containsKey(leaseId)) return false;
        long started = absentSince.computeIfAbsent(leaseId, ignored -> gameTime);
        return gameTime - started >= DRAIN_HYSTERESIS_TICKS && !playerWithinSafeRadius;
    }
    static boolean playerWithinSafeRadius(ServerLevel level, SceneLease lease) {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(new BlockPos(lease.handoffPosition().x(), lease.handoffPosition().y(), lease.handoffPosition().z()));
        for (SceneMember member : lease.members()) {
            Entity entity = level.getEntity(member.entityId());
            if (entity != null) positions.add(entity.blockPosition());
        }
        return FrontierV3SceneDemand.observerWithin(level, positions, DRAIN_SAFE_RADIUS_BLOCKS);
    }
    private static void forgetInactiveDemand(FrontierV3ServerRuntime<?, ?> runtime, FrontierWorldState state) {
        Map<SceneLeaseId, Long> absentSince = COLD_DEMAND_SINCE.get(runtime);
        if (absentSince != null) absentSince.keySet().removeIf(id -> {
            SceneLease lease = state.sceneLeases().get(id);
            return lease == null || lease.status() != SceneLeaseStatus.HOT;
        });
        if (absentSince != null && absentSince.isEmpty()) COLD_DEMAND_SINCE.remove(runtime);
        Map<SceneLeaseId, Long> recovery = RESTART_RECOVERY_SINCE.get(runtime);
        if (recovery != null) recovery.keySet().removeIf(id -> {
            SceneLease lease = state.sceneLeases().get(id);
            return lease == null || lease.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART;
        });
        if (recovery != null && recovery.isEmpty()) RESTART_RECOVERY_SINCE.remove(runtime);
        FrontierV3RestartDemandGrace.reap(runtime, state);
    }
    private static void forgetColdDemand(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId) {
        Map<SceneLeaseId, Long> absentSince = COLD_DEMAND_SINCE.get(runtime);
        if (absentSince == null) return;
        absentSince.remove(leaseId);
        if (absentSince.isEmpty()) COLD_DEMAND_SINCE.remove(runtime);
    }
    static void forgetLeaseTransient(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId) {
        forgetColdDemand(runtime, leaseId);

        forgetRestartRecoveryObservation(runtime, leaseId);
        FrontierV3SceneStoredRecovery.forget(runtime, leaseId);
    }
    private static void forgetRestartRecoveryObservation(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId) {
        Map<SceneLeaseId, Long> observations = RESTART_RECOVERY_SINCE.get(runtime);
        if (observations == null) return;
        observations.remove(leaseId);
        if (observations.isEmpty()) RESTART_RECOVERY_SINCE.remove(runtime);
    }
    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        COLD_DEMAND_SINCE.remove(runtime);
        RESTART_RECOVERY_SINCE.remove(runtime);
        FrontierV3RestartDemandGrace.forget(runtime);
        FrontierV3SceneStoredRecovery.forget(runtime);
        FrontierV3SceneTurnScheduler.forget(runtime);
        FrontierV3ProductionTransformationExecutor.forget(runtime);
        FrontierV3ExactItemConsumptionExecutor.forget(runtime);
    }
    private static BlockPos spawnCandidate(BlockPosition anchor, int ordinal) {
        return new BlockPos(anchor.x() + (ordinal % 2) * 2, anchor.y(), anchor.z() + (ordinal / 2) * 2);
    }
    static Optional<Entity> explosionCause(ServerLevel level, FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.EXPLOSION || !bomber(state, intent.causeSubjectId())) return Optional.empty();
        return state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.HOT)
                .filter(FrontierSceneBehaviors::isSettlementAssault)
                .filter(lease -> FrontierSceneBehaviors.settlementAssault(lease).assaultId().equals(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ENGAGEMENT)))
                .flatMap(lease -> lease.members().stream().filter(member -> member.actorId().equals(intent.causeSubjectId()))
                        .map(member -> new LeaseMember(lease, member))).filter(value -> owned(level.getEntity(value.member().entityId()), state, value.lease(), value.member()))
                .map(value -> level.getEntity(value.member().entityId())).filter(entity -> entity instanceof Mob).filter(Entity::isAlive).findFirst();
    }
    /**
     * Read-only proof for the shared Graybox admission boundary. A scene body is
     * allowed only when every persisted identity component still agrees with an
     * active (including conflict-preserved) canonical scene lease.
     */
    static boolean recognizes(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        FrontierWorldState state = state(runtime);
        if (state == null || entity.isRemoved()) return false;
        return state.sceneLeases().values().stream().filter(lease -> lease.status() != SceneLeaseStatus.CLOSED)
                .flatMap(lease -> lease.members().stream().map(member -> new LeaseMember(lease, member)))
                .anyMatch(value -> owned(entity, state, value.lease(), value.member()));
    }

    /** Source ingress/death keeps provenance even when unresolved evidence forbids work. */
    static boolean recognizesDeclaration(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        FrontierWorldState state = state(runtime);
        if (state == null || entity.isRemoved()) return false;
        return state.sceneLeases().values().stream().filter(lease -> lease.status() != SceneLeaseStatus.CLOSED)
                .flatMap(lease -> lease.members().stream().map(member -> new LeaseMember(lease, member)))
                .anyMatch(value -> ownsDeclaration(entity, state, value.lease(), value.member()));
    }
    private static boolean bomber(FrontierWorldState state, SubjectId actorId) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(bioform -> bioform.id().equals(actorId)).anyMatch(Bioform::isExplosiveAssaulter);
    }
    /** Exact scene-body provenance check shared by typed physical effects attached to a HOT scene. */
    static boolean owned(Entity entity, FrontierWorldState state, SceneLease lease, SceneMember member) {
        if (!ownsDeclaration(entity, state, lease, member) || !(entity.level() instanceof ServerLevel level)) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        return FrontierV3ActorOwnerBinding.from(entity).filter(ledger::permitsRecordedOwner).isPresent()
                && FrontierV3SceneDepartureObserver.permitsLiveWork(ledger, member);
    }

    /** Recognition retains a conflicting managed body; it is not permission to execute work. */
    static boolean ownsDeclaration(Entity entity, FrontierWorldState state, SceneLease lease, SceneMember member) {
        return entity != null && member.entityId().equals(entity.getUUID())
                && lease.members().contains(member)
                && FrontierV3ActorCarrierComposition.declaredBy(entity)
                    .filter(declaration -> declaration.actorId().equals(member.actorId())).isPresent()
                && FrontierV3ActorBodyController.recognizes(state, entity);
    }
    static boolean bioform(FrontierWorldState state, SubjectId actorId) {
        var actor = state.actorLocations().get(actorId);
        if (actor == null) throw new IllegalArgumentException("actor kind is absent from canonical identity");
        return actor.kind() == ActorKind.BIOFORM;
    }
    /** A loaded-world obstruction or altered owned body is a physical conflict, not restart evidence. */
    static void conflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                 SceneLease lease, String cause) {
        // Readiness comes from the registered scene family; transport custody is not a scene property.
        FrontierV3SceneReadiness.Value readiness = FrontierV3SceneReadiness.forLease(level, state, lease);
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "scene_conflict:" + cause + ":" + readiness.bodies(), lease,
                submit(runtime, "scene-conflict", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.CONFLICT)));
    }
    /** A loaded demand point has disproved exact reclaimability; record conflict rather than loop forever or replace a body. */
    private static void recoveryUnresolved(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, java.util.Set<SubjectId> missingActors) {
        submit(runtime, "scene-recovery-unresolved", lease.id().value(), new SceneLeaseRecoveryUnresolved(lease.id(), missingActors));
    }
    static CommandResult submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, String phase, String id, FrontierPayload payload) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = checkpoint(runtime);
        // A scene can perform a sequence of distinct durable transitions while retaining the
        // same lease identity.  Bind the command identity to the expected canonical revision,
        // as every other physical executor does, so a later grid checkpoint is not mistaken
        // for a duplicate of the earlier checkpoint.
        CommandId commandId = FrontierV3CommandIds.scene(phase, checkpoint.revision().value());
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), payload))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("scene executor transition was rejected: " + result);
        return result;
    }
    private static io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
    }
    static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.decodedState().orElse(null);
    }
    record Body(SceneMember member, Mob entity, boolean bioform) { }
    private record LeaseMember(SceneLease lease, SceneMember member) { }
}
