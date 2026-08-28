package io.farfrontier.palemirror.internal.frontier.v3;

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
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.model.BioformRole;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;
import io.farfrontier.palemirror.frontier.v3.model.ResidentRole;
import io.farfrontier.palemirror.frontier.v3.model.SceneEngagementCandidate;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
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
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Loaded-chunk HOT executor for exact route participants.
 *
 * <p>A persisted lease is the authority boundary. PREPARED may safely finish materialization of
 * its deterministic bodies after a restart; HOT never respawns a missing body and instead enters
 * the observable unknown state. This executor neither loads chunks nor writes world blocks.</p>
 */
final class FrontierV3SceneExecutor {
    static final String LEASE_KEY = "pale_mirror_frontier_v3_scene_lease";
    static final String ACTOR_KEY = "pale_mirror_frontier_v3_scene_actor";
    static final String REVISION_KEY = "pale_mirror_frontier_v3_scene_revision";
    private static final int DEMAND_RADIUS_BLOCKS = 96;
    private static final double RESIDENT_SPEED = 0.055D, BIOFORM_SPEED = 0.075D, ARRIVAL_DISTANCE = 0.35D;

    enum BodyMaterialization { COMPLETE, DEFERRED, CONFLICT }

    private FrontierV3SceneExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = state(runtime);
        if (state == null) return;
        Optional<SceneEngagementCandidate> engagement = state.coldEngagementSceneCandidates().stream()
                .filter(candidate -> state.sceneLeases().values().stream().noneMatch(lease -> lease.engagementId().filter(candidate.engagementId()::equals).isPresent()
                        && lease.status() != SceneLeaseStatus.CLOSED))
                .filter(candidate -> demandExists(level, candidate.handoffPosition())).findFirst();
        if (engagement.isPresent()) {
            SceneEngagementCandidate candidate = engagement.orElseThrow();
            SceneLease lease = lease(runtime, candidate);
            if (FrontierSceneAdmission.available(state, candidate.actorIds())) prepare(runtime, lease);
            else handoff(level, runtime, state, lease);
            return;
        }
        Optional<RouteOperation> demand = state.operations().values().stream().sorted(Comparator.comparing(RouteOperation::id))
                .filter(operation -> operation.stage() == io.farfrontier.palemirror.frontier.v3.model.OperationStage.EN_ROUTE)
                .filter(operation -> state.sceneLeases().values().stream().noneMatch(lease -> lease.operationId().equals(operation.id())
                        && lease.status() != SceneLeaseStatus.CLOSED))
                .filter(operation -> demandExists(level, operation.route().get(operation.routeIndex()))).findFirst();
        if (demand.isPresent()) {
            RouteOperation operation = demand.orElseThrow();
            SceneLease lease = lease(runtime, operation);
            if (FrontierSceneAdmission.available(state, operation.participantIds())) prepare(runtime, lease);
            else handoff(level, runtime, state, lease);
            return;
        }
        state.sceneLeases().values().stream().sorted(Comparator.comparing(SceneLease::id)).filter(lease -> lease.status() != SceneLeaseStatus.CLOSED)
                .findFirst().ifPresent(lease -> execute(level, runtime, state, lease));
        cleanReleasedBodies(level, state);
    }

    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, RouteOperation operation) {
        CheckpointImage checkpoint = checkpoint(runtime);
        SceneLeaseId id = new SceneLeaseId("lease:" + operation.id().value().substring("operation:".length()) + "-r" + checkpoint.revision().value());
        List<SceneMember> members = operation.participantIds().stream().sorted().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(checkpoint.worldId(), id, actor))).toList();
        return new SceneLease(id, checkpoint.worldId(), operation.id(), operation.cargoId(), operation.route().get(operation.routeIndex()), checkpoint.instant(), checkpoint.revision().value(),
                SceneLeaseStatus.PREPARED, members);
    }

    private static SceneLease lease(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneEngagementCandidate candidate) {
        CheckpointImage checkpoint = checkpoint(runtime);
        SceneLeaseId id = new SceneLeaseId("lease:" + candidate.engagementId().value().substring("engagement:".length()) + "-r" + checkpoint.revision().value());
        List<SceneMember> members = candidate.actorIds().stream().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(checkpoint.worldId(), id, actor))).toList();
        return new SceneLease(id, checkpoint.worldId(), candidate.operationId(), candidate.cargoId(), candidate.handoffPosition(), checkpoint.instant(), checkpoint.revision().value(),
                SceneLeaseStatus.PREPARED, Optional.of(candidate.engagementId()), members);
    }

    private static void prepare(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        submit(runtime, "scene-prepare", lease.id().value(), new SceneLeasePrepared(lease));
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
            captures.add(new SceneMemberPosition(member.actorId(), new BlockPosition(body.getBlockX(), body.getBlockY(), body.getBlockZ()), fixed(mob.getHealth())));
        }
        if (!captures.isEmpty()) submit(runtime, "scene-handoff", lease.id().value(), new SceneLeaseHandoff(lease.withAmbientHandoff(
                captures.stream().map(SceneMemberPosition::actorId).collect(java.util.stream.Collectors.toSet())), captures));
    }

    private static void execute(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        switch (lease.status()) {
            case PREPARED -> materializePrepared(level, runtime, state, lease);
            case HOT -> {
                executeLocalGoals(level, state, lease);
                if (level.getGameTime() % 20L == 0L && !executeExplosion(level, runtime, state, lease)) executeStrike(level, runtime, state, lease);
                if (!demandExists(level, lease.handoffPosition())) submit(runtime, "scene-draining", lease.id().value(),
                        new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING));
            }
            case DRAINING -> release(level, runtime, state, lease);
            case UNKNOWN_AFTER_RESTART -> reclaim(level, runtime, state, lease);
            case CLOSED -> { }
        }
    }

    private static void materializePrepared(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        BodyMaterialization result = materializeBodies(level, state, lease);
        if (result == BodyMaterialization.COMPLETE) {
            submit(runtime, "scene-hot", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT));
        } else if (result == BodyMaterialization.CONFLICT) {
            unknown(runtime, lease);
        }
    }

    /** Reclaims only a complete observed body set; missing bodies remain explicit UNKNOWN. */
    private static void reclaim(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        if (!demandExists(level, lease.handoffPosition())) return;
        reclaimObservedBodies(level, runtime, state, lease);
    }

    /** Accepts only the already observed exact body set; player demand remains the caller's responsibility. */
    static boolean reclaimObservedBodies(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        for (SceneMember member : lease.members()) {
            if (state.actorLocations().get(member.actorId()).condition().status() == io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.DEAD) continue;
            Entity entity = level.getEntity(member.entityId());
            if (!owned(entity, state, lease, member) || !(entity instanceof Mob body) || body.getHealth() <= 0.0F) return false;
        }
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
                if (owned(existing, state, lease, member)) continue;
                if (!FrontierV3AmbientActorExecutor.owned(existing, member.actorId(), bioform(state, member.actorId()))) return BodyMaterialization.CONFLICT;
                body.getNavigation().stop(); body.setNoAi(true); body.setCustomNameVisible(true); mark(body, lease, member);
                continue;
            }
            if (lease.ambientHandoffActorIds().contains(member.actorId())) return BodyMaterialization.DEFERRED;
            BlockPos candidate = spawnCandidate(lease.handoffPosition(), index);
            if (!level.hasChunkAt(candidate)) return BodyMaterialization.DEFERRED;
            BlockPos position = spawnPosition(level, candidate);
            if (position == null) return BodyMaterialization.CONFLICT;
            boolean bioform = bioform(state, member.actorId());
            Mob body = bioform ? EntityType.ZOMBIE.create(level) : EntityType.VILLAGER.create(level);
            if (body == null) throw new IllegalStateException("Minecraft could not create a Frontier v3 scene body");
            body.setUUID(member.entityId());
            body.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
            body.setPersistenceRequired();
            body.setNoAi(true);
            body.setCustomName(Component.literal((bioform ? "Hive " : "Frontier ") + member.actorId().value()));
            body.setCustomNameVisible(true);
            mark(body, lease, member);
            if (!level.addFreshEntity(body)) return BodyMaterialization.CONFLICT;
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
        for (Body actor : bodies) moveToward(level, actor.entity(), localTarget(state, actor, bodies, lease));
    }

    /** Executes one durable effect phase; HOT scheduling supplies the twenty-tick cadence. */
    static boolean executeExplosion(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    FrontierWorldState state, SceneLease lease) {
        if (lease.engagementId().isEmpty()) return false;
        SubjectId engagement = lease.engagementId().orElseThrow();
        Optional<PhysicalIntent> unresolved = state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.EXPLOSION)
                .filter(intent -> intent.subjectIds().size() == 2 && intent.subjectIds().getLast().equals(engagement))
                .filter(intent -> intent.status() != PhysicalIntentStatus.CONFIRMED).min(Comparator.comparing(PhysicalIntent::id));
        if (unresolved.isPresent()) return true;
        List<Body> bodies = lease.members().stream().map(member -> body(level, state, lease, member)).flatMap(Optional::stream).toList();
        Body bomber = bodies.stream().filter(value -> bomber(state, value.member().actorId())).min(Comparator.comparing(value -> value.member().actorId())).orElse(null);
        Body target = bomber == null ? null : bodies.stream().filter(value -> !value.bioform()).min(Comparator.comparingDouble((Body value) -> bomber.entity().distanceToSqr(value.entity()))
                .thenComparing(value -> value.member().actorId())).orElse(null);
        if (bomber == null || target == null || bomber.entity().distanceToSqr(target.entity()) > 36.0D) return false;
        BlockPos origin = target.entity().blockPosition();
        String key = lease.id().value().replace(':', '-') + "-" + bomber.member().actorId().value().replace(':', '-');
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:explosion-" + key), PhysicalIntentKind.EXPLOSION, PhysicalIntentStatus.PREPARED,
                bomber.member().actorId(), List.of(bomber.member().actorId(), engagement), position(origin), 4, PhysicalPostcondition.EXPLOSION_OBSERVED);
        submit(runtime, "explosion-prepare", key, new PhysicalIntentPrepared(intent));
        return true;
    }

    static void executeStrike(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        if (lease.engagementId().isEmpty()) return;
        List<Body> bodies = lease.members().stream().map(member -> body(level, state, lease, member)).flatMap(Optional::stream).toList();
        Optional<PhysicalIntent> pending = state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.SCENE_STRIKE
                && intent.causeSubjectId().equals(lease.operationId()) && intent.status() != PhysicalIntentStatus.CONFIRMED).min(Comparator.comparing(PhysicalIntent::id));
        if (pending.filter(intent -> intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART).isPresent()) return;
        if (pending.isEmpty()) {
            boolean hiveTurn = confirmedStrikeCount(state, lease) % 2L == 0L;
            List<Body> attackers = bodies.stream().filter(body -> hiveTurn ? body.bioform() : residentGuard(state, body.member().actorId())).toList();
            List<Body> targets = bodies.stream().filter(body -> hiveTurn ? !body.bioform() : body.bioform()).toList();
            if (attackers.isEmpty() || targets.isEmpty()) return;
            Body attacker = attackers.stream().min(Comparator.comparing(body -> body.member().actorId())).orElseThrow();
            Body target = targets.stream().min(Comparator.comparingDouble((Body body) -> attacker.entity().distanceToSqr(body.entity()))
                    .thenComparing(body -> body.member().actorId())).orElseThrow();
            if (attacker.entity().distanceToSqr(target.entity()) > 3.61D) return;
            String key = lease.id().value().replace(':', '-') + "-" + attacker.member().actorId().value().replace(':', '-')
                    + "-" + target.member().actorId().value().replace(':', '-') + "-t" + level.getGameTime();
            PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:scene-strike-" + key), PhysicalIntentKind.SCENE_STRIKE, PhysicalIntentStatus.PREPARED,
                    lease.operationId(), List.of(attacker.member().actorId(), target.member().actorId()), position(attacker.entity()), 0, PhysicalPostcondition.SCENE_STRIKE_OBSERVED);
            submit(runtime, "scene-strike-prepare", key, new PhysicalIntentPrepared(intent)); return;
        }
        PhysicalIntent intent = pending.orElseThrow();
        Body attacker = bodies.stream().filter(body -> body.member().actorId().equals(intent.subjectIds().getFirst())).findFirst().orElse(null);
        Body target = bodies.stream().filter(body -> body.member().actorId().equals(intent.subjectIds().getLast())).findFirst().orElse(null);
        if (attacker == null || target == null || attacker.entity().distanceToSqr(target.entity()) > 3.61D) return;
        if (intent.status() == PhysicalIntentStatus.PREPARED) { submit(runtime, "scene-strike-running", intent.id().value(), new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty())); return; }
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

    private static long confirmedStrikeCount(FrontierWorldState state, SceneLease lease) {
        return state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.SCENE_STRIKE
                && intent.causeSubjectId().equals(lease.operationId()) && intent.status() == PhysicalIntentStatus.CONFIRMED).count();
    }

    private static boolean residentGuard(FrontierWorldState state, SubjectId actorId) {
        return state.bootstrap().settlements().stream().flatMap(settlement -> settlement.residents().stream())
                .anyMatch(resident -> resident.id().equals(actorId) && resident.role() == ResidentRole.GUARD);
    }

    private static void moveToward(ServerLevel level, Mob actor, Vec3 target) {
        Vec3 delta = target.subtract(actor.position()); double distance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (distance <= ARRIVAL_DISTANCE) return;
        double speed = actor instanceof Zombie ? BIOFORM_SPEED : RESIDENT_SPEED;
        Vec3 direct = new Vec3(delta.x / distance * speed, 0.0D, delta.z / distance * speed);
        Vec3 step = List.of(direct, new Vec3(-direct.z, 0.0D, direct.x), new Vec3(direct.z, 0.0D, -direct.x)).stream()
                .filter(candidate -> level.noCollision(actor, actor.getBoundingBox().move(candidate))).findFirst().orElse(null);
        if (step == null) return;
        actor.setYRot((float) Math.toDegrees(Math.atan2(-step.x, step.z))); actor.yBodyRot = actor.getYRot(); actor.move(MoverType.SELF, step);
    }

    /** Accepts only an actual loaded-world death of a body owned by the active HOT lease. */
    static boolean observeDeath(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity, Entity source) {
        FrontierWorldState state = state(runtime);
        if (state == null) return false;
        Optional<SceneLease> matchingLease = state.sceneLeases().values().stream()
                .filter(lease -> lease.status() == SceneLeaseStatus.HOT)
                .filter(lease -> lease.members().stream().anyMatch(member -> member.entityId().equals(entity.getUUID()) && owned(entity, state, lease, member)))
                .findFirst();
        if (matchingLease.isEmpty()) return false;
        SceneLease lease = matchingLease.orElseThrow();
        SceneMember member = lease.members().stream().filter(candidate -> candidate.entityId().equals(entity.getUUID())).findFirst().orElseThrow();
        String cause = source == null ? "environment" : "entity:" + source.getUUID();
        submit(runtime, "scene-death", lease.id().value() + "-" + member.actorId().value(),
                new ActorDied(lease.id(), member.actorId(), new BlockPosition(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ()), cause));
        return true;
    }

    private static void release(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SceneLease lease) {
        List<SceneMemberPosition> positions = new ArrayList<>();
        for (SceneMember member : lease.members()) {
            if (state.actorLocations().get(member.actorId()).condition().status() == io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.DEAD) continue;
            Entity entity = level.getEntity(member.entityId());
            if (!owned(entity, state, lease, member)) { unknown(runtime, lease); return; }
            if (!(entity instanceof Mob body) || body.getHealth() <= 0.0F) { unknown(runtime, lease); return; }
            long health = Math.round((double) body.getHealth() * FixedScalar.SCALE);
            positions.add(new SceneMemberPosition(member.actorId(), new BlockPosition(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ()), new FixedScalar(health)));
        }
        submit(runtime, "scene-release", lease.id().value(), new SceneLeaseReleased(lease.id(), positions));
    }

    private static void cleanReleasedBodies(ServerLevel level, FrontierWorldState state) {
        state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.CLOSED).forEach(lease -> lease.members().forEach(member -> {
            Entity entity = level.getEntity(member.entityId());
            if (owned(entity, state, lease, member)) entity.discard();
        }));
    }

    private static boolean demandExists(ServerLevel level, BlockPosition anchor) {
        BlockPos position = new BlockPos(anchor.x(), anchor.y(), anchor.z());
        return level.hasChunkAt(position) && level.players().stream().filter(player -> !player.isSpectator())
                .anyMatch(player -> player.blockPosition().closerThan(position, DEMAND_RADIUS_BLOCKS));
    }

    private static BlockPos spawnCandidate(BlockPosition anchor, int ordinal) {
        return new BlockPos(anchor.x() + (ordinal % 2) * 2, anchor.y(), anchor.z() + (ordinal / 2) * 2);
    }
    private static BlockPos spawnPosition(ServerLevel level, BlockPos position) {
        if (!level.getBlockState(position).isAir() || !level.getBlockState(position.above()).isAir()
                || !level.getBlockState(position.below()).isFaceSturdy(level, position.below(), Direction.UP)) return null;
        return position;
    }

    static Optional<Entity> explosionCause(ServerLevel level, FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.EXPLOSION || intent.subjectIds().size() != 2 || !bomber(state, intent.causeSubjectId())) return Optional.empty();
        return state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.HOT)
                .filter(lease -> lease.engagementId().filter(intent.subjectIds().getLast()::equals).isPresent())
                .flatMap(lease -> lease.members().stream().filter(member -> member.actorId().equals(intent.causeSubjectId()))
                        .map(member -> new LeaseMember(lease, member))).filter(value -> owned(level.getEntity(value.member().entityId()), state, value.lease(), value.member()))
                .map(value -> level.getEntity(value.member().entityId())).filter(entity -> entity instanceof Mob).filter(Entity::isAlive).findFirst();
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
    private static void unknown(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneLease lease) {
        submit(runtime, "scene-unknown", lease.id().value(), new SceneLeaseTransition(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART));
    }
    private static void submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, String phase, String id, FrontierPayload payload) {
        CheckpointImage checkpoint = checkpoint(runtime);
        CommandId commandId = new CommandId("executor:" + phase + "-" + id.replace(':', '-'));
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId), payload))
                .orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        if (!(result instanceof CommandResult.Accepted)) throw new IllegalStateException("scene executor transition was rejected: " + result);
    }
    private static CheckpointImage checkpoint(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
    }
    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.checkpointImage().map(image -> new FrontierWorldStateCodec().decode(image.canonicalState())).orElse(null);
    }
    private record Body(SceneMember member, Mob entity, boolean bioform) { }
    private record LeaseMember(SceneLease lease, SceneMember member) { }
}
