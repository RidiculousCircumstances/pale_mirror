package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeasePrepared;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.AmbientGoalKind;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/** Materialized recovery evidence for the ambient before-effect actor lease boundary. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3AmbientActorGameTests {
    private FrontierV3AmbientActorGameTests() { }

    @GameTest(batch = "pm-frontier-v3-ambient-prepared-recovery", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void preparedAmbientLeaseSurvivesRecoveryAndMaterializesOneInertBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(12, 8, 0)); prepareFloor(level, origin);
        WorldId world = new WorldId("frontier:ambient-prepared-game-test");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(world, 91L), new EphemeralStore(), 10_000);
        SubjectId resident = new SubjectId("resident:1-1");
        FrontierWorldState initial = state(runtime);
        AmbientActorLease lease = AmbientActorProcess.nextLease(initial, resident, runtime.checkpointImage().orElseThrow().instant());
        FrontierV3CommandSubmission.submit(runtime, "ambient-prepared-game-test", resident.value(), new AmbientLeasePrepared(lease));

        helper.assertValueEqual(FrontierV3AmbientLeaseRestartSafety.quarantineActiveLeases(runtime), 0,
                "restart must retain a before-effect PREPARED lease for loaded-chunk inspection");
        helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.PREPARED,
                "prepared recovery must not turn an unacknowledged body into UNKNOWN");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), resident,
                        new BlockPosition(origin.getX(), origin.getY(), origin.getZ())), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "a retained PREPARED lease may create exactly its expected loaded-world body");
        Villager body = (Villager) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), resident));
        helper.assertTrue(body != null && body.isNoAi(), "the before-HOT body must stay inert across the acknowledgement window");
        helper.assertTrue(FrontierV3AmbientActorExecutor.recognizes(runtime, body),
                "the shared graybox boundary must accept only the exact prepared V3 carrier");
        Villager forged = new Villager(net.minecraft.world.entity.EntityType.VILLAGER, level);
        forged.getPersistentData().putString(FrontierV3AmbientActorExecutor.ACTOR_KEY, resident.value());
        forged.getPersistentData().putString(FrontierV3AmbientActorExecutor.KIND_KEY, "RESIDENT");
        helper.assertFalse(FrontierV3AmbientActorExecutor.recognizes(runtime, forged),
                "a copied V3 tag without the canonical UUID is never an admissible carrier");
        FrontierV3CommandSubmission.submit(runtime, "ambient-prepared-game-test-hot", resident.value(),
                new AmbientLeaseTransition(resident, AmbientLeaseStatus.HOT));
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), resident,
                        new BlockPosition(origin.getX(), origin.getY(), origin.getZ())), FrontierV3AmbientActorExecutor.Result.CURRENT,
                "the durable HOT acknowledgement retains the one existing UUID rather than duplicating it");
        SubjectId carpetResident = new SubjectId("resident:1-2"); BlockPos carpet = origin.offset(4, 0, 0);
        level.setBlock(carpet.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(carpet, Blocks.RED_CARPET.defaultBlockState(), 3);
        level.setBlock(carpet.above(), Blocks.AIR.defaultBlockState(), 3); level.setBlock(carpet.above(2), Blocks.AIR.defaultBlockState(), 3);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state(runtime), carpetResident,
                        new BlockPosition(carpet.getX(), carpet.getY(), carpet.getZ())), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "an owned infection carpet is a physical surface, not a reason to strand the canonical actor");
        Villager carpetBody = (Villager) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state(runtime), carpetResident));
        helper.assertTrue(carpetBody != null && carpetBody.getY() >= carpet.getY() + 1.0D
                        && level.getBlockState(carpet).is(Blocks.RED_CARPET),
                "the exact body must stand above the carpet without replacing it: body="
                        + (carpetBody == null ? "missing" : carpetBody.position()) + " carpet=" + carpet);
        helper.assertFalse(FrontierV3AmbientActorExecutor.observeLeave(runtime, body, false),
                "an ordinary EntityLeave callback is too late to close a serialized HOT body into COLD");
        helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.HOT,
                "unexpected departure retains the exact saved UUID for later recovery instead of permitting a duplicate");
        helper.assertFalse(FrontierV3AmbientActorExecutor.observeLeave(runtime, body, true),
                "server teardown must not release a saved HOT body into COLD");
        helper.assertValueEqual(state(runtime).ambientLeases().get(resident).status(), AmbientLeaseStatus.HOT,
                "a graceful shutdown retains the HOT lease for exact UUID recovery after restart");
        body.discard(); carpetBody.discard(); FrontierV3AmbientActorExecutor.forget(runtime); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-ambient-local-brain", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void hotAmbientBodiesUseRoleAwareControlledMotionWithoutVanillaAi(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(0, 8, 0)); prepareSquareFloor(level, origin, 16);
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:ambient-local-brain"), 91L), new EphemeralStore(), 10_000);
        FrontierWorldState state = state(runtime);
        SubjectId farmer = new SubjectId("resident:1-1"), scout = new SubjectId("bioform:west-1");
        BlockPosition anchor = new BlockPosition(origin.getX(), origin.getY(), origin.getZ());
        AmbientActorLease farmerLease = new AmbientActorLease(farmer, anchor, io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO, 1L,
                AmbientLeaseStatus.HOT, AmbientGoalKind.WORK, new BlockPosition(anchor.x() + 12, anchor.y(), anchor.z()));
        AmbientActorLease scoutLease = new AmbientActorLease(scout, new BlockPosition(anchor.x() + 2, anchor.y(), anchor.z()),
                io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO, 1L, AmbientLeaseStatus.HOT, AmbientGoalKind.PATROL, anchor);
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, farmer, anchor), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "the farmer fixture must materialize as one exact body");
        helper.assertValueEqual(FrontierV3AmbientActorExecutor.materialize(level, state, scout,
                        new BlockPosition(anchor.x() + 2, anchor.y(), anchor.z())), FrontierV3AmbientActorExecutor.Result.APPLIED,
                "the scout fixture must materialize as one exact body");
        Villager farmerBody = (Villager) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, farmer));
        Zombie scoutBody = (Zombie) level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, scout));
        double farmerBefore = farmerBody.distanceToSqr(origin.getX() + 0.5D, farmerBody.getY(), origin.getZ() + 0.5D);
        for (int tick = 0; tick < 80; tick++) {
            FrontierV3AmbientActorExecutor.pursueLocalGoal(level, runtime, state, farmer, farmerBody, farmerLease);
            FrontierV3AmbientActorExecutor.pursueLocalGoal(level, runtime, state, scout, scoutBody, scoutLease);
        }
        helper.assertTrue(farmerBody.isNoAi() && scoutBody.isNoAi(),
                "HOT ambient bodies must stay outside uncontrolled vanilla target/combat AI");
        helper.assertTrue(farmerBody.distanceToSqr(origin.getX() + 0.5D, farmerBody.getY(), origin.getZ() + 0.5D) > farmerBefore + 0.1D,
                "the farmer must visibly work around its assigned facility rather than freeze at its hand-off point");
        helper.assertTrue(FrontierV3AmbientActorExecutor.localTarget(state, scout, scoutLease, 0L)
                        .distanceToSqr(scoutLease.handoffPosition().x() + 0.5D, scoutLease.handoffPosition().y(), scoutLease.handoffPosition().z() + 0.5D)
                        > FrontierV3AmbientActorExecutor.localTarget(state, farmer, farmerLease, 0L)
                        .distanceToSqr(farmerLease.handoffPosition().x() + 0.5D, farmerLease.handoffPosition().y(), farmerLease.handoffPosition().z() + 0.5D),
                "a scout patrol must use a wider role-specific perimeter than a farmer work cycle");
        helper.assertTrue(FrontierV3AmbientActorExecutor.localTarget(state, farmer, farmerLease, 0L)
                        .distanceToSqr(farmerLease.goalPosition().x() + 0.5D, farmerLease.goalPosition().y(), farmerLease.goalPosition().z() + 0.5D) > 25.0D,
                "ambient local motion must remain anchored at the exact exterior hand-off slot, not cross a semantic-object centre");
        helper.assertTrue(FrontierV3AmbientActorExecutor.localTarget(state, farmer, farmerLease, 0L)
                        .distanceToSqr(FrontierV3AmbientActorExecutor.localTarget(state, farmer, farmerLease, 180L)) > 0.01D,
                "a durable ambient work lease must produce a continuing cycle rather than one static target");
        farmerBody.discard(); scoutBody.discard(); FrontierV3AmbientActorExecutor.forget(runtime); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-assembly-headroom", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void exactAssemblyTargetRejectsLoadedObstructionWithoutClimbingToAnotherFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos floor = helper.absolutePos(new BlockPos(4, 8, 0));
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3); level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
        BlockPosition anchor = new BlockPosition(floor.getX(), floor.getY(), floor.getZ());
        helper.assertTrue(FrontierV3StandingPosition.hasExactHeadroom(level, anchor),
                "a canonical floor with two clear body cells admits its exact assembly cursor");
        level.setBlock(floor.above(), Blocks.GRAY_CONCRETE.defaultBlockState(), 3);
        helper.assertFalse(FrontierV3StandingPosition.hasExactHeadroom(level, anchor),
                "a player block at the exact feet cell is a loaded-world deferral, not an invitation to climb it");
        level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3); level.setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(FrontierV3StandingPosition.hasExactHeadroom(level, anchor),
                "headroom checks only the exact canonical body cells; ordinary Minecraft collision remains the support authority");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-ambient-transit-motion", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void controlledMotionUsesTheClearLaneBesideRouteSurfaceButNeverPassesThroughAFullWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos origin = helper.absolutePos(new BlockPos(0, 8, 0)); prepareSquareFloor(level, origin, 12);
        for (int x = 1; x <= 6; x++) level.setBlock(origin.offset(x, 0, 0), Blocks.GRAY_CARPET.defaultBlockState(), 3);
        Villager body = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (body == null) throw new IllegalStateException("game test could not create resident body");
        body.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 1.5D); body.setPersistenceRequired(); body.setNoAi(true);
        helper.assertTrue(level.addFreshEntity(body), "the controlled-motion fixture must enter the loaded world");
        for (int tick = 0; tick < 120; tick++) FrontierV3ControlledMobMotion.moveToward(level, body,
                new Vec3(origin.getX() + 6.5D, origin.getY(), origin.getZ() + 1.5D));
        helper.assertTrue(body.getX() > origin.getX() + 4.0D,
                "a resident must advance through the clear lane beside the materialized route surface");

        body.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 1.5D);
        for (int z = -5; z <= 5; z++) for (int y = 0; y <= 2; y++) level.setBlock(origin.offset(3, y, z), Blocks.GRAY_CONCRETE.defaultBlockState(), 3);
        for (int tick = 0; tick < 100; tick++) FrontierV3ControlledMobMotion.moveToward(level, body,
                new Vec3(origin.getX() + 7.5D, origin.getY(), origin.getZ() + 1.5D));
        helper.assertTrue(body.getX() < origin.getX() + 3.0D,
                "controlled motion may sidestep but must never cross a full materialized wall");
        body.discard(); helper.succeed();
    }

    private static void prepareFloor(ServerLevel level, BlockPos position) {
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(position.above(), Blocks.AIR.defaultBlockState(), 3);
    }
    private static void prepareSquareFloor(ServerLevel level, BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) prepareFloor(level, center.offset(x, 0, z));
    }
    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
    }
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
