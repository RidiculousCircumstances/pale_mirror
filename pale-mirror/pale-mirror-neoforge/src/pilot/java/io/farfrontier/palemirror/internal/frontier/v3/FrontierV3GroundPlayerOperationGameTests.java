package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3GroundPlayerOperationGameTests {
    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void partialAndLastItemPickupUseTheRealMutationAndExactOwnerConfirmation(GameTestHelper helper) {
        var level = helper.getLevel(); var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var point = helper.absolutePos(new BlockPos(1, 1, 1));
        var item = new ItemEntity(level, point.getX() + .5, point.getY(), point.getZ() + .5, new ItemStack(Items.BREAD, 5));
        item.setNoGravity(true); helper.assertTrue(level.addFreshEntity(item), "source admitted");
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:ground-player-operation"), 91L);
        var owner = base.initialState().bootstrap().settlements().getFirst().id();
        var account = new SubjectId("custody:ground-player-operation");
        var lot = new ResourceLot(new SubjectId("lot:ground-player-operation"), owner, "minecraft:bread", 5, "test:pickup", List.of());
        var resources = FungibleResourceLedger.empty().issue(lot, new CustodyAccount(account,
                new ResourceCustody.Ground(item.getUUID(), new BlockPosition(point.getX(), point.getY(), point.getZ())), Map.of(lot.id(), 5), Map.of()));
        resources = resources.rebind(account, 1, FungiblePhysicalObservation.bind(resources, account, 1,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.WorldEntity(item.getUUID()), lot.itemKind(), 5))));
        var initial = base.initialState().withInventory(base.initialState().inventory().withFungibleResources(resources));
        var config = new FrontierEngineConfiguration<>(base.worldId(), initial, base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                base.initialSchedules(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var runtime = FrontierV3ServerRuntime.start(config, new FrontierV3AmbientActorGameTests.EphemeralStore(), 10_000);
        try {
            for (int i = 0; i < player.getInventory().items.size(); i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            player.getInventory().setItem(0, new ItemStack(Items.BREAD, 61));
            // Vanilla add returns the last insertion-loop result, which can be false
            // after a partial insertion fills the final available slot.
            FrontierV3GroundPlayerPickup.add(level, item, player.getInventory(), item.getItem(), runtime);
            helper.assertTrue(player.getInventory().getItem(0).getCount() == 64 && item.getItem().getCount() == 2 && !item.isRemoved(),
                    "exact capacity moves three, preserving the native remainder");
            var state = runtime.decodedState().orElseThrow();
            helper.assertTrue(state.inventory().fungibleResources().accounts().get(account).lotQuantities().get(lot.id()) == 2,
                    "same registered canonical command accounts for only the actual departure");
            helper.assertTrue(FrontierV3ResourceHandoffLedger.get(level).losses().isEmpty(), "confirmed owner witness retires");
            // A matching empty postimage without an operation must never be accepted as
            // causal proof of another pickup, even though the player has matching bread.
            var unwitnessed = GroundResourcePlayerWithdrawal.prepare(state, account, player.getUUID(), UUID.randomUUID(), 2);
            var journal = FrontierV3ResourceHandoffLedger.get(level); journal.prepareLoss(unwitnessed); journal.persist(level);
            helper.assertTrue(!FrontierV3GroundPlayerPickup.reconcile(level, runtime, unwitnessed), "counts do not manufacture an operation receipt");
            journal.retireLoss(unwitnessed); journal.persist(level);
            player.getInventory().setItem(1, ItemStack.EMPTY);
            FrontierV3OperationEntityColumns.forRemoval(level, item.chunkPosition());
            helper.assertTrue(FrontierV3GroundPlayerPickup.add(level, item, player.getInventory(), item.getItem(), runtime), "last-item pickup succeeds");
            helper.assertTrue(item.isRemoved() && player.getInventory().getItem(1).getCount() == 2,
                    "native removal and player insertion actually occur");
            helper.assertTrue(!runtime.decodedState().orElseThrow().inventory().fungibleResources().accounts().containsKey(account)
                    && journal.losses().isEmpty(), "canonical source retires through the common confirmation, not count inference");
            var receipts = FrontierV3NativeOperationMarkers.read(FrontierV3OperationPlayers.current(player).persistent());
            helper.assertTrue(receipts.size() == 2, "both distinct pickup receipts await real player persistence");
        } finally { runtime.shutdown(); }
        helper.succeed();
    }
}
