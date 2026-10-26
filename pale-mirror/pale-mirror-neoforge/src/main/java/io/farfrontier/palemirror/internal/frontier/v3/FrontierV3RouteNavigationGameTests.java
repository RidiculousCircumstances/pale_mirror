package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.stream.IntStream;

/** Real Minecraft candidates discriminate a route hint from a hard task restriction. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3RouteNavigationGameTests {
    private FrontierV3RouteNavigationGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 110)
    public static void wideBodyCentersFromAdjacentNativeEndpointWithoutFictionalArrival(GameTestHelper helper) {
        floor(helper); var level = helper.getLevel(); var target = anchor(helper, 4, 4);
        var animal = helper.spawnWithNoFreeWill(EntityType.DONKEY,
                new Vec3(4.7088D, 1.0D, 5.3651D));
        var goal = FrontierV3GoalNavigation.Goal.routed(order(target), List.of(target), bounds(helper));
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(!FrontierV3SemanticMovement.arrived(level, animal, target), "native endpoint is not semantic arrival");
            for (int turn = 0; turn < 180 && !FrontierV3SemanticMovement.arrived(level, animal, target); turn++) {
                FrontierV3GoalNavigation.pursue(level, animal, goal);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(animal); animal.aiStep();
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, animal, target),
                    "wide body must actually enter and centre on its named support: " + animal.position());
            FrontierV3GoalNavigation.stop(animal); animal.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void widePedestrianObservesBlockerAtActualNativeNodeOffset(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        var animal = helper.spawnWithNoFreeWill(EntityType.DONKEY, new Vec3(2.5D, 1.0D, 3.5D));
        var occupant = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(3.5D, 1.0D, 4.65D));
        var target = anchor(helper, 5, 3);
        var scope = new FrontierV3NavigationScope.ObservedWorld(bounds(helper));
        helper.runAfterDelay(1, () -> {
            FrontierV3BodyObservation.refreshGroundContact(level, animal);
            var query = FrontierV3PedestrianTraffic.query(level, animal,
                    new BlockPos(target.x(), target.y() + 1, target.z()), scope);
            helper.assertTrue(query.blockers().stream().anyMatch(body -> body.id().equals(occupant.getUUID())),
                    "traffic must inspect the wide body's native-node offset, not the narrow station centre: " + query);
            helper.assertTrue(FrontierV3PhysicalPathPolicy.reject(level, query.path(), scope).isEmpty(),
                    "the same provider must find the available physical detour");
            animal.discard(); occupant.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 110)
    public static void wideBodyCentersFromOverhangingRaisedApron(GameTestHelper helper) {
        floor(helper); var level = helper.getLevel();
        for (int x = 1; x <= 6; x++) for (int z = 1; z <= 6; z++)
            level.setBlock(helper.absolutePos(new BlockPos(x, 3, z)), Blocks.AIR.defaultBlockState(), 3);
        for (int x = 3; x <= 5; x++) for (int z = 2; z <= 4; z++)
            level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
        var support = helper.absolutePos(new BlockPos(4, 1, 4));
        var target = SurfaceAnchor.at(support.getX(), support.getY(), support.getZ());
        var animal = helper.spawnWithNoFreeWill(EntityType.DONKEY, new Vec3(4.7088D, 2.0D, 5.3651D));
        var goal = FrontierV3GoalNavigation.Goal.routed(order(target), List.of(target), bounds(helper));
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(level.noCollision(animal, animal.getBoundingBox()), "fixture has actual body clearance");
            helper.assertTrue(level.getBlockState(BlockPos.containing(animal.getX(), animal.getY() - 0.000001D,
                    animal.getZ())).getCollisionShape(level, animal.blockPosition().below()).isEmpty(),
                    "the centre overhangs: this is not a flat-floor reproduction");
            helper.assertTrue(FrontierV3BodyObservation.hasDryContact(level, animal.getBoundingBox()),
                    "the physical footprint still contacts the raised apron");
            helper.assertTrue(!FrontierV3SemanticMovement.arrived(level, animal, target), "contact is not arrival");
            for (int turn = 0; turn < 180 && !FrontierV3SemanticMovement.arrived(level, animal, target); turn++) {
                FrontierV3GoalNavigation.pursue(level, animal, goal);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(animal); animal.aiStep();
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, animal, target),
                    "the same native provider must actually centre the supported wide body: " + animal.position()
                            + "; support=" + FrontierV3BodyObservation.capture(animal)
                            + "; navigation=" + FrontierV3MinecraftGoalNavigation.observation(animal)
                            + "; collisions=" + java.util.stream.StreamSupport.stream(level.getBlockCollisions(animal,
                            animal.getBoundingBox().expandTowards(FrontierV3SemanticMovement.point(level, target)
                            .subtract(animal.position())).deflate(0.000001D)).spliterator(), false).toList());
            FrontierV3GoalNavigation.stop(animal); animal.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 110)
    public static void chestAnimalFollowsRaisedAdvisoryApronWithoutChangingItsGoal(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        for (int z = 1; z <= 6; z++) level.setBlock(helper.absolutePos(new BlockPos(3, 1, z)), Blocks.STONE.defaultBlockState(), 3);
        var animal = helper.spawnWithNoFreeWill(EntityType.DONKEY, new Vec3(3.5D, 2.0D, 2.5D));
        var start = helper.absolutePos(new BlockPos(3, 1, 2));
        var target = SurfaceAnchor.at(start.getX(), start.getY(), start.getZ() + 3);
        var hint = IntStream.rangeClosed(0, 3).mapToObj(offset ->
                SurfaceAnchor.at(start.getX(), start.getY(), start.getZ() + offset)).toList();
        var goal = FrontierV3GoalNavigation.Goal.routed(order(target), hint, bounds(helper));
        helper.runAfterDelay(1, () -> {
            for (int turn = 0; turn < 180 && !FrontierV3SemanticMovement.arrived(level, animal, target); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, animal, goal);
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "same pedestrian provider must support the actual animal footprint: " + result + "; body=" + animal.position());
                FrontierV3GoalNavigation.advanceAtEntityBoundary(animal);
                animal.aiStep();
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, animal, target),
                    "the animal must physically reach the declared supported station: " + animal.position()
                            + "; target=" + target + "; support=" + FrontierV3BodyObservation.capture(animal)
                            + "; semantic=" + FrontierV3SemanticMovement.at(level, animal, target)
                            + "; nativeDone=" + animal.getNavigation().isDone()
                            + "; control=" + FrontierV3MinecraftGoalNavigation.observation(animal));
            FrontierV3GoalNavigation.stop(animal); animal.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void enclosedPedestrianReportsLivingBlockersAndReachabilityUsesTheSameTrafficPolicy(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        Villager actor = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(3.5D, 1.0D, 3.5D));
        var occupants = List.of(
                helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 3.5D)),
                helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(4.5D, 1.0D, 3.5D)),
                helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(3.5D, 1.0D, 2.5D)),
                helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(3.5D, 1.0D, 4.5D)));
        SurfaceAnchor target = anchor(helper, 6, 3);
        var scope = new FrontierV3NavigationScope.ObservedWorld(bounds(helper));
        helper.runAfterDelay(1, () -> {
            actor.setOnGround(true);
            var query = FrontierV3PedestrianTraffic.query(level, actor,
                    new BlockPos(target.x(), target.y() + 1, target.z()), scope);
            helper.assertTrue(query.trafficBlocked(level, scope), "a clear terrain route must identify the living enclosure: "
                    + query + "; rejection=" + FrontierV3PhysicalPathPolicy.reject(level, query.path(), scope));
            helper.assertTrue(query.blockers().stream().anyMatch(blocker -> blocker.id().equals(occupants.get(1).getUUID())),
                    "the obstruction must retain the exact east-side blocker, not a generic no-path string");
            helper.assertFalse(FrontierV3GoalNavigation.canReach(level, actor, FrontierV3GoalNavigation.Goal.station(target, scope)),
                    "a prospective destination cannot ignore the occupants which execution will respect");
            // Negative discriminator: with bodies gone this is not a terrain failure.
            occupants.forEach(Villager::discard);
            helper.assertTrue(FrontierV3GoalNavigation.canReach(level, actor, FrontierV3GoalNavigation.Goal.station(target, scope)),
                    "the same goal must become reachable without a reconnect or terrain edit");
            actor.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 110)
    public static void pedestrianDetoursAroundResidentWithoutChangingItsGoal(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 0, 3));
        for (int x = 1; x <= 6; x++) for (int z = 1; z <= 6; z++) {
            BlockPos support = helper.absolutePos(new BlockPos(x, 0, z));
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 3.5D));
        Villager occupant = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(4.5D, 1.0D, 3.5D));
        SurfaceAnchor from = SurfaceAnchor.at(start.getX(), start.getY(), start.getZ());
        SurfaceAnchor to = SurfaceAnchor.at(start.getX() + 3, start.getY(), start.getZ());
        var goal = new FrontierV3GoalNavigation.Goal(to,
                io.farfrontier.palemirror.frontier.v3.model.TraversalCapability.PEDESTRIAN,
                LocalNavigationEnvelope.around(from.standingBody(), to.standingBody()));
        helper.runAfterDelay(1, () -> {
            boolean detoured = false;
            for (int turn = 0; turn < 90 && !FrontierV3SemanticMovement.arrived(level, worker, to); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, worker, goal);
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "a resident is a transient physical obstacle, not an impossible task: " + result);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(worker);
                worker.aiStep();
                if (Math.abs(worker.getZ() - (start.getZ() + .5D)) > .35D) detoured = true;
            }
            helper.assertTrue(detoured, "the native path must go around the resident's occupied body volume");
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, worker, to), "the same goal must be reached");
            FrontierV3GoalNavigation.stop(worker); worker.discard(); occupant.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void sharedGoalBypassesBlockedHintBeyondOldStripeWithoutFalseIntermediateArrival(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        for (int z = 0; z <= 3; z++) {
            BlockPos wall = helper.absolutePos(new BlockPos(3, 1, z));
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
        }
        Villager actor = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(1.5D, 1.0D, 1.5D));
        List<SurfaceAnchor> hint = IntStream.rangeClosed(1, 6).mapToObj(x -> anchor(helper, x, 1)).toList();
        SurfaceAnchor target = hint.getLast();
        var order = order(target);
        var bounds = bounds(helper);
        var goal = FrontierV3GoalNavigation.Goal.routed(order, hint, bounds);
        helper.runAfterDelay(1, () -> {
            actor.setOnGround(true);
            var candidate = actor.getNavigation().createPath(new BlockPos(target.x(), target.y() + 1, target.z()), 0);
            helper.assertTrue(candidate != null && candidate.canReach(), "Minecraft must find the real building detour");
            var obsolete = new FrontierV3NavigationScope.Restricted(LocalNavigationEnvelope.localLeg(hint, target));
            helper.assertTrue(FrontierV3PhysicalPathPolicy.reject(level, candidate, obsolete).orElseThrow().reason()
                    == FrontierV3GoalNavigation.BlockReason.OFF_CONTRACT, "the old stripe must reject this exact candidate");
            helper.assertTrue(FrontierV3PhysicalPathPolicy.reject(level, candidate, goal.scope()).isEmpty(),
                    "shared physical policy must accept the loaded dry detour inside real world bounds");
            for (int turn = 0; turn < 180 && !FrontierV3SemanticMovement.arrived(level, actor, target); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, actor, goal);
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "a micro-waypoint must not complete the semantic order: " + result);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(actor);
                actor.aiStep();
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, actor, target),
                    "the same actor must reach the original final station: " + actor.position());
            helper.assertTrue(FrontierV3GoalNavigation.pursue(level, actor, goal).status()
                    == FrontierV3GoalNavigation.Status.ARRIVED, "only final physical arrival completes navigation");
            helper.assertTrue(actor.isNoAi(), "native task AI must not acquire activity ownership");
            FrontierV3GoalNavigation.stop(actor); actor.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void unchangedGoalResumesAfterPhysicalObstructionClearsWithoutReconnect(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 0, 2));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (x == 0 && z == 0) continue;
            level.setBlock(start.offset(x, 1, z), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(start.offset(x, 2, z), Blocks.STONE.defaultBlockState(), 3);
        }
        Villager actor = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 2.5D));
        SurfaceAnchor target = anchor(helper, 6, 2);
        var goal = FrontierV3GoalNavigation.Goal.routed(order(target), List.of(), bounds(helper));
        helper.runAtTickTime(1, () -> FrontierV3GoalNavigation.pursue(level, actor, goal));
        helper.runAtTickTime(25, () -> helper.assertTrue(FrontierV3GoalNavigation.pursue(level, actor, goal).status()
                == FrontierV3GoalNavigation.Status.BLOCKED, "a real obstruction has a finite local failure"));
        helper.runAtTickTime(50, () -> {
            level.setBlock(start.east().above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(start.east().above(2), Blocks.AIR.defaultBlockState(), 3);
            for (int turn = 0; turn < 120 && !FrontierV3SemanticMovement.arrived(level, actor, target); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, actor, goal);
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "a fresh physical candidate must resume the unchanged order: " + result);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(actor); actor.aiStep();
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, actor, target), "clearance must restore movement");
            FrontierV3GoalNavigation.stop(actor); actor.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 110)
    public static void wideTransitPassesHintBeforeCenteringButStillProvesFinalArrival(GameTestHelper helper) {
        floor(helper); var level = helper.getLevel();
        // Wide native nodes use a width-dependent offset. Leave their full
        // footprint clear: the former east/south walls blocked every transit
        // candidate, correctly selecting the ordinary final-goal fallback.
        for (int x = 1; x <= 7; x++) for (int z = 1; z <= 7; z++) {
            level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(helper.absolutePos(new BlockPos(x, 2, z)), Blocks.AIR.defaultBlockState(), 3);
        }
        var animal = helper.spawnWithNoFreeWill(EntityType.DONKEY, new Vec3(2.5, 1, 2.5));
        var hint = new java.util.ArrayList<SurfaceAnchor>();
        for (int x = 2; x <= 6; x++) hint.add(anchor(helper, x, 2));
        for (int z = 3; z <= 6; z++) hint.add(anchor(helper, 6, z));
        for (int x = 5; x >= 2; x--) hint.add(anchor(helper, x, 6));
        for (int z = 5; z >= 3; z--) hint.add(anchor(helper, 2, z));
        hint.add(anchor(helper, 3, 3)); hint.add(anchor(helper, 4, 3));
        var target = hint.getLast();
        var goal = FrontierV3GoalNavigation.Goal.routed(order(target), hint, bounds(helper));
        helper.runAfterDelay(1, () -> {
            boolean passed = false;
            for (int turn = 0; turn < 240 && !FrontierV3SemanticMovement.arrived(level, animal, target); turn++) {
                var stations = FrontierV3MinecraftGoalNavigation.observation(animal).targets();
                var handoff = stations.isEmpty() ? java.util.Optional.<SurfaceAnchor>empty()
                        : FrontierV3MinecraftGoalNavigation.transitHandoff(animal,
                            new FrontierV3MinecraftGoalNavigation.Demand(stations, goal.scope(), goal.order()), goal.legalStations());
                if (handoff.isPresent()) {
                    helper.assertTrue(!goal.legalStations().contains(handoff.orElseThrow()), "transit must not waive final arrival");
                    passed |= !FrontierV3SemanticMovement.arrived(level, animal, handoff.orElseThrow());
                }
                var result = FrontierV3GoalNavigation.pursue(level, animal, goal);
                helper.assertTrue(result.status() != FrontierV3GoalNavigation.Status.ARRIVED,
                        "passing a hint cannot report the terminal task as complete");
                FrontierV3GoalNavigation.advanceAtEntityBoundary(animal); animal.aiStep();
            }
            helper.assertTrue(passed, "wide body must hand off before exact intermediate centering");
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, animal, target), "final work station still requires actual arrival: " + animal.position());
            FrontierV3GoalNavigation.stop(animal); animal.discard(); helper.succeed();
        });
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/treasure/big_air_full", timeoutTicks = 180)
    public static void unreachableFirstExitBatchDoesNotCancelTheNinthReachableExit(GameTestHelper helper) {
        floor(helper); var level = helper.getLevel();
        // All eight northern supports stand safely but cannot be reached through this wall.
        for (int x = 1; x <= 6; x++) for (int y = 1; y <= 3; y++)
            level.setBlock(helper.absolutePos(new BlockPos(x, y, 3)), Blocks.STONE.defaultBlockState(), 3);
        var candidates = new java.util.ArrayList<SurfaceAnchor>();
        for (int x = 1; x <= 4; x++) for (int z = 1; z <= 2; z++) candidates.add(anchor(helper, x, z));
        var exit = anchor(helper, 5, 5); candidates.add(exit);
        var actor = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(3.5D, 1.0D, 5.5D));
        var pass = new java.util.concurrent.atomic.AtomicReference<>(new FrontierV3GoalAlternatives(candidates));
        var later = new java.util.concurrent.atomic.AtomicBoolean();
        helper.onEachTick(() -> {
            var current = pass.get().nextPassIfExhausted(); pass.set(current);
            var batch = current.batch();
            var order = new MovementOrder(new SubjectId("test:exit-owner"), new SubjectId("test:exit-actor"), 0, 1,
                    batch, TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.ANY_DECLARED_STATION);
            var result = FrontierV3GoalNavigation.pursue(level, actor,
                    FrontierV3GoalNavigation.Goal.routed(order, List.of(), bounds(helper)));
            if (FrontierV3GoalAlternatives.reconsider(result)) pass.set(current.reject(batch));
            later.set(later.get() || batch.equals(List.of(exit)));
            FrontierV3GoalNavigation.advanceAtEntityBoundary(actor);
            if (FrontierV3SemanticMovement.arrived(level, actor, exit)) {
                helper.assertTrue(later.get(), "the later supported exit must actually be selected and walked");
                FrontierV3GoalNavigation.stop(actor); actor.discard(); helper.succeed();
            }
        });
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) for (int z = 0; z <= 7; z++) {
            BlockPos support = helper.absolutePos(new BlockPos(x, 0, z));
            helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
            if (x == 0 || x == 7 || z == 0 || z == 7) {
                helper.getLevel().setBlock(support.above(), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(support.above(2), Blocks.STONE.defaultBlockState(), 3);
            }
        }
    }

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void repeatedRoutedGoalRefreshPreservesPathAndCompletesOneBlockAscent(GameTestHelper helper) {
        floor(helper);
        var level = helper.getLevel();
        for (int x = 3; x <= 6; x++) for (int z = 1; z <= 6; z++) {
            level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(helper.absolutePos(new BlockPos(x, 2, z)), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(helper.absolutePos(new BlockPos(x, 3, z)), Blocks.AIR.defaultBlockState(), 3);
        }
        Villager actor = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(1.5D, 1.0D, 2.5D));
        List<SurfaceAnchor> hint = IntStream.rangeClosed(1, 6).mapToObj(x -> {
            BlockPos support = helper.absolutePos(new BlockPos(x, x >= 3 ? 1 : 0, 2));
            return SurfaceAnchor.at(support.getX(), support.getY(), support.getZ());
        }).toList();
        SurfaceAnchor target = hint.getLast();
        var goal = FrontierV3GoalNavigation.Goal.routed(order(target), hint, bounds(helper));
        helper.runAfterDelay(1, () -> {
            FrontierV3GoalNavigation.pursue(level, actor, goal);
            var originalPath = actor.getNavigation().getPath();
            helper.assertTrue(originalPath != null, "the real +1 path must exist");
            FrontierV3GoalNavigation.pursue(level, actor, goal);
            helper.assertTrue(actor.getNavigation().getPath() == originalPath,
                    "acquiring the native actuator must not delete the route leg and restart the same path");
            for (int turn = 0; turn < 180 && !FrontierV3SemanticMovement.arrived(level, actor, target); turn++) {
                var result = FrontierV3GoalNavigation.pursue(level, actor, goal);
                helper.assertTrue(result.status() == FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "same-goal refresh must not block the supported ascent: " + result);
                FrontierV3GoalNavigation.advanceAtEntityBoundary(actor);
                actor.aiStep();
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(level, actor, target),
                    "repeated production pursuit must complete the jump and land: " + actor.position());
            FrontierV3GoalNavigation.stop(actor);
            actor.discard(); helper.succeed();
        });
    }
    private static SurfaceAnchor anchor(GameTestHelper helper, int x, int z) {
        BlockPos support = helper.absolutePos(new BlockPos(x, 0, z));
        return SurfaceAnchor.at(support.getX(), support.getY(), support.getZ());
    }
    private static WorldBounds bounds(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return new WorldBounds(origin.getX(), origin.getZ(), 8, 8);
    }
    private static MovementOrder order(SurfaceAnchor target) {
        return new MovementOrder(new SubjectId("test:route-owner"), new SubjectId("test:route-actor"), 0, 1,
                List.of(target), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
}
