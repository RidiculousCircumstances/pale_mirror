package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

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

        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodies(level, lease), FrontierV3SceneExecutor.BodyMaterialization.COMPLETE,
                "a loaded supported scene site must materialize each deterministic Villager body exactly once");
        for (SceneMember member : lease.members()) {
            Villager body = (Villager) level.getEntity(member.entityId());
            helper.assertTrue(body != null, "each leased actor must have its deterministic Villager body");
            helper.assertValueEqual(body.getPersistentData().getString(FrontierV3SceneExecutor.LEASE_KEY), lease.id().value(),
                    "materialized body must carry its scene lease ownership");
            helper.assertValueEqual(body.getPersistentData().getString(FrontierV3SceneExecutor.ACTOR_KEY), member.actorId().value(),
                    "materialized body must carry its canonical actor identity");
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

        helper.assertValueEqual(FrontierV3SceneExecutor.materializeBodies(level, lease), FrontierV3SceneExecutor.BodyMaterialization.CONFLICT,
                "an unowned body with a leased UUID is a visible conflict, never a body the executor claims");
        helper.assertTrue(level.getEntity(foreign.getUUID()) == foreign, "the foreign body must remain untouched");
        foreign.discard();
        helper.succeed();
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
}
