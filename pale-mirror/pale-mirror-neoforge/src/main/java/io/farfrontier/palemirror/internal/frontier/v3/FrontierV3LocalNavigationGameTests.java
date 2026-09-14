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
import java.util.List;
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
                FrontierV3ControlledMobMotion.advance(actor);
                FrontierV3ControlledMobMotion.moveWithinEnvelope(helper.getLevel(), actor, checkpoint, envelope);
            }
            FrontierV3ControlledMobMotion.advance(actor);
            helper.assertTrue(actor.getBlockX() == origin.getX() + 5 && actor.getBlockY() == origin.getY() + 2 && actor.getBlockZ() == origin.getZ(),
                    "only the retained stair-top checkpoint may complete local travel; observed=" + actor.position()
                            + " motion=" + FrontierV3ControlledMobMotion.readiness(actor));
            helper.assertTrue(detoured.get(), "the harmless blocked column must use only envelope-local avoidance");
            helper.assertTrue(helper.getLevel().getBlockState(door).getValue(DoorBlock.OPEN), "local traversal must not rewrite the physical door");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 240)
    public static void hotWorkerPursuesOneRetainedEdgeWithoutSyntheticReversal(GameTestHelper helper) {
        // This stock blank template has its safe interior at local z=2; z=10 is a real
        // template wall and would turn a cadence proof into an obstruction test.
        BlockPos origin = helper.absolutePos(new BlockPos(2, 0, 2));
        for (int x = 0; x <= 5; x++) {
            helper.getLevel().setBlock(origin.offset(x, 0, 0), Blocks.STONE.defaultBlockState(), 3);
            // `bastion/mobs/empty` is a normal vanilla structure, not an air-only harness.
            // Make this particular retained pedestrian corridor genuinely unobstructed so a
            // failed cadence trace cannot be misclassified from template collision.
            helper.getLevel().setBlock(origin.offset(x, 1, 0), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(origin.offset(x, 2, 0), Blocks.AIR.defaultBlockState(), 3);
        }
        // GameTestHelper converts spawn vectors from template-relative to world coordinates;
        // the retained checkpoint below is already absolute because it comes from the physical
        // provider. Mixing the two would place the body outside this natural test cell.
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 2.5D));
        Vec3 currentCanonicalCheckpoint = new Vec3(origin.getX() + .5D, origin.getY() + 1.0D, origin.getZ() + .5D);
        Vec3 nextCanonicalCheckpoint = new Vec3(origin.getX() + 1.5D, origin.getY() + 1.0D, origin.getZ() + .5D);
        // The physical actuator receives only the retained next checkpoint.  Scheduler time is
        // intentionally absent: it must not manufacture an edge-local turnaround while a
        // production process awaits the observed checkpoint command.
        for (int turn = 1; turn <= 24; turn++) {
            helper.runAtTickTime(turn, () -> {
                FrontierV3ControlledMobMotion.advance(worker);
                FrontierV3ControlledMobMotion.pursueRetainedCheckpoint(helper.getLevel(), worker, nextCanonicalCheckpoint);
            });
        }
        helper.runAtTickTime(25, () -> {
            FrontierV3ControlledMobMotion.advance(worker);
            List<FrontierV3ControlledMobMotion.MotionSample> trace = FrontierV3ControlledMobMotion.trace(worker);
            long distinctSubBlockPositions = trace.stream().map(value -> Math.round(value.x() * 1_000.0D)).distinct().count();
            long timestampedTurns = trace.stream().map(FrontierV3ControlledMobMotion.MotionSample::gameTime).distinct().count();
            long distinctColumns = trace.stream().map(value -> (int) Math.floor(value.x())).distinct().count();
            long normalCadenceTurns = trace.stream().filter(value -> value.horizontalVelocity() >= .15D).count();
            helper.assertTrue(distinctSubBlockPositions >= 3 && timestampedTurns >= 3 && distinctColumns >= 2
                            && normalCadenceTurns >= 3,
                    "a HOT worker must pursue the retained checkpoint at normal cadence without synthetic pacing: "
                            + "samples=" + trace.size() + " subBlocks=" + distinctSubBlockPositions
                            + " turns=" + timestampedTurns + " columns=" + distinctColumns
                            + " normalCadenceTurns=" + normalCadenceTurns);
            helper.assertTrue(worker.getX() >= currentCanonicalCheckpoint.x - .35D && worker.getX() <= nextCanonicalCheckpoint.x + .35D,
                    "smooth local pose remains on the retained edge and does not create a canonical route cursor");
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void continuousTendingSurvivesOneShotSceneSubmissionUntilRealHandoff(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 0, 2));
        for (int x = 0; x <= 5; x++) {
            helper.getLevel().setBlock(origin.offset(x, 0, 0), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(origin.offset(x, 1, 0), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(origin.offset(x, 2, 0), Blocks.AIR.defaultBlockState(), 3);
        }
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 2.5D));
        Vec3 localTendingPose = new Vec3(origin.getX() + 4.5D, origin.getY() + 1.0D, origin.getZ() + .5D);
        // One scene submission models the production interval between durable semantic turns.
        // The actuator must not consume it as a three-frame tracker burst followed by a frozen
        // worker; only a real retained checkpoint or stop may supersede this local pose.
        helper.runAtTickTime(1, () -> FrontierV3ControlledMobMotion.followContinuously(helper.getLevel(), worker, localTendingPose));
        for (int turn = 2; turn <= 7; turn++) {
            helper.runAtTickTime(turn, () -> FrontierV3ControlledMobMotion.advanceAtEntityBoundary(worker));
        }
        helper.runAtTickTime(8, () -> {
            List<FrontierV3ControlledMobMotion.MotionSample> trace = FrontierV3ControlledMobMotion.trace(worker);
            long normalCadenceTurns = trace.stream().filter(value -> value.horizontalVelocity() >= .15D).count();
            helper.assertTrue(normalCadenceTurns >= 5 && longestStall(trace.stream()
                            .map(FrontierV3ControlledMobMotion.MotionSample::x).toList()) == 0,
                    "a one-shot local tending directive must retain normal physical cadence until handoff: " + trace);
            FrontierV3ControlledMobMotion.stop(worker);
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void retainedTravelSupersedesTheFormerCropTendingPose(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 0, 2));
        for (int x = 0; x <= 5; x++) {
            helper.getLevel().setBlock(origin.offset(x, 0, 0), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(origin.offset(x, 1, 0), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(origin.offset(x, 2, 0), Blocks.AIR.defaultBlockState(), 3);
        }
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 2.5D));
        BlockPosition currentCrop = new BlockPosition(origin.getX(), origin.getY() + 1, origin.getZ());
        Vec3 nextCheckpoint = new Vec3(origin.getX() + 4.5D, origin.getY() + 1.0D, origin.getZ() + .5D);
        helper.runAtTickTime(1, () -> FrontierV3ControlledMobMotion.tendCurrentCrop(helper.getLevel(), worker, currentCrop));
        helper.runAtTickTime(2, () -> FrontierV3ControlledMobMotion.advanceAtEntityBoundary(worker));
        helper.runAtTickTime(3, () -> FrontierV3ControlledMobMotion.pursueRetainedCheckpoint(helper.getLevel(), worker, nextCheckpoint));
        for (int turn = 4; turn <= 20; turn++) {
            helper.runAtTickTime(turn, () -> FrontierV3ControlledMobMotion.advanceAtEntityBoundary(worker));
        }
        helper.runAtTickTime(21, () -> {
            List<FrontierV3ControlledMobMotion.MotionSample> trace = FrontierV3ControlledMobMotion.trace(worker);
            long forwardTurns = trace.stream().filter(value -> value.horizontalVelocity() >= .15D).count();
            helper.assertTrue(worker.getX() >= origin.getX() + 4.1D && forwardTurns >= 3,
                    "the next retained edge must displace a former crop worker instead of preserving its stale tending pose: " + trace);
            FrontierV3ControlledMobMotion.stop(worker);
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 120)
    public static void providerRecurrenceChangesRetainedEdgesOnlyAfterObservedArrival(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 0, 2));
        for (int x = 0; x <= 5; x++) {
            helper.getLevel().setBlock(origin.offset(x, 0, 0), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(origin.offset(x, 1, 0), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(origin.offset(x, 2, 0), Blocks.AIR.defaultBlockState(), 3);
        }
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 2.5D));
        // Each 16-turn interval models the same production hand-off: physical arrival is
        // observed first, then and only then does the canonical owner expose the next retained
        // edge. The test never invents a presentation cursor or lets a body skip a support.
        for (int turn = 1; turn <= 30; turn++) {
            int edge = Math.min(4, (turn - 1) / 6);
            int withinEdge = (turn - 1) % 6;
            Vec3 current = new Vec3(origin.getX() + edge + .5D, origin.getY() + 1.0D, origin.getZ() + .5D);
            Vec3 next = new Vec3(origin.getX() + edge + 1.5D, origin.getY() + 1.0D, origin.getZ() + .5D);
            helper.runAtTickTime(turn, () -> {
                FrontierV3ControlledMobMotion.advance(worker);
                FrontierV3ControlledMobMotion.pursueRetainedCheckpoint(helper.getLevel(), worker, next);
            });
            if (withinEdge == 5 && edge < 4) {
                helper.runAtTickTime(turn + 1, () -> helper.assertTrue(worker.getBlockX() == (int) Math.floor(next.x()),
                        "the next retained edge may appear only after the exact physical body cell is observed: actual="
                                + worker.getX() + " expectedCell=" + (int) Math.floor(next.x())));
            }
        }
        helper.runAtTickTime(31, () -> {
            FrontierV3ControlledMobMotion.advance(worker);
            List<FrontierV3ControlledMobMotion.MotionSample> trace = FrontierV3ControlledMobMotion.trace(worker);
            long columns = trace.stream().map(value -> (int) Math.floor(value.x())).distinct().count();
            long normalCadenceTurns = trace.stream().filter(value -> value.horizontalVelocity() >= .15D).count();
            helper.assertTrue(columns >= 5 && normalCadenceTurns >= 15 && normalCadenceTurns == trace.size() && longestStall(trace.stream()
                            .map(FrontierV3ControlledMobMotion.MotionSample::x).toList()) <= 2,
                    "successive observed retained edges must retain normal cadence without a route/cursor fork: columns="
                            + columns + " normalCadenceTurns=" + normalCadenceTurns + " samples=" + trace.size());
            helper.succeed();
        });
    }

    private static BlockPosition support(Vec3 feet) {
        return new BlockPosition((int) Math.floor(feet.x), (int) Math.floor(feet.y) - 1, (int) Math.floor(feet.z));
    }

    private static int longestStall(List<Double> trace) {
        int longest = 0, current = 0;
        for (int index = 1; index < trace.size(); index++) {
            current = Math.abs(trace.get(index) - trace.get(index - 1)) < 0.00001D ? current + 1 : 0;
            longest = Math.max(longest, current);
        }
        return longest;
    }
}
