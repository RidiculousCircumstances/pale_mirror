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
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind;
import io.farfrontier.palemirror.frontier.v3.model.Bioform;
import io.farfrontier.palemirror.frontier.v3.model.BioformRole;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneAdmission;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.ResidentProfile;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMigrationJourney;
import io.farfrontier.palemirror.frontier.v3.model.ResidentMigrationStatus;
import io.farfrontier.palemirror.frontier.v3.model.ResidentTransitAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.ScoutPatrolAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.OperationAssembly;
import io.farfrontier.palemirror.frontier.v3.model.OperationAssemblyDeferral;
import io.farfrontier.palemirror.frontier.v3.model.OperationAssemblyAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.OperationAssemblyDeferred;
import io.farfrontier.palemirror.frontier.v3.model.OperationStage;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;
import io.farfrontier.palemirror.frontier.v3.model.Settlement;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAccessPort;
import io.farfrontier.palemirror.frontier.v3.model.SettlementStructure;
import io.farfrontier.palemirror.frontier.v3.model.StructureKind;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
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
        // Commands submitted below synchronously install a new immutable checkpoint.  Preserve
        // the deterministic actor order, but never let a later actor make another physical
        // decision from the predecessor's stale snapshot.
        for (SubjectId actorId : state.actorLocations().keySet().stream().sorted().toList()) {
            if (admitted >= MAX_ACTORS_PER_TICK) return;
            state = runtime.decodedState().orElse(null);
            if (state == null) return;
            var location = state.actorLocations().get(actorId);
            if (location == null || location.condition().status() != ActorLifeStatus.ALIVE
                    || FrontierSceneAdmission.reserved(state, actorId)) {
                forgetColdDemand(runtime, actorId);
                continue;
            }
            var lease = state.ambientLeases().get(actorId);
            boolean demanded = demand(level, location.position());
            if (!demanded) {
                if (lease != null && lease.status() == AmbientLeaseStatus.HOT) {
                    Entity body = level.getEntity(entityId(state, actorId));
                    if (body instanceof Mob mob && owned(mob, actorId, bioform(state, actorId))) {
                        if (directedGoal(lease)) {
                            if (pursueLocalGoal(level, runtime, state, actorId, mob, lease)) return;
                            if (observeDirectedArrival(level, runtime, state, actorId, mob, lease)) admitted++;
                        }
                        if (drainAfterDemandHysteresis(level, runtime, actorId, mob)) admitted++;
                    }
                } else {
                    forgetColdDemand(runtime, actorId);
                }
                continue;
            }
            forgetColdDemand(runtime, actorId);
            if (lease == null || lease.status() == AmbientLeaseStatus.CLOSED) {
                submit(runtime, "ambient-prepare", actorId.value(), new AmbientLeasePrepared(AmbientActorProcess.nextLease(state, actorId, runtime.checkpointImage().orElseThrow().instant())));
                admitted++;
                continue;
            }
            if (lease.status() == AmbientLeaseStatus.PREPARED) {
                Result result = materialize(level, runtime, state, actorId, lease.handoffPosition());
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
                }
                continue;
            }
            if (lease.status() == AmbientLeaseStatus.HOT && body instanceof Mob mob && owned(body, actorId, bioform(state, actorId))) {
                if (pursueLocalGoal(level, runtime, state, actorId, mob, lease)) return;
                if (observeDirectedArrival(level, runtime, state, actorId, mob, lease)) admitted++;
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
        BlockPos position = FrontierV3StandingPosition.aboveFloor(level, canonicalPosition);
        if (position == null) return Result.DEFERRED;
        Mob body = bioform ? EntityType.ZOMBIE.create(level) : EntityType.VILLAGER.create(level);
        if (body == null) throw new IllegalStateException("Minecraft could not create a Frontier v3 ambient actor");
        body.setUUID(entityId); body.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D); body.setPersistenceRequired();
        // The body exists before the durable PREPARED -> HOT acknowledgement. Do not let
        // vanilla AI move it across that crash window.
        body.setNoAi(true);
        if (body instanceof Zombie zombie) configureBioform(zombie, bioformRole(state, actorId));
        body.setCustomName(FrontierV3ScenePresentation.actorName(state, actorId, bioform)); body.setCustomNameVisible(false);
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
                    ? AdmissionDiagnostic.indexed(expectedId, pending(runtime, expectedId) != null,
                    new BlockPosition(existing.getBlockX(), existing.getBlockY(), existing.getBlockZ()))
                    : AdmissionDiagnostic.conflict(expectedId);
        }
        BlockPos anchor = new BlockPos(location.position().x(), location.position().y(), location.position().z());
        if (!level.hasChunkAt(anchor)) return AdmissionDiagnostic.unloaded(expectedId);
        BlockPos position = FrontierV3StandingPosition.aboveFloor(level, location.position());
        if (position == null) return AdmissionDiagnostic.blocked(expectedId, new BlockPosition(anchor.getX(), anchor.getY(), anchor.getZ()));
        return AdmissionDiagnostic.ready(expectedId, new BlockPosition(position.getX(), position.getY(), position.getZ()));
    }

    /**
     * Bounded read-only evidence for a stalled assembly cursor.  The immutable cursor remains
     * authoritative; this only exposes the loaded physical cell that Minecraft is refusing to
     * traverse and never probes an unloaded chunk.
     */
    static java.util.Optional<AssemblyReadiness> assemblyReadiness(ServerLevel level, FrontierWorldState state, SubjectId operationId) {
        RouteOperation operation = state.operations().get(operationId);
        if (operation == null || operation.activeAssembly().isEmpty()) return java.util.Optional.empty();
        List<AssemblyMemberReadiness> members = operation.activeAssembly().orElseThrow().members().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).map(entry -> assemblyMemberReadiness(level, state, entry.getKey(), entry.getValue())).toList();
        return java.util.Optional.of(new AssemblyReadiness(members));
    }

    private static AssemblyMemberReadiness assemblyMemberReadiness(ServerLevel level, FrontierWorldState state, SubjectId actorId,
                                                                     OperationAssembly.Member member) {
        BlockPosition next = member.arrived() ? null : member.corridor().get(member.cursor() + 1);
        Entity body = level.getEntity(entityId(state, actorId));
        BlockPosition observed = body == null ? null : new BlockPosition(body.getBlockX(), body.getBlockY(), body.getBlockZ());
        ObservedPosition observedExact = body == null ? null : new ObservedPosition(body.getX(), body.getY(), body.getZ());
        if (next == null) return new AssemblyMemberReadiness(actorId, member.currentPosition(), null, observed, observedExact, "ARRIVED", "", "", "", "", List.of());
        BlockPos target = new BlockPos(next.x(), next.y(), next.z());
        if (!level.hasChunkAt(target)) {
            return new AssemblyMemberReadiness(actorId, member.currentPosition(), next, observed, observedExact, "UNLOADED", "", "", "", "", List.of());
        }
        List<String> occupants = level.getEntities((Entity) null, new AABB(target.getX(), target.getY(), target.getZ(),
                        target.getX() + 1.0D, target.getY() + 3.0D, target.getZ() + 1.0D), entity -> entity != body).stream()
                .sorted(java.util.Comparator.comparing(entity -> entity.getUUID().toString())).limit(4).map(FrontierV3AmbientActorExecutor::occupantKind).toList();
        String status = !FrontierV3StandingPosition.hasExactHeadroom(level, next) ? "BLOCKED" : occupants.isEmpty() ? "CLEAR" : "OCCUPIED";
        return new AssemblyMemberReadiness(actorId, member.currentPosition(), next, observed, observedExact, status,
                blockKind(level, target), blockKind(level, target.below()), blockKind(level, target.above()), blockKind(level, target.above(2)), occupants);
    }

    private static String blockKind(ServerLevel level, BlockPos position) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock()).toString();
    }

    private static String occupantKind(Entity entity) {
        String actorId = entity.getPersistentData().getString(ACTOR_KEY);
        return actorId.isBlank() ? BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString() : "frontier_actor";
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
                    || !lease.goalPosition().equals(journey.nextColdPosition())) {
                body.getNavigation().stop();
                return false;
            }
        }
        if (lease.goal() == AmbientGoalKind.SCOUT_PATROL && !lease.goalPosition().equals(state.actorLocations().get(actorId).position())) {
            // The command reducer owns the cursor transition; movement may only approach the
            // exact next lease target, never choose a local substitute after COLD hand-off.
            BlockPos physicalTarget = FrontierV3StandingPosition.aboveFloor(level, lease.goalPosition());
            if (physicalTarget == null) {
                body.getNavigation().stop();
                return false;
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
            BlockPosition obstruction = assemblyObstruction(level, state, operation, lease.goalPosition());
            if (obstruction != null) {
                OperationAssemblyDeferral deferral = new OperationAssemblyDeferral(actorId, lease.goalPosition(), obstruction,
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
        }
        FrontierV3ControlledMobMotion.moveToward(level, body, localTarget(state, actorId, lease, level.getGameTime()));
        return false;
    }

    static Vec3 localTarget(FrontierWorldState state, SubjectId actorId, AmbientActorLease lease, long gameTime) {
        if (directedGoal(lease)) {
            BlockPosition target = lease.goalPosition();
            return new Vec3(target.x() + 0.5D, target.y(), target.z() + 0.5D);
        }
        LocalBrain brain = localBrain(state, actorId);
        long cycle = Math.floorMod(gameTime, brain.periodTicks());
        double phase = (cycle / (double) brain.periodTicks()) + (brain.identityPhase() / 16.0D);
        double angle = phase * Math.PI * 2.0D;
        BlockPosition anchor = lease.handoffPosition();
        return new Vec3(anchor.x() + 0.5D + Math.cos(angle) * brain.radius(), anchor.y(), anchor.z() + 0.5D + Math.sin(angle) * brain.radius());
    }

    private static LocalBrain localBrain(FrontierWorldState state, SubjectId actorId) {
        int identityPhase = Math.floorMod(actorId.value().hashCode(), 16);
        ResidentProfile resident = state.humanPopulation().resident(actorId);
        if (resident != null) {
            return switch (resident.role()) {
                case FARMER -> new LocalBrain(1.00D, 360L, identityPhase);
                case BUILDER -> new LocalBrain(1.25D, 300L, identityPhase);
                case CRAFTER -> new LocalBrain(0.75D, 280L, identityPhase);
                case GUARD -> new LocalBrain(1.50D, 480L, identityPhase);
                case MEDIC -> new LocalBrain(0.50D, 240L, identityPhase);
                case HAULER -> new LocalBrain(1.50D, 320L, identityPhase);
            };
        }
        BioformRole role = bioformRole(state, actorId);
        return switch (role) {
            case WORKER -> new LocalBrain(1.00D, 320L, identityPhase);
            case SCOUT -> new LocalBrain(1.50D, 240L, identityPhase);
            case GUARD -> new LocalBrain(1.25D, 480L, identityPhase);
            case BOMBER -> new LocalBrain(1.50D, 300L, identityPhase);
        };
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
        ResidentMigrationJourney journey = state.humanPopulation().migration(actorId);
        if (state.ambientLeases().get(actorId).goal() == AmbientGoalKind.TRANSIT) {
            if (journey == null || !sameColumn(position, journey.currentPosition())) return false;
            // Canonical Transit positions use the entity's feet-cell convention.  Keep the
            // durable cursor authoritative rather than deriving a new vertical datum on drain.
            position = journey.currentPosition();
        }
        if (state.ambientLeases().get(actorId).goal() == AmbientGoalKind.OPERATION_ASSEMBLY) {
            OperationAssembly.Member member = assemblyMember(state, actorId, state.ambientLeases().get(actorId));
            if (member == null || !sameColumn(position, member.currentPosition())) return false;
            position = member.currentPosition();
        }
        if (state.ambientLeases().get(actorId).goal() == AmbientGoalKind.SCOUT_PATROL) {
            if (!(body.level() instanceof ServerLevel level) || !sameFloorAnchor(level, position, current.position())) return false;
            position = current.position();
        }
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
    private static boolean observeDirectedArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                                   SubjectId actorId, Mob body, AmbientActorLease lease) {
        if (lease.goal() == AmbientGoalKind.OPERATION_ASSEMBLY) return observeAssemblyArrival(level, runtime, state, actorId, body, lease);
        if (lease.goal() == AmbientGoalKind.SCOUT_PATROL) return observeScoutPatrolArrival(level, runtime, state, actorId, body, lease);
        if (lease.goal() != AmbientGoalKind.TRANSIT) return false;
        ResidentMigrationJourney journey = state.humanPopulation().migration(actorId);
        if (journey == null || journey.status() != ResidentMigrationStatus.EN_ROUTE || journey.arriving()
                || !lease.goalPosition().equals(journey.nextColdPosition())) return false;
        BlockPosition position = new BlockPosition(body.getBlockX(), body.getBlockY(), body.getBlockZ());
        if (!sameColumn(position, journey.nextColdPosition())) return false;
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-transit", actorId.value(),
                new ResidentTransitAdvanced(actorId, journey.nextRouteIndex()));
        FrontierV3DiagnosticTrace.record(level.getServer(), "resident-transit:" + actorId.value(), "resident_transit_advanced", actorId, result);
        return true;
    }
    private static boolean observeAssemblyArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                                   SubjectId actorId, Mob body, AmbientActorLease lease) {
        OperationAssembly.Member member = assemblyMember(state, actorId, lease);
        if (member == null || member.arrived() || !sameColumn(new BlockPosition(body.getBlockX(), body.getBlockY(), body.getBlockZ()), lease.goalPosition())) return false;
        RouteOperation operation = assemblingOperation(state, actorId); OperationAssembly assembly = operation.activeAssembly().orElseThrow();
        Map<SubjectId, OperationAssembly.Member> members = new LinkedHashMap<>(assembly.members());
        members.put(actorId, new OperationAssembly.Member(member.corridor(), member.cursor() + 1));
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-operation-assembly", actorId.value(),
                new OperationAssemblyAdvanced(operation.id(), new OperationAssembly(members, assembly.cargoCarrierId())));
        FrontierV3DiagnosticTrace.record(level.getServer(), "operation-assembly:" + operation.id().value(), "operation_assembly_advanced", actorId, result);
        return true;
    }
    private static boolean observeScoutPatrolArrival(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
                                                      SubjectId actorId, Mob body, AmbientActorLease lease) {
        BlockPosition current = state.actorLocations().get(actorId).position();
        if (!sameFloorAnchor(level, new BlockPosition(body.getBlockX(), body.getBlockY(), body.getBlockZ()), lease.goalPosition())) return false;
        io.farfrontier.palemirror.frontier.v3.api.CommandResult result = submit(runtime, "ambient-scout-patrol", actorId.value(),
                new ScoutPatrolAdvanced(actorId, runtime.checkpointImage().orElseThrow().instant().ticks(), lease.goalPosition(), java.util.Optional.of(current)));
        FrontierV3DiagnosticTrace.record(level.getServer(), "scout-patrol:" + actorId.value(), "scout_patrol_advanced", actorId, result);
        return true;
    }
    private static boolean directedGoal(AmbientActorLease lease) {
        return lease.goal() == AmbientGoalKind.TRANSIT || lease.goal() == AmbientGoalKind.OPERATION_ASSEMBLY || lease.goal() == AmbientGoalKind.SCOUT_PATROL;
    }
    private static RouteOperation assemblingOperation(FrontierWorldState state, SubjectId actorId) {
        return state.operations().values().stream().filter(operation -> operation.stage() == OperationStage.ASSEMBLING)
                .filter(operation -> operation.activeAssembly().map(assembly -> assembly.members().containsKey(actorId)).orElse(false))
                .findFirst().orElse(null);
    }
    /** Returns only one authored port floor that is presently unsafe, never an inferred nearby route. */
    private static BlockPosition assemblyObstruction(ServerLevel level, FrontierWorldState state, RouteOperation operation, BlockPosition target) {
        if (!FrontierV3StandingPosition.hasExactHeadroom(level, target)) return target;
        Settlement settlement = state.bootstrap().settlements().stream().filter(value -> value.id().equals(operation.settlementId())).findFirst().orElse(null);
        SettlementStructure hall = settlement == null ? null : settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL).findFirst().orElse(null);
        BlockPosition throat = hall == null ? null : SettlementAccessPort.forHall(hall).throatFloor();
        return throat != null && !FrontierV3StandingPosition.hasExactHeadroom(level, throat) ? throat : null;
    }
    private static OperationAssembly.Member assemblyMember(FrontierWorldState state, SubjectId actorId, AmbientActorLease lease) {
        RouteOperation operation = assemblingOperation(state, actorId);
        if (operation == null || lease.goal() != AmbientGoalKind.OPERATION_ASSEMBLY) return null;
        OperationAssembly.Member member = operation.activeAssembly().orElseThrow().members().get(actorId);
        BlockPosition expected = member.arrived() ? member.currentPosition() : member.corridor().get(member.cursor() + 1);
        return lease.goalPosition().equals(expected) ? member : null;
    }
    private static boolean sameColumn(BlockPosition physicalBodyCell, BlockPosition canonicalFloorCell) {
        return physicalBodyCell.x() == canonicalFloorCell.x() && physicalBodyCell.y() == canonicalFloorCell.y()
                && physicalBodyCell.z() == canonicalFloorCell.z();
    }
    /** A Scout cursor is a semantic floor anchor; a Minecraft body's feet must stand above it. */
    private static boolean sameFloorAnchor(ServerLevel level, BlockPosition physicalBodyCell, BlockPosition canonicalFloorAnchor) {
        BlockPos expected = FrontierV3StandingPosition.aboveFloor(level, canonicalFloorAnchor);
        return expected != null && physicalBodyCell.x() == expected.getX() && physicalBodyCell.y() == expected.getY()
                && physicalBodyCell.z() == expected.getZ();
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
    private static io.farfrontier.palemirror.frontier.v3.api.CommandResult submit(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                                                                    String phase, String id, FrontierPayload payload) {
        return FrontierV3CommandSubmission.submit(runtime, phase, id, payload);
    }
    private record PendingAdmission(Entity entity, long expiresAtGameTime) { }
    private record LocalBrain(double radius, long periodTicks, int identityPhase) { }
    record AdmissionDiagnostic(String status, UUID entityId, boolean pending, BlockPosition placement, BlockPosition observedPosition) {
        private static AdmissionDiagnostic notCanonical() { return new AdmissionDiagnostic("NOT_CANONICAL", null, false, null, null); }
        private static AdmissionDiagnostic indexed(UUID entityId, boolean pending, BlockPosition observedPosition) { return new AdmissionDiagnostic("INDEXED", entityId, pending, null, observedPosition); }
        private static AdmissionDiagnostic conflict(UUID entityId) { return new AdmissionDiagnostic("UUID_CONFLICT", entityId, false, null, null); }
        private static AdmissionDiagnostic unloaded(UUID entityId) { return new AdmissionDiagnostic("UNLOADED", entityId, false, null, null); }
        private static AdmissionDiagnostic blocked(UUID entityId, BlockPosition placement) { return new AdmissionDiagnostic("BLOCKED", entityId, false, placement, null); }
        private static AdmissionDiagnostic ready(UUID entityId, BlockPosition placement) { return new AdmissionDiagnostic("READY", entityId, false, placement, null); }
    }
    record AssemblyReadiness(List<AssemblyMemberReadiness> members) {
        AssemblyReadiness { members = List.copyOf(members); }
    }
    record ObservedPosition(double x, double y, double z) { }
    record AssemblyMemberReadiness(SubjectId actorId, BlockPosition current, BlockPosition next, BlockPosition observed, ObservedPosition observedExact,
                                   String targetStatus, String floorBlock, String supportBlock, String bodyBlock, String headBlock, List<String> occupants) {
        AssemblyMemberReadiness { occupants = List.copyOf(occupants); }
    }
    enum Result { APPLIED, CURRENT, PENDING, DEFERRED, CONFLICT }
}
