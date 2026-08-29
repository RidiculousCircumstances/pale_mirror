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
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
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
import net.minecraft.world.level.block.Blocks;
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

    private static void prepareFloor(ServerLevel level, BlockPos position) {
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(position.above(), Blocks.AIR.defaultBlockState(), 3);
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
