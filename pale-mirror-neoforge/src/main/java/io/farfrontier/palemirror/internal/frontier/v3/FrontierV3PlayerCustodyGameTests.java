package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceTransition;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/** Materialized player withdrawal and return evidence for exact v3 inventory custody. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3PlayerCustodyGameTests {
    private FrontierV3PlayerCustodyGameTests() { }

    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void activeChestTransfersOneExactStackToAndFromItsRealPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); WorldId world = new WorldId("frontier:player-withdrawal-game-test");
        FrontierV3ServerRuntime<FrontierWorldState, io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection> runtime =
                FrontierV3ServerRuntime.start(FrontierWorldRuntimeDefinition.configuration(world, 91L), new EphemeralStore(), 10_000);
        SubjectId container = new SubjectId("container:1-depot"); BlockPos chestPosition = helper.absolutePos(new BlockPos(58, 8, 0));
        level.setBlock(chestPosition.below(), Blocks.STONE.defaultBlockState(), 3);
        ChestBlockEntity chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, chestPosition, container);
        helper.assertTrue(chest != null, "the player custody fixture needs an owned chest");
        FrontierV3CommandSubmission.submit(runtime, "player-withdrawal-prepare", container.value(), new ContainerSurfaceTransition(container, ContainerSurfaceStatus.PREPARED));
        FrontierV3CommandSubmission.submit(runtime, "player-withdrawal-active", container.value(), new ContainerSurfaceTransition(container, ContainerSurfaceStatus.ACTIVE));
        ExactItemStack exact = state(runtime).inventory().itemAt(container, 0).orElseThrow();
        var player = helper.makeMockServerPlayerInLevel();
        chest.setItem(0, FrontierV3CargoHandoffExecutor.materializedStack(exact));
        player.getInventory().setItem(0, chest.removeItemNoUpdate(0));

        helper.assertTrue(FrontierV3InventoryObservationExecutor.observeOne(level, runtime, state(runtime), FrontierV3HopperCarrierLedger.get(level),
                        new FrontierV3InventoryObservationExecutor.StoreChest(chestPosition, container), chest),
                "one exact stack physically moved to one player must become player custody");
        helper.assertValueEqual(state(runtime).inventory().items().get(exact.id()).custody(), new InventoryCustody.Player(player.getUUID()),
                "withdrawal must retain the exact canonical item identity and player UUID");
        helper.assertTrue(FrontierV3InventoryObservationExecutor.hasExactItem(player, exact),
                "the player must retain the same tagged physical stack after canonical acknowledgement");

        chest.setItem(0, player.getInventory().removeItemNoUpdate(0));
        helper.assertTrue(FrontierV3InventoryObservationExecutor.observeOne(level, runtime, state(runtime), FrontierV3HopperCarrierLedger.get(level),
                        new FrontierV3InventoryObservationExecutor.StoreChest(chestPosition, container), chest),
                "the same tagged player stack returned to the owned chest must be observed once");
        helper.assertValueEqual(state(runtime).inventory().items().get(exact.id()).custody(), new InventoryCustody.ContainerSlot(container, 0),
                "return must restore the original exact slot without creating or aggregating a resource");
        helper.assertTrue(FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(0), exact),
                "the physical return must retain the exact durable tag and count");
        runtime.shutdown(); helper.succeed();
    }

    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
    }
    /** GameTest-only store; filesystem restart behavior is covered by the server-runtime test. */
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
