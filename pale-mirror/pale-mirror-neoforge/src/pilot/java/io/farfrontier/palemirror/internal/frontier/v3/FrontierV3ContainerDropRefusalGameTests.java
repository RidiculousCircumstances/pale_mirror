package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ContainerDropRefusalGameTests {
    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void partialNativeRefusalPreservesUnspawnedStockAndCannotClaimOrReplayCompletion(GameTestHelper helper) {
        var level = helper.getLevel(); var point = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(point, Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) level.getBlockEntity(point);
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:container-partial-refusal"), 94L);
        var owner = base.initialState().bootstrap().settlements().get(1).id(); var container = FrontierWorldState.depotId(owner);
        chest.getPersistentData().putString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY, container.value());
        chest.setItem(25, new ItemStack(Items.BREAD, 64));
        var account = ReferenceContainerCustody.scopeId(container);
        var lot = new ResourceLot(new SubjectId("lot:container-partial-refusal"), owner, "minecraft:bread", 64, "test:drop", List.of());
        var resources = FungibleResourceLedger.empty().issue(lot,
                new CustodyAccount(account, new ResourceCustody.Container(container), Map.of(lot.id(), 64), Map.of()));
        var address = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(container, 25));
        resources = resources.rebind(account, 1, FungiblePhysicalObservation.bind(resources, account, 1,
                List.of(new FungiblePhysicalObservation.Stack(address, lot.itemKind(), 64))));
        var initial = base.initialState().withInventory(base.initialState().inventory().withFungibleResources(resources));
        var config = new FrontierEngineConfiguration<>(base.worldId(), initial, base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), List.of(),
                base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var runtime = FrontierV3ServerRuntime.start(config, new FrontierV3AmbientActorGameTests.EphemeralStore(), 10_000);
        var admitted = new ArrayList<ItemEntity>();
        Consumer<EntityJoinLevelEvent> refusal = event -> {
            if (event.getLevel() != level || !(event.getEntity() instanceof ItemEntity item)) return;
            boolean ours = FrontierV3ResourceHandoffLedger.get(level).containerDrops().stream()
                    .anyMatch(drop -> drop.container().equals(container) && drop.slot() == 25 && drop.blocks(item.getUUID()));
            if (ours) { if (!admitted.isEmpty()) event.setCanceled(true); else admitted.add(item); }
        };
        NeoForge.EVENT_BUS.addListener(refusal);
        try {
            boolean refused = false;
            try { FrontierV3ContainerDropOperation.perform(level, runtime, FrontierV3PhysicalContainer.declaredChest(container, chest),
                    25, chest.getItem(25), point.getX(), point.getY(), point.getZ()); }
            catch (IllegalStateException expected) { refused = expected.getMessage().startsWith("native container drop spawn rejected:"); }
            helper.assertTrue(refused && admitted.size() == 1 && level.getEntity(admitted.getFirst().getUUID()) == admitted.getFirst()
                    && chest.getItem(25).getCount() + admitted.getFirst().getItem().getCount() == 64 && !chest.getItem(25).isEmpty(),
                    "one accepted fragment plus the real remaining source conserve all 64 items");
            var transaction = FrontierV3ResourceHandoffLedger.get(level).containerDrops().stream()
                    .filter(drop -> drop.container().equals(container) && drop.slot() == 25).findFirst().orElseThrow();
            helper.assertTrue(!FrontierV3ContainerDropOperation.reconcile(level, runtime, transaction)
                    && runtime.decodedState().orElseThrow().equals(initial)
                    && !FrontierV3OperationLedger.get(level).unattempted(transaction.id()),
                    "partial native execution remains explicit ambiguity, never successful accounting or another spawn permit");
        } finally { NeoForge.EVENT_BUS.unregister(refusal); runtime.shutdown(); }
        helper.succeed();
    }
}
