package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.BioformRole;
import io.farfrontier.palemirror.frontier.v3.model.OperationTravel;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.SceneEngagementCandidate;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.SceneStrikeObservation;
import io.farfrontier.palemirror.frontier.v3.model.ResidentRole;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import io.farfrontier.palemirror.frontier.v3.model.TraversalKind;
import io.farfrontier.palemirror.frontier.v3.model.TraversalTopology;
import io.farfrontier.palemirror.frontier.v3.model.TraversalTopologyId;
import io.farfrontier.palemirror.frontier.v3.model.TransportAnchor;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Materialized ownership and conflict evidence for exact v3 HOT scene bodies. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3SceneGameTests {
    private FrontierV3SceneGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-handoff", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void hotTravelTargetRetainsTheSurveyedOneBlockGrade(GameTestHelper helper) {
        SubjectId hauler = new SubjectId("resident:grade-hauler");
        TraversalTopology topology = TraversalTopology.corridor(new TraversalTopologyId("topology:scene-grade"), 1L,
                new SubjectId("route:frontier-network"), TraversalKind.PEDESTRIAN,
                java.util.Set.of(TraversalCapability.PEDESTRIAN, TraversalCapability.GROUND_BIOFORM),
                List.of(SurfaceAnchor.at(4, 63, 8), SurfaceAnchor.at(5, 64, 8)));
        OperationTravel travel = new OperationTravel(topology, 0, java.util.Map.of(hauler, new BodyPosition(4, 65, 9)),
                TransportAnchor.atLegacySupport(new BlockPosition(4, 63, 7)));

        helper.assertValueEqual(FrontierV3SceneExecutor.operationTravelTargetPosition(travel, hauler), new BodyPosition(5, 66, 9),
                "the HOT adapter must retain the next canonical grade instead of flattening its target Y");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-handoff", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void declaredPhysicalExecutionOrderIsBoundedAndReadOnly(GameTestHelper helper) {
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:physical-execution-plan"), 91L),
                        new EphemeralStore(), 20_000);
        try {
            String execution = FrontierV3PhysicalExecutionDiagnostic.render(runtime.checkpointImage().orElseThrow());
            helper.assertTrue(execution.contains("\"kind\":\"execution\"")
                            && execution.contains("\"id\":\"physical-observation\"")
                            && execution.contains("\"id\":\"scenes\""),
                    "the physical execution diagnostic must expose the closed registered plan");
            helper.assertTrue(execution.indexOf("\"id\":\"physical-observation\"") < execution.indexOf("\"id\":\"scenes\""),
                    "the read-only plan must preserve observation before scene execution");
            helper.assertTrue(execution.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 8_192,
                    "the execution plan diagnostic must remain bounded");
        } finally {
            runtime.shutdown();
        }
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-handoff", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void hotSceneWaitsForStableAbsenceAndSafeDistanceBeforeColdHandoff(GameTestHelper helper) {
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:scene-hysteresis-game-test"), 91L), new EphemeralStore(), 20_000);
        SceneLeaseId leaseId = new SceneLeaseId("lease:scene-hysteresis-game-test");
        try {
            helper.assertFalse(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 1L, false, false),
                    "the first absent-demand tick must retain a HOT scene instead of abruptly despawning it");
            helper.assertFalse(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 200L, false, false),
                    "the final tick before the bounded 200-tick hand-off window must remain HOT");
            helper.assertTrue(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 201L, false, false),
                    "only sustained absent demand may permit a COLD hand-off");
            helper.assertFalse(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 202L, true, false),
                    "returning player demand must cancel the pending drain rather than leaving a latent despawn");
            helper.assertFalse(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 203L, false, false),
                    "a fresh departure begins a new bounded hysteresis interval");
            helper.assertFalse(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 403L, false, true),
                    "an otherwise absent player near an exact scene body prevents unsafe capture");
            helper.assertTrue(FrontierV3SceneExecutor.drainAfterDemandHysteresis(runtime, leaseId, 404L, false, false),
                    "once safely distant after the hysteresis, the next durable release may proceed");
        } finally {
            FrontierV3SceneExecutor.forget(runtime);
            runtime.shutdown();
        }
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-restart-reclaim", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void restartRecoveryWaitsForBoundedEntityRegistrationAfterNaturalLoad(GameTestHelper helper) {
        long completeLoadTick = 40L;
        helper.assertFalse(FrontierV3SceneExecutor.restartRecoveryObservationReady(completeLoadTick, completeLoadTick),
                "the first naturally loaded tick is not yet evidence that saved entity UUIDs are absent");
        helper.assertFalse(FrontierV3SceneExecutor.restartRecoveryObservationReady(completeLoadTick, 59L),
                "recovery must not turn a short entity-registration delay into a durable missing-body fact");
        helper.assertTrue(FrontierV3SceneExecutor.restartRecoveryObservationReady(completeLoadTick, 60L),
                "after the fixed complete-load observation barrier, a missing owned UUID is durable recovery evidence");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-handoff", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void logisticsSceneNeverAcquiresCombatAuthorityWithoutAnEngagement(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(8, 8, 0));
        SceneLease logistics = lease(origin);
        SceneLease engagement = fixtureLease(new SceneLeaseId("lease:scene-combat-authority-game-test"), logistics.worldId(),
                FrontierSceneBehaviors.logistics(logistics).operationId(), FrontierSceneBehaviors.logistics(logistics).cargoId(),
                logistics.handoffPosition(), logistics.handoffInstant(), logistics.revision(), logistics.status(), Optional.of(new SubjectId("engagement:scene-combat-authority-game-test")), logistics.members());
        helper.assertFalse(FrontierV3SceneExecutor.combatEnabled(logistics),
                "a HOT route carrier may move exact cargo but must not invent combat against its own escort");
        helper.assertTrue(FrontierV3SceneExecutor.combatEnabled(engagement),
                "only a coupled canonical engagement lease may unlock the durable combat/effect executor");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-bodies", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedSceneCreatesOnlyItsExactVillagerBodies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 8, 0));
        prepareFloor(level, origin); prepareFloor(level, origin.east(2));
        // A route deck may physically occupy the strategic hand-off height.
        level.setBlock(origin, Blocks.GRAY_CARPET.defaultBlockState(), 3);
        WorldId world = new WorldId("frontier:scene-body-test");
        List<SceneMember> members = List.of(member(world, "resident:frontier-v3-test-hauler"), member(world, "resident:frontier-v3-test-guard"));
        SceneLease lease = SceneLease.atExactPositions(new SceneLeaseId("lease:frontier-v3-game-test"), world,
                new SubjectId("operation:frontier-v3-game-test"), new SubjectId("cargo:frontier-v3-game-test"),
                new BlockPosition(origin.getX(), origin.getY(), origin.getZ()), new BlockPosition(origin.getX() + 3, origin.getY() - 1, origin.getZ()),
                SimInstant.ZERO, 0L, SceneLeaseStatus.PREPARED, Optional.empty(), members,
                java.util.Map.of(members.getFirst().actorId(), new BodyPosition(origin.getX(), origin.getY() + 1, origin.getZ()),
                        members.getLast().actorId(), new BodyPosition(origin.getX() + 2, origin.getY(), origin.getZ())));

        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L));
        helper.assertFalse(FrontierV3SceneExecutor.entityStorageReady(true, false),
                "a production scene must wait for saved entity storage before admitting deterministic body UUIDs");
        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodiesForFixture(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                "a loaded thin route surface must materialize each deterministic Villager body exactly once");
        for (SceneMember member : lease.members()) {
            Villager body = (Villager) level.getEntity(member.entityId());
            helper.assertTrue(body != null, "each leased actor must have its deterministic Villager body");
            helper.assertValueEqual(body.getPersistentData().getString(FrontierV3SceneExecutor.LEASE_KEY), lease.id().value(),
                    "materialized body must carry its scene lease ownership");
            helper.assertValueEqual(body.getPersistentData().getString(FrontierV3SceneExecutor.ACTOR_KEY), member.actorId().value(),
                    "materialized body must carry its canonical actor identity");
            helper.assertTrue(body.isNoAi(), "a HOT body must not retain uncontrolled vanilla AI or combat authority");
            if (member.equals(lease.members().getFirst())) {
                helper.assertTrue(!body.blockPosition().equals(origin), "a scene body must stand above a loaded route deck, never inside it");
            }
            body.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-bodies", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 60)
    public static void activeSceneBodiesCarryStrictGrayboxAdmissionProof(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(0, 8, 0));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(new WorldId("frontier:scene-admission-proof"), 91L), new EphemeralStore(), 20_000);
        SceneEngagementCandidate candidate = state(runtime).coldEngagementSceneCandidates().getFirst();
        SceneLeaseId leaseId = new SceneLeaseId("lease:scene-admission-proof");
        var checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("the admission fixture runtime must remain active"));
        SceneLease lease = FrontierV3GameTestSceneLeases.exact(state(runtime), checkpoint, candidate, leaseId);
        FrontierV3CommandSubmission.submit(runtime, "scene-admission-proof-prepare", leaseId.value(), new SceneLeasePrepared(lease));
        List<net.minecraft.world.entity.Mob> admittedBodies = new java.util.ArrayList<>();
        for (int index = 0; index < lease.members().size(); index++) {
            BlockPos position = origin.offset((index % 2) * 2, 0, (index / 2) * 2);
            prepareFloor(level, position);
            admittedBodies.add(addOwnedBody(helper, level, state(runtime), lease, lease.members().get(index), position));
        }
        // addFreshEntity is accepted on this server tick, but under the parallel GameTest
        // runner its UUID index may settle one more tick later.  Retain the exact references
        // and assert both their continued liveness and their indexed identities; the wait does
        // not create/repair a body or affect canonical state.
        helper.runAfterDelay(2L, () -> {
            try {
                for (int index = 0; index < lease.members().size(); index++) {
                    SceneMember member = lease.members().get(index);
                    Entity created = admittedBodies.get(index);
                    Entity body = level.getEntity(member.entityId());
                    helper.assertTrue(!created.isRemoved() && body == created && FrontierV3SceneExecutor.recognizes(runtime, body),
                            "only a body whose UUID, kind, actor, lease and revision match an active canonical scene may pass Graybox admission: "
                                    + admissionDetail(runtime, member, body));
                }
                Zombie foreign = EntityType.ZOMBIE.create(level);
                helper.assertTrue(foreign != null && !FrontierV3SceneExecutor.recognizes(runtime, foreign),
                        "an untagged native mob must not acquire a scene admission proof");
                FrontierV3CommandSubmission.submit(runtime, "scene-admission-proof-hot", leaseId.value(), new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));
                FrontierV3CommandSubmission.submit(runtime, "scene-admission-proof-drain", leaseId.value(), new SceneLeaseTransition(leaseId, SceneLeaseStatus.DRAINING));
                FrontierV3CommandSubmission.submit(runtime, "scene-admission-proof-close", leaseId.value(), new io.farfrontier.palemirror.frontier.v3.model.SceneLeaseReleased(leaseId,
                        lease.members().stream().map(member -> new io.farfrontier.palemirror.frontier.v3.model.SceneMemberPosition(member.actorId(), lease.memberPosition(member.actorId()))).toList()));
                Entity formerBody = level.getEntity(lease.members().getFirst().entityId());
                helper.assertTrue(formerBody != null && !FrontierV3SceneExecutor.recognizes(runtime, formerBody),
                        "a stale body from a closed scene must be denied rather than retained as a permanent exception");
                FrontierV3SceneExecutor.cleanClosedBodies(level, state(runtime));
                helper.assertTrue(level.getEntity(lease.members().getFirst().entityId()) == null,
                        "the shared scene lifecycle must discard a closed projection before another behavior can inherit its lease tag");
                helper.succeed();
            } finally {
                lease.members().forEach(member -> { Entity body = level.getEntity(member.entityId()); if (body != null) body.discard(); });
                runtime.shutdown();
            }
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-conflict", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedSceneRefusesForeignBodyWithItsExpectedUuid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 8, 0));
        SceneLease lease = lease(origin);
        Villager foreign = EntityType.VILLAGER.create(level);
        helper.assertTrue(foreign != null, "the foreign body fixture must be constructible");
        foreign.setUUID(lease.members().getFirst().entityId());
        foreign.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        helper.assertTrue(level.addFreshEntity(foreign), "the foreign body fixture must enter the loaded world");

        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scene-conflict-test"), 91L));
        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodiesForFixture(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.CONFLICT,
                "an unowned body with a leased UUID is a visible conflict, never a body the executor claims");
        helper.assertTrue(level.getEntity(foreign.getUUID()) == foreign, "the foreign body must remain untouched");
        foreign.discard();
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-bodies", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedSceneUsesCanonicalBioformIdentityForZombieBodies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(16, 8, 0)); prepareFloor(level, origin);
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scene-bioform-test"), 91L));
        SceneLeaseId id = new SceneLeaseId("lease:frontier-v3-bioform-test");
        SubjectId bioform = new SubjectId("bioform:west-0");
        SceneLease lease = fixtureLease(id, state.bootstrap().worldId(), new SubjectId("operation:frontier-v3-bioform-test"), new SubjectId("cargo:frontier-v3-bioform-test"),
                new BlockPosition(origin.getX(), origin.getY(), origin.getZ()), SimInstant.ZERO, 0L, SceneLeaseStatus.PREPARED, Optional.empty(),
                List.of(new SceneMember(bioform, SceneLease.deterministicEntityId(state.bootstrap().worldId(), bioform))));

        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodiesForFixture(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                "a canonical hive participant must materialize as its graybox Zombie, never as a Villager");
        Entity entity = level.getEntity(lease.members().getFirst().entityId());
        helper.assertTrue(entity instanceof Zombie, "the scene body must retain the canonical bioform kind");
        helper.assertTrue(((Zombie) entity).hasEffect(MobEffects.FIRE_RESISTANCE),
                "a graybox hive bioform must survive daylight without becoming an unaccounted vanilla death");
        helper.assertTrue(((Zombie) entity).getItemBySlot(EquipmentSlot.HEAD).is(Items.LIME_WOOL)
                        && !((Zombie) entity).getItemBySlot(EquipmentSlot.HEAD).isDamageableItem(),
                "a WORKER bioform must carry its unbreakable lime role marker to prevent vanilla daylight ignition");
        entity.discard();
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-bodies", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedEngagementSceneKeepsBothSidesAsExactBodies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(24, 8, 0)); prepareFloor(level, origin); prepareFloor(level, origin.east(2));
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:engagement-scene-bodies"), 91L));
        SceneLeaseId id = new SceneLeaseId("lease:frontier-v3-engagement-bodies"); SubjectId resident = new SubjectId("resident:1-1"), bioform = new SubjectId("bioform:west-0");
        SceneLease lease = fixtureLease(id, state.bootstrap().worldId(), new SubjectId("operation:frontier-v3-engagement-bodies"), new SubjectId("cargo:frontier-v3-engagement-bodies"),
                new BlockPosition(origin.getX(), origin.getY(), origin.getZ()), SimInstant.ZERO, 0L, SceneLeaseStatus.PREPARED,
                Optional.of(new SubjectId("engagement:frontier-v3-game-test")), List.of(new SceneMember(resident, SceneLease.deterministicEntityId(state.bootstrap().worldId(), resident)),
                        new SceneMember(bioform, SceneLease.deterministicEntityId(state.bootstrap().worldId(), bioform))));
        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodiesForFixture(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                "a loaded engagement scene must materialize both exact human and hive members without a second actor set");
        helper.assertTrue(level.getEntity(lease.members().getFirst().entityId()) instanceof Villager, "engagement resident remains one Villager");
        helper.assertTrue(level.getEntity(lease.members().getLast().entityId()) instanceof Zombie, "engagement bioform remains one Zombie");
        lease.members().forEach(member -> level.getEntity(member.entityId()).discard()); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-ambient-actors", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void ambientActorsKeepExactIdAndUseVillagerOrZombieKind(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:ambient-test"), 91L));
        SubjectId resident = new SubjectId("resident:1-1"); SubjectId bioform = new SubjectId("bioform:west-0");
        BlockPos residentSpot = helper.absolutePos(new BlockPos(4, 8, 0)); BlockPos bioformSpot = helper.absolutePos(new BlockPos(8, 8, 0));
        prepareFloor(level, residentSpot); prepareFloor(level, bioformSpot);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, resident,
                        new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(residentSpot.getX(), residentSpot.getY(), residentSpot.getZ())), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "an exact resident receives one owned Villager body");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, bioform,
                        new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(bioformSpot.getX(), bioformSpot.getY(), bioformSpot.getZ())), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "an exact hive bioform receives one owned Zombie body");
        helper.assertTrue(level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, resident)) instanceof Villager, "resident identity maps to Villager");
        helper.assertTrue(level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, bioform)) instanceof net.minecraft.world.entity.monster.Zombie, "bioform identity maps to Zombie");
        helper.assertTrue(((Zombie) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, bioform))).hasEffect(MobEffects.FIRE_RESISTANCE),
                "an ambient graybox bioform must survive daylight without becoming an unaccounted vanilla death");
        helper.assertTrue(!((Zombie) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, bioform))).getItemBySlot(EquipmentSlot.HEAD).isEmpty(),
                "an ambient bioform has a physical role marker that suppresses vanilla daylight flames");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, resident,
                        new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(residentSpot.getX(), residentSpot.getY(), residentSpot.getZ())), FrontierV3AmbientActorExecutor.Result.CURRENT,
                "a repeated loaded-chunk pass never duplicates the exact resident");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, new SubjectId("resident:unknown"),
                        new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(residentSpot.getX(), residentSpot.getY(), residentSpot.getZ())), FrontierV3AmbientActorExecutor.Result.CONFLICT,
                "an unknown canonical identity is never converted into a new Villager body");
        level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, resident)).discard();
        level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, bioform)).discard();
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-handoff", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void ambientBodyTransfersIntoSceneWithoutCloneOrReplacement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(20, 8, 0)); prepareFloor(level, origin);
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scene-handoff-body-test"), 91L));
        SubjectId resident = new SubjectId("resident:1-1");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, resident,
                        new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(origin.getX(), origin.getY(), origin.getZ())), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "the HOT ambient resident must be present before transfer");
        Entity original = level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, resident));
        SceneLeaseId id = new SceneLeaseId("lease:frontier-v3-ambient-transfer");
        SceneLease lease = fixtureLease(id, state.bootstrap().worldId(), new SubjectId("operation:frontier-v3-ambient-transfer"), new SubjectId("cargo:frontier-v3-ambient-transfer"),
                new BlockPosition(origin.getX(), origin.getY(), origin.getZ()), SimInstant.ZERO, 0L, SceneLeaseStatus.PREPARED, Optional.empty(),
                List.of(new SceneMember(resident, SceneLease.deterministicEntityId(state.bootstrap().worldId(), resident))));

        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodiesForFixture(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                "a prepared scene must adopt its exact ambient body rather than recreate it");
        Entity transferred = level.getEntity(lease.members().getFirst().entityId());
        helper.assertTrue(transferred == original, "the transferred Villager must keep its exact Minecraft entity instance and UUID");
        helper.assertValueEqual(transferred.getPersistentData().getString(FrontierV3SceneExecutor.LEASE_KEY), lease.id().value(),
                "the same body must now carry scene authority");
        helper.assertTrue(!FrontierV3AmbientActorExecutor.owned(transferred, resident, false), "ambient ownership markers must not survive the transfer");
        helper.assertTrue(((Villager) transferred).isNoAi(), "the transferred body must enter controlled scene execution");
        transferred.discard(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-ambient-restart-reclaim", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void restoredOwnedBodyReclaimsUnknownAmbientLeaseWithoutDuplication(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(12, 8, 0)); prepareFloor(level, origin);
        WorldId world = new WorldId("frontier:ambient-reclaim-game-test");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(world, 91L), new EphemeralStore(), 10_000);
        SubjectId resident = new SubjectId("resident:1-1");
        FrontierWorldState initial = state(runtime);
        AmbientActorLease lease = AmbientActorProcess.nextLease(initial, resident, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "ambient-game-test-prepare", resident.value(), new AmbientLeasePrepared(lease));
        FrontierV3CommandSubmission.submit(runtime, "ambient-game-test-hot", resident.value(), new AmbientLeaseTransition(resident, AmbientLeaseStatus.HOT));
        helper.assertValueEqual(FrontierV3AmbientLeaseRestartSafety.quarantineActiveLeases(runtime), 1,
                "restart recovery must make an active ambient lease UNKNOWN before any body is accepted");

        Villager restored = EntityType.VILLAGER.create(level);
        helper.assertTrue(restored != null, "the restored owned-body fixture must be constructible");
        restored.setUUID(FrontierV3AmbientActorExecutor.entityId(initial, resident));
        restored.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        restored.getPersistentData().putString(FrontierV3AmbientActorExecutor.ACTOR_KEY, resident.value());
        restored.getPersistentData().putString(FrontierV3AmbientActorExecutor.KIND_KEY, "RESIDENT");
        helper.assertTrue(level.addFreshEntity(restored), "the restored body fixture must enter the loaded world");

        helper.assertValueEqual(FrontierV3AmbientActorExecutor.observeJoin(runtime, restored), FrontierV3AmbientActorExecutor.JoinDisposition.RETAINED,
                "only the exact loaded owned body may reclaim an UNKNOWN ambient lease");
        helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.HOT,
                "loaded-world reclaim must make the same canonical lease HOT");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), resident,
                        new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(origin.getX(), origin.getY(), origin.getZ())), FrontierV3AmbientActorExecutor.Result.CURRENT,
                "reclaim must retain the existing body instead of creating another one");
        helper.assertTrue(level.getEntity(restored.getUUID()) == restored, "the observed restored body remains the sole UUID owner");
        FrontierV3AmbientActorExecutor.forget(runtime);
        restored.discard();
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-restart-reclaim", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void completeOwnedSceneReclaimsAfterRestartAndMissingBodyStaysUnknown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(20, 8, 0));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(new WorldId("frontier:scene-reclaim-game-test"), 91L), new EphemeralStore(), 20_000);
        SceneEngagementCandidate candidate = state(runtime).coldEngagementSceneCandidates().getFirst(); SceneLeaseId leaseId = new SceneLeaseId("lease:scene-reclaim-game-test");
        var checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("the scene reclaim fixture runtime must remain active"));
        SceneLease lease = FrontierV3GameTestSceneLeases.exact(state(runtime), checkpoint, candidate, leaseId);
        FrontierV3CommandSubmission.submit(runtime, "scene-reclaim-lease-prepare", leaseId.value(), new SceneLeasePrepared(lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-reclaim-lease-hot", leaseId.value(), new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));
        for (int index = 0; index < lease.members().size(); index++) {
            BlockPos position = origin.offset(index & 1, 0, index / 2); prepareFloor(level, position); addOwnedBody(helper, level, state(runtime), lease, lease.members().get(index), position);
        }
        BlockPos cargo = cargoPosition(origin, lease); prepareFloor(level, cargo);
        MinecartChest carrier = addOwnedCarrier(helper, level, state(runtime), lease, cargo);
        helper.runAfterDelay(1L, () -> {
        helper.assertValueEqual(FrontierV3SceneLeaseRestartSafety.quarantineActiveLeases(runtime), 1,
                "restart recovery must first retain the active scene as UNKNOWN");
        helper.assertTrue(FrontierV3SceneExecutor.reclaimObservedBodies(level, runtime, state(runtime), lease),
                "the loaded complete exact scene body set must be eligible for reclaim after demand admission");
        helper.assertValueEqual(state(runtime).sceneLeases().get(leaseId).status(), SceneLeaseStatus.HOT,
                "only the complete exact owned scene body set may reclaim HOT");
        for (SceneMember member : lease.members()) helper.assertTrue(level.getEntity(member.entityId()) != null,
                "scene reclaim must retain each original UUID instead of creating a substitute body");

        helper.assertValueEqual(FrontierV3SceneLeaseRestartSafety.quarantineActiveLeases(runtime), 1,
                "the reclaimed scene must return to UNKNOWN exactly once on a later restart");
        Entity missing = level.getEntity(lease.members().getLast().entityId()); helper.assertTrue(missing != null, "fixture must retain a body to remove"); missing.discard();
        helper.assertTrue(!FrontierV3SceneExecutor.reclaimObservedBodies(level, runtime, state(runtime), lease),
                "a partial observed body set must be ineligible for scene reclaim");
        helper.assertValueEqual(state(runtime).sceneLeases().get(leaseId).status(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART,
                "a missing exact scene body must remain visible UNKNOWN and never be recreated during reclaim");
        lease.members().forEach(member -> { Entity body = level.getEntity(member.entityId()); if (body != null) body.discard(); });
        if (!carrier.isRemoved()) carrier.discard();
        runtime.shutdown(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-strikes", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void durableHotStrikeHurtsExactBodyAndNeverReplaysUnknownEffect(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(4, 8, 4)); prepareFloor(level, origin); prepareFloor(level, origin.east());
        prepareFloor(level, origin.east(8));
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(new WorldId("frontier:scene-strike-game-test"), 91L), new EphemeralStore(), 20_000);
        SceneEngagementCandidate candidate = state(runtime).coldEngagementSceneCandidates().getFirst();
        SceneLeaseId leaseId = new SceneLeaseId("lease:scene-strike-game-test");
        var checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("the strike fixture runtime must remain active"));
        SceneLease lease = FrontierV3GameTestSceneLeases.exact(state(runtime), checkpoint, candidate, leaseId);
        FrontierV3CommandSubmission.submit(runtime, "scene-strike-lease-prepare", leaseId.value(), new SceneLeasePrepared(lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-strike-lease-hot", leaseId.value(), new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));
        for (int index = 0; index < lease.members().size(); index++) {
            addOwnedBody(helper, level, state(runtime), lease, lease.members().get(index), origin.offset(index & 1, 0, index / 2));
        }

        Zombie attacker = lease.members().stream().map(member -> level.getEntity(member.entityId())).filter(Zombie.class::isInstance).map(Zombie.class::cast)
                .findFirst().orElseThrow(() -> new IllegalStateException("the exact scene fixture did not retain one Zombie attacker"));
        SubjectId guardId = state(runtime).bootstrap().settlements().stream().flatMap(settlement -> settlement.residents().stream())
                .filter(resident -> resident.role() == ResidentRole.GUARD).map(resident -> resident.id()).filter(actor -> lease.members().stream()
                        .anyMatch(member -> member.actorId().equals(actor))).findFirst().orElseThrow(() -> new IllegalStateException("the exact scene fixture did not retain one resident guard"));
        Villager target = lease.members().stream().filter(member -> !member.actorId().equals(guardId)).map(member -> level.getEntity(member.entityId())).filter(Villager.class::isInstance).map(Villager.class::cast)
                .findFirst().orElseThrow(() -> new IllegalStateException("the exact scene fixture did not retain one Villager target"));
        Villager guard = (Villager) level.getEntity(lease.members().stream().filter(member -> member.actorId().equals(guardId)).findFirst().orElseThrow().entityId());
        helper.assertTrue(guard != null, "the exact scene fixture must materialize its resident guard");
        attacker.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D); target.setPos(origin.getX() + 1.25D, origin.getY(), origin.getZ() + 0.5D);

        FrontierV3SceneExecutor.executeStrike(level, runtime, state(runtime), lease);
        PhysicalIntent first = onlyStrike(state(runtime));
        helper.assertValueEqual(first.status(), PhysicalIntentStatus.PREPARED, "a scene strike must be durable before any Minecraft damage");
        FrontierV3SceneExecutor.executeStrike(level, runtime, state(runtime), lease);
        helper.assertValueEqual(onlyStrike(state(runtime)).status(), PhysicalIntentStatus.RUNNING, "the durable strike must enter RUNNING before its physical hit");
        float healthBefore = target.getHealth();
        FrontierV3SceneExecutor.executeStrike(level, runtime, state(runtime), lease);
        PhysicalIntent confirmed = onlyStrike(state(runtime));
        SceneStrikeObservation receipt = (SceneStrikeObservation) state(runtime).physicalObservations().get(confirmed.postconditionObservationId()
                .orElseThrow(() -> new IllegalStateException("a confirmed physical scene strike must retain its receipt identity")));
        helper.assertValueEqual(confirmed.status(), PhysicalIntentStatus.CONFIRMED, "the observed hit must durably confirm its exact intent");
        helper.assertTrue(target.getHealth() < healthBefore, "only the real owned Villager must take the executor's Minecraft damage");
        helper.assertValueEqual(receipt.targetHealthBefore(), new FixedScalar(Math.round(healthBefore * FixedScalar.SCALE)), "receipt must retain the exact physical pre-hit health");
        helper.assertValueEqual(receipt.targetHealthAfter(), new FixedScalar(Math.round(target.getHealth() * FixedScalar.SCALE)), "receipt must retain the exact physical post-hit health");

        target.setPos(origin.getX() + 8.5D, origin.getY(), origin.getZ() + 0.5D);
        guard.setPos(origin.getX() + 1.25D, origin.getY(), origin.getZ() + 0.5D);
        FrontierV3SceneExecutor.executeStrike(level, runtime, state(runtime), lease);
        PhysicalIntent counterStrike = pendingStrike(state(runtime), lease);
        helper.assertTrue(counterStrike.subjectIds().getFirst().value().startsWith("resident:"),
                "the exact resident guard must receive the alternating defensive HOT strike");
        FrontierV3SceneExecutor.executeStrike(level, runtime, state(runtime), lease);
        helper.assertValueEqual(FrontierV3PhysicalIntentRestartSafety.quarantineUninspectableRunningIntents(runtime), 1,
                "restart recovery must quarantine one unresolved physical strike");
        int retainedIntentCount = state(runtime).physicalIntents().size();
        FrontierV3SceneExecutor.executeStrike(level, runtime, state(runtime), lease);
        helper.assertValueEqual(state(runtime).physicalIntents().size(), retainedIntentCount,
                "an unknown strike must remain visible and prevent a new same-pair hit after restart");
        helper.assertTrue(state(runtime).physicalIntents().values().stream().anyMatch(intent -> intent.kind() == PhysicalIntentKind.SCENE_STRIKE
                && intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART), "recovery must retain the unresolved strike as explicit canonical evidence");
        lease.members().forEach(member -> {
            Entity body = level.getEntity(member.entityId());
            if (body != null) body.discard();
        });
        runtime.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-explosion", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void hotBomberMaterializesOneOwnedTntAndDoesNotReplayItsDisappearance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(48, 8, 0));
        // GameTest cells run concurrently in one Minecraft level. Every physical UUID derives
        // from canonical identity, so this fixture must derive its world/lease identity from
        // its own stable cell instead of competing with another TNT scene for the same body.
        String fixture = "scene-explosion-game-test-" + origin.getX() + "-" + origin.getZ();
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(new WorldId("frontier:" + fixture), 91L), new EphemeralStore(), 20_000);
        SceneEngagementCandidate candidate = state(runtime).coldEngagementSceneCandidates().getFirst(); SceneLeaseId leaseId = new SceneLeaseId("lease:" + fixture);
        var checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("the explosion fixture runtime must remain active"));
        SceneLease lease = FrontierV3GameTestSceneLeases.exact(state(runtime), checkpoint, candidate, leaseId);
        FrontierV3CommandSubmission.submit(runtime, "scene-explosion-lease-prepare", leaseId.value(), new SceneLeasePrepared(lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-explosion-lease-hot", leaseId.value(), new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));
        for (int index = 0; index < lease.members().size(); index++) {
            BlockPos position = origin.offset(index & 1, 0, index / 2); prepareFloor(level, position); addOwnedBody(helper, level, state(runtime), lease, lease.members().get(index), position);
        }
        SubjectId bomber = lease.members().stream().map(SceneMember::actorId).filter(actor -> state(runtime).bootstrap().hive().bioforms().stream()
                .anyMatch(bioform -> bioform.id().equals(actor) && bioform.role() == BioformRole.BOMBER)).findFirst().orElseThrow();
        helper.runAfterDelay(1L, () -> {
            Entity bomberBody = level.getEntity(lease.members().stream().filter(member -> member.actorId().equals(bomber)).findFirst().orElseThrow().entityId());
            helper.assertTrue(bomberBody != null, "the exact HOT bomber body must be materialized");
            bomberBody.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
            lease.members().stream().filter(member -> member.actorId().value().startsWith("resident:")).findFirst().map(SceneMember::entityId).map(level::getEntity)
                    .ifPresent(body -> body.setPos(origin.getX() + 1.25D, origin.getY(), origin.getZ() + 0.5D));
            helper.assertTrue(FrontierV3SceneExecutor.executeExplosion(level, runtime, state(runtime), lease), "a nearby owned HOT bomber must prepare a blast instead of inventing a non-durable hit");
            PhysicalIntent intent = state(runtime).physicalIntents().values().stream().filter(value -> value.kind() == PhysicalIntentKind.EXPLOSION).findFirst()
                    .orElseThrow(() -> new IllegalStateException("the hot bomber did not retain its explosion intent"));
            helper.assertValueEqual(intent.status(), PhysicalIntentStatus.PREPARED, "the blast must be durable before Minecraft receives it");
            helper.assertValueEqual(intent.causeSubjectId(), bomber, "the receipt must retain the exact canonical bomber identity");
            helper.assertValueEqual(FrontierV3SceneExecutor.explosionCause(level, state(runtime), intent).orElseThrow(), bomberBody,
                    "only the matching tagged HOT zombie may become the Minecraft explosion source");
            FrontierV3ExplosionExecutor.tick(level, runtime);
            PhysicalIntent running = state(runtime).physicalIntents().get(intent.id());
            helper.assertValueEqual(running.status(), PhysicalIntentStatus.RUNNING, "the durable intent must run before its physical TNT body enters the world");
            Entity physicalBomb = level.getEntity(FrontierV3BomberBomb.entityId(intent.id()));
            helper.assertTrue(physicalBomb instanceof net.minecraft.world.entity.item.PrimedTnt
                            && FrontierV3BomberBomb.isCurrent(physicalBomb, running),
                    "the exact intent must materialize one tagged ordinary PrimedTnt body, never an immediate synthetic blast");
            helper.assertValueEqual(FrontierV3PhysicalIntentRestartSafety.quarantineUninspectableRunningIntents(runtime, level), 0,
                    "restart recovery must retain the exact loaded owned TNT for postcondition inspection instead of replaying or quarantining it");
            physicalBomb.discard();
            helper.assertValueEqual(FrontierV3PhysicalIntentRestartSafety.quarantineUninspectableRunningIntents(runtime, level), 1,
                    "a missing bomb in an already loaded chunk must become visible unknown at restart, never be recreated");
            helper.assertValueEqual(state(runtime).physicalIntents().get(intent.id()).status(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART,
                    "a missing loaded-world bomb becomes visible unknown evidence and is never spawned or exploded again");
            lease.members().forEach(member -> { Entity body = level.getEntity(member.entityId()); if (body != null) body.discard(); });
            runtime.shutdown(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-explosion-live", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void hotBomberBlastUsesRealTntEventAndRetainsPostImpactInspection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(4, 8, 0));
        // The dedicated server runs fixture cells in parallel.  Scene-body UUIDs deliberately
        // derive from WorldId + actor, so a fixed fixture WorldId would collide with another
        // concurrent test using the same bootstrap actors.  The physical cell is stable for
        // this run and makes the fixture identity isolated without changing production IDs.
        String fixture = "scene-real-explosion-game-test-" + origin.getX() + "-" + origin.getZ();
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(new WorldId("frontier:" + fixture), 91L), new EphemeralStore(), 20_000);
        SceneEngagementCandidate candidate = state(runtime).coldEngagementSceneCandidates().getFirst(); SceneLeaseId leaseId = new SceneLeaseId("lease:" + fixture);
        var checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("the real-blast fixture runtime must remain active"));
        SceneLease lease = FrontierV3GameTestSceneLeases.exact(state(runtime), checkpoint, candidate, leaseId);
        FrontierV3CommandSubmission.submit(runtime, "scene-real-explosion-lease-prepare", leaseId.value(), new SceneLeasePrepared(lease));
        FrontierV3CommandSubmission.submit(runtime, "scene-real-explosion-lease-hot", leaseId.value(), new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));
        // The regular graybox is flat; the bare GameTest template is not.  Give the ballistic
        // vanilla TNT the same supported ground instead of allowing it to fall out of the fixture.
        for (int x = -2; x <= 8; x++) for (int z = -2; z <= 4; z++) prepareFloor(level, origin.offset(x, 0, z));
        for (int index = 0; index < lease.members().size(); index++) {
            BlockPos position = origin.offset(index & 1, 0, index / 2);
            prepareFloor(level, position);
            addOwnedBody(helper, level, state(runtime), lease, lease.members().get(index), position);
        }
        BlockPos blastTarget = origin.east(3); prepareFloor(level, blastTarget); level.setBlock(blastTarget, Blocks.STONE.defaultBlockState(), 3);
        AtomicBoolean active = new AtomicBoolean(true), captured = new AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<String> detonationSource = new java.util.concurrent.atomic.AtomicReference<>("no detonation event");
        Consumer<ExplosionEvent.Detonate> listener = event -> {
            if (active.get() && event.getLevel() == level && FrontierV3ExplosionExecutionScope.currentIntent().isPresent()) {
                Entity direct = event.getExplosion().getDirectSourceEntity();
                detonationSource.set(direct == null ? "null" : direct.getType().toString() + ":" + direct.getUUID() + ":" + direct.getPersistentData().getString(FrontierV3BomberBomb.INTENT_KEY));
                captured.set(FrontierV3ServerLifecycle.observeExplosion(level, runtime, event.getExplosion(), event.getAffectedBlocks(), event.getAffectedEntities()));
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ExplosionEvent.Detonate.class, listener);
        // In the full parallel suite entity indexing can lag one GameTest callback.  This
        // fixture uses a deterministic UUID and needs the real indexed body, so wait two
        // ordinary server ticks instead of asserting an incidental one-tick race.
        helper.runAfterDelay(2L, () -> {
            try {
                SubjectId bomber = lease.members().stream().map(SceneMember::actorId).filter(actor -> state(runtime).bootstrap().hive().bioforms().stream()
                        .anyMatch(bioform -> bioform.id().equals(actor) && bioform.role() == BioformRole.BOMBER)).findFirst().orElseThrow();
                Entity bomberBody = level.getEntity(lease.members().stream().filter(member -> member.actorId().equals(bomber)).findFirst().orElseThrow().entityId());
                helper.assertTrue(bomberBody != null, "the real TNT fixture must retain its exact bomber body"); bomberBody.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
                lease.members().stream().filter(member -> member.actorId().value().startsWith("resident:")).findFirst().map(SceneMember::entityId).map(level::getEntity)
                        .ifPresent(body -> body.setPos(origin.getX() + 1.25D, origin.getY(), origin.getZ() + 0.5D));
                helper.assertTrue(FrontierV3SceneExecutor.executeExplosion(level, runtime, state(runtime), lease), "the hot scene must durably prepare its real blast");
                PhysicalIntent intent = state(runtime).physicalIntents().values().stream().filter(value -> value.kind() == PhysicalIntentKind.EXPLOSION).findFirst().orElseThrow();
                FrontierV3ExplosionExecutor.tick(level, runtime);
                Entity bomb = level.getEntity(FrontierV3BomberBomb.entityId(intent.id()));
                helper.assertTrue(bomb instanceof net.minecraft.world.entity.item.PrimedTnt && FrontierV3BomberBomb.isCurrent(bomb, state(runtime).physicalIntents().get(intent.id())),
                        "the hot bomber must release a visible normal TNT entity before it detonates");
                helper.assertFalse(captured.get(), "TNT admission itself is not an invented detonation observation");
                helper.assertValueEqual(state(runtime).physicalIntents().get(intent.id()).status(), PhysicalIntentStatus.RUNNING, "the blast stays running until real-world reconciliation completes");
                ((net.minecraft.world.entity.item.PrimedTnt) bomb).setFuse(1);
                ((net.minecraft.world.entity.item.PrimedTnt) bomb).tick();
                FrontierV3ManagedExplosionLedger retained = FrontierV3ManagedExplosionLedger.get(level);
                helper.assertTrue(captured.get(), "the normal TNT tick must enter the v3 observation bridge; source=" + detonationSource.get());
                helper.assertTrue(retained.has(intent.id()), "post-impact inspection must be persisted before receipt confirmation");
                helper.runAfterDelay(2L, () -> {
                    try {
                        var pendingEntity = retained.nextEntity(intent.id(), level.getGameTime());
                        helper.assertTrue(pendingEntity.isPresent(), "a real unloaded post-impact entity remains explicit inspection work, never a guessed death");
                        helper.assertValueEqual(state(runtime).physicalIntents().get(intent.id()).status(), PhysicalIntentStatus.RUNNING,
                                "the out-of-profile test world cannot fabricate canonical confirmation");
                        helper.assertTrue(!level.getBlockState(blastTarget).equals(Blocks.STONE.defaultBlockState()), "ordinary TNT geometry must alter an unprotected nearby block");
                        lease.members().forEach(member -> { Entity body = level.getEntity(member.entityId()); if (body != null) body.discard(); });
                        active.set(false); NeoForge.EVENT_BUS.unregister(listener); runtime.shutdown(); helper.succeed();
                    } catch (RuntimeException failure) {
                        active.set(false); NeoForge.EVENT_BUS.unregister(listener); runtime.shutdown(); throw failure;
                    }
                });
            } catch (RuntimeException failure) {
                active.set(false); NeoForge.EVENT_BUS.unregister(listener); runtime.shutdown(); throw failure;
            }
        });
    }

    private static SceneLease lease(BlockPos origin) {
        SceneLeaseId id = new SceneLeaseId("lease:frontier-v3-game-test");
        WorldId world = new WorldId("frontier:scene-game-test");
        List<SceneMember> members = List.of(member(world, "resident:frontier-v3-test-hauler"), member(world, "resident:frontier-v3-test-guard"));
        return fixtureLease(id, world, new SubjectId("operation:frontier-v3-game-test"), new SubjectId("cargo:frontier-v3-game-test"),
                new BlockPosition(origin.getX(), origin.getY(), origin.getZ()), SimInstant.ZERO, 0L, SceneLeaseStatus.PREPARED, Optional.empty(), members);
    }
    private static SceneLease fixtureLease(SceneLeaseId id, WorldId world, SubjectId operation, SubjectId cargo, BlockPosition handoff,
                                           SimInstant instant, long revision, SceneLeaseStatus status, Optional<SubjectId> engagement,
                                           List<SceneMember> members) {
        java.util.Map<SubjectId, io.farfrontier.palemirror.frontier.v3.model.BodyPosition> bodies = new java.util.LinkedHashMap<>();
        for (int index = 0; index < members.size(); index++) {
            bodies.put(members.get(index).actorId(), new io.farfrontier.palemirror.frontier.v3.model.BodyPosition(handoff.x() + index * 2, handoff.y(), handoff.z()));
        }
        return SceneLease.atExactPositions(id, world, operation, cargo, handoff, handoff, instant, revision, status, engagement, members, bodies);
    }
    private static SceneMember member(WorldId world, String actorId) {
        SubjectId actor = new SubjectId(actorId);
        return new SceneMember(actor, SceneLease.deterministicEntityId(world, actor));
    }
    private static void prepareFloor(ServerLevel level, BlockPos position) {
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(position.above(), Blocks.AIR.defaultBlockState(), 3);
    }
    private static BlockPos cargoPosition(BlockPos anchor, SceneLease lease) {
        int ordinal = lease.members().size();
        return anchor.offset((ordinal % 2) * 2 + 1, 0, (ordinal / 2) * 2);
    }
    private static net.minecraft.world.entity.Mob addOwnedBody(GameTestHelper helper, ServerLevel level, FrontierWorldState state,
                                                                SceneLease lease, SceneMember member, BlockPos position) {
        boolean bioform = member.actorId().value().startsWith("bioform:");
        net.minecraft.world.entity.Mob body = bioform ? EntityType.ZOMBIE.create(level) : EntityType.VILLAGER.create(level);
        helper.assertTrue(body != null, "the exact HOT body fixture must be constructible");
        body.setUUID(member.entityId()); body.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D); body.setPersistenceRequired(); body.setNoAi(true);
        if (body instanceof Zombie zombie) FrontierV3AmbientActorExecutor.configureBioform(zombie,
                FrontierV3AmbientActorExecutor.bioformRole(state, member.actorId()));
        body.getPersistentData().putString(FrontierV3SceneExecutor.LEASE_KEY, lease.id().value());
        body.getPersistentData().putString(FrontierV3SceneExecutor.ACTOR_KEY, member.actorId().value());
        body.getPersistentData().putLong(FrontierV3SceneExecutor.REVISION_KEY, lease.revision());
        helper.assertTrue(level.addFreshEntity(body), "the exact HOT body fixture must enter the loaded world");
        return body;
    }
    /** Local GameTest representation of one already-canonical exact cargo batch. */
    private static MinecartChest addOwnedCarrier(GameTestHelper helper, ServerLevel level, FrontierWorldState state,
                                                 SceneLease lease, BlockPos position) {
        MinecartChest cart = EntityType.CHEST_MINECART.create(level);
        helper.assertTrue(cart != null, "the exact HOT cargo carrier fixture must be constructible");
        cart.setUUID(FrontierV3CargoCarrierExecutor.id(lease)); cart.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        cart.getPersistentData().putString(FrontierV3CargoCarrierExecutor.LEASE_KEY, lease.id().value());
        cart.getPersistentData().putString(FrontierV3CargoCarrierExecutor.CARGO_KEY, FrontierSceneBehaviors.logistics(lease).cargoId().value());
        var cargo = state.inventory().cargo().get(FrontierSceneBehaviors.logistics(lease).cargoId());
        for (int slot = 0; slot < cargo.itemIds().size(); slot++) {
            cart.setItem(slot, FrontierV3CargoHandoffExecutor.materializedStack(state.inventory().items().get(cargo.itemIds().get(slot))));
        }
        helper.assertTrue(level.addFreshEntity(cart), "the exact HOT cargo carrier fixture must enter the loaded world");
        return cart;
    }
    private static PhysicalIntent onlyStrike(FrontierWorldState state) {
        return state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.SCENE_STRIKE).reduce((left, right) -> right)
                .orElseThrow(() -> new IllegalStateException("the HOT strike executor did not retain a physical intent"));
    }
    private static PhysicalIntent pendingStrike(FrontierWorldState state, SceneLease lease) {
        return state.physicalIntents().values().stream().filter(intent -> intent.kind() == PhysicalIntentKind.SCENE_STRIKE
                && intent.causeSubjectId().equals(FrontierSceneBehaviors.logistics(lease).operationId()) && intent.status() == PhysicalIntentStatus.PREPARED).findFirst()
                .orElseThrow(() -> new IllegalStateException("the HOT scene did not prepare its exact next strike"));
    }
    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return new FrontierWorldStateCodec().decode(runtime.checkpointImage()
                .orElseThrow(() -> new IllegalStateException("the v3 GameTest runtime must remain active: "
                        + runtime.status().detail().orElse(runtime.status().kind().name()))).canonicalState());
    }
    private static String admissionDetail(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SceneMember member, Entity body) {
        if (body == null) return "missing body for " + member.actorId() + " expectedUuid=" + member.entityId();
        String lease = body.getPersistentData().getString(FrontierV3SceneExecutor.LEASE_KEY);
        String actor = body.getPersistentData().getString(FrontierV3SceneExecutor.ACTOR_KEY);
        long revision = body.getPersistentData().getLong(FrontierV3SceneExecutor.REVISION_KEY);
        return "actor=" + member.actorId() + ", expectedUuid=" + member.entityId() + ", actualUuid=" + body.getUUID()
                + ", type=" + body.getType() + ", removed=" + body.isRemoved() + ", lease=" + lease
                + ", actorTag=" + actor + ", revision=" + revision + ", canonicalRevision="
                + state(runtime).sceneLeases().values().stream().filter(value -> value.id().value().equals(lease))
                .map(value -> value.revision() + "/" + value.status()).findFirst().orElse("missing");
    }
    /** GameTest-only memory port: the filesystem restart proof lives in FrontierV3ServerRuntimeTest. */
    private static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) {
            return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value());
        }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("GameTest does not checkpoint"); }
        @Override public CompactionReceipt compact(WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision coveredRevision) {
            throw new UnsupportedOperationException("GameTest does not compact");
        }
    }
}
