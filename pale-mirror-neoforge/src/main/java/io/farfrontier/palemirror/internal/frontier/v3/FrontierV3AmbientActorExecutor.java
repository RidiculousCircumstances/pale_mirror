package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorDied;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorObserved;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseReleased;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.model.BioformRole;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private static final int DEMAND_RADIUS_BLOCKS = 96;
    private static final int DRAIN_SAFE_RADIUS_BLOCKS = 64;
    private static final long DRAIN_HYSTERESIS_TICKS = 200L;
    private static final int MAX_PENDING_ADMISSIONS = 4_096;
    private static final int MAX_VERTICAL_PLACEMENT_SEARCH = 4;
    private static final int GRAYBOX_BIOFORM_FIRE_RESISTANCE_TICKS = Integer.MAX_VALUE;
    private static final long PENDING_ADMISSION_TICKS = 20L;
    /** Noncanonical, short-lived bridge across EntityJoinLevelEvent and the UUID index. */
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
        int admitted = 0;
        for (var entry : state.actorLocations().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            if (admitted >= MAX_ACTORS_PER_TICK) return;
            if (entry.getValue().condition().status() != ActorLifeStatus.ALIVE
                    || FrontierSceneAdmission.reserved(state, entry.getKey())) {
                forgetColdDemand(runtime, entry.getKey());
                continue;
            }
            var lease = state.ambientLeases().get(entry.getKey());
            boolean demanded = demand(level, entry.getValue().position());
            if (!demanded) {
                if (lease != null && lease.status() == AmbientLeaseStatus.HOT) {
                    Entity body = level.getEntity(entityId(state, entry.getKey()));
                    if (body instanceof Mob mob && owned(mob, entry.getKey(), bioform(state, entry.getKey()))
                            && drainAfterDemandHysteresis(level, runtime, entry.getKey(), mob)) {
                        admitted++;
                    }
                } else {
                    forgetColdDemand(runtime, entry.getKey());
                }
                continue;
            }
            forgetColdDemand(runtime, entry.getKey());
            if (lease == null || lease.status() == AmbientLeaseStatus.CLOSED) {
                submit(runtime, "ambient-prepare", entry.getKey().value(), new AmbientLeasePrepared(AmbientActorProcess.nextLease(state, entry.getKey(), runtime.checkpointImage().orElseThrow().instant())));
                admitted++;
                continue;
            }
            if (lease.status() == AmbientLeaseStatus.PREPARED) {
                Result result = materialize(level, runtime, state, entry.getKey(), lease.handoffPosition());
                if (result == Result.APPLIED || result == Result.CURRENT || result == Result.PENDING) {
                    if (result != Result.PENDING) submit(runtime, "ambient-hot", entry.getKey().value(), new AmbientLeaseTransition(entry.getKey(), AmbientLeaseStatus.HOT));
                    admitted++;
                }
                continue;
            }
            Entity body = level.getEntity(entityId(state, entry.getKey()));
            if (lease.status() == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART) {
                if (body != null && owned(body, entry.getKey(), bioform(state, entry.getKey()))) {
                    submit(runtime, "ambient-recovered", entry.getKey().value(), new AmbientLeaseTransition(entry.getKey(), AmbientLeaseStatus.HOT));
                    admitted++;
                }
                continue;
            }
            if (lease.status() == AmbientLeaseStatus.HOT && body instanceof Mob mob && owned(body, entry.getKey(), bioform(state, entry.getKey()))) {
                pursueLocalGoal(mob, lease.goalPosition());
            }
        }
    }

    static Result materialize(ServerLevel level, FrontierWorldState state, SubjectId actorId, BlockPosition canonicalPosition) {
        if (!state.actorLocations().containsKey(actorId)) return Result.CONFLICT;
        UUID entityId = entityId(state, actorId); Entity existing = level.getEntity(entityId); boolean bioform = bioform(state, actorId);
        if (existing != null) {
            if (!owned(existing, actorId, bioform)) return Result.CONFLICT;
            if (existing instanceof Zombie zombie) configureBioform(zombie, bioformRole(state, actorId));
            // A recovered PREPARED body has not yet crossed the canonical HOT boundary.
            // Keep it inert until that durable transition is accepted.
            if (existing instanceof Mob body) {
                body.getNavigation().stop();
                body.setNoAi(true);
            }
            return Result.CURRENT;
        }
        BlockPos position = standingPosition(level, canonicalPosition);
        if (position == null) return Result.DEFERRED;
        Mob body = bioform ? EntityType.ZOMBIE.create(level) : EntityType.VILLAGER.create(level);
        if (body == null) throw new IllegalStateException("Minecraft could not create a Frontier v3 ambient actor");
        body.setUUID(entityId); body.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D); body.setPersistenceRequired();
        // The body exists before the durable PREPARED -> HOT acknowledgement. Do not let
        // vanilla AI move it across that crash window.
        body.setNoAi(true);
        if (body instanceof Zombie zombie) configureBioform(zombie, bioformRole(state, actorId));
        body.setCustomName(Component.literal((bioform ? "Hive " : "Frontier ") + actorId.value())); body.setCustomNameVisible(false);
        body.getPersistentData().putString(ACTOR_KEY, actorId.value()); body.getPersistentData().putString(KIND_KEY, bioform ? "BIOFORM" : "RESIDENT");
        return level.addFreshEntity(body) ? Result.APPLIED : Result.CONFLICT;
    }

    private static Result materialize(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                      FrontierWorldState state, SubjectId actorId, BlockPosition canonicalPosition) {
        PendingAdmission pending = pending(runtime, entityId(state, actorId));
        if (pending != null && owned(pending.entity(), actorId, bioform(state, actorId))) return Result.PENDING;
        return materialize(level, state, actorId, canonicalPosition);
    }

    static UUID entityId(FrontierWorldState state, SubjectId actorId) { return io.farfrontier.palemirror.frontier.v3.model.SceneLease.deterministicEntityId(state.bootstrap().worldId(), actorId); }

    /**
     * Read-only loaded-world admission evidence for one canonical ambient actor.  This must not
     * load a chunk or alter a lease: it exists so an operator can distinguish a legitimate
     * unloaded/blocked deferral from a UUID ownership conflict while investigating a visible
     * PREPARED lease.
     */
    static AdmissionDiagnostic admissionDiagnostic(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                    FrontierWorldState state, SubjectId actorId) {
        var location = state.actorLocations().get(actorId);
        if (location == null) return AdmissionDiagnostic.notCanonical();
        UUID expectedId = entityId(state, actorId);
        Entity existing = level.getEntity(expectedId);
        if (existing != null) {
            return owned(existing, actorId, bioform(state, actorId))
                    ? AdmissionDiagnostic.indexed(expectedId, pending(runtime, expectedId) != null)
                    : AdmissionDiagnostic.conflict(expectedId);
        }
        BlockPos anchor = new BlockPos(location.position().x(), location.position().y(), location.position().z());
        if (!level.hasChunkAt(anchor)) return AdmissionDiagnostic.unloaded(expectedId);
        BlockPos position = standingPosition(level, location.position());
        if (position == null) return AdmissionDiagnostic.blocked(expectedId, new BlockPosition(anchor.getX(), anchor.getY(), anchor.getZ()));
        return AdmissionDiagnostic.ready(expectedId, new BlockPosition(position.getX(), position.getY(), position.getZ()));
    }

    /** Finds the first two-block-clear standing space directly above the naturally loaded surface. */
    private static BlockPos standingPosition(ServerLevel level, BlockPosition canonicalPosition) {
        BlockPos anchor = new BlockPos(canonicalPosition.x(), canonicalPosition.y(), canonicalPosition.z());
        if (!level.hasChunkAt(anchor)) return null;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ());
        for (int y = surface; y <= surface + MAX_VERTICAL_PLACEMENT_SEARCH; y++) {
            BlockPos candidate = new BlockPos(anchor.getX(), y, anchor.getZ());
            if (!level.hasChunkAt(candidate)) return null;
            if (!level.getBlockState(candidate).isAir() || !level.getBlockState(candidate.above()).isAir()
                    || level.getBlockState(candidate.below()).isAir()) continue;
            return candidate;
        }
        return null;
    }

    /** Retains only an exact expected body during the short join-to-index hand-off. */
    static boolean observeJoin(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Entity entity) {
        FrontierWorldState state = runtime.decodedState().orElse(null);
        if (state == null || !recognizes(runtime, entity)) return false;
        String rawActorId = entity.getPersistentData().getString(ACTOR_KEY);
        if (rawActorId.isBlank()) return false;
        SubjectId actorId;
        try { actorId = new SubjectId(rawActorId); } catch (IllegalArgumentException invalid) { return false; }
        if (!state.actorLocations().containsKey(actorId) || state.actorLocations().get(actorId).condition().status() != ActorLifeStatus.ALIVE
                || !entityId(state, actorId).equals(entity.getUUID()) || !owned(entity, actorId, bioform(state, actorId))) return false;
        Map<UUID, PendingAdmission> pending = PENDING_ADMISSIONS.computeIfAbsent(runtime, ignored -> new LinkedHashMap<>());
        if (pending.size() >= MAX_PENDING_ADMISSIONS && !pending.containsKey(entity.getUUID())) return false;
        pending.put(entity.getUUID(), new PendingAdmission(entity, entity.level().getGameTime() + PENDING_ADMISSION_TICKS));
        if (state.ambientLeases().get(actorId) != null && state.ambientLeases().get(actorId).status() == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART) {
            submit(runtime, "ambient-recovered", actorId.value(), new AmbientLeaseTransition(actorId, AmbientLeaseStatus.HOT));
        }
        return true;
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
                new AmbientActorDied(actorId, new BlockPosition(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ()), cause));
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
        BlockPos target = new BlockPos(position.x(), position.y(), position.z());
        return level.hasChunkAt(target) && level.players().stream().filter(player -> !player.isSpectator())
                .anyMatch(player -> player.blockPosition().closerThan(target, DEMAND_RADIUS_BLOCKS));
    }
    private static boolean bioform(FrontierWorldState state, SubjectId actorId) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .map(Bioform::id).anyMatch(actorId::equals);
    }
    static BioformRole bioformRole(FrontierWorldState state, SubjectId actorId) {
        return java.util.stream.Stream.concat(state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(bioform -> bioform.id().equals(actorId)).map(Bioform::role).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("not a canonical Frontier v3 bioform: " + actorId));
    }
    static boolean owned(Entity entity, SubjectId actorId, boolean bioform) {
        return !entity.isRemoved() && actorId.value().equals(entity.getPersistentData().getString(ACTOR_KEY))
                && (bioform ? entity instanceof Zombie : entity instanceof Villager)
                && (bioform ? "BIOFORM" : "RESIDENT").equals(entity.getPersistentData().getString(KIND_KEY));
    }
    /** The graybox Zombie is a hive creature, not a vanilla undead exposed to daylight. */
    static void configureBioform(Zombie body, BioformRole role) {
        if (!body.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            body.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, GRAYBOX_BIOFORM_FIRE_RESISTANCE_TICKS, 0, true, false));
        }
        // Fire resistance prevents daylight damage but vanilla Zombies still render as burning.
        // The non-damageable role marker suppresses only vanilla sunlight ignition; real fire and
        // explosion effects remain visible and flow through their normal physical observation path.
        body.setCanPickUpLoot(false);
        if (body.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            body.setItemSlot(EquipmentSlot.HEAD, new ItemStack(switch (role) {
                case WORKER -> Items.LIME_WOOL;
                case SCOUT -> Items.CYAN_WOOL;
                case GUARD -> Items.PURPLE_WOOL;
                case BOMBER -> Items.RED_WOOL;
            }));
            body.setDropChance(EquipmentSlot.HEAD, 0.0F);
        }
    }
    private static void pursueLocalGoal(Mob body, BlockPosition goal) {
        body.setNoAi(false);
        if (body.tickCount % 20 != 0) return;
        body.getNavigation().moveTo(goal.x() + 0.5D, goal.y(), goal.z() + 0.5D, body instanceof Zombie ? 0.85D : 0.70D);
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
        BlockPosition position = new BlockPosition(body.getBlockX(), body.getBlockY(), body.getBlockZ());
        FixedScalar health = new FixedScalar(Math.round((double) body.getHealth() * FixedScalar.SCALE));
        submit(runtime, "ambient-draining", actorId.value(), new AmbientLeaseTransition(actorId, AmbientLeaseStatus.DRAINING));
        submit(runtime, "ambient-release", actorId.value(), new AmbientLeaseReleased(actorId, position, health));
        body.discard();
        return true;
    }
    static void forget(FrontierV3ServerRuntime<?, ?> runtime) {
        PENDING_ADMISSIONS.remove(runtime);
        COLD_DEMAND_SINCE.remove(runtime);
    }
    private static PendingAdmission pending(FrontierV3ServerRuntime<?, ?> runtime, UUID entityId) {
        Map<UUID, PendingAdmission> pending = PENDING_ADMISSIONS.get(runtime);
        return pending == null ? null : pending.get(entityId);
    }
    private static void cleanPending(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime) {
        Map<UUID, PendingAdmission> pending = PENDING_ADMISSIONS.get(runtime);
        if (pending == null) return;
        pending.values().removeIf(candidate -> candidate.entity().isRemoved() || candidate.entity().isAddedToLevel()
                || candidate.expiresAtGameTime() < level.getGameTime());
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
    private static void forgetColdDemand(FrontierV3ServerRuntime<?, ?> runtime, SubjectId actorId) {
        Map<SubjectId, Long> absentSince = COLD_DEMAND_SINCE.get(runtime);
        if (absentSince == null) return;
        absentSince.remove(actorId);
        if (absentSince.isEmpty()) COLD_DEMAND_SINCE.remove(runtime);
    }
    private static boolean playerWithin(ServerLevel level, BlockPos position, int radius) {
        return level.players().stream().filter(player -> !player.isSpectator()).anyMatch(player -> player.blockPosition().closerThan(position, radius));
    }
    private static void submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, String phase, String id, FrontierPayload payload) {
        FrontierV3CommandSubmission.submit(runtime, phase, id, payload);
    }
    private record PendingAdmission(Entity entity, long expiresAtGameTime) { }
    record AdmissionDiagnostic(String status, UUID entityId, boolean pending, BlockPosition placement) {
        private static AdmissionDiagnostic notCanonical() { return new AdmissionDiagnostic("NOT_CANONICAL", null, false, null); }
        private static AdmissionDiagnostic indexed(UUID entityId, boolean pending) { return new AdmissionDiagnostic("INDEXED", entityId, pending, null); }
        private static AdmissionDiagnostic conflict(UUID entityId) { return new AdmissionDiagnostic("UUID_CONFLICT", entityId, false, null); }
        private static AdmissionDiagnostic unloaded(UUID entityId) { return new AdmissionDiagnostic("UNLOADED", entityId, false, null); }
        private static AdmissionDiagnostic blocked(UUID entityId, BlockPosition placement) { return new AdmissionDiagnostic("BLOCKED", entityId, false, placement); }
        private static AdmissionDiagnostic ready(UUID entityId, BlockPosition placement) { return new AdmissionDiagnostic("READY", entityId, false, placement); }
    }
    enum Result { APPLIED, CURRENT, PENDING, DEFERRED, CONFLICT }
}
