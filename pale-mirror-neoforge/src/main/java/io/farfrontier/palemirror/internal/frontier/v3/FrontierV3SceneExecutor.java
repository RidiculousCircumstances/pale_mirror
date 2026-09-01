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
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
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
import io.farfrontier.palemirror.frontier.v3.model.BioformRole;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.OperationTravel;
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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private static final int DEMAND_RADIUS_BLOCKS = 96;
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

    /**
     * Bounded volatile evidence from the last complete, naturally loaded HOT scene. Vanilla may
     * serialize an entity as soon as a player leaves its chunk, well before the intentional
     * hysteresis completes. This cache is a hand-off aid, never a second authority: restart
     * without it waits for natural inspection instead of inventing a release.
     */
    private static final Map<FrontierV3ServerRuntime<?, ?>, Map<SceneLeaseId, List<SceneMemberPosition>>> LAST_OBSERVED = new IdentityHashMap<>();

    enum BodyMaterialization { COMPLETE, DEFERRED, CONFLICT }

    /** Read-only materialization preflight; never creates, moves, claims or loads a body. */
    /**
     * Bounded read-only loaded-world preflight. Member positions make a paused PREPARED scene
     * diagnosable without granting the diagnostic any movement or materialization authority.
     */
    record Readiness(String bodies, String carrier, List<String> members) {
        Readiness {
            members = List.copyOf(members);
        }
    }

    private FrontierV3SceneExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierV3SceneBehaviorRegistry.tick(level, runtime);
    }

    /** Logistics behavior entry point; the closed registry owns inter-family ordering. */
    static boolean tickLogistics(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = state(runtime);
        if (state == null) return false;
        forgetInactiveDemand(runtime, state);
        Optional<SceneEngagementCandidate> engagement = state.coldEngagementSceneCandidates().stream()
                .filter(candidate -> state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isLogistics).noneMatch(lease -> FrontierSceneBehaviors.logistics(lease).engagementId().filter(candidate.engagementId()::equals).isPresent()
                        && lease.status() != SceneLeaseStatus.CLOSED))
                .filter(candidate -> demandExists(level, candidate.handoffPosition())).findFirst();
        if (engagement.isPresent()) {
            SceneEngagementCandidate candidate = engagement.orElseThrow();
            SceneLease lease = lease(runtime, state, candidate);
            if (FrontierSceneAdmission.available(state, candidate.actorIds())) prepare(level, runtime, lease);
            else handoff(level, runtime, state, lease);
            return true;
        }
        Optional<RouteOperation> demand = state.operations().values().stream().sorted(Comparator.comparing(RouteOperation::id))
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
                .filter(operation -> demandExists(level, operation.currentPosition())).findFirst();
        if (demand.isPresent()) {
            RouteOperation operation = demand.orElseThrow();
            SceneLease lease = lease(runtime, state, operation);
            if (FrontierSceneAdmission.available(state, operation.participantIds())) prepare(level, runtime, lease);
            else handoff(level, runtime, state, lease);
            return true;
        }
        state.sceneLeases().values().stream().sorted(Comparator.comparing(SceneLease::id)).filter(FrontierSceneBehaviors::isLogistics)
                .filter(lease -> lease.status() != SceneLeaseStatus.CLOSED)
                .findFirst().ifPresent(lease -> execute(level, runtime, state, lease));
        return true;
    }

    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, RouteOperation operation) {
        CheckpointImage checkpoint = checkpoint(runtime);
        SceneLeaseId id = new SceneLeaseId("lease:" + operation.id().value().substring("operation:".length()) + "-r" + checkpoint.revision().value());
        List<SceneMember> members = operation.participantIds().stream().sorted().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(checkpoint.worldId(), id, actor))).toList();
        OperationTravel travel = operation.activeTravel().orElseThrow(() -> new IllegalArgumentException("materialized route operation has no current travel"));
        BlockPosition cargoPosition = travel.cargoAnchor().surface().support();
        return SceneLease.atExactPositions(id, checkpoint.worldId(), operation.id(), operation.cargoId(), operation.currentPosition(), cargoPosition, checkpoint.instant(), checkpoint.revision().value(),
                SceneLeaseStatus.PREPARED, Optional.empty(), members, travel.formation());
    }

    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneEngagementCandidate candidate) {
        CheckpointImage checkpoint = checkpoint(runtime);
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
            captures.add(new SceneMemberPosition(member.actorId(), new BodyPosition(body.getBlockX(), body.getBlockY(), body.getBlockZ()), fixed(mob.getHealth())));
        }
        if (!captures.isEmpty()) {
            Map<SubjectId, BodyPosition> positions = new LinkedHashMap<>(lease.memberPositions());
            captures.forEach(capture -> positions.put(capture.actorId(), capture.body()));
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "scene_handoff", lease,
                    submit(runtime, "scene-handoff", lease.id().value(), new SceneLeaseHandoff(lease.withMemberPositions(positions).withAmbientHandoff(
                            captures.stream().map(SceneMemberPosition::actorId).collect(java.util.stream.Collectors.toSet())), captures)));
        }
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        switch (lease.status()) {
            case PREPARED -> materializePrepared(level, runtime, state, lease);
            case HOT -> {
                boolean demand = demandExists(level, lease.handoffPosition());
                if (drainAfterDemandHysteresis(runtime, lease.id(), level.getGameTime(), demand, playerWithinSafeRadius(level, lease))) {
                    FrontierV3DiagnosticTrace.recordScene(level.getServer(), "scene_draining", lease,
                            submit(runtime, "scene-draining", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING)));
                    return;
                }
                // An absent-demand scene owns no naturally loaded execution surface.  Do not
                // turn its expected unloaded cart/body into a conflict while the durable
                // hand-off window is still running.
                if (!demand) return;
                executeLocalGoals(level, state, lease);
                if (!FrontierV3CargoCarrierExecutor.move(level, state, lease, cargoDestination(state, lease))) {
                    conflict(level, runtime, state, lease, "carrier-unavailable"); return;
                }
                if (observeHotTravelAdvance(level, runtime, state, lease)) return;
                if (combatEnabled(lease) && level.getGameTime() % 20L == 0L
                        && !executeExplosion(level, runtime, state, lease)) executeStrike(level, runtime, state, lease);
                rememberObserved(level, runtime, state, lease);
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
        BodyMaterialization result = materializeBodies(level, state, lease);
        BodyMaterialization carrier = result == BodyMaterialization.COMPLETE
                ? FrontierV3CargoCarrierExecutor.materialize(level, state, lease) : BodyMaterialization.DEFERRED;
        if (result == BodyMaterialization.COMPLETE && carrier == BodyMaterialization.COMPLETE) {
            rememberObserved(level, runtime, state, lease);
            FrontierV3DiagnosticTrace.recordScene(level.getServer(), "scene_hot", lease,
                    submit(runtime, "scene-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT)));
        } else if (result == BodyMaterialization.CONFLICT || carrier == BodyMaterialization.CONFLICT) {
            conflict(level, runtime, state, lease, "prepared-" + result.name().toLowerCase(java.util.Locale.ROOT)
                    + "-" + carrier.name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** Reclaims only a complete observed body set; missing bodies remain explicit UNKNOWN. */
    static void reclaim(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        if (!demandExists(level, lease.handoffPosition())) {
            forgetRestartRecoveryObservation(runtime, lease.id());
            return;
        }
        if (lease.recoveryEvidence().isPresent()) {
            forgetRestartRecoveryObservation(runtime, lease.id());
            return;
        }
        if (!allRestartRecoveryColumnsLoaded(level, lease)) {
            forgetRestartRecoveryObservation(runtime, lease.id());
            return;
        }
        long completeLoadSince = restartRecoveryObservationSince(runtime, lease.id(), level.getGameTime());
        if (!restartRecoveryObservationReady(completeLoadSince, level.getGameTime())) return;
        java.util.Set<SubjectId> missing = lease.members().stream()
                .filter(member -> state.actorLocations().get(member.actorId()).condition().status() == io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.ALIVE)
                .filter(member -> !owned(level.getEntity(member.entityId()), state, lease, member))
                .map(SceneMember::actorId).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!missing.isEmpty()) {
            forgetRestartRecoveryObservation(runtime, lease.id());
            recoveryUnresolved(runtime, lease, missing, false);
            return;
        }
        boolean hasCargoCarrier = FrontierV3SceneBehaviorRegistry.hasCargoCarrier(lease);
        RouteOperation operation = hasCargoCarrier ? state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId()) : null;
        boolean interrupted = hasCargoCarrier && operation != null && operation.stage() == io.farfrontier.palemirror.frontier.v3.model.OperationStage.INTERRUPTED;
        if (hasCargoCarrier && !interrupted && !FrontierV3CargoCarrierExecutor.intact(level, state, lease)) {
            forgetRestartRecoveryObservation(runtime, lease.id());
            recoveryUnresolved(runtime, lease, java.util.Set.of(), true);
            return;
        }
        if (reclaimObservedBodies(level, runtime, state, lease)) forgetRestartRecoveryObservation(runtime, lease.id());
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
        boolean hasDeadMember = lease.members().stream()
                .anyMatch(member -> state.actorLocations().get(member.actorId()).condition().status() == io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.DEAD);
        submit(runtime, hasDeadMember ? "scene-recovery-draining" : "scene-reclaimed", lease.id().value(),
                new SceneLeaseTransition(lease.id(), hasDeadMember ? SceneLeaseStatus.DRAINING : SceneLeaseStatus.HOT));
        return true;
    }

    static BodyMaterialization materializeBodies(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        for (int index = 0; index < lease.members().size(); index++) {
            SceneMember member = lease.members().get(index);
            Entity existing = level.getEntity(member.entityId());
            if (existing != null) {
                if (!(existing instanceof Mob body) || body.getHealth() <= 0.0F) return BodyMaterialization.CONFLICT;
                if (owned(existing, state, lease, member)) {
                    if (body instanceof Zombie zombie) FrontierV3AmbientActorExecutor.configureBioform(zombie,
                            FrontierV3AmbientActorExecutor.bioformRole(state, member.actorId()));
                    continue;
                }
                if (!FrontierV3AmbientActorExecutor.owned(existing, member.actorId(), bioform(state, member.actorId()))) return BodyMaterialization.CONFLICT;
                body.getNavigation().stop(); body.setNoAi(true); body.setCustomName(FrontierV3ScenePresentation.actorName(state, member.actorId(), bioform(state, member.actorId()))); body.setCustomNameVisible(true);
                if (body instanceof Zombie zombie) FrontierV3AmbientActorExecutor.configureBioform(zombie,
                        FrontierV3AmbientActorExecutor.bioformRole(state, member.actorId()));
                mark(body, lease, member);
                continue;
            }
            if (lease.ambientHandoffActorIds().contains(member.actorId())) return BodyMaterialization.DEFERRED;
            BodyPosition canonical = lease.memberPosition(member.actorId());
            BlockPos candidate = new BlockPos(canonical.x(), canonical.y() - 1, canonical.z());
            if (!level.hasChunkAt(candidate)) return BodyMaterialization.DEFERRED;
            BlockPos position = FrontierV3StandingPosition.aboveExactFloor(level, candidate);
            // The semantic floor is projected independently and only into naturally loaded
            // chunks.  A fresh empty support column is therefore a normal materialization
            // dependency, not permission to substitute a higher terrain surface or to mark a
            // player/world conflict before the owned projection has had a chance to run.
            if (position == null) return BodyMaterialization.DEFERRED;
            boolean bioform = bioform(state, member.actorId());
            Mob body = bioform ? EntityType.ZOMBIE.create(level) : EntityType.VILLAGER.create(level);
            if (body == null) throw new IllegalStateException("Minecraft could not create a Frontier v3 scene body");
            body.setUUID(member.entityId());
            if (position.getY() != canonical.y()) return BodyMaterialization.CONFLICT;
            body.setPos(canonical.x() + 0.5D, canonical.y(), canonical.z() + 0.5D);
            body.setPersistenceRequired();
            body.setNoAi(true);
            if (body instanceof Zombie zombie) FrontierV3AmbientActorExecutor.configureBioform(zombie,
                    FrontierV3AmbientActorExecutor.bioformRole(state, member.actorId()));
            body.setCustomName(FrontierV3ScenePresentation.actorName(state, member.actorId(), bioform));
            body.setCustomNameVisible(true);
            mark(body, lease, member);
            if (!level.addFreshEntity(body)) {
                PaleMirrorMod.LOGGER.warn("Frontier v3 scene body admission failed lease={} actor={} uuid={} body={}",
                        lease.id().value(), member.actorId().value(), member.entityId(), canonical);
                return BodyMaterialization.CONFLICT;
            }
        }
        return BodyMaterialization.COMPLETE;
    }

    static Optional<Readiness> readiness(ServerLevel level, FrontierWorldState state, SubjectId sceneSubject) {
        SceneLease lease = currentLease(state, sceneSubject).orElse(null);
        if (lease == null) return Optional.empty();
        String carrier = FrontierV3SceneBehaviorRegistry.hasCargoCarrier(lease)
                ? FrontierV3CargoCarrierExecutor.readiness(level, state, lease).name() : "NOT_APPLICABLE";
        return Optional.of(new Readiness(bodyReadiness(level, state, lease), carrier, observedMembers(level, lease)));
    }

    /**
     * A scene is addressable either through its engagement or through its route operation.  A
     * terminal historical lease must never hide the current continuation from diagnostics.
     */
    static Optional<SceneLease> currentLease(FrontierWorldState state, SubjectId sceneSubject) {
        return state.sceneLeases().values().stream()
                .filter(lease -> FrontierSceneBehaviors.owns(lease, sceneSubject))
                .max(Comparator.comparingInt((SceneLease lease) -> lease.status() == SceneLeaseStatus.CLOSED ? 0 : 1)
                        .thenComparing(SceneLease::handoffInstant)
                        .thenComparingLong(SceneLease::revision)
                        .thenComparing(SceneLease::id));
    }

    private static String bodyReadiness(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        // A CLOSED lease is retained canonical history, not a claimant over its former Minecraft
        // bodies.  Its tags can still be present for a tick while the normal cleanup/ambient
        // hand-off runs; treating that expected release window as a UUID conflict made the
        // read-only diagnostic falsely imply duplicate actor ownership.
        if (lease.status() == SceneLeaseStatus.CLOSED) return "CLOSED";
        boolean allCurrent = true;
        for (int index = 0; index < lease.members().size(); index++) {
            SceneMember member = lease.members().get(index); Entity existing = level.getEntity(member.entityId());
            if (existing != null) {
                if (owned(existing, state, lease, member)) continue;
                return "UUID_CONFLICT";
            }
            allCurrent = false;
            if (lease.ambientHandoffActorIds().contains(member.actorId())) return "AWAITING_AMBIENT_HANDOFF";
            BodyPosition canonical = lease.memberPosition(member.actorId());
            BlockPos candidate = new BlockPos(canonical.x(), canonical.y() - 1, canonical.z());
            if (!level.hasChunkAt(candidate)) return "UNLOADED";
            if (FrontierV3StandingPosition.aboveExactFloor(level, candidate) == null) return "AWAITING_EXACT_FLOOR";
        }
        return allCurrent ? "CURRENT" : "READY";
    }

    private static List<String> observedMembers(ServerLevel level, SceneLease lease) {
        return lease.members().stream().map(member -> {
            Entity entity = level.getEntity(member.entityId());
            if (entity == null) return member.actorId().value() + "@absent";
            return member.actorId().value() + "@" + entity.getBlockX() + "," + entity.getBlockY() + "," + entity.getBlockZ();
        }).toList();
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
        for (Body actor : bodies) FrontierV3ControlledMobMotion.moveToward(level, actor.entity(), localTarget(state, actor, bodies, lease));
    }

    /** Commits one reached physical grid step with the operation and its current HOT lease together. */
    private static boolean observeHotTravelAdvance(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                   FrontierWorldState state, SceneLease lease) {
        if (FrontierSceneBehaviors.logistics(lease).engagementId().isPresent()) return false;
        RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
        if (operation == null || operation.activeTravel().isEmpty()) return false;
        OperationTravel current = operation.activeTravel().orElseThrow();
        if (current.arrived()) return false;
        OperationTravel next = translateTravel(current, current.nextHotCursor());
        for (SceneMember member : lease.members()) {
            Entity entity = level.getEntity(member.entityId());
            BodyPosition expected = next.formation().get(member.actorId());
            if (!(entity instanceof Mob body) || !owned(entity, state, lease, member)
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
                                    FrontierWorldState state, SceneLease lease) {
        if (FrontierSceneBehaviors.logistics(lease).engagementId().isEmpty()) return false;
        SubjectId engagement = FrontierSceneBehaviors.logistics(lease).engagementId().orElseThrow();
        Optional<PhysicalIntent> unresolved = state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.EXPLOSION)
                .filter(intent -> intent.subjectIds().size() == 2 && intent.subjectIds().getLast().equals(engagement))
                .filter(intent -> intent.status() != PhysicalIntentStatus.CONFIRMED).min(Comparator.comparing(PhysicalIntent::id));
        if (unresolved.isPresent()) return true;
        List<Body> bodies = lease.members().stream().map(member -> body(level, state, lease, member)).flatMap(Optional::stream).toList();
        Body bomber = bodies.stream().filter(value -> bomber(state, value.member().actorId())).min(Comparator.comparing(value -> value.member().actorId())).orElse(null);
        Body target = bomber == null ? null : bodies.stream().filter(value -> !value.bioform()).min(Comparator.comparingDouble((Body value) -> bomber.entity().distanceToSqr(value.entity()))
                .thenComparing(value -> value.member().actorId())).orElse(null);
        if (bomber == null || target == null || bomber.entity().distanceToSqr(target.entity()) > 36.0D) return false;
        // A durable physical effect is about to take ownership of the scene's next truth. A
        // volatile pre-effect sample must never be used to release a later unloaded aftermath.
        forgetLastObserved(runtime, lease.id());
        BlockPos origin = target.entity().blockPosition();
        long effectEpoch = state.physicalIntents().values().stream().filter(value -> value.kind() == PhysicalIntentKind.EXPLOSION)
                .filter(value -> value.subjectIds().contains(engagement)).count();
        // A PhysicalIntent drives a deterministic Minecraft entity UUID. Include the canonical
        // world identity so two independent v3 fixtures in one GameTest level cannot claim the
        // same TNT body; production still has one stable ID for the same world/scene/epoch.
        String world = state.bootstrap().worldId().value().replace(':', '-');
        String key = world + "-scene-r" + lease.revision() + "-e" + effectEpoch;
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:explosion-" + key), PhysicalIntentKind.EXPLOSION, PhysicalIntentStatus.PREPARED,
                bomber.member().actorId(), List.of(bomber.member().actorId(), engagement), position(origin), 4, PhysicalPostcondition.EXPLOSION_OBSERVED);
        submit(runtime, "explosion-prepare", key, new PhysicalIntentPrepared(intent));
        return true;
    }

    static void executeStrike(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        SubjectId sceneCause = FrontierV3SceneBehaviorRegistry.strikeCause(lease);
        if (sceneCause == null) return;
        boolean settlementAssault = FrontierSceneBehaviors.isSettlementAssault(lease);
        List<Body> bodies = lease.members().stream().map(member -> body(level, state, lease, member)).flatMap(Optional::stream).toList();
        Optional<PhysicalIntent> pending = state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.SCENE_STRIKE
                && intent.causeSubjectId().equals(sceneCause) && intent.status() != PhysicalIntentStatus.CONFIRMED).min(Comparator.comparing(PhysicalIntent::id));
        if (pending.filter(intent -> intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART).isPresent()) return;
        if (pending.isEmpty()) {
            boolean hiveTurn = confirmedStrikeCount(state, sceneCause) % 2L == 0L;
            List<Body> attackers = bodies.stream().filter(body -> hiveTurn ? body.bioform()
                    : !body.bioform() && (settlementAssault || residentGuard(state, body.member().actorId()))).toList();
            List<Body> targets = bodies.stream().filter(body -> hiveTurn ? !body.bioform() : body.bioform()).toList();
            if (attackers.isEmpty() || targets.isEmpty()) return;
            Body attacker = attackers.stream().min(Comparator.comparing(body -> body.member().actorId())).orElseThrow();
            Body target = targets.stream().min(Comparator.comparingDouble((Body body) -> attacker.entity().distanceToSqr(body.entity()))
                    .thenComparing(body -> body.member().actorId())).orElseThrow();
            if (attacker.entity().distanceToSqr(target.entity()) > 3.61D) return;
            forgetLastObserved(runtime, lease.id());
            String key = state.bootstrap().worldId().value().replace(':', '-') + "-" + sceneCause.value().replace(':', '-')
                    + "-r" + lease.revision() + "-s" + confirmedStrikeCount(state, sceneCause);
            PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:scene-strike-" + key), PhysicalIntentKind.SCENE_STRIKE, PhysicalIntentStatus.PREPARED,
                    sceneCause, List.of(attacker.member().actorId(), target.member().actorId()), position(attacker.entity()), 0, PhysicalPostcondition.SCENE_STRIKE_OBSERVED);
            submit(runtime, "scene-strike-prepare", key, new PhysicalIntentPrepared(intent)); return;
        }
        PhysicalIntent intent = pending.orElseThrow();
        Body attacker = bodies.stream().filter(body -> body.member().actorId().equals(intent.subjectIds().getFirst())).findFirst().orElse(null);
        Body target = bodies.stream().filter(body -> body.member().actorId().equals(intent.subjectIds().getLast())).findFirst().orElse(null);
        if (attacker == null || target == null || attacker.entity().distanceToSqr(target.entity()) > 3.61D) return;
        if (intent.status() == PhysicalIntentStatus.PREPARED) { submit(runtime, "scene-strike-running", intent.id().value(), new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty())); return; }
        forgetLastObserved(runtime, lease.id());
        float before = target.entity().getHealth(); attacker.entity().swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        target.entity().hurt(level.damageSources().mobAttack(attacker.entity()), attacker.bioform() ? 2.0F : 1.5F);
        SceneStrikeObservation receipt = new SceneStrikeObservation(new PhysicalObservationId("observation:" + intent.id().value().replace(':', '-')), intent.id(),
                attacker.member().actorId(), target.member().actorId(), fixed(before), fixed(target.entity().getHealth()));
        submit(runtime, "scene-strike-confirm", intent.id().value(), new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)));
    }

    private static FixedPosition position(Entity entity) {
        return new FixedPosition(new FixedScalar(Math.round(entity.getX() * FixedScalar.SCALE)),
                new FixedScalar(Math.round(entity.getY() * FixedScalar.SCALE)), new FixedScalar(Math.round(entity.getZ() * FixedScalar.SCALE)));
    }
    private static FixedPosition position(BlockPos position) {
        return new FixedPosition(FixedScalar.whole(position.getX()), FixedScalar.whole(position.getY()), FixedScalar.whole(position.getZ()));
    }
    private static FixedScalar fixed(float health) { return new FixedScalar(Math.max(0L, Math.round(health * FixedScalar.SCALE))); }

    private static Optional<Body> body(ServerLevel level, FrontierWorldState state, SceneLease lease, SceneMember member) {
        Entity entity = level.getEntity(member.entityId());
        return owned(entity, state, lease, member) && entity instanceof Mob mob && mob.isAlive() ? Optional.of(new Body(member, mob, bioform(state, member.actorId()))) : Optional.empty();
    }

    private static Vec3 localTarget(FrontierWorldState state, Body actor, List<Body> bodies, SceneLease lease) {
        if (FrontierSceneBehaviors.logistics(lease).engagementId().isEmpty()) {
            RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
            if (operation != null && operation.activeTravel().isPresent()) {
                OperationTravel travel = operation.activeTravel().orElseThrow();
                if (!travel.arrived()) {
                    BodyPosition target = operationTravelTargetPosition(travel, actor.member().actorId());
                    return new Vec3(target.x() + 0.5D, target.y(), target.z() + 0.5D);
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

    private static long confirmedStrikeCount(FrontierWorldState state, SubjectId sceneCause) {
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

    private static boolean residentGuard(FrontierWorldState state, SubjectId actorId) {
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
                .filter(lease -> lease.status() == SceneLeaseStatus.HOT || lease.status() == SceneLeaseStatus.DRAINING)
                .filter(lease -> lease.members().stream().anyMatch(member -> member.entityId().equals(entity.getUUID()) && owned(entity, state, lease, member)))
                .findFirst();
        if (matchingLease.isEmpty()) return false;
        SceneLease lease = matchingLease.orElseThrow();
        forgetLastObserved(runtime, lease.id());
        SceneMember member = lease.members().stream().filter(candidate -> candidate.entityId().equals(entity.getUUID())).findFirst().orElseThrow();
        String cause = source == null ? "environment" : "entity:" + source.getUUID();
        submit(runtime, "scene-death", lease.id().value() + "-" + member.actorId().value(),
                new ActorDied(lease.id(), member.actorId(), new BodyPosition(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ()), cause));
        return true;
    }

    /**
     * Releases a draining scene from the latest durable state, not the tick's earlier projection.
     * A real death listener may have committed one or more member deaths between the initial scene
     * selection and this release pass; those dead bodies are no longer release candidates.
     */
    static void release(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease selectedLease) {
        FrontierWorldState state = state(runtime);
        if (state == null) return;
        SceneLease lease = state.sceneLeases().get(selectedLease.id());
        if (lease == null || lease.status() != SceneLeaseStatus.DRAINING) return;
        boolean hasCargoCarrier = FrontierV3SceneBehaviorRegistry.hasCargoCarrier(lease);
        RouteOperation operation = hasCargoCarrier ? state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId()) : null;
        boolean interrupted = hasCargoCarrier && operation != null && operation.stage() == io.farfrontier.palemirror.frontier.v3.model.OperationStage.INTERRUPTED;
        if (!loaded(level, lease)) {
            List<SceneMemberPosition> observed = lastObserved(runtime, lease.id());
            // The entire hand-off surface is naturally unloaded. Its complete exact body/carrier
            // set was observed while HOT, and cannot change while Minecraft does not tick it.
            // Persist that hand-off now; any serialized old projection is removed on ordinary
            // chunk return before a new scene is permitted to materialize.
            if (observed != null) {
                FrontierV3DiagnosticTrace.recordScene(level.getServer(), "scene_released_unloaded", lease,
                        submit(runtime, "scene-release-unloaded", lease.id().value(), new SceneLeaseReleased(lease.id(), observed)));
                forgetLeaseTransient(runtime, lease.id());
            }
            // A restart deliberately has no volatile observation. Keep DRAINING visible until a
            // normal player/world load permits full physical postcondition inspection.
            return;
        }
        if (hasCargoCarrier && !interrupted && !FrontierV3CargoCarrierExecutor.intact(level, state, lease)) {
            conflict(level, runtime, state, lease, "release-carrier-unavailable"); return;
        }
        List<SceneMemberPosition> positions = new ArrayList<>();
        for (SceneMember member : lease.members()) {
            if (state.actorLocations().get(member.actorId()).condition().status() == io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.DEAD) continue;
            Entity entity = level.getEntity(member.entityId());
            if (!owned(entity, state, lease, member)) {
                conflict(level, runtime, state, lease, "release-body-unavailable"); return;
            }
            if (!(entity instanceof Mob body) || body.getHealth() <= 0.0F) {
                conflict(level, runtime, state, lease, "release-body-dead"); return;
            }
            long health = Math.round((double) body.getHealth() * FixedScalar.SCALE);
            positions.add(new SceneMemberPosition(member.actorId(), new BodyPosition(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ()), new FixedScalar(health)));
        }
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "scene_released", lease,
                submit(runtime, "scene-release", lease.id().value(), new SceneLeaseReleased(lease.id(), positions)));
        forgetLeaseTransient(runtime, lease.id());
    }

    /**
     * Removes only stale closed-scene projections before any behavior receives its turn.
     *
     * <p>This is deliberately shared registry lifecycle work, rather than logistics work:
     * every typed scene can close, and a higher-priority active behavior must not leave a
     * former medical/engineering/assault body permanently tagged by its old lease.</p>
     */
    static void cleanClosedBodies(ServerLevel level, FrontierWorldState state) {
        state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.CLOSED).forEach(lease -> lease.members().forEach(member -> {
            Entity entity = level.getEntity(member.entityId());
            // A CLOSED lease is historical state, and its former projection may have an older
            // hand-off revision than the retained record. Here the closed lease ID, exact UUID,
            // actor ID and expected body type are the complete safe identity; relaxing the
            // revision check is confined to deletion of that stale projection and never to
            // admission or mutation.
            if (ownedByClosedLease(entity, state, lease, member)) entity.discard();
        }));
        // Only logistics scenes have a cargo carrier.  A typed assault is deliberately
        // cargo-free; asking its typed cause for a legacy cargo ID would turn normal cleanup
        // into an exception after its last body is released.
        state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.CLOSED)
                .filter(FrontierV3SceneBehaviorRegistry::hasCargoCarrier)
                .forEach(lease -> FrontierV3CargoCarrierExecutor.discardClosed(level, state, lease));
    }

    static boolean demandExists(ServerLevel level, BlockPosition anchor) {
        BlockPos position = new BlockPos(anchor.x(), anchor.y(), anchor.z());
        return level.hasChunkAt(position) && level.players().stream().filter(player -> !player.isSpectator())
                .anyMatch(player -> player.blockPosition().closerThan(position, DEMAND_RADIUS_BLOCKS));
    }

    private static boolean loaded(ServerLevel level, SceneLease lease) {
        BlockPosition anchor = lease.handoffPosition();
        return level.hasChunkAt(new BlockPos(anchor.x(), anchor.y(), anchor.z()));
    }

    static void rememberObserved(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                         FrontierWorldState state, SceneLease lease) {
        if (!loaded(level, lease)) return;
        if (FrontierV3SceneBehaviorRegistry.hasCargoCarrier(lease) && !FrontierV3CargoCarrierExecutor.intact(level, state, lease)) {
            forgetLastObserved(runtime, lease.id());
            return;
        }
        List<SceneMemberPosition> positions = new ArrayList<>();
        for (SceneMember member : lease.members()) {
            if (state.actorLocations().get(member.actorId()).condition().status() == io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.DEAD) continue;
            Entity entity = level.getEntity(member.entityId());
            if (!owned(entity, state, lease, member) || !(entity instanceof Mob body) || body.getHealth() <= 0.0F) {
                forgetLastObserved(runtime, lease.id());
                return;
            }
            positions.add(new SceneMemberPosition(member.actorId(), new BodyPosition(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ()), fixed(body.getHealth())));
        }
        Map<SceneLeaseId, List<SceneMemberPosition>> observations = LAST_OBSERVED.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        if (observations.size() < MAX_PENDING_DRAINS || observations.containsKey(lease.id())) observations.put(lease.id(), List.copyOf(positions));
    }

    private static List<SceneMemberPosition> lastObserved(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId) {
        Map<SceneLeaseId, List<SceneMemberPosition>> observations = LAST_OBSERVED.get(runtime);
        return observations == null ? null : observations.get(leaseId);
    }

    private static void forgetLastObserved(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId) {
        Map<SceneLeaseId, List<SceneMemberPosition>> observations = LAST_OBSERVED.get(runtime);
        if (observations == null) return;
        observations.remove(leaseId);
        if (observations.isEmpty()) LAST_OBSERVED.remove(runtime);
    }

    /** Package-visible deterministic policy hook for the negative hand-off GameTest. */
    static boolean drainAfterDemandHysteresis(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId, long gameTime,
                                              boolean demandExists, boolean playerWithinSafeRadius) {
        if (demandExists) {
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
        return level.players().stream().filter(player -> !player.isSpectator())
                .anyMatch(player -> positions.stream().anyMatch(position -> player.blockPosition().closerThan(position, DRAIN_SAFE_RADIUS_BLOCKS)));
    }

    private static void forgetInactiveDemand(FrontierV3ServerRuntime<?, ?> runtime, FrontierWorldState state) {
        Map<SceneLeaseId, Long> absentSince = COLD_DEMAND_SINCE.get(runtime);
        if (absentSince != null) absentSince.keySet().removeIf(id -> {
            SceneLease lease = state.sceneLeases().get(id);
            return lease == null || lease.status() != SceneLeaseStatus.HOT;
        });
        if (absentSince != null && absentSince.isEmpty()) COLD_DEMAND_SINCE.remove(runtime);
        Map<SceneLeaseId, List<SceneMemberPosition>> observations = LAST_OBSERVED.get(runtime);
        if (observations != null) observations.keySet().removeIf(id -> {
            SceneLease lease = state.sceneLeases().get(id);
            return lease == null || (lease.status() != SceneLeaseStatus.HOT && lease.status() != SceneLeaseStatus.DRAINING);
        });
        if (observations != null && observations.isEmpty()) LAST_OBSERVED.remove(runtime);
        Map<SceneLeaseId, Long> recovery = RESTART_RECOVERY_SINCE.get(runtime);
        if (recovery != null) recovery.keySet().removeIf(id -> {
            SceneLease lease = state.sceneLeases().get(id);
            return lease == null || lease.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART;
        });
        if (recovery != null && recovery.isEmpty()) RESTART_RECOVERY_SINCE.remove(runtime);
    }

    private static void forgetColdDemand(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId) {
        Map<SceneLeaseId, Long> absentSince = COLD_DEMAND_SINCE.get(runtime);
        if (absentSince == null) return;
        absentSince.remove(leaseId);
        if (absentSince.isEmpty()) COLD_DEMAND_SINCE.remove(runtime);
    }

    private static void forgetLeaseTransient(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId) {
        forgetColdDemand(runtime, leaseId);
        forgetLastObserved(runtime, leaseId);
        forgetRestartRecoveryObservation(runtime, leaseId);
    }

    private static void forgetRestartRecoveryObservation(FrontierV3ServerRuntime<?, ?> runtime, SceneLeaseId leaseId) {
        Map<SceneLeaseId, Long> observations = RESTART_RECOVERY_SINCE.get(runtime);
        if (observations == null) return;
        observations.remove(leaseId);
        if (observations.isEmpty()) RESTART_RECOVERY_SINCE.remove(runtime);
    }

    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        COLD_DEMAND_SINCE.remove(runtime);
        LAST_OBSERVED.remove(runtime);
        RESTART_RECOVERY_SINCE.remove(runtime);
    }

    private static BlockPos spawnCandidate(BlockPosition anchor, int ordinal) {
        return new BlockPos(anchor.x() + (ordinal % 2) * 2, anchor.y(), anchor.z() + (ordinal / 2) * 2);
    }
    static Optional<Entity> explosionCause(ServerLevel level, FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.EXPLOSION || intent.subjectIds().size() != 2 || !bomber(state, intent.causeSubjectId())) return Optional.empty();
        return state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.HOT)
                .filter(FrontierSceneBehaviors::isLogistics)
                .filter(lease -> FrontierSceneBehaviors.logistics(lease).engagementId().filter(intent.subjectIds().getLast()::equals).isPresent())
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

    private static boolean bomber(FrontierWorldState state, SubjectId actorId) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(bioform -> bioform.id().equals(actorId)).map(Bioform::role).anyMatch(BioformRole.BOMBER::equals);
    }
    private static boolean owned(Entity entity, FrontierWorldState state, SceneLease lease, SceneMember member) {
        return (bioform(state, member.actorId()) ? entity instanceof Zombie : entity instanceof Villager) && !entity.isRemoved() && member.entityId().equals(entity.getUUID())
                && lease.id().value().equals(entity.getPersistentData().getString(LEASE_KEY))
                && member.actorId().value().equals(entity.getPersistentData().getString(ACTOR_KEY))
                && lease.revision() == entity.getPersistentData().getLong(REVISION_KEY);
    }

    /** Only stale closed-projection cleanup may relax the historical lease revision. */
    private static boolean ownedByClosedLease(Entity entity, FrontierWorldState state, SceneLease lease, SceneMember member) {
        return (bioform(state, member.actorId()) ? entity instanceof Zombie : entity instanceof Villager) && !entity.isRemoved()
                && member.entityId().equals(entity.getUUID()) && lease.id().value().equals(entity.getPersistentData().getString(LEASE_KEY))
                && member.actorId().value().equals(entity.getPersistentData().getString(ACTOR_KEY));
    }
    private static boolean bioform(FrontierWorldState state, SubjectId actorId) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .anyMatch(bioform -> bioform.id().equals(actorId));
    }
    private static void mark(Entity entity, SceneLease lease, SceneMember member) {
        entity.getPersistentData().remove(FrontierV3AmbientActorExecutor.ACTOR_KEY);
        entity.getPersistentData().remove(FrontierV3AmbientActorExecutor.KIND_KEY);
        entity.getPersistentData().putString(LEASE_KEY, lease.id().value());
        entity.getPersistentData().putString(ACTOR_KEY, member.actorId().value());
        entity.getPersistentData().putLong(REVISION_KEY, lease.revision());
    }
    /** A loaded-world obstruction or altered owned body is a physical conflict, not restart evidence. */
    private static void conflict(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                 SceneLease lease, String cause) {
        Readiness readiness = new Readiness(bodyReadiness(level, state, lease), FrontierV3CargoCarrierExecutor.readiness(level, state, lease).name(), observedMembers(level, lease));
        FrontierV3DiagnosticTrace.recordScene(level.getServer(), "scene_conflict:" + cause + ":" + readiness.bodies() + ":" + readiness.carrier(), lease,
                submit(runtime, "scene-conflict", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.CONFLICT)));
    }
    /** A loaded demand point has disproved exact reclaimability; record conflict rather than loop forever or replace a body. */
    private static void recoveryUnresolved(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease, java.util.Set<SubjectId> missingActors,
                                           boolean missingCarrier) {
        submit(runtime, "scene-recovery-unresolved", lease.id().value(), new SceneLeaseRecoveryUnresolved(lease.id(), missingActors, missingCarrier));
    }
    private static CommandResult submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, String phase, String id, FrontierPayload payload) {
        CheckpointImage checkpoint = checkpoint(runtime);
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
    private static CheckpointImage checkpoint(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
    }
    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.decodedState().orElse(null);
    }
    private record Body(SceneMember member, Mob entity, boolean bioform) { }
    private record LeaseMember(SceneLease lease, SceneMember member) { }
}
