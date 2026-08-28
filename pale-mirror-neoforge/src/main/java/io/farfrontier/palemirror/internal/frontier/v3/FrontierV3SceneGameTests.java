package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;
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
