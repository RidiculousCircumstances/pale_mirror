package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.seating.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native contact evidence, independent of the activity's canonical outcome. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3SeatContactGameTests {
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/treasure/big_air_full", timeoutTicks = 40)
    public static void savedApplicationObservationSurvivesDisplacementWithoutReplayingThePose(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 3; x <= 9; x++) for (int z = 3; z <= 9; z++) {
            var floor = helper.absolutePos(new BlockPos(x, 30, z));
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            for (int y = 1; y <= 4; y++) level.setBlock(floor.above(y), Blocks.AIR.defaultBlockState(), 3);
        }
        var chair = helper.absolutePos(new BlockPos(6, 31, 6));
        var actor = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(5, 31, 6));
        var id = new SubjectId("resident:seat-contact-native");
        var execution = new ActorExecutionId(id, ActorActivityKind.MEAL, new SubjectId("claim:seat-contact-native"), 1);
        var authority = new ActorActuationId(new ActorBodyId(id, 7), execution);
        var approach = SurfaceAnchor.at(chair.getX()-1, chair.getY()-1, chair.getZ());
        var place = new SeatPlace(new SeatPlace.Id(new SubjectId("structure:seat-contact-native"), "dining"),
                new SubjectId("settlement:1"), io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingSeat.Kind.HANDCRAFTED_CHAIR,
                "handcrafted:spruce_chair", "handcrafted:spruce_table",
                new BlockPosition(chair.getX(), chair.getY(), chair.getZ()),
                new BlockPosition(chair.getX(), chair.getY(), chair.getZ()+1), approach, approach, FacilityFacing.SOUTH);
        var use = SeatUse.reserve(execution, place, 0).prepared(authority, SeatUse.Action.ENTER, SeatUse.ExecutionMode.LOCAL);
        var mutations = new AtomicInteger();
        var contact = new FrontierV3SeatProvider.Contact(() -> true, () -> {
            mutations.incrementAndGet();
            actor.setPos(chair.getX()+.5, chair.getY(), chair.getZ()+.5);
            FrontierV3BodyObservation.refreshGroundContact(level, actor);
            return FrontierV3NativeEffect.Outcome.APPLIED;
        });
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(FrontierV3SeatExecutor.changePose(actor, use, authority, contact)
                    == FrontierV3NativeEffect.Outcome.APPLIED, "native application must capture actual support");
            var observed = FrontierV3SupportedBodyCapture.observe(level, actor).orElseThrow();
            var saved = actor.getPersistentData().copy();
            actor.setPos(chair.getX()-1+.5, chair.getY(), chair.getZ()+.5);
            FrontierV3BodyObservation.refreshGroundContact(level, actor);
            var displaced = actor.position();
            actor.getPersistentData().merge(saved); // Reload the independently saved actor participant evidence.
            helper.assertTrue(FrontierV3SeatExecutor.applicationObservation(actor, authority, use).equals(Optional.of(observed)),
                    "saved fact must survive a later physical pose");
            helper.assertTrue(FrontierV3SeatExecutor.applicationObservation(actor,
                    new ActorActuationId(new ActorBodyId(id, 8), execution), use).isEmpty(), "another body epoch cannot use this fact");
            for (var dimension : java.util.List.of("actor", "activityKind", "owner", "generation", "physicalEpoch", "revision", "mode", "action", "observedX")) {
                actor.getPersistentData().getCompound("pm_v3_seat_operation").remove(dimension);
                helper.assertTrue(FrontierV3SeatExecutor.applicationObservation(actor, authority, use).isEmpty(),
                        "missing native observation dimension must fail closed: " + dimension);
                actor.getPersistentData().merge(saved);
            }
            helper.assertTrue(FrontierV3SeatExecutor.changePose(actor, use, authority, contact)
                    == FrontierV3NativeEffect.Outcome.APPLIED && mutations.get()==1 && actor.position().equals(displaced),
                    "an applied attempt can be acknowledged without replaying an old body pose");
            actor.discard(); helper.succeed();
        });
    }
}
