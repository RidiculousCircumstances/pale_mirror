package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.List;

/** Physical placement coverage separate from locomotion and activity ownership. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3BodyPlacementGameTests {
    private FrontierV3BodyPlacementGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void preparedBodyUsesCurrentTargetButHotBodyIsNeverReprojected(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(4, 0, 2));
        level.setBlock(target, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(target.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(target.above(2), Blocks.AIR.defaultBlockState(), 3);
        var actor = new io.farfrontier.palemirror.frontier.v3.api.SubjectId("resident:1-1");
        var currentBody = SurfaceAnchor.at(target.getX(), target.getY(), target.getZ()).standingBody();
        var lease = new io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease(actor, currentBody,
                io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO, 3L,
                io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.PREPARED,
                io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind.WORK, currentBody);
        var body = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(1.5D, 1.0D, 2.5D));
        var uuid = body.getUUID(); body.setHealth(7.0F);
        helper.assertValueEqual(FrontierV3AmbientBodyHandoff.placeDeclared(level, lease, body, currentBody, List.of(currentBody.supportingSurface()),
                FrontierV3StandingPosition::aboveExactFloor), FrontierV3AmbientActorExecutor.Result.CURRENT,
                "prepared admission uses the current canonical target, not the retained old pose");
        helper.assertTrue(FrontierV3BodyObservation.capture(body).supportedBody().filter(currentBody::equals).isPresent(),
                "actual supported body must match the current handoff");
        helper.assertTrue(uuid.equals(body.getUUID()) && body.getHealth() == 7.0F, "placement retains identity and health");
        var hot = lease.withStatus(io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus.HOT);
        body.setPos(body.getX() - 1.0D, body.getY(), body.getZ()); var hotPose = body.position();
        helper.assertValueEqual(FrontierV3AmbientBodyHandoff.placeDeclared(level, hot, body, currentBody, List.of(currentBody.supportingSurface()),
                FrontierV3StandingPosition::aboveExactFloor), FrontierV3AmbientActorExecutor.Result.CURRENT, "HOT refresh is not admission");
        helper.assertValueEqual(body.position(), hotPose, "ongoing HOT movement must never teleport to its old admission target");
        body.discard(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 30)
    public static void admissionUsesConnectedFreePlacementWithoutPublishingOrOverlappingBodies(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) {
            BlockPos support = helper.absolutePos(new BlockPos(x, 0, z));
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        BlockPos original = helper.absolutePos(new BlockPos(2, 0, 2));
        BlockPos alternative = original.east(2);
        Villager occupant = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 2.5D));
        Villager candidate = EntityType.VILLAGER.create(level);
        SurfaceAnchor exact = SurfaceAnchor.at(original.getX(), original.getY(), original.getZ());
        SurfaceAnchor side = SurfaceAnchor.at(alternative.getX(), alternative.getY(), alternative.getZ());
        var scope = new FrontierV3NavigationScope.Restricted(LocalNavigationEnvelope.along(List.of(exact, side), List.of(exact, side)));
        var selected = FrontierV3BodyPlacement.assess(level, candidate, List.of(exact, side), scope, FrontierV3StandingPosition::aboveExactFloor);
        helper.assertTrue(selected.surface().filter(side::equals).isPresent(), "an occupied exact placement selects a connected free candidate: " + selected.rejections());
        helper.assertTrue(FrontierV3BodyObservation.capture(candidate).support().filter(side::equals).isPresent(), "placement uses the actual support");
        helper.assertTrue(level.getEntity(candidate.getUUID()) == null, "selection cannot publish a physical body or acquire custody");
        helper.assertTrue(!candidate.getBoundingBox().intersects(occupant.getBoundingBox()), "alternative never overlaps the socket occupant");
        for (int z = 1; z <= 5; z++) for (int y = 1; y <= 2; y++)
            level.setBlock(helper.absolutePos(new BlockPos(3, y, z)), Blocks.STONE.defaultBlockState(), 3);
        var disconnected = FrontierV3BodyPlacement.assess(level, candidate, List.of(exact, side), scope, FrontierV3StandingPosition::aboveExactFloor);
        helper.assertTrue(disconnected.surface().isEmpty()
                && disconnected.rejections().stream().anyMatch(rejection -> rejection.reason() == FrontierV3BodyPlacement.Reason.NO_CONNECTED_PATH),
                "a free but physically disconnected alternative is not an admissible placement");
        for (int z = 1; z <= 5; z++) for (int y = 1; y <= 2; y++)
            level.setBlock(helper.absolutePos(new BlockPos(3, y, z)), Blocks.AIR.defaultBlockState(), 3);
        Villager second = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(4.5D, 1.0D, 2.5D));
        helper.assertTrue(FrontierV3BodyPlacement.select(level, candidate, List.of(exact, side), scope,
                FrontierV3StandingPosition::aboveExactFloor).isEmpty(), "a fully occupied declared zone defers, never invents a remote spawn");
        var retainedPose = candidate.position();
        helper.assertFalse(FrontierV3BodyPlacement.placeRetained(level, candidate, List.of(exact, side), scope,
                FrontierV3StandingPosition::aboveExactFloor), "retained admission defers when every declared candidate is occupied");
        helper.assertValueEqual(candidate.position(), retainedPose, "failed probes cannot move a retained body");
        occupant.discard(); second.discard();
        helper.assertTrue(FrontierV3BodyPlacement.select(level, candidate, List.of(exact, side), scope,
                FrontierV3StandingPosition::aboveExactFloor).filter(exact::equals).isPresent(), "the exact placement remains preferred once free");
        candidate.setPos(retainedPose.x, retainedPose.y, retainedPose.z);
        helper.assertTrue(FrontierV3BodyPlacement.placeRetained(level, candidate, List.of(exact, side), scope,
                FrontierV3StandingPosition::aboveExactFloor), "a released body can reproject to the prepared current target");
        helper.assertTrue(FrontierV3BodyObservation.capture(candidate).support().filter(exact::equals).isPresent(),
                "admission installs the current support, not a walk from the obsolete saved pose");
        candidate.discard(); helper.succeed();
    }

}
