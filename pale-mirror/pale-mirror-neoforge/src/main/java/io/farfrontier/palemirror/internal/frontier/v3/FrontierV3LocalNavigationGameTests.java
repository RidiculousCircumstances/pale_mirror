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
        Vec3 retainedNext = new Vec3(nextSupport.getX() + .5D, nextSupport.getY() + 1.0D, nextSupport.getZ() + .5D);
        helper.runAfterDelay(1, () -> {
            // GameTest can coalesce registered tick callbacks after a catch-up, so model the
            // ordinary scene-post submission and following entity-pre turn directly here.  It
            // still invokes the production actuator's real gravity/collision path and supplies
            // no body position, target, route, or collision result from the fixture.
            FrontierV3ControlledMobMotion.moveToward(helper.getLevel(), worker, retainedNext);
            for (int turn = 0; turn < 24; turn++) {
                FrontierV3ControlledMobMotion.advanceAtEntityBoundary(worker);
                FrontierV3ControlledMobMotion.moveToward(helper.getLevel(), worker, retainedNext);
            }
            FrontierV3ControlledMobMotion.advanceAtEntityBoundary(worker);
            helper.assertTrue(worker.getX() <= nextSupport.getX() + .85D && worker.getY() >= nextSupport.getY() + .65D,
                    "a retained grade-one hand-off edge must physically advance without changing its cursor or route: actual="
                            + worker.position() + " trace=" + FrontierV3ControlledMobMotion.trace(worker));
            FrontierV3ControlledMobMotion.stop(worker);
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
     * Farmland's real collision top is 15/16 of a block, while the retained semantic surface
     * remains its exact integer floor cell.  The common support provider must therefore hand
     * the actuator that physical feet height; otherwise each gravity turn is mistaken for a
     * new ascent and the worker never starts the next retained horizontal edge.
     */
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void retainedHarvestEdgeUsesSharedFarmlandSupportTopWithoutAscentLoop(GameTestHelper helper) {
        BlockPos currentSupport = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos nextSupport = currentSupport.north();
        for (BlockPos support : List.of(currentSupport, nextSupport)) {
            helper.getLevel().setBlock(support, Blocks.FARMLAND.defaultBlockState(), 3);
            helper.getLevel().setBlock(support.above(), Blocks.WHEAT.defaultBlockState(), 3);
            helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(4.5D, 1.0D, 4.5D));
        SurfaceAnchor current = SurfaceAnchor.at(currentSupport.getX(), currentSupport.getY(), currentSupport.getZ());
        SurfaceAnchor next = SurfaceAnchor.at(nextSupport.getX(), nextSupport.getY(), nextSupport.getZ());
        Vec3 physicalNext = FrontierV3SemanticMovement.point(helper.getLevel(), next);
        helper.assertTrue(Math.abs(physicalNext.y - (nextSupport.getY() + .9375D)) <= 1.0E-8D,
                "the shared support point must be the real farmland top, not its logical air cell: " + physicalNext);
        FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(worker);
        helper.runAfterDelay(1, () -> {
            for (int turn = 0; turn < 24; turn++) {
                FrontierV3ControlledMobMotion.pursueRetainedSemanticCheckpoint(helper.getLevel(), worker, physicalNext,
                        LocalNavigationEnvelope.around(current.standingBody(), next.standingBody()));
                FrontierV3ControlledMobMotion.advanceAtEntityBoundary(worker);
            }
            helper.assertTrue(FrontierV3SemanticMovement.arrived(helper.getLevel(), worker, next),
                    "a retained harvest worker must reach the next exact farmland support instead of repeating ascent: actual="
                            + worker.position() + " motion=" + FrontierV3ControlledMobMotion.motionObservation(worker));
            helper.assertTrue(FrontierV3SurfaceObservation.observedAt(worker, next).equals(next.standingBody()),
                    "the fractional farmland feet position must checkpoint as the named retained semantic body, not floor into its support cell");
            helper.assertTrue(FrontierV3ControlledMobMotion.trace(worker).stream().anyMatch(sample -> sample.horizontalVelocity() > .0D),
                    "farmland support must admit an actual horizontal collision move");
            FrontierV3ControlledMobMotion.stop(worker); worker.discard(); helper.succeed();
        });
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
                boolean yielded = false;
                for (int turn = 0; turn < 32; turn++) {
                    FrontierV3ControlledMobMotion.advance(worker);
                    FrontierV3ProductionWorkSceneExecutor.pursueRetainedTraversalEdge(helper.getLevel(), worker, current, next);
                    BlockPosition support = support(worker.position());
                    helper.assertTrue(envelope.contains(support),
                            "a production yield must remain inside the two-support HOT envelope: " + worker.position());
                    yielded |= worker.getBlockZ() != currentSupport.getZ();
                }
                helper.assertTrue(yielded,
                        "a live occupied direct body column must use bounded physical latitude rather than pin the exact worker: "
                                + FrontierV3ControlledMobMotion.trace(worker));
                blocker.discard();
                for (int turn = 0; turn < 32; turn++) {
                    FrontierV3ControlledMobMotion.advance(worker);
                    FrontierV3ProductionWorkSceneExecutor.pursueRetainedTraversalEdge(helper.getLevel(), worker, current, next);
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

        for (int turn = 1; turn <= 32; turn++) {
            helper.runAtTickTime(turn, () -> {
                FrontierV3ControlledMobMotion.advance(worker);
                if (!FrontierV3SemanticMovement.arrived(helper.getLevel(), worker, next)) {
                    FrontierV3ProductionWorkSceneExecutor.pursueRetainedTraversalEdge(helper.getLevel(), worker, current, next);
                }
            });
        }
        helper.runAtTickTime(33, () -> {
            FrontierV3ControlledMobMotion.advance(worker);
            helper.assertTrue(FrontierV3SemanticMovement.arrived(helper.getLevel(), worker, next),
                    "a descending retained production edge must reach its named lower support without changing cursor authority: "
                            + worker.position() + " trace=" + FrontierV3ControlledMobMotion.trace(worker));
            FrontierV3ControlledMobMotion.stop(worker); worker.discard(); helper.succeed();
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

    /** A reserved production worker must not retain an old ambient WORK actuator into hand-off. */
    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void productionPreLeaseReservationStopsOnlyTheAmbientLocalPose(GameTestHelper helper) {
        BlockPos support = helper.absolutePos(new BlockPos(4, 0, 4));
        helper.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(support.getX() + .5D, support.getY() + 1.0D, support.getZ() + .5D));
        helper.runAtTickTime(1, () -> FrontierV3ControlledMobMotion.followContinuously(helper.getLevel(), worker,
                new Vec3(support.getX() + 4.5D, support.getY() + 1.0D, support.getZ() + .5D)));
        helper.runAtTickTime(2, () -> {
            FrontierV3ControlledMobMotion.advance(worker);
            double observedX = worker.getX(), observedY = worker.getY(), observedZ = worker.getZ();
            FrontierV3AmbientActorExecutor.holdForPreLeaseHandoff(worker);
            FrontierV3ControlledMobMotion.advance(worker);
            helper.assertTrue("IDLE".equals(FrontierV3ControlledMobMotion.readiness(worker))
                            && Math.abs(worker.getX() - observedX) < 1.0E-8D
                            && Math.abs(worker.getY() - observedY) < 1.0E-8D
                            && Math.abs(worker.getZ() - observedZ) < 1.0E-8D,
                    "a production pre-lease hold must retire only the old ambient actuator, without moving the exact body: " + worker.position());
            helper.succeed();
        });
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

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void continuousTendingSurvivesOneShotSceneSubmissionUntilRealHandoff(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 0, 2));
        for (int x = 0; x <= 5; x++) {
            helper.getLevel().setBlock(origin.offset(x, 0, 0), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(origin.offset(x, 1, 0), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(origin.offset(x, 2, 0), Blocks.AIR.defaultBlockState(), 3);
        }
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 1.0D, 2.5D));
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

    @GameTest(batch = "pm-frontier-v3-scene-local-navigation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void retainedHarvestEdgeRemainsContinuousInsideItsSemanticEnvelope(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(2, 0, 2));
        for (int x = 0; x <= 5; x++) {
            helper.getLevel().setBlock(origin.offset(x, 0, 0), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(origin.offset(x, 1, 0), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(origin.offset(x, 2, 0), Blocks.AIR.defaultBlockState(), 3);
        }
        Zombie worker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(2.5D, 1.0D, 2.5D));
        BlockPosition formerCrop = new BlockPosition(origin.getX(), origin.getY() + 1, origin.getZ());
        SurfaceAnchor current = SurfaceAnchor.at(origin.getX(), origin.getY(), origin.getZ());
        SurfaceAnchor next = SurfaceAnchor.at(origin.getX() + 1, origin.getY(), origin.getZ());
        LocalNavigationEnvelope envelope = LocalNavigationEnvelope.around(current.standingBody(), next.standingBody());
        Vec3 target = new Vec3(next.x() + .5D, next.y() + 1.0D, next.z() + .5D);
        // A production field does not travel through empty air: the next exact standing cell
        // contains the mature crop that it is about to harvest.  Keep that collision shape in
        // this EntityTick-owned regression instead of proving only an empty corridor.
        helper.getLevel().setBlock(origin.east().above(), Blocks.WHEAT.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CropBlock.AGE, 7), 3);

        helper.runAtTickTime(1, () -> FrontierV3ControlledMobMotion.tendCurrentCrop(helper.getLevel(), worker, formerCrop));
        // One scene hand-off is deliberately enough.  A later semantic callback must not be
        // required to keep the post-crop field edge moving. Do not invoke the actuator directly
        // below: this proves the ordinary EntityTick.Pre owner that the live field uses.
        helper.runAtTickTime(3, () -> FrontierV3ControlledMobMotion.pursueRetainedSemanticCheckpoint(
                helper.getLevel(), worker, target, envelope));
        helper.runAfterDelay(24, () -> {
            List<FrontierV3ControlledMobMotion.MotionSample> trace = FrontierV3ControlledMobMotion.trace(worker);
            long forwardTurns = trace.stream().filter(value -> value.horizontalVelocity() >= .15D).count();
            boolean inside = trace.stream().allMatch(value -> envelope.contains(support(new Vec3(value.x(), value.y(), value.z()))));
            helper.assertTrue(worker.getX() >= origin.getX() + 1.1D && forwardTurns >= 3 && inside,
                    "a harvested field edge must continue at normal cadence only inside its declared envelope: " + trace);
            FrontierV3ControlledMobMotion.stop(worker); worker.discard(); helper.succeed();
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
        BlockPosition crop = new BlockPosition(origin.getX(), origin.getY() + 1, origin.getZ());
        helper.runAtTickTime(1, () -> FrontierV3ControlledMobMotion.tendCurrentCrop(helper.getLevel(), worker, crop));
        for (int turn = 2; turn <= 16; turn++) helper.runAtTickTime(turn, () -> FrontierV3ControlledMobMotion.advanceAtEntityBoundary(worker));
        helper.runAtTickTime(17, () -> {
            List<FrontierV3ControlledMobMotion.MotionSample> trace = FrontierV3ControlledMobMotion.trace(worker);
            double totalHorizontalMotion = trace.stream().mapToDouble(FrontierV3ControlledMobMotion.MotionSample::horizontalVelocity).sum();
            helper.assertTrue(totalHorizontalMotion < .01D,
                    "a retained crop work pose must stay at its exact station rather than circle as filler: " + trace);
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
