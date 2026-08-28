package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/** Materialized ownership and conflict evidence for exact v3 HOT scene bodies. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3SceneGameTests {
    private FrontierV3SceneGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-bodies", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedSceneCreatesOnlyItsExactVillagerBodies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 8, 0));
        prepareFloor(level, origin); prepareFloor(level, origin.east(2));
        SceneLease lease = lease(origin);

        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scene-body-test"), 91L));
        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodies(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                "a loaded supported scene site must materialize each deterministic Villager body exactly once");
        for (SceneMember member : lease.members()) {
            Villager body = (Villager) level.getEntity(member.entityId());
            helper.assertTrue(body != null, "each leased actor must have its deterministic Villager body");
            helper.assertValueEqual(body.getPersistentData().getString(FrontierV3SceneExecutor.LEASE_KEY), lease.id().value(),
                    "materialized body must carry its scene lease ownership");
            helper.assertValueEqual(body.getPersistentData().getString(FrontierV3SceneExecutor.ACTOR_KEY), member.actorId().value(),
                    "materialized body must carry its canonical actor identity");
            helper.assertTrue(body.isNoAi(), "a HOT body must not retain uncontrolled vanilla AI or combat authority");
            body.discard();
        }
        helper.succeed();
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
        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodies(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.CONFLICT,
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
        SceneLease lease = new SceneLease(id, new SubjectId("operation:frontier-v3-bioform-test"), new SubjectId("cargo:frontier-v3-bioform-test"),
                new BlockPosition(origin.getX(), origin.getY(), origin.getZ()), SimInstant.ZERO, 0L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(bioform, SceneLease.deterministicEntityId(id, bioform))));

        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodies(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                "a canonical hive participant must materialize as its graybox Zombie, never as a Villager");
        Entity entity = level.getEntity(lease.members().getFirst().entityId());
        helper.assertTrue(entity instanceof Zombie, "the scene body must retain the canonical bioform kind");
        entity.discard();
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-bodies", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedEngagementSceneKeepsBothSidesAsExactBodies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(24, 8, 0)); prepareFloor(level, origin); prepareFloor(level, origin.east(2));
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:engagement-scene-bodies"), 91L));
        SceneLeaseId id = new SceneLeaseId("lease:frontier-v3-engagement-bodies"); SubjectId resident = new SubjectId("resident:1-1"), bioform = new SubjectId("bioform:west-0");
        SceneLease lease = new SceneLease(id, new SubjectId("operation:frontier-v3-engagement-bodies"), new SubjectId("cargo:frontier-v3-engagement-bodies"),
                new BlockPosition(origin.getX(), origin.getY(), origin.getZ()), SimInstant.ZERO, 0L, SceneLeaseStatus.PREPARED,
                Optional.of(new SubjectId("engagement:frontier-v3-game-test")), List.of(new SceneMember(resident, SceneLease.deterministicEntityId(id, resident)),
                        new SceneMember(bioform, SceneLease.deterministicEntityId(id, bioform))));
        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodies(level, state, lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
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
                        new BlockPosition(residentSpot.getX(), residentSpot.getY(), residentSpot.getZ())), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "an exact resident receives one owned Villager body");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, bioform,
                        new BlockPosition(bioformSpot.getX(), bioformSpot.getY(), bioformSpot.getZ())), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "an exact hive bioform receives one owned Zombie body");
        helper.assertTrue(level.getEntity(FrontierV3AmbientActorExecutor.entityId(resident)) instanceof Villager, "resident identity maps to Villager");
        helper.assertTrue(level.getEntity(FrontierV3AmbientActorExecutor.entityId(bioform)) instanceof net.minecraft.world.entity.monster.Zombie, "bioform identity maps to Zombie");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, resident,
                        new BlockPosition(residentSpot.getX(), residentSpot.getY(), residentSpot.getZ())), FrontierV3AmbientActorExecutor.Result.CURRENT,
                "a repeated loaded-chunk pass never duplicates the exact resident");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, new SubjectId("resident:unknown"),
                        new BlockPosition(residentSpot.getX(), residentSpot.getY(), residentSpot.getZ())), FrontierV3AmbientActorExecutor.Result.CONFLICT,
                "an unknown canonical identity is never converted into a new Villager body");
        level.getEntity(FrontierV3AmbientActorExecutor.entityId(resident)).discard();
        level.getEntity(FrontierV3AmbientActorExecutor.entityId(bioform)).discard();
        helper.succeed();
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
        restored.setUUID(FrontierV3AmbientActorExecutor.entityId(resident));
        restored.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
        restored.getPersistentData().putString(FrontierV3AmbientActorExecutor.ACTOR_KEY, resident.value());
        restored.getPersistentData().putString(FrontierV3AmbientActorExecutor.KIND_KEY, "RESIDENT");
        helper.assertTrue(level.addFreshEntity(restored), "the restored body fixture must enter the loaded world");

        helper.assertTrue(FrontierV3AmbientActorExecutor.observeJoin(runtime, restored),
                "only the exact loaded owned body may reclaim an UNKNOWN ambient lease");
        helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.HOT,
                "loaded-world reclaim must make the same canonical lease HOT");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), resident,
                        new BlockPosition(origin.getX(), origin.getY(), origin.getZ())), FrontierV3AmbientActorExecutor.Result.CURRENT,
                "reclaim must retain the existing body instead of creating another one");
        helper.assertTrue(level.getEntity(restored.getUUID()) == restored, "the observed restored body remains the sole UUID owner");
        FrontierV3AmbientActorExecutor.forget(runtime);
        restored.discard();
        helper.succeed();
    }

    private static SceneLease lease(BlockPos origin) {
        SceneLeaseId id = new SceneLeaseId("lease:frontier-v3-game-test");
        List<SceneMember> members = List.of(member(id, "resident:frontier-v3-test-hauler"), member(id, "resident:frontier-v3-test-guard"));
        return new SceneLease(id, new SubjectId("operation:frontier-v3-game-test"), new SubjectId("cargo:frontier-v3-game-test"),
                new BlockPosition(origin.getX(), origin.getY(), origin.getZ()), SimInstant.ZERO, 0L, SceneLeaseStatus.PREPARED, members);
    }
    private static SceneMember member(SceneLeaseId leaseId, String actorId) {
        SubjectId actor = new SubjectId(actorId);
        return new SceneMember(actor, SceneLease.deterministicEntityId(leaseId, actor));
    }
    private static void prepareFloor(ServerLevel level, BlockPos position) {
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(position.above(), Blocks.AIR.defaultBlockState(), 3);
    }
    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
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
