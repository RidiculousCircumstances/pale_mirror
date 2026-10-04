package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Physical proof for the bounded HOT local-navigation actuator. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3LocalNavigationGameTests {
    private FrontierV3LocalNavigationGameTests() { }


    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 65)
    public static void minecraftGoalNavigatorReportsAnUnreachableGoalWithinFinitePhysicalTurns(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(3, 0, 3));
        BlockPos destination = start.east(2);
        for (int x = 1; x <= 6; x++) for (int z = 1; z <= 6; z++) {
            BlockPos support = helper.absolutePos(new BlockPos(x, 0, z));
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (x == 0 && z == 0) continue;
            BlockPos wall = start.offset(x, 1, z);
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
        }
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(3.5D, 1.0D, 3.5D));
        SurfaceAnchor from = SurfaceAnchor.at(start.getX(), start.getY(), start.getZ());
        SurfaceAnchor to = SurfaceAnchor.at(destination.getX(), destination.getY(), destination.getZ());
        var goal = new FrontierV3GoalNavigation.Goal(to,
                io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.PEDESTRIAN,
                LocalNavigationEnvelope.around(from.standingBody(), to.standingBody()));
        var status = new java.util.concurrent.atomic.AtomicReference<>(FrontierV3GoalNavigation.Status.IN_PROGRESS);
        for (int turn = 1; turn <= 26; turn++) helper.runAtTickTime(turn, () -> {
            status.set(FrontierV3GoalNavigation.pursue(level, worker, goal).status());
            helper.assertTrue(worker.isNoAi(), "the blocked path cannot enlist vanilla goal or Brain AI");
        });
        helper.runAtTickTime(27, () -> {
            helper.assertValueEqual(status.get(), FrontierV3GoalNavigation.Status.BLOCKED,
                    "an unreachable physical goal must have a finite, explicit local disposition");
            helper.assertTrue(worker.getOnPos().equals(start), "failure may not move the worker through the obstruction");
            FrontierV3GoalNavigation.stop(worker); worker.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 110)
    public static void minecraftGoalNavigatorDetoursWithoutVanillaTaskAi(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 0, 2));
        BlockPos destination = start.east(3);
        for (int x = 1; x <= 6; x++) for (int z = 1; z <= 3; z++) {
            BlockPos support = helper.absolutePos(new BlockPos(x, 0, z));
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(start.east(2).above(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(start.east(2).above(2), Blocks.STONE.defaultBlockState(), 3);
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 2.5D));
        SurfaceAnchor from = SurfaceAnchor.at(start.getX(), start.getY(), start.getZ());
        SurfaceAnchor to = SurfaceAnchor.at(destination.getX(), destination.getY(), destination.getZ());
        LocalNavigationEnvelope envelope = LocalNavigationEnvelope.around(from.standingBody(), to.standingBody());
        var goal = new FrontierV3GoalNavigation.Goal(to, io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.PEDESTRIAN, envelope);
        AtomicBoolean detoured = new AtomicBoolean();
        helper.runAfterDelay(1, () -> {
            List<String> samples = new ArrayList<>();
            // GameTest catch-up collapses many runAtTickTime callbacks into a single
            // physical entity turn. Drive the same NoAI navigation/control/travel
            // methods as separate bounded physical turns, as the local-motion proof
            // below does, without supplying any synthetic position or path result.
            for (int tick = 1; tick <= 75 && !FrontierV3SemanticMovement.arrived(level, worker, to); tick++) {
                var result = FrontierV3GoalNavigation.pursue(level, worker, goal);
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "Minecraft path must remain available within the retained goal: " + result
                                + " position=" + worker.position() + " onGround=" + worker.onGround()
                                + " support=" + worker.getOnPos() + " target=" + destination
                                + " path=" + worker.getNavigation().getPath());
                helper.assertTrue(worker.isNoAi(), "the farmer must not activate vanilla task/Brain AI");
                var observed = worker.getOnPos();
                helper.assertTrue(envelope.contains(new BlockPosition(observed.getX(), observed.getY(), observed.getZ())),
                        "the physical body must remain inside the retained navigation envelope");
                if (Math.abs(worker.getZ() - (start.getZ() + .5D)) > .35D) detoured.set(true);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(worker);
                worker.aiStep();
                if (tick % 5 == 0) samples.add(tick + ":" + worker.position() + "/" + worker.getOnPos()
                        + "/" + worker.getNavigation().getPath());
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, worker, to),
                    "the exact retained goal must be physically reached: " + worker.position()
                            + " ground=" + worker.onGround() + " path=" + worker.getNavigation().getPath()
                            + " speed=" + worker.getSpeed() + " zza=" + worker.zza
                            + " velocity=" + worker.getDeltaMovement() + " samples=" + samples);
            helper.assertTrue(detoured.get(), "the Minecraft path must avoid the real blocked column");
            FrontierV3ControlledMobMotion.stop(worker); worker.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 110)
    public static void observedGoalCanDetourBeyondTheOldKnownPathStripe(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 0, 2));
        BlockPos destination = start.east(4);
        for (int x = 1; x <= 7; x++) for (int z = 0; z <= 5; z++) {
            BlockPos support = helper.absolutePos(new BlockPos(x, 0, z));
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        for (int z = 0; z <= 3; z++) {
            BlockPos wall = helper.absolutePos(new BlockPos(4, 1, z));
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
        }
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 2.5D));
        SurfaceAnchor from = SurfaceAnchor.at(start.getX(), start.getY(), start.getZ());
        SurfaceAnchor to = SurfaceAnchor.at(destination.getX(), destination.getY(), destination.getZ());
        List<SurfaceAnchor> oldKnownPath = java.util.stream.IntStream.rangeClosed(0, 4)
                .mapToObj(index -> SurfaceAnchor.at(start.getX() + index, start.getY(), start.getZ()))
                .toList();
        LocalNavigationEnvelope oldStripe = LocalNavigationEnvelope.along(oldKnownPath, List.of(to));
        LocalNavigationEnvelope latitude = LocalNavigationEnvelope.between(from.standingBody(), List.of(to));
        helper.assertTrue(!oldStripe.contains(new BlockPosition(start.getX() + 2, start.getY(), start.getZ() + 2)),
                "the old stripe must exclude the only legal two-cell detour");
        helper.assertTrue(latitude.contains(new BlockPosition(start.getX() + 2, start.getY(), start.getZ() + 2)),
                "bounded HOT latitude must admit the physical detour without choosing another task");
        var goal = new FrontierV3GoalNavigation.Goal(to,
                io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.PEDESTRIAN, latitude);
        AtomicBoolean detoured = new AtomicBoolean();
        helper.runAfterDelay(1, () -> {
            for (int tick = 1; tick <= 90 && !FrontierV3SemanticMovement.arrived(level, worker, to); tick++) {
                var result = FrontierV3GoalNavigation.pursue(level, worker, goal);
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "real Minecraft path must remain within the bounded replan latitude: " + result);
                if (worker.getZ() >= start.getZ() + 1.9D) detoured.set(true);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(worker);
                worker.aiStep();
            }
            helper.assertTrue(detoured.get(), "the worker must physically leave the obsolete one-cell stripe");
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, worker, to),
                    "the same semantic goal must be reached without a synthetic body move: " + worker.position());
            FrontierV3GoalNavigation.stop(worker); worker.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 90)
    public static void minecraftGoalNavigatorUsesReachableStationInDeclaredServiceRegion(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 0, 2));
        BlockPos obstructed = helper.absolutePos(new BlockPos(4, 0, 2));
        BlockPos service = helper.absolutePos(new BlockPos(4, 0, 3));
        Set<BlockPosition> supports = new LinkedHashSet<>();
        for (int x = 1; x <= 6; x++) for (int z = 1; z <= 5; z++) {
            BlockPos support = helper.absolutePos(new BlockPos(x, 0, z));
            supports.add(new BlockPosition(support.getX(), support.getY(), support.getZ()));
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(obstructed.above(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(obstructed.above(2), Blocks.STONE.defaultBlockState(), 3);
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 2.5D));
        SurfaceAnchor blockedStation = SurfaceAnchor.at(obstructed.getX(), obstructed.getY(), obstructed.getZ());
        SurfaceAnchor serviceStation = SurfaceAnchor.at(service.getX(), service.getY(), service.getZ());
        var goal = new FrontierV3GoalNavigation.Goal(List.of(blockedStation, serviceStation),
                io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.PEDESTRIAN,
                new LocalNavigationEnvelope(supports));
        helper.runAfterDelay(1, () -> {
            for (int turn = 0; turn < 70 && !FrontierV3SemanticMovement.arrived(level, worker, serviceStation); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, worker, goal);
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "declared service region should use its reachable station: " + result);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(worker);
                worker.aiStep();
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, worker, serviceStation),
                    "physical actor must reach the legal, unobstructed service support");
            var arrived = FrontierV3GoalNavigation.pursue(level, worker, goal);
            helper.assertValueEqual(arrived.status(), FrontierV3GoalNavigation.Status.ARRIVED,
                    "only an observed legal station closes the goal");
            helper.assertValueEqual(arrived.arrivedStation().orElseThrow(), serviceStation,
                    "arrival must carry the exact service station rather than an arbitrary representative");
            helper.assertTrue(worker.isNoAi(), "service navigation may not activate vanilla task AI");
            FrontierV3GoalNavigation.stop(worker); worker.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void minecraftGoalNavigatorReachesHydratedCropSupportWithoutTrampling(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 0, 2));
        BlockPos middle = start.east();
        BlockPos soil = middle.east();
        for (BlockPos support : List.of(start, middle, soil)) {
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(soil, Blocks.FARMLAND.defaultBlockState()
                .setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 3);
        level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState(), 3);
        level.setBlock(soil.south(), Blocks.WATER.defaultBlockState(), 3);
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 2.5D));
        SurfaceAnchor from = SurfaceAnchor.at(start.getX(), start.getY(), start.getZ());
        SurfaceAnchor target = SurfaceAnchor.at(soil.getX(), soil.getY(), soil.getZ());
        var goal = new FrontierV3GoalNavigation.Goal(target,
                io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.PEDESTRIAN,
                LocalNavigationEnvelope.around(from.standingBody(), target.standingBody()));
        helper.runAfterDelay(1, () -> {
            String lastReason = "not-started";
            for (int turn = 0; turn < 65 && !FrontierV3SemanticMovement.arrived(level, worker, target); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, worker, goal);
                lastReason = result.reason();
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "hydrated farmland remains a reachable physical support: " + result);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(worker);
                worker.aiStep();
                helper.assertTrue(level.getBlockState(soil).is(Blocks.FARMLAND),
                        "ordinary Minecraft travel may not trample this hydrated field");
            }
            var diagnosticPath = worker.getNavigation().createPath(soil.above(), 0);
            var pathNodes = diagnosticPath == null ? List.of() : java.util.stream.IntStream.range(0, diagnosticPath.getNodeCount())
                    .mapToObj(index -> diagnosticPath.getNode(index).asBlockPos().toString()).toList();
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, worker, target),
                    "exact farmland support must be observed after vanilla travel: " + worker.position()
                            + " reason=" + lastReason + " ground=" + worker.onGround()
                            + " observed=" + worker.getOnPos() + " path=" + worker.getNavigation().getPath()
                            + " soil=" + level.getBlockState(soil) + " crop=" + level.getBlockState(soil.above())
                            + " diagnosticPath=" + diagnosticPath + " nodes=" + pathNodes);
            helper.assertTrue(FrontierV3GoalNavigation.pursue(level, worker, goal).status()
                            == FrontierV3GoalNavigation.Status.ARRIVED,
                    "the shared boundary reports only physically observed arrival");
            helper.assertTrue(worker.isNoAi(), "the navigation bridge must retain the persisted NoAI flag");
            FrontierV3GoalNavigation.stop(worker); worker.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/treasure/big_air_full", timeoutTicks = 180)
    public static void minecraftGoalNavigatorKeepsFarmerAboveEveryCropOnRepeatedFieldEdges(GameTestHelper helper) {
        var level = helper.getLevel();
        List<BlockPos> route = new ArrayList<>();
        for (int z = 4; z <= 5; z++) {
            for (int step = 0; step < 8; step++) {
                int x = z == 4 ? 4 + step : 11 - step;
                BlockPos soil = helper.absolutePos(new BlockPos(x, 30, z));
                level.setBlock(soil.below(), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(soil, Blocks.FARMLAND.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 3);
                level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.CropBlock.AGE, 7), 3);
                level.setBlock(soil.above(2), Blocks.AIR.defaultBlockState(), 3);
                route.add(soil);
            }
        }
        Villager farmer = helper.spawnWithNoFreeWill(EntityType.VILLAGER,
                new Vec3(4.5D, 30.9375D, 4.5D));
        helper.runAfterDelay(1, () -> {
            for (int index = 1; index < route.size(); index++) {
                BlockPos previous = route.get(index - 1), next = route.get(index);
                SurfaceAnchor from = SurfaceAnchor.at(previous.getX(), previous.getY(), previous.getZ());
                SurfaceAnchor target = SurfaceAnchor.at(next.getX(), next.getY(), next.getZ());
                var goal = new FrontierV3GoalNavigation.Goal(target,
                        io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.PEDESTRIAN,
                        LocalNavigationEnvelope.around(from.standingBody(), target.standingBody()));
                for (int turn = 0; turn < 80 && !FrontierV3SemanticMovement.arrived(level, farmer, target); turn++) {
                    var motion = FrontierV3GoalNavigation.pursue(level, farmer, goal);
                    helper.assertTrue(motion.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                            "field edge " + index + " blocked: " + motion + " body=" + farmer.position());
                    FrontierV3GoalNavigation.advanceAtEntityBoundary(farmer);
                    farmer.aiStep();
                }
                helper.assertTrue(FrontierV3SemanticMovement.arrived(level, farmer, target),
                        "field edge " + index + " missed exact crop support: " + farmer.position()
                                + " on=" + farmer.getOnPos());
                helper.assertValueEqual(FrontierV3SupportedBodyCapture.observe(level, farmer),
                        java.util.Optional.of(new BodyPosition(next.getX(), next.getY() + 1, next.getZ())),
                        "field edge " + index + " may not retain a body inside farmland");
                FrontierV3GoalNavigation.stop(farmer);
                level.setBlock(next.above(), Blocks.AIR.defaultBlockState(), 3);
                for (int workTurn = 0; workTurn < 20; workTurn++) {
                    FrontierV3ControlledMobMotion.showStationWorkGesture(level, farmer);
                    FrontierV3ControlledMobMotion.advanceAtEntityBoundary(farmer);
                    farmer.aiStep();
                }
                helper.assertValueEqual(FrontierV3SupportedBodyCapture.observe(level, farmer),
                        java.util.Optional.of(new BodyPosition(next.getX(), next.getY() + 1, next.getZ())),
                        "field work " + index + " may not lower the retained body into its soil");
                FrontierV3ControlledMobMotion.stop(farmer);
            }
            FrontierV3GoalNavigation.stop(farmer); farmer.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-harvest-support", templateNamespace = "minecraft", template = "bastion/treasure/big_air_full", timeoutTicks = 140)
    public static void tendingThenLeavingHydratedFarmlandDoesNotInventTrampling(GameTestHelper helper) {
        var level = helper.getLevel();
        var soil = helper.absolutePos(new BlockPos(4, 30, 4));
        var road = soil.east();
        level.setBlock(soil, Blocks.FARMLAND.defaultBlockState()
                .setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7), 3);
        level.setBlock(soil.west(), Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState(), 3);
        level.setBlock(soil.above(2), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(road, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(road.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(road.above(2), Blocks.AIR.defaultBlockState(), 3);
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(4.5D, 30.9375D, 4.5D));
        FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(worker);
        var from = SurfaceAnchor.at(soil.getX(), soil.getY(), soil.getZ());
        var to = SurfaceAnchor.at(road.getX(), road.getY(), road.getZ());
        for (int tick = 1; tick <= 100; tick++) {
            final int turn = tick;
            helper.runAtTickTime(tick, () -> {
                helper.assertTrue(level.getBlockState(soil).is(Blocks.FARMLAND),
                        "ordinary tending/exit must retain hydrated soil; turn=" + turn
                                + " pos=" + worker.position() + " fall=" + worker.fallDistance
                                + " motion=" + FrontierV3ControlledMobMotion.motionObservation(worker));
                if (turn < 50) {
                    FrontierV3ControlledMobMotion.showStationWorkGesture(level, worker);
                } else {
                    if (turn == 50) level.setBlock(soil.above(), Blocks.AIR.defaultBlockState(), 3);
                    if (turn == 50) FrontierV3ControlledMobMotion.clearStationWorkGesture(level, worker);
                    pursueFixtureRetainedEdge(level, worker, from, to);
                }
                // GameTest callbacks can run after EntityTick.Pre; drive that same
                // idempotent boundary explicitly so a missed tick is not an arrival proof.
                FrontierV3ControlledMobMotion.advanceAtEntityBoundary(worker);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(worker);
            });
        }
        helper.runAtTickTime(110, () -> {
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, worker, to),
                    "worker must actually leave the fractional support for its retained road checkpoint: pos="
                            + worker.position() + " support=" + worker.getOnPos() + " target=" + road
                            + " arrival=" + FrontierV3SemanticMovement.at(level, worker, to)
                            + " motion=" + FrontierV3ControlledMobMotion.motionObservation(worker));
            helper.assertTrue(level.getBlockState(soil).is(Blocks.FARMLAND), "soil remains after exit");
            FrontierV3ControlledMobMotion.stop(worker); worker.discard(); helper.succeed();
        });
    }

    /**
     * A production hand-off may begin immediately below its first retained grade-one edge.  It
     * must use the same ordinary collision path as every other retained pedestrian edge: the
     * physical actuator cannot strand the exact worker at cursor zero merely because the next
     * named support is one block higher.
     */
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void retainedGradeOneEdgeLeavesTheObservedHandoffSurface(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 0, 4));
        // Keep this complete two-column edge in the template interior.  The first support is
        // deliberately one block below the next named support, matching the production route
        // shape without supplying a synthetic position or alternate path.
        helper.getLevel().setBlock(origin, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.above(2), Blocks.AIR.defaultBlockState(), 3);
        BlockPos nextSupport = origin.west().above();
        helper.getLevel().setBlock(nextSupport, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(nextSupport.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(nextSupport.above(2), Blocks.AIR.defaultBlockState(), 3);
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 1.0D, 4.5D));
        // A production hand-off retains the ordinary ambient-physics registration.  The same
        // exact body must still clear this one-grade retained edge instead of having gravity
        // erase each bounded ascent increment.
        FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(worker);
        SurfaceAnchor retainedCurrent = SurfaceAnchor.at(origin.getX(), origin.getY(), origin.getZ());
        SurfaceAnchor retainedNext = SurfaceAnchor.at(nextSupport.getX(), nextSupport.getY(), nextSupport.getZ());
        helper.runAfterDelay(1, () -> {
            // GameTest can coalesce registered tick callbacks after a catch-up, so model the
            // ordinary scene-post submission and following entity-pre turn directly here.  It
            // still invokes the production actuator's real gravity/collision path and supplies
            // no body position, target, route, or collision result from the fixture.
            pursueFixtureRetainedEdge(helper.getLevel(), worker, retainedCurrent, retainedNext);
            for (int turn = 0; turn < 100 && !FrontierV3SemanticMovement.arrived(helper.getLevel(), worker, retainedNext); turn++) {
                FrontierV3MobMotionLifecycle.advanceAtEntityBoundary(worker);
                worker.aiStep();
                pursueFixtureRetainedEdge(helper.getLevel(), worker, retainedCurrent, retainedNext);
            }
            FrontierV3MobMotionLifecycle.advanceAtEntityBoundary(worker);
            worker.aiStep();
            helper.assertTrue(FrontierV3SemanticMovement.arrived(helper.getLevel(), worker, retainedNext),
                    "a retained grade-one hand-off edge must physically advance without changing its cursor or route: actual="
                            + worker.position() + " trace=" + FrontierV3ControlledMobMotion.trace(worker));
            FrontierV3ControlledMobMotion.stop(worker);
            // The same body must actually settle on the raised support without treating the
            // actuator's gravity/lift halves as an accumulated fall from a fictitious height.
            for (int turn = 0; turn < 4; turn++) FrontierV3ControlledMobMotion.advanceAtEntityBoundary(worker);
            helper.assertTrue(worker.isAlive() && worker.getHealth() == worker.getMaxHealth()
                            && worker.fallDistance == 0.0F,
                    "a completed one-block ascent must land without synthetic fall damage: health="
                            + worker.getHealth() + " fallDistance=" + worker.fallDistance);
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void retainedProductionEdgeRequiresItsNamedPhysicalSupport(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 0, 4));
        helper.getLevel().setBlock(origin, Blocks.STONE.defaultBlockState(), 3);
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 1.0D, 4.5D));
        SurfaceAnchor target = SurfaceAnchor.at(origin.getX() - 1, origin.getY() + 1, origin.getZ());

        helper.assertFalse(FrontierV3ProductionWorkSceneExecutor.clearNextBody(helper.getLevel(), worker, target),
                "an air cell below a retained target body is an owned blocked edge, not a valid upward movement target");
        BlockPos support = new BlockPos(target.x(), target.y(), target.z());
        helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(FrontierV3ProductionWorkSceneExecutor.clearNextBody(helper.getLevel(), worker, target),
                "the same exact target becomes eligible only when its named support is physically current");
        helper.succeed();
    }

    /**
     * The shared arrival provider reads named collision supports, not a hard-coded body Y or a
     * near-enough point.  Thin legitimate supports remain admissible; water and a foreign body
     * cell are explicit local blocks for an ordinary pedestrian edge.
     */
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void semanticTraversalDistinguishesThinSupportFromUndeclaredMediumAndForeignObstruction(GameTestHelper helper) {
        BlockPos support = helper.absolutePos(new BlockPos(4, 0, 4));
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 1.0D, 4.5D));
        SurfaceAnchor target = SurfaceAnchor.at(support.getX(), support.getY(), support.getZ());
        helper.getLevel().setBlock(support, Blocks.OAK_SLAB.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(FrontierV3SemanticMovement.targetIsNavigable(helper.getLevel(), worker, target),
                "a declared slab support must remain a valid semantic pedestrian target");
        helper.getLevel().setBlock(support, Blocks.WHITE_CARPET.defaultBlockState(), 3);
        helper.assertTrue(FrontierV3SemanticMovement.targetIsNavigable(helper.getLevel(), worker, target),
                "a declared carpet support must remain a valid semantic pedestrian target");
        helper.getLevel().setBlock(support, Blocks.FARMLAND.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(), Blocks.WHEAT.defaultBlockState(), 3);
        helper.assertTrue(FrontierV3SemanticMovement.targetIsNavigable(helper.getLevel(), worker, target),
                "a normal crop workstation must use its farmland support and collision-clear crop body cell");
        helper.getLevel().setBlock(support.above(), Blocks.WATER.defaultBlockState(), 3);
        helper.assertFalse(FrontierV3SemanticMovement.targetIsNavigable(helper.getLevel(), worker, target),
                "water in an ordinary pedestrian body cell is an explicit undeclared-medium block");
        helper.getLevel().setBlock(support.above(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertFalse(FrontierV3SemanticMovement.targetIsNavigable(helper.getLevel(), worker, target),
                "a foreign occupied body cell is an explicit clearance block, never a proximity arrival");
        worker.discard(); helper.succeed();
    }

    /**
     * A second naturally loaded body can occupy a retained production column after COLD
     * admission.  The exact worker may use only the fixed current/next neighborhood to pass
     * it; no alternate route or cursor is supplied to the physical actuator.
     */
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 160)
    public static void productionTraversalYieldsAroundLiveBodyWithoutChangingRetainedCheckpoint(GameTestHelper helper) {
        BlockPos currentSupport = helper.absolutePos(new BlockPos(3, 0, 3));
        BlockPos nextSupport = currentSupport.east();
        for (BlockPos support : List.of(currentSupport, currentSupport.north(), currentSupport.south(), nextSupport,
                nextSupport.north(), nextSupport.south())) {
            helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        // GameTestHelper converts these body positions from template-relative coordinates;
        // the retained supports above are deliberately absolute world observations.
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(3.5D, 1.0D, 3.5D));
        Zombie blocker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 1.0D, 3.5D));
        SurfaceAnchor current = SurfaceAnchor.at(currentSupport.getX(), currentSupport.getY(), currentSupport.getZ());
        SurfaceAnchor next = SurfaceAnchor.at(nextSupport.getX(), nextSupport.getY(), nextSupport.getZ());
        LocalNavigationEnvelope envelope = LocalNavigationEnvelope.around(current.standingBody(), next.standingBody());

        helper.runAfterDelay(1, () -> {
            try {
                for (int turn = 0; turn < 32; turn++) {
                    pursueFixtureRetainedEdge(helper.getLevel(), worker, current, next);
                    FrontierV3MobMotionLifecycle.advanceAtEntityBoundary(worker);
                    worker.aiStep();
                    BlockPosition support = support(worker.position());
                    helper.assertTrue(envelope.contains(support),
                            "a production yield must remain inside the two-support HOT envelope: " + worker.position());
                    helper.assertTrue(!FrontierV3SemanticMovement.arrived(helper.getLevel(), worker, next),
                            "an occupied exact station cannot award production arrival");
                }
                blocker.discard();
                for (int turn = 0; turn < 32; turn++) {
                    pursueFixtureRetainedEdge(helper.getLevel(), worker, current, next);
                    FrontierV3MobMotionLifecycle.advanceAtEntityBoundary(worker);
                    worker.aiStep();
                }
                helper.assertTrue(FrontierV3SurfaceObservation.at(worker, next),
                        "only the original retained production checkpoint may complete the yielded edge: " + worker.position());
                FrontierV3ControlledMobMotion.stop(worker); worker.discard(); helper.succeed();
            } catch (RuntimeException failure) {
                FrontierV3ControlledMobMotion.stop(worker); worker.discard(); blocker.discard(); throw failure;
            }
        });
    }

    /**
     * The retained workshop corridor descends diagonally immediately after its first level
     * hand-off edge.  A no-AI body must physically walk off that lip and settle on the named
     * lower support; retaining an envelope must not leave it hovering at the old datum.
     */
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 120)
    public static void productionTraversalSettlesADescendingDiagonalRetainedEdge(GameTestHelper helper) {
        BlockPos currentSupport = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos nextSupport = currentSupport.south().below();
        for (BlockPos support : List.of(currentSupport, nextSupport)) {
            helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 1.0D, 4.5D));
        SurfaceAnchor current = SurfaceAnchor.at(currentSupport.getX(), currentSupport.getY(), currentSupport.getZ());
        SurfaceAnchor next = SurfaceAnchor.at(nextSupport.getX(), nextSupport.getY(), nextSupport.getZ());

        helper.runAfterDelay(1, () -> {
            for (int turn = 0; turn < 45; turn++) {
                if (!FrontierV3SemanticMovement.arrived(helper.getLevel(), worker, next))
                    pursueFixtureRetainedEdge(helper.getLevel(), worker, current, next);
                FrontierV3MobMotionLifecycle.advanceAtEntityBoundary(worker);
                worker.aiStep();
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(helper.getLevel(), worker, next),
                    "a descending retained production edge must reach its named lower support without changing cursor authority: "
                            + worker.position() + " trace=" + FrontierV3ControlledMobMotion.trace(worker));
            FrontierV3GoalNavigation.stop(worker); worker.discard(); helper.succeed();
        });
    }

    /** A fractional in-flight body must not seed a production cursor from its rounded cell. */
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void productionHandoffRequiresCurrentObservedSupport(GameTestHelper helper) {
        BlockPos support = helper.absolutePos(new BlockPos(4, 0, 4));
        helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 1.0D, 4.5D));
        BodyPosition observed = new BodyPosition(support.getX(), support.getY() + 1, support.getZ());

        helper.assertTrue(FrontierV3ProductionWorkSceneExecutor.observedHandoffSurfaceIsCurrent(helper.getLevel(), worker, observed),
                "a body standing at its inferred support may transfer its exact production custody");
        worker.setPos(worker.getX(), worker.getY() + .90D, worker.getZ());
        helper.assertFalse(FrontierV3ProductionWorkSceneExecutor.observedHandoffSurfaceIsCurrent(helper.getLevel(), worker, observed),
                "the same rounded body cell while vertically in-flight must defer the transfer rather than invent a floor");
        helper.succeed();
    }

    /** A settled accepted hand-off retires only historical fall evidence from the exact body. */
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void acceptedProductionHandoffClearsOnlyGroundedHistoricalFallDistance(GameTestHelper helper) {
        BlockPos support = helper.absolutePos(new BlockPos(4, 0, 4));
        helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 1.0D, 4.5D));
        BodyPosition observed = new BodyPosition(support.getX(), support.getY() + 1, support.getZ());

        worker.fallDistance = 64.0F;
        helper.assertTrue(FrontierV3ProductionWorkSceneExecutor.clearHistoricalFallDistanceAtAcceptedHandoff(helper.getLevel(), worker, observed),
                "an exact grounded production hand-off must retire its old vanilla fall counter");
        helper.assertTrue(worker.fallDistance == 0.0F,
                "clearing an accepted hand-off must preserve the same body and only retire historical fall evidence");
        worker.setPos(worker.getX(), worker.getY() + .90D, worker.getZ());
        worker.fallDistance = 6.0F;
        helper.assertFalse(FrontierV3ProductionWorkSceneExecutor.clearHistoricalFallDistanceAtAcceptedHandoff(helper.getLevel(), worker, observed),
                "an in-flight body must retain ordinary fall evidence instead of borrowing the old grounded hand-off");
        helper.assertTrue(worker.fallDistance == 6.0F,
                "an unsupported body must keep its own physical fall counter");
        helper.succeed();
    }

    /** A retained scene body on its named support must not manufacture fall velocity between hand-off turns. */
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void groundedHandoffBodyDoesNotAccumulateSyntheticFallVelocity(GameTestHelper helper) {
        BlockPos support = helper.absolutePos(new BlockPos(4, 0, 4));
        helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 1.0D, 4.5D));
        FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(worker);

        helper.runAfterDelay(1, () -> {
            for (int turn = 0; turn < 16; turn++) FrontierV3ControlledMobMotion.advanceAtEntityBoundary(worker);
            helper.assertTrue(Math.abs(worker.getY() - support.getY() - 1.0D) <= 0.01D
                            && Math.abs(worker.getDeltaMovement().y) <= 1.0E-8D,
                    "a grounded retained body must keep its exact hand-off floor without synthetic fall momentum: position="
                            + worker.position() + " velocity=" + worker.getDeltaMovement());
            FrontierV3ControlledMobMotion.stop(worker);
            helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 60)
    public static void harvestTargetWithForeignLivingBodyIsAnExactClearanceBlocker(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 0, 2));
        helper.getLevel().setBlock(origin, Blocks.FARMLAND.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.east(), Blocks.FARMLAND.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.above(), Blocks.WHEAT.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CropBlock.AGE, 7), 3);
        helper.getLevel().setBlock(origin.east().above(), Blocks.WHEAT.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CropBlock.AGE, 7), 3);
        helper.getLevel().setBlock(origin.above(2), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.east().above(2), Blocks.AIR.defaultBlockState(), 3);
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 2.5D));
        Villager blocker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(3.5D, 1.0D, 2.5D));
        SurfaceAnchor target = SurfaceAnchor.at(origin.getX() + 1, origin.getY(), origin.getZ());
        helper.runAtTickTime(2, () -> {
            helper.assertValueEqual(FrontierV3SemanticMovement.target(helper.getLevel(), worker, target),
                    io.farfrontier.palemirror.frontier.v3.model.SemanticTraversalArrival.Disposition.BLOCKED_CLEARANCE,
                    "a retained harvest target occupied by a foreign living body must not be reported navigable");
            FrontierV3ControlledMobMotion.stop(worker); worker.discard(); blocker.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void cropTendingIsAStationaryWorkPoseNotAnOrbit(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 0, 2));
        helper.getLevel().setBlock(origin, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.above(2), Blocks.AIR.defaultBlockState(), 3);
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 2.5D));
        Vec3 station = worker.position();
        for (int turn = 1; turn <= 16; turn++) helper.runAtTickTime(turn, () -> {
            FrontierV3ControlledMobMotion.showStationWorkGesture(helper.getLevel(), worker);
            helper.assertTrue(worker.position().distanceToSqr(station) < .0001D,
                    "the production work gesture must not move the worker away from its station: " + worker.position());
        });
        helper.runAtTickTime(17, () -> {
            helper.assertTrue(worker.position().distanceToSqr(station) < .0001D,
                    "a crop work gesture must stay at its station rather than circle as filler: " + worker.position());
            FrontierV3ControlledMobMotion.clearStationWorkGesture(helper.getLevel(), worker);
            helper.succeed();
        });
    }

    /** Disposable path-provider fixture; no canonical actor or activity is admitted here. */
    private static void pursueFixtureRetainedEdge(net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.entity.Mob worker, SurfaceAnchor current, SurfaceAnchor next) {
        FrontierV3GoalNavigation.pursue(level, worker, FrontierV3GoalNavigation.Goal.station(next,
                new FrontierV3NavigationScope.Restricted(io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope.around(
                        current.standingBody(), next.standingBody()))));
    }

    private static BlockPosition support(Vec3 feet) {
        return new BlockPosition((int) Math.floor(feet.x), (int) Math.floor(feet.y) - 1, (int) Math.floor(feet.z));
    }
}
