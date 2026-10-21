package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.VanillaInventoryCodeHooks;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import java.util.*;

@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3AutomationOperationGameTests {
    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void nativeHopperTransferRetainsTitleAcrossPartialMergeAndReturn(GameTestHelper helper) {
        var level = helper.getLevel(); var point = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(point, Blocks.HOPPER.defaultBlockState());
        level.setBlockAndUpdate(point.above(), Blocks.HOPPER.defaultBlockState());
        var destination = (HopperBlockEntity) level.getBlockEntity(point);
        var source = (HopperBlockEntity) level.getBlockEntity(point.above());
        source.setItem(0, new ItemStack(Items.BREAD, 9));
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:native-automation"), 97L);
        var initial = base.initialState(); var owner = initial.bootstrap().settlements().getFirst().id();
        var endpoint = FrontierV3AutomationSlot.container(level, initial, source, 0);
        var lot = new ResourceLot(new SubjectId("lot:native-automation"), owner, "minecraft:bread", 9, "test:automation", List.of());
        var resources = initial.inventory().fungibleResources().issue(lot,
                new CustodyAccount(endpoint.account(), endpoint.custody(), Map.of(lot.id(), 9), Map.of()));
        resources = resources.rebind(endpoint.account(), endpoint.epoch(), FungiblePhysicalObservation.bind(resources,
                endpoint.account(), endpoint.epoch(), List.of(new FungiblePhysicalObservation.Stack(endpoint.address(), lot.itemKind(), 9))));
        initial = initial.withInventory(initial.inventory().withFungibleResources(resources));
        var config = new FrontierEngineConfiguration<>(base.worldId(), initial, base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var runtime = FrontierV3ServerRuntime.start(config, new FrontierV3AmbientActorGameTests.EphemeralStore(), 10_000);
        try {
            for (int i = 0; i < 2; i++) helper.assertTrue(FrontierV3AutomationTransfers.extract(level, runtime,
                    new InvWrapper(source), source, destination, () -> Boolean.TRUE.equals(VanillaInventoryCodeHooks.extractHook(level, destination))),
                    "native NeoForge transfer runs under one source/destination operation");
            var after = runtime.decodedState().orElseThrow(); var target = FrontierV3AutomationSlot.container(level, after, destination, 0);
            helper.assertTrue(source.getItem(0).getCount() == 7 && destination.getItem(0).getCount() == 2
                    && after.inventory().fungibleResources().accounts().get(endpoint.account()).lotQuantities().equals(Map.of(lot.id(), 7))
                    && after.inventory().fungibleResources().accounts().get(target.account()).lotQuantities().equals(Map.of(lot.id(), 2))
                    && after.inventory().fungibleResources().lots().get(lot.id()).equals(lot),
                    "second transfer merges into the same tracked native slot without changing economic title");
            helper.assertTrue(FrontierV3AutomationTransfers.insert(level, runtime, destination, new InvWrapper(source),
                    () -> HopperBlockEntity.addItem(destination, source, destination.removeItem(0, 1), null).isEmpty()),
                    "the same accounting supports a native partial return to an existing account");
            helper.assertTrue(source.getItem(0).getCount() == 8 && destination.getItem(0).getCount() == 1
                    && FrontierV3ResourceHandoffLedger.get(level).pending().isEmpty(), "return has one terminal domain outcome");
            helper.assertTrue(FrontierV3NativeOperationMarkers.read(source.getPersistentData()).size() == 3
                    && FrontierV3NativeOperationMarkers.read(destination.getPersistentData()).size() == 3,
                    "both actual block entities retain matching native receipts until saved");
            var stable = runtime.decodedState().orElseThrow(); var calls = new java.util.concurrent.atomic.AtomicInteger();
            source.getItem(0).shrink(1); // An unobserved external change cannot be explained by another matching stack.
            helper.assertFalse(FrontierV3AutomationTransfers.extract(level, runtime, new InvWrapper(source), source, destination,
                    () -> { calls.incrementAndGet(); return true; }), "mismatching current source refuses a new transfer");
            helper.assertTrue(calls.get() == 0 && runtime.decodedState().orElseThrow().equals(stable),
                    "refusal occurs before either native mutation or canonical acceptance");
        } finally { runtime.shutdown(); }
        helper.succeed();
    }
}
