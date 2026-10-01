package io.farfrontier.palemirror.internal.frontier.v3;
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
import io.farfrontier.palemirror.frontier.v3.model.ActorDied;
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
import io.farfrontier.palemirror.frontier.v3.model.OperationTravel;
import io.farfrontier.palemirror.frontier.v3.model.OperationFront;
import io.farfrontier.palemirror.frontier.v3.model.ActorDirective;
import io.farfrontier.palemirror.frontier.v3.model.OperationTravelAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.LogisticsSceneCause;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;
import io.farfrontier.palemirror.frontier.v3.model.ResidentRole;
import io.farfrontier.palemirror.frontier.v3.model.SceneEngagementCandidate;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseRecoveryUnresolved;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseHandoff;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeasePrepared;
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
    static final String ACTOR_KEY = "pale_mirror_frontier_v3_scene_actor";
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
    static void requireRegisteredSceneTurn(SceneLease lease) {
        FrontierDurationProcessDriverRegistry.requireSceneTick(lease, 1);
    }
    /** Logistics behavior entry point; the closed registry owns inter-family ordering. */
    static boolean tickLogistics(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = state(runtime);
        if (state == null) return false;
        forgetInactiveDemand(runtime, state);
        return FrontierV3SceneTurnScheduler.run(runtime, state, io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind.LOGISTICS,
                lease -> execute(level, runtime, state, lease, PhysicalIntentLifecycleOwner.ROUTE_ENGAGEMENT,
                        PhysicalIntentLifecycleOwner.HIVE_MOBILIZATION), () -> admitLogistics(level, runtime, state));
    }

    private static boolean admitLogistics(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                           FrontierWorldState state) {
        // Local admission backpressure only: active leases still get their execution/release turn.
        if (!state.fencedRecovery().canAdmitCargoProjection()) return false;
        List<LogisticsAdmission> candidates = new ArrayList<>();
        var engagements = state.coldEngagementSceneCandidates().stream()
                .filter(candidate -> state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isLogistics).noneMatch(lease -> FrontierSceneBehaviors.logistics(lease).engagementId().filter(candidate.engagementId()::equals).isPresent()
                        && lease.status() != SceneLeaseStatus.CLOSED))
                .filter(candidate -> demandExists(level, candidate.handoffPosition())).toList();
        for (SceneEngagementCandidate candidate : engagements) {
            candidates.add(new LogisticsAdmission(candidate.engagementId(), candidate.actorIds(), () -> lease(runtime, state, candidate)));
        }
        var routes = state.operations().values().stream().sorted(Comparator.comparing(RouteOperation::id))
                .filter(operation -> operation.stage() == io.farfrontier.palemirror.frontier.v3.model.OperationStage.EN_ROUTE)
                .filter(operation -> state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isLogistics).noneMatch(lease -> FrontierSceneBehaviors.logistics(lease).operationId().equals(operation.id())
                        && lease.status() != SceneLeaseStatus.CLOSED))
                .filter(operation -> !FrontierSceneAdmission.hasUnresolvedRouteEngagement(state, operation.id()))
                // A completed strategic segment is a canonical transition point, not a
                // materializable convoy state.  COLD must first atomically open the next
                // segment with the same formation and cargo anchor; otherwise a new HOT
                // lease would suspend that COLD action and own a stale, already-arrived
                // corridor.  The bounded wait is one ordinary operation-progress interval.
                .filter(RouteOperation::hasInProgressTravel)
                .filter(operation -> demandExists(level, operation.currentPosition())).toList();
        for (RouteOperation operation : routes) {
            candidates.add(new LogisticsAdmission(operation.id(), operation.participantIds(), () -> lease(runtime, state, operation)));
        }
        var selected = FrontierV3SceneTurnScheduler.candidate(runtime,
                io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind.LOGISTICS, candidates, LogisticsAdmission::id);
        if (selected.isEmpty()) return false;
        LogisticsAdmission candidate = selected.orElseThrow();
        SceneLease lease = candidate.lease().get();
        if (FrontierSceneAdmission.available(state, candidate.actorIds())) prepare(level, runtime, lease);
        else handoff(level, runtime, state, lease);
        return true;
    }

    private record LogisticsAdmission(SubjectId id, java.util.Collection<SubjectId> actorIds,
                                      java.util.function.Supplier<SceneLease> lease) { }
    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, RouteOperation operation) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = checkpoint(runtime);
        SceneLeaseId id = new SceneLeaseId("lease:" + operation.id().value().substring("operation:".length()) + "-r" + checkpoint.revision().value());
        List<SceneMember> members = operation.participantIds().stream().sorted().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(checkpoint.worldId(), id, actor))).toList();
        OperationTravel travel = operation.activeTravel().orElseThrow(() -> new IllegalArgumentException("materialized route operation has no current travel"));
        BlockPosition cargoPosition = travel.cargoAnchor().surface().support();
        return SceneLease.atExactPositions(id, checkpoint.worldId(), operation.id(), operation.cargoId(), operation.currentPosition(), cargoPosition, checkpoint.instant(), checkpoint.revision().value(),
                SceneLeaseStatus.PREPARED, Optional.empty(), members, travel.formation());
    }
    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneEngagementCandidate candidate) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = checkpoint(runtime);
        SceneLeaseId id = new SceneLeaseId("lease:" + candidate.engagementId().value().substring("engagement:".length()) + "-r" + checkpoint.revision().value());
        List<SceneMember> members = candidate.actorIds().stream().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(checkpoint.worldId(), id, actor))).toList();
        return SceneLease.atExactPositions(id, checkpoint.worldId(), candidate.operationId(), candidate.cargoId(), candidate.handoffPosition(), candidate.cargoPosition(), checkpoint.instant(), checkpoint.revision().value(),
                SceneLeaseStatus.PREPARED, Optional.of(candidate.engagementId()), members, positions(state, members));
    }
    private static Map<SubjectId, BodyPosition> positions(FrontierWorldState state, List<SceneMember> members) {
        Map<SubjectId, BodyPosition> positions = new LinkedHashMap<>();
        for (SceneMember member : members) positions.put(member.actorId(), state.actorLocations().get(member.actorId()).body());
        return Map.copyOf(positions);
    }
    private static void prepare(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "scene_prepared", lease,
                submit(runtime, "scene-prepare", lease.id().value(), new SceneLeasePrepared(lease)));
    }
    /** Transfers already-loaded exact ambient bodies without despawning, cloning or teleporting them. */
    private static void handoff(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        List<SceneMemberPosition> captures = new ArrayList<>();
        for (SceneMember member : lease.members()) {
            var ambient = state.ambientLeases().get(member.actorId());
            if (ambient == null || ambient.status() == AmbientLeaseStatus.CLOSED) continue;
            if (ambient.status() != AmbientLeaseStatus.HOT) return;
            Entity body = level.getEntity(member.entityId());
            if (!(body instanceof Mob mob) || !mob.isAlive() || !FrontierV3AmbientActorExecutor.owned(body, member.actorId(), bioform(state, member.actorId()))) return;
            captures.add(new SceneMemberPosition(member.actorId(), FrontierV3BodyObservation.position(body), fixed(mob.getHealth())));
        }
        if (!captures.isEmpty()) {
            Map<SubjectId, BodyPosition> positions = new LinkedHashMap<>(lease.memberPositions());
            captures.forEach(capture -> positions.put(capture.actorId(), capture.body()));
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "scene_handoff", lease,
                    submit(runtime, "scene-handoff", lease.id().value(), new SceneLeaseHandoff(lease.withMemberPositions(positions).withAmbientHandoff(
                            captures.stream().map(SceneMemberPosition::actorId).collect(java.util.stream.Collectors.toSet())), captures)));
        }
    }
    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease,
                                PhysicalIntentLifecycleOwner strikeOwner, PhysicalIntentLifecycleOwner explosionOwner) {
        requireRegisteredSceneTurn(lease);
        switch (lease.status()) {
            case PREPARED -> materializePrepared(level, runtime, state, lease);
            case HOT -> {
                FrontierV3SceneDemand.Snapshot demand = demandSnapshot(level, lease.handoffPosition());
                if (drainAfterDemandHysteresis(runtime, lease.id(), level.getGameTime(), demand, playerWithinSafeRadius(level, lease))) {
                    FrontierV3DiagnosticTrace.recordScene(level.getServer(), "scene_draining", lease,
                            submit(runtime, "scene-draining", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING)));
                    return;
                }
                // An absent-demand scene owns no naturally loaded execution surface.  Do not
                // turn its expected unloaded cart/body into a conflict while the durable
                // hand-off window is still running.
                if (!demand.active()) return;
                executeLocalGoals(level, state, lease);
                if (!FrontierV3CargoCarrierExecutor.move(level, state, lease, cargoDestination(state, lease))) {
                    conflict(level, runtime, state, lease, "carrier-unavailable"); return;
                }
                if (observeHotTravelAdvance(level, runtime, state, lease)) return;
                if (combatEnabled(lease) && level.getGameTime() % 20L == 0L
                        && !executeExplosion(level, runtime, state, lease, explosionOwner)) executeStrike(level, runtime, state, lease, strikeOwner);

            }
            case DRAINING -> {
                forgetColdDemand(runtime, lease.id());
                release(level, runtime, lease);
            }
            case UNKNOWN_AFTER_RESTART -> reclaim(level, runtime, state, lease);
            case CONFLICT -> { }
            case CLOSED -> { }
        }
    }
    private static void materializePrepared(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        BodyMaterialization result = FrontierV3ActorCarrierFactory.materializeSceneBodies(FrontierV3ActorCarrierComposition.InventoryEntry.SCENE_BODY, level, state, lease);
        BodyMaterialization carrier = result == BodyMaterialization.COMPLETE
                ? FrontierV3CargoCarrierExecutor.materialize(level, state, lease) : BodyMaterialization.DEFERRED;
        if (result == BodyMaterialization.COMPLETE && carrier == BodyMaterialization.COMPLETE) {

            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "scene_hot", lease,
                    submit(runtime, "scene-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT)));
        } else if (result == BodyMaterialization.CONFLICT || carrier == BodyMaterialization.CONFLICT) {
            conflict(level, runtime, state, lease, "prepared-" + result.name().toLowerCase(java.util.Locale.ROOT)
                    + "-" + carrier.name().toLowerCase(java.util.Locale.ROOT));
        }
    }
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
        if (!allRestartRecoveryColumnsLoaded(level, lease)
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
            FrontierV3ActorCarrierFactory.materializeSceneBodies(
                    FrontierV3ActorCarrierComposition.InventoryEntry.SCENE_BODY, level, state, lease);
        }
        java.util.Set<SubjectId> missing = lease.members().stream()
                .filter(member -> state.actorLocations().get(member.actorId()).condition().status() == io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.ALIVE)
                .filter(member -> !owned(level.getEntity(member.entityId()), state, lease, member))
                .map(SceneMember::actorId).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!missing.isEmpty()) {
            forgetRestartRecoveryObservation(runtime, lease.id());
            if (lease.recoveryEvidence().isEmpty()) recoveryUnresolved(runtime, lease, missing, false);
            return;
        }
        boolean hasCargoCarrier = FrontierV3SceneBehaviorRegistry.hasCargoCarrier(lease);
        RouteOperation operation = hasCargoCarrier ? state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId()) : null;
        boolean interrupted = hasCargoCarrier && operation != null && operation.stage() == io.farfrontier.palemirror.frontier.v3.model.OperationStage.INTERRUPTED;
        if (hasCargoCarrier && !interrupted && !FrontierV3CargoCarrierExecutor.intact(level, state, lease)) {
            forgetRestartRecoveryObservation(runtime, lease.id());
            if (lease.recoveryEvidence().isEmpty()) recoveryUnresolved(runtime, lease, java.util.Set.of(), true);
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
        if (FrontierV3SceneBehaviorRegistry.hasCargoCarrier(lease)) {
            if (level.getEntity(FrontierV3CargoCarrierExecutor.id(lease)) == null) return false;
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
            var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state, member.actorId(),
                    FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, member.entityId(),
                    FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, lease.revision(), 1L);
            if (!FrontierV3AmbientActorExecutor.hasUnusedFirstAdmission(ledger, declaration)
                    || ledger.pendingAdoption(member.actorId()).isPresent()
                    || ledger.pendingHandoff(member.actorId()).isPresent()
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
    private static boolean allRestartRecoveryColumnsLoaded(ServerLevel level, SceneLease lease) {
        for (SceneMember member : lease.members()) {
            BodyPosition position = lease.memberPosition(member.actorId());
            if (!level.hasChunkAt(new BlockPos(position.x(), position.y() - 1, position.z()))) return false;
        }
        if (!FrontierV3SceneBehaviorRegistry.hasCargoCarrier(lease)) return true;
        BlockPosition cargo = FrontierSceneBehaviors.logistics(lease).cargoPosition();
        return level.hasChunkAt(new BlockPos(cargo.x(), cargo.y(), cargo.z()));
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
        boolean hasCargoCarrier = FrontierV3SceneBehaviorRegistry.hasCargoCarrier(lease);
        RouteOperation operation = hasCargoCarrier ? state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId()) : null;
        boolean interrupted = hasCargoCarrier && operation != null && operation.stage() == io.farfrontier.palemirror.frontier.v3.model.OperationStage.INTERRUPTED;
        if (hasCargoCarrier && !interrupted && !FrontierV3CargoCarrierExecutor.intact(level, state, lease)) return false;
        SceneLeaseStatus recoveredStatus = FrontierSceneBehaviors.recoveredStatus(state, lease);
        return submit(runtime, recoveredStatus == SceneLeaseStatus.DRAINING ? "scene-recovery-draining" : "scene-reclaimed",
                lease.id().value(), new SceneLeaseTransition(lease.id(), recoveredStatus))
                instanceof CommandResult.Accepted;
    }
    static BodyMaterialization materializeBodies(ServerLevel level, FrontierWorldState state, SceneLease lease,
                                                 FrontierV3ActorCarrierComposition.InventoryEntry adopter) {
        FrontierV3ActorCarrierComposition.requireRegistered(adopter);
        // Blocks become available before Minecraft has necessarily restored the saved entity
        // columns. A deterministic body UUID must never be admitted into that interval: a saved
        // scene member could still join with the same identity. This only reads readiness; it
        // neither force-loads nor treats absence as death.
        if (lease.members().stream().map(member -> lease.memberPosition(member.actorId()))
                .anyMatch(body -> !entityStorageReady(level, new BlockPos(body.x(), body.y() - 1, body.z())))) {
            return BodyMaterialization.DEFERRED;
        }
        return materializeBodiesInReadyColumns(level, state, lease,
                FrontierV3SceneBehaviorRegistry.standingPositionProvider(lease));
    }
    /** Isolated GameTest fixture entry point; production code must use the inventory-bound overload. */
    static BodyMaterialization materializeBodiesForFixture(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        return materializeBodiesInReadyColumns(level, state, lease, FrontierV3StandingPosition::aboveExactFloor);
    }
    static boolean entityStorageReady(ServerLevel level, BlockPos position) {
        return entityStorageReady(level.hasChunkAt(position), level.areEntitiesLoaded(ChunkPos.asLong(position)));
    }
    /** Pure admission gate retained for the storage-restoration negative regression. */
    static boolean entityStorageReady(boolean chunkLoaded, boolean entitiesLoaded) {
        return chunkLoaded && entitiesLoaded;
    }
    private static BodyMaterialization materializeBodiesInReadyColumns(ServerLevel level, FrontierWorldState state, SceneLease lease,
                                                                        FrontierV3SceneBehaviorRegistry.StandingPositionProvider standingPosition) {
        for (int index = 0; index < lease.members().size(); index++) {
            SceneMember member = lease.members().get(index);
            Entity existing = level.getEntity(member.entityId());
            if (existing != null) {
                if (!(existing instanceof Mob body) || body.getHealth() <= 0.0F) return BodyMaterialization.CONFLICT;
                FrontierV3ActorHandoffRecovery.resume(level, state, body);
                // A fenced inactive carrier and any indexed body would be concurrent custody.
                // In particular, a crash after fencing a closed resource body must not let a
                // successor scene silently reclaim that old Java object.
                if (FrontierV3AmbientActorExecutor.hasInactiveCarrier(level, state, member.actorId())) return BodyMaterialization.CONFLICT;
                if (owned(existing, state, lease, member)) {
                    if (body instanceof Zombie zombie) FrontierV3AmbientActorExecutor.configureBioform(zombie,
                            FrontierV3AmbientActorExecutor.bioformProfile(state, member.actorId()));
                    continue;
                }
                if (FrontierV3ResourceSiteHarvestSceneExecutor.adoptableClosedBody(existing, state, lease, member)) {
                    // The terminal receipt intentionally has no physical depot journey. Keep
                    // its named farmer at the exact released station until a declared successor
                    // claims the same UUID; changing a scene lease must never recreate or move
                    // that player-visible body.
                    FrontierV3ControlledMobMotion.stop(body);
                    if (!mark(body, state, lease, member)) return BodyMaterialization.CONFLICT;
                    continue;
                }
                if (!FrontierV3AmbientActorExecutor.owned(existing, member.actorId(), bioform(state, member.actorId()))) return BodyMaterialization.CONFLICT;
                // An ambient body can have one already-due controlled-motion intent when the
                // durable hand-off closes its ambient lease.  Navigation.stop()/NoAI alone do
                // not clear that executor-local intent; leaving it would move the exact body
                // along its former ambient goal after the scene adopts it and violate the
                // retained scene cursor.  The scene owns the same body now, so cancel only
                // that uncommitted actuator intent, never rewrite its canonical position.
                FrontierV3ControlledMobMotion.stop(body);
                body.setCustomName(FrontierV3ScenePresentation.actorName(state, member.actorId(), bioform(state, member.actorId()))); body.setCustomNameVisible(true);
                if (body instanceof Zombie zombie) FrontierV3AmbientActorExecutor.configureBioform(zombie,
                        FrontierV3AmbientActorExecutor.bioformProfile(state, member.actorId()));
                if (!mark(body, state, lease, member)) return BodyMaterialization.CONFLICT;
                continue;
            }
            if (lease.ambientHandoffActorIds().contains(member.actorId())) return BodyMaterialization.DEFERRED;
            // A direct PREPARED scene may be the first natural return after its predecessor
            // fenced and released the exact body.  Reconstruct it here only from that same
            // inactive UUID carrier at a newer physical revision.  This shared scene boundary
            // consumes the carrier after admission, so it cannot leave concurrent custody or
            // fall back to a newly selected worker.
            FrontierV3AmbientCarrierLedger ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
            if (ledger.pendingAdoption(member.actorId()).isPresent() || ledger.pendingHandoff(member.actorId()).isPresent()) return BodyMaterialization.CONFLICT;
            FrontierV3AmbientCarrierLedger.Reconciliation carrier = ledger.reconciliation(FrontierV3AmbientActorExecutor.carrierDeclaration(state, member.actorId(),
                    FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, member.entityId(), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY,
                    lease.revision(), 1L));
            if (carrier != FrontierV3AmbientCarrierLedger.Reconciliation.NO_FENCED_CARRIER
                    && carrier != FrontierV3AmbientCarrierLedger.Reconciliation.READY) return BodyMaterialization.CONFLICT;
            BodyPosition canonical = lease.memberPosition(member.actorId());
            BlockPos candidate = new BlockPos(canonical.x(), canonical.y() - 1, canonical.z());
            if (!level.hasChunkAt(candidate)) return BodyMaterialization.DEFERRED;
            BlockPos position = standingPosition.resolve(level, candidate);
            // The semantic floor is projected independently and only into naturally loaded
            // chunks.  A fresh empty support column is therefore a normal materialization
            // dependency, not permission to substitute a higher terrain surface or to mark a
            // player/world conflict before the owned projection has had a chance to run.
            if (position == null) return BodyMaterialization.DEFERRED;
            boolean bioform = bioform(state, member.actorId());
            if (position.getY() != canonical.y()) return BodyMaterialization.CONFLICT;
            long custodyEpoch = carrier == FrontierV3AmbientCarrierLedger.Reconciliation.READY ? ledger.reconstructionEpoch(member.actorId()) : 1L;
            var firstDeclaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state, member.actorId(),
                    FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, member.entityId(),
                    FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, lease.revision(), custodyEpoch);
            if (carrier != FrontierV3AmbientCarrierLedger.Reconciliation.READY
                    && !FrontierV3AmbientActorExecutor.hasUnusedFirstAdmission(ledger, firstDeclaration)) return BodyMaterialization.CONFLICT;
            Mob body = FrontierV3ActorCarrierFactory.create(FrontierV3ActorCarrierComposition.InventoryEntry.SCENE_BODY, level, FrontierV3AmbientActorExecutor.carrierDeclaration(state, member.actorId(),
                    FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, member.entityId(), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY,
                    lease.revision(), custodyEpoch), state.actorLocations().get(member.actorId()).condition());
            body.setPos(canonical.x() + 0.5D, canonical.y(), canonical.z() + 0.5D);
            body.setPersistenceRequired();
            body.getPersistentData().putLong(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY, custodyEpoch);
            body.getPersistentData().putString(LEASE_KEY, lease.id().value());
            body.getPersistentData().putString(ACTOR_KEY, member.actorId().value());
            body.getPersistentData().putLong(REVISION_KEY, lease.revision());
            body.setNoAi(true);
            if (body instanceof Zombie zombie) FrontierV3AmbientActorExecutor.configureBioform(zombie,
                    FrontierV3AmbientActorExecutor.bioformProfile(state, member.actorId()));
            body.setCustomName(FrontierV3ScenePresentation.actorName(state, member.actorId(), bioform));
            body.setCustomNameVisible(true);
            if (!FrontierV3BakeryHandProjection.prepareNew(state, lease, member, body)) return BodyMaterialization.CONFLICT;
            if (!mark(body, state, lease, member)) return BodyMaterialization.CONFLICT;
            var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state, member.actorId(),
                    FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, member.entityId(),
                    FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, lease.revision(), custodyEpoch);
            boolean added = carrier == FrontierV3AmbientCarrierLedger.Reconciliation.READY
                    ? FrontierV3ActorAdoptionAdmission.admit(ledger, FrontierV3ActorOwnerBinding.scene(declaration, lease.id()),
                        () -> ledger.persist(level, state.bootstrap().worldId()), () -> level.addFreshEntity(body))
                    : FrontierV3ActorFirstAdmissionBoundary.admit(ledger, FrontierV3ActorOwnerBinding.scene(declaration, lease.id()),
                        () -> ledger.persist(level, state.bootstrap().worldId()), () -> level.addFreshEntity(body));
            if (!added) {
                PaleMirrorMod.LOGGER.warn("Frontier v3 scene body admission failed lease={} actor={} uuid={} body={}",
                        lease.id().value(), member.actorId().value(), member.entityId(), canonical);
                return BodyMaterialization.CONFLICT;
            }
        }
        return BodyMaterialization.COMPLETE;
    }
    /**
     * Bounded local motion for one loaded HOT lease. This intentionally owns no strategic
     * decision and cannot inflict damage: a later durable SCENE_STRIKE executor is the sole
     * effect boundary. Native AI stays disabled so neither a Zombie nor a Villager can invent
     * an unaccounted target, attack, breeding decision, or path outside the canonical scene.
     */
    private static void executeLocalGoals(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        List<Body> bodies = lease.members().stream().map(member -> body(level, state, lease, member)).flatMap(Optional::stream)
                .sorted(Comparator.comparing(value -> value.member().actorId())).toList();
        for (Body actor : bodies) {
            Optional<ActorDirective> directive = logisticsDirective(state, lease, actor.member().actorId());
            if (directive.isPresent() && !directive.orElseThrow().movement().permitsObservedSupport(supportPosition(actor.entity().blockPosition()))) continue;
            if (directive.isPresent()) {
                FrontierV3GoalNavigation.pursueLocalFeetTarget(level, actor.entity(), localTarget(state, actor, bodies, lease),
                        new FrontierV3NavigationScope.Restricted(directive.orElseThrow().movement().envelope()));
            } else FrontierV3GoalNavigation.pursueLocalFeetTarget(level, actor.entity(), localTarget(state, actor, bodies, lease),
                    new FrontierV3NavigationScope.ObservedWorld(state.bootstrap().bounds()));
        }
    }
    /** Commits one reached physical grid step with the operation and its current HOT lease together. */
    private static boolean observeHotTravelAdvance(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                   FrontierWorldState state, SceneLease lease) {
        if (FrontierSceneBehaviors.logistics(lease).engagementId().isPresent()) return false;
        RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
        if (operation == null || operation.activeTravel().isEmpty()) return false;
        OperationTravel current = operation.activeTravel().orElseThrow();
        if (current.arrived()) return false;
        OperationFront front = OperationFront.logistics(operation);
        OperationTravel next = translateTravel(current, current.nextHotCursor());
        for (SceneMember member : lease.members()) {
            Entity entity = level.getEntity(member.entityId());
            BodyPosition expected = next.formation().get(member.actorId());
            ActorDirective directive = front.directive(operation, lease.id(), member.actorId());
            if (!(entity instanceof Mob body) || !owned(entity, state, lease, member)
                    || !directive.movement().permitsObservedSupport(supportPosition(body.blockPosition()))
                    || body.getBlockX() != expected.x() || body.getBlockY() != expected.y() || body.getBlockZ() != expected.z()) return false;
        }
        if (!FrontierV3CargoCarrierExecutor.atDestination(level, state, lease, next.cargoAnchor().surface().support())) return false;
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "scene-operation-travel", lease.id().value(),
                new OperationTravelAdvanced(operation.id(), next));
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "operation_travel_advanced", lease, result);
        return result instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted;
    }
    /** Executes one durable effect phase; HOT scheduling supplies the twenty-tick cadence. */
    static boolean executeExplosion(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    FrontierWorldState state, SceneLease lease, PhysicalIntentLifecycleOwner lifecycleOwner) {
        if (FrontierSceneBehaviors.logistics(lease).engagementId().isEmpty()) return false;
        SubjectId engagement = FrontierSceneBehaviors.logistics(lease).engagementId().orElseThrow();
        Optional<PhysicalIntent> unresolved = state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.EXPLOSION)
                .filter(intent -> intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ENGAGEMENT).equals(engagement))
                .filter(intent -> intent.status() != PhysicalIntentStatus.CONFIRMED).min(Comparator.comparing(PhysicalIntent::id));
        if (unresolved.isPresent()) return true;
        List<Body> bodies = lease.members().stream().map(member -> body(level, state, lease, member)).flatMap(Optional::stream).toList();
        Body bomber = bodies.stream().filter(value -> bomber(state, value.member().actorId())).min(Comparator.comparing(value -> value.member().actorId())).orElse(null);
        Body target = bomber == null ? null : bodies.stream().filter(value -> !value.bioform()).min(Comparator.comparingDouble((Body value) -> bomber.entity().distanceToSqr(value.entity()))
                .thenComparing(value -> value.member().actorId())).orElse(null);
        if (bomber == null || target == null || bomber.entity().distanceToSqr(target.entity()) > 36.0D) return false;
        // A durable physical effect is about to take ownership of the scene's next truth. A
        // volatile pre-effect sample must never be used to release a later unloaded aftermath.

        BlockPos origin = target.entity().blockPosition();
        long effectEpoch = state.physicalIntents().values().stream().filter(value -> value.kind() == PhysicalIntentKind.EXPLOSION)
                .filter(value -> value.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ENGAGEMENT).equals(engagement)).count();
        // A PhysicalIntent drives a deterministic Minecraft entity UUID. Include the canonical
        // world identity so two independent v3 fixtures in one GameTest level cannot claim the
        // same TNT body; production still has one stable ID for the same world/scene/epoch.
        String world = state.bootstrap().worldId().value().replace(':', '-');
        String key = world + "-scene-r" + lease.revision() + "-e" + effectEpoch;
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:explosion-" + key), PhysicalIntentKind.EXPLOSION, PhysicalIntentStatus.PREPARED,
                bomber.member().actorId(), PhysicalIntentRoleBinding.explosion(bomber.member().actorId(), engagement), position(origin), 4, PhysicalPostcondition.EXPLOSION_OBSERVED, lifecycleOwner);
        submit(runtime, "explosion-prepare", key, new PhysicalIntentPrepared(intent));
        return true;
    }
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
    private static Vec3 localTarget(FrontierWorldState state, Body actor, List<Body> bodies, SceneLease lease) {
        if (FrontierSceneBehaviors.logistics(lease).engagementId().isEmpty()) {
            RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
            if (operation != null && operation.activeTravel().isPresent()) {
                OperationTravel travel = operation.activeTravel().orElseThrow();
                if (!travel.arrived()) {
                    ActorDirective directive = OperationFront.logistics(operation).directive(operation, lease.id(), actor.member().actorId());
                    BlockPosition target = directive.movement().nextCheckpoint();
                    return new Vec3(target.x() + 0.5D, target.y() + 1.0D, target.z() + 0.5D);
                }
            }
        }
        Optional<Body> opponent = bodies.stream().filter(other -> other.bioform() != actor.bioform()).min(Comparator.comparingDouble(other -> actor.entity().distanceToSqr(other.entity())));
        if (opponent.isPresent()) {
            Vec3 delta = opponent.orElseThrow().entity().position().subtract(actor.entity().position());
            if (actor.bioform() || residentGuard(state, actor.member().actorId())) return opponent.orElseThrow().entity().position();
            if (delta.horizontalDistanceSqr() > 0.0001D) return actor.entity().position().subtract(delta.normalize().scale(5.0D));
        }
        int phase = Math.floorMod(actor.member().actorId().value().hashCode(), 8);
        double angle = phase * Math.PI / 4.0D;
        return new Vec3(lease.handoffPosition().x() + 0.5D + Math.cos(angle) * 2.0D, actor.entity().getY(), lease.handoffPosition().z() + 0.5D + Math.sin(angle) * 2.0D);
    }
    private static Optional<ActorDirective> logisticsDirective(FrontierWorldState state, SceneLease lease, SubjectId actorId) {
        if (FrontierSceneBehaviors.logistics(lease).engagementId().isPresent()) return Optional.empty();
        RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
        if (operation == null || operation.activeTravel().isEmpty() || operation.activeTravel().orElseThrow().arrived()) return Optional.empty();
        return Optional.of(OperationFront.logistics(operation).directive(operation, lease.id(), actorId));
    }
    static long confirmedStrikeCount(FrontierWorldState state, SubjectId sceneCause) {
        return state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.SCENE_STRIKE
                && intent.causeSubjectId().equals(sceneCause) && intent.status() == PhysicalIntentStatus.CONFIRMED).count();
    }
    private static BlockPosition cargoDestination(FrontierWorldState state, SceneLease lease) {
        LogisticsSceneCause logistics = FrontierSceneBehaviors.logistics(lease);
        RouteOperation operation = state.operations().get(logistics.operationId());
        if (logistics.engagementId().isEmpty() && operation != null && operation.activeTravel().isPresent()) {
            OperationTravel travel = operation.activeTravel().orElseThrow();
            if (!travel.arrived()) return translateTravel(travel, travel.nextHotCursor()).cargoAnchor().surface().support();
        }
        return logistics.cargoPosition();
    }
    private static OperationTravel translateTravel(OperationTravel travel, int nextCursor) {
        BlockPosition from = travel.currentPosition(), to = travel.corridor().get(nextCursor);
        int deltaX = to.x() - from.x(), deltaY = to.y() - from.y(), deltaZ = to.z() - from.z(); Map<SubjectId, BodyPosition> formation = new LinkedHashMap<>();
        travel.formation().forEach((actor, position) -> formation.put(actor, position.offset(deltaX, deltaY, deltaZ)));
        return travel.advance(nextCursor, formation, travel.cargoAnchor().offset(deltaX, deltaY, deltaZ));
    }
    /** Exact feet target for the next retained edge; package-visible for the terrain regression. */
    static BodyPosition operationTravelTargetPosition(OperationTravel travel, SubjectId actorId) {
        BodyPosition target = translateTravel(travel, travel.nextHotCursor()).formation().get(actorId);
        if (target == null) throw new IllegalArgumentException("operation travel has no formation target for " + actorId);
        // Formation cells are exact feet positions.  Retaining their Y datum is essential: the
        // motion provider may traverse only the next surveyed topology edge, including grade.
        return target;
    }
    static boolean residentGuard(FrontierWorldState state, SubjectId actorId) {
        return state.bootstrap().settlements().stream().flatMap(settlement -> settlement.residents().stream())
                .anyMatch(resident -> resident.id().equals(actorId) && resident.role() == ResidentRole.GUARD);
    }
    /**
     * Accepts an actual loaded-world death of an exact owned scene body.
     *
     * <p>The first fatality drains a HOT scene, but an ordinary single Minecraft explosion can
     * kill several leased bodies in the same tick. Those later deaths are still evidence for
     * the exact DRAINING lease and must be persisted before release captures its survivors.</p>
     */
    static boolean observeDeath(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity, Entity source) {
        FrontierWorldState state = state(runtime);
        if (state == null) return false;
        Optional<SceneLease> matchingLease = state.sceneLeases().values().stream()
                .filter(lease -> lease.members().stream().anyMatch(member -> lease.retainsMemberCustody(member.actorId())
                        && member.entityId().equals(entity.getUUID()) && ownsDeclaration(entity, state, lease, member)))
                .findFirst();
        if (matchingLease.isEmpty()) return false;
        SceneLease lease = matchingLease.orElseThrow();

        SceneMember member = lease.members().stream().filter(candidate -> candidate.entityId().equals(entity.getUUID())).findFirst().orElseThrow();
        String cause = source == null ? "environment" : "entity:" + source.getUUID();
        CommandResult result = submit(runtime, "scene-death", lease.id().value() + "-" + member.actorId().value(),
                new ActorDied(lease.id(), member.actorId(), FrontierV3BodyObservation.position(entity), cause));
        if (!(result instanceof CommandResult.Accepted)) return false;
        if (entity.level() instanceof ServerLevel level) {
            FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId()).retireDeadActor(state(runtime), member.actorId());
        }
        return true;
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
    static void cleanClosedBodies(ServerLevel level, FrontierWorldState state) {
        FrontierV3ClosedSceneBodyRecovery.cleanClosedBodies(level, state);
    }
    static boolean adoptRetainedClosedBodyForAmbient(Entity entity, FrontierWorldState state, SubjectId actorId) {
        return FrontierV3ClosedSceneBodyRecovery.adoptRetainedClosedBodyForAmbient(entity, state, actorId);
    }
    static List<SubjectId> retainedClosedActorsDemandedBy(ServerLevel level, FrontierWorldState state) {
        return FrontierV3ClosedSceneBodyRecovery.retainedClosedActorsDemandedBy(level, state);
    }
    static ClosedSceneReturnRecovery inspectClosedHarvestReturn(ServerLevel level, FrontierWorldState state, SubjectId actorId) {
        return FrontierV3ClosedSceneBodyRecovery.inspectClosedHarvestReturn(level, state, actorId);
    }
    static ClosedSceneReturnRecovery retainedClosedReturnAdmission(FrontierV3AmbientCarrierLedger ledger,
                                                                   FrontierV3ActorCarrierComposition.Declaration next) {
        return FrontierV3ClosedSceneBodyRecovery.retainedClosedReturnAdmission(ledger, next);
    }
    enum ClosedSceneReturnRecovery { NOT_RETAINED, LIVE_BODY, PENDING, FENCED, CONFLICT }
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
    /** A logistics scene owns local transport only; combat exists solely under an engagement lease. */
    static boolean combatEnabled(SceneLease lease) {
        return FrontierSceneBehaviors.logistics(lease).engagementId().isPresent();
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
                .filter(FrontierSceneBehaviors::isLogistics)
                .filter(lease -> FrontierSceneBehaviors.logistics(lease).engagementId().filter(intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ENGAGEMENT)::equals).isPresent())
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
        // An observed entity is untrusted until it presents every physical authority field.
        // In particular, NBT's absent-long default must be a boolean rejection, never an
        // exception while inspecting a foreign body that happens to have this scene UUID.
        if (entity == null || entity.isRemoved()
                || !entity.getPersistentData().contains(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY)
                || entity.getPersistentData().getLong(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY) < 1L) return false;
        long epoch = entity.getPersistentData().getLong(FrontierV3AmbientActorExecutor.CUSTODY_EPOCH_KEY);
        FrontierV3ActorCarrierComposition.Declaration declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state, member.actorId(),
                FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, member.entityId(), FrontierV3ActorCarrierComposition.Representation.LIVE_BODY,
                lease.revision(), epoch);
        return FrontierV3ActorCarrierComposition.owns(entity, declaration)
                && (bioform(state, member.actorId()) ? entity instanceof Zombie : entity instanceof Villager) && !entity.isRemoved() && member.entityId().equals(entity.getUUID())
                && lease.id().value().equals(entity.getPersistentData().getString(LEASE_KEY))
                && member.actorId().value().equals(entity.getPersistentData().getString(ACTOR_KEY))
                && lease.revision() == entity.getPersistentData().getLong(REVISION_KEY);
    }
    /** Closing a lease preserves its revision; historical bodies must prove it too. */
    static boolean ownedByClosedLease(Entity entity, FrontierWorldState state, SceneLease lease, SceneMember member) {
        return lease.status() == SceneLeaseStatus.CLOSED && ownsDeclaration(entity, state, lease, member);
    }
    static boolean bioform(FrontierWorldState state, SubjectId actorId) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .anyMatch(bioform -> bioform.id().equals(actorId));
    }
    private static boolean mark(Entity entity, FrontierWorldState state, SceneLease lease, SceneMember member) {
        var from = FrontierV3ActorCarrierComposition.declaredBy(entity).orElse(null);
        if (from == null || !(entity.level() instanceof ServerLevel level)) return false;
        var target = FrontierV3AmbientActorExecutor.carrierDeclaration(state, member.actorId(),
                FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, member.entityId(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, lease.revision(), from.epoch());
        if (from.owner() == FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE) {
            var ambient = state.ambientLeases().get(member.actorId());
            if (ambient == null || ambient.status() != AmbientLeaseStatus.CLOSED
                    || ambient.revision() != from.authorityRevision()) return false;
        }
        FrontierV3ActorCarrierComposition.requireRole(FrontierV3ActorCarrierComposition.InventoryEntry.SCENE_BODY,
                FrontierV3ActorCarrierComposition.Role.PRODUCER);
        return FrontierV3ActorHandoffAdmission.transfer(level, state.bootstrap().worldId(), entity, FrontierV3ActorOwnerBinding.scene(target, lease.id()));
    }
    /** A loaded-world obstruction or altered owned body is a physical conflict, not restart evidence. */
    static void conflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                 SceneLease lease, String cause) {
        // Cargo is not a generic scene property.  Engineering, medical and assault scenes are
        // deliberately cargo-free, so conflict reporting must consult the typed registry before
        // asking the logistics-only carrier executor to decode a cause.  The diagnostic remains
        // complete without inventing a synthetic logistics binding for other scene families.
        FrontierV3SceneReadiness.Value readiness = FrontierV3SceneReadiness.forLease(level, state, lease);
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "scene_conflict:" + cause + ":" + readiness.bodies() + ":" + readiness.carrier(), lease,
                submit(runtime, "scene-conflict", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.CONFLICT)));
    }
    /** A loaded demand point has disproved exact reclaimability; record conflict rather than loop forever or replace a body. */
    private static void recoveryUnresolved(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, java.util.Set<SubjectId> missingActors,
                                           boolean missingCarrier) {
        submit(runtime, "scene-recovery-unresolved", lease.id().value(), new SceneLeaseRecoveryUnresolved(lease.id(), missingActors, missingCarrier));
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
