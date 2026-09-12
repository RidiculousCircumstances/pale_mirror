package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Physical proof for the bounded HOT local-navigation actuator. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3LocalNavigationGameTests {
    private FrontierV3LocalNavigationGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void hotEnvelopeUsesOpenDoorStairAndHarmlessDetourWithoutChangingCheckpoint(GameTestHelper helper) {
        // Keep the full retained envelope inside the stock blank GameTest room rather than on
        // its structural perimeter, which is itself a real collision wall.
        BlockPos origin = helper.absolutePos(new BlockPos(2, 0, 2));
        Set<BlockPosition> supports = new LinkedHashSet<>();
        for (int x = 0; x <= 5; x++) for (int z = -1; z <= 1; z++) {
            int rise = x >= 4 ? 1 : 0;
            BlockPos floor = origin.offset(x, rise, z);
            helper.getLevel().setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            supports.add(new BlockPosition(floor.getX(), floor.getY(), floor.getZ()));
        }
        // The physical door is deliberately already open: this actor uses normal collision, not
        // a synthetic world mutation or a hidden alternative port.
        BlockPos door = origin.offset(0, 1, 1);
        BlockState openDoor = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.OPEN, true);
        helper.getLevel().setBlock(door, openDoor, 3);
        helper.getLevel().setBlock(door.above(), openDoor.setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
        // A single ordinary obstruction on the retained edge has harmless latitude in the
        // declared local envelope; it must not alter the port, checkpoint, or canonical intent.
        helper.getLevel().setBlock(origin.offset(2, 1, 0), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.offset(4, 1, 0), Blocks.OAK_STAIRS.defaultBlockState(), 3);

        LocalNavigationEnvelope envelope = new LocalNavigationEnvelope(supports);
        Zombie actor = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 2.5D));
        Vec3 checkpoint = new Vec3(origin.getX() + 5.5D, origin.getY() + 2.0D, origin.getZ() + .5D);
        AtomicBoolean detoured = new AtomicBoolean();

        helper.runAfterDelay(1, () -> {
            // Vanilla GameTest collapses registered onEachTick callbacks after a server
            // catch-up into one game-time observation. Execute the actual pre-tick delegate
            // and post-tick submit pair as separate bounded physical turns here instead: no
            // synthetic position, goal, route, or collision result is supplied by the test.
            for (int turn = 0; turn < 48; turn++) {
                BlockPosition observed = support(actor.position());
                helper.assertTrue(FrontierV3ControlledMobMotion.insideEnvelope(helper.getLevel(), actor, checkpoint, envelope),
                        "HOT local navigation must stay inside its retained envelope: " + observed);
                if (Math.abs(actor.getZ() - (origin.getZ() + .5D)) > .35D) detoured.set(true);
                FrontierV3ServerLifecycle.advanceControlledMob(actor);
                FrontierV3ControlledMobMotion.moveWithinEnvelope(helper.getLevel(), actor, checkpoint, envelope);
            }
            FrontierV3ServerLifecycle.advanceControlledMob(actor);
            helper.assertTrue(actor.getBlockX() == origin.getX() + 5 && actor.getBlockY() == origin.getY() + 2 && actor.getBlockZ() == origin.getZ(),
                    "only the retained stair-top checkpoint may complete local travel; observed=" + actor.position()
                            + " motion=" + FrontierV3ControlledMobMotion.readiness(actor));
            helper.assertTrue(detoured.get(), "the harmless blocked column must use only envelope-local avoidance");
            helper.assertTrue(helper.getLevel().getBlockState(door).getValue(DoorBlock.OPEN), "local traversal must not rewrite the physical door");
            helper.succeed();
        });
    }

    private static BlockPosition support(Vec3 feet) {
        return new BlockPosition((int) Math.floor(feet.x), (int) Math.floor(feet.y) - 1, (int) Math.floor(feet.z));
    }
}
