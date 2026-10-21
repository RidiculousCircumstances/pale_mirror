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
    public static void groundPickupCommitsPartialFalseAndFullRemovalExactlyOnce(GameTestHelper helper) {
        var level = helper.getLevel(); var point = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(point, Blocks.HOPPER.defaultBlockState());
        level.setBlockAndUpdate(point.above(), Blocks.HOPPER.defaultBlockState());
        var destination = (HopperBlockEntity) level.getBlockEntity(point);
        var empty = (HopperBlockEntity) level.getBlockEntity(point.above());
        for (int i = 0; i < destination.getContainerSize(); i++) destination.setItem(i, new ItemStack(Items.BREAD, 63));
        var drop = new net.minecraft.world.entity.item.ItemEntity(level, point.getX() + .5, point.getY() + .5,
                point.getZ() + .5, new ItemStack(Items.BREAD, 9));
        drop.setNoGravity(true); helper.assertTrue(level.addFreshEntity(drop), "native ground source admitted");
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:ground-automation"), 98L);
        var initial = base.initialState(); var owner = initial.bootstrap().settlements().getFirst().id();
        var endpoint = FrontierV3AutomationSlot.container(level, initial, destination, 0);
        var lot = new ResourceLot(new SubjectId("lot:ground-automation"), owner, "minecraft:bread", 324, "test:automation", List.of());
        var groundId = new SubjectId("custody:ground-automation");
        var ground = new ResourceCustody.Ground(drop.getUUID(), new BlockPosition(point.getX(), point.getY(), point.getZ()));
        var resources = initial.inventory().fungibleResources().issue(lot,
                new CustodyAccount(groundId, ground, Map.of(lot.id(), 324), Map.of()));
        resources = resources.transferToNewAccount(groundId,
                new CustodyAccount(endpoint.account(), endpoint.custody(), Map.of(lot.id(), 315), Map.of()));
        var layout = new ArrayList<FungiblePhysicalObservation.Stack>();
        for (int i = 0; i < destination.getContainerSize(); i++) layout.add(new FungiblePhysicalObservation.Stack(
                FrontierV3AutomationSlot.container(level, initial, destination, i).address(), lot.itemKind(), 63));
        resources = resources.rebind(endpoint.account(), endpoint.epoch(), FungiblePhysicalObservation.bind(resources,
                endpoint.account(), endpoint.epoch(), layout));
        resources = resources.rebind(groundId, 1, FungiblePhysicalObservation.bind(resources, groundId, 1,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.WorldEntity(drop.getUUID()), lot.itemKind(), 9))));
        initial = initial.withInventory(initial.inventory().withFungibleResources(resources));
        var config = new FrontierEngineConfiguration<>(base.worldId(), initial, base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var runtime = FrontierV3ServerRuntime.start(config, new FrontierV3AmbientActorGameTests.EphemeralStore(), 10_000);
        try {
            helper.assertFalse(FrontierV3GroundInventoryPickup.add(level, runtime, destination, drop,
                    () -> HopperBlockEntity.addItem(destination, drop)), "Vanilla false means an incomplete pickup, not no effect");
            var next = runtime.decodedState().orElseThrow().inventory().fungibleResources();
            helper.assertTrue(drop.getItem().getCount() == 4 && !drop.isRemoved()
                    && next.accounts().get(groundId).lotQuantities().equals(Map.of(lot.id(), 4))
                    && next.accounts().get(endpoint.account()).lotQuantities().equals(Map.of(lot.id(), 320)),
                    "one atomic handoff accounts for all five filled native slots and the surviving ground remainder");
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var stable = runtime.decodedState().orElseThrow();
            helper.assertFalse(FrontierV3GroundInventoryPickup.add(level, runtime, destination, drop,
                    () -> { calls.incrementAndGet(); return true; }), "full destination refuses before native mutation");
            helper.assertTrue(calls.get() == 0 && runtime.decodedState().orElseThrow().equals(stable), "full inventory grants no phantom transfer");
            drop.getItem().shrink(1);
            helper.assertFalse(FrontierV3GroundInventoryPickup.add(level, runtime, empty, drop,
                    () -> { calls.incrementAndGet(); return true; }), "source drift cannot authorize a pickup");
            helper.assertTrue(calls.get() == 0, "mismatch precedes native callback");
            drop.getItem().grow(1); // Restore only this isolated negative fixture's input, not a runtime recovery.
            var foreign = new net.minecraft.world.entity.item.ItemEntity(level, point.getX(), point.getY(), point.getZ(), new ItemStack(Items.BREAD));
            empty.getPersistentData().putString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY, "container:fenced-automation-test");
            helper.assertFalse(FrontierV3GroundInventoryPickup.add(level, runtime, empty, foreign,
                    () -> { calls.incrementAndGet(); return true; }), "missing operational lease cannot make a declared endpoint untracked");
            helper.assertTrue(calls.get() == 0 && empty.isEmpty(), "untracked ingress cannot bypass a managed endpoint fence");
            empty.getPersistentData().remove(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY);
            FrontierV3OperationEntityColumns.forRemoval(level, drop.chunkPosition());
            helper.assertTrue(FrontierV3GroundInventoryPickup.add(level, runtime, empty, drop,
                    () -> HopperBlockEntity.addItem(empty, drop)), "complete native pickup returns true");
            var finalState = runtime.decodedState().orElseThrow();
            var target = FrontierV3AutomationSlot.container(level, finalState, empty, 0);
            helper.assertTrue(drop.isRemoved() && empty.getItem(0).getCount() == 4
                    && !finalState.inventory().fungibleResources().accounts().containsKey(groundId)
                    && finalState.inventory().fungibleResources().accounts().get(target.account()).lotQuantities().equals(Map.of(lot.id(), 4))
                    && finalState.inventory().fungibleResources().lots().get(lot.id()).equals(lot)
                    && FrontierV3ResourceHandoffLedger.get(level).pending().isEmpty(),
                    "source removal, destination custody and unchanged title close together");
            helper.assertTrue(FrontierV3NativeOperationMarkers.read(destination.getPersistentData()).size() == 1
                    && FrontierV3NativeOperationMarkers.read(empty.getPersistentData()).size() == 1,
                    "each affected native destination carries one common operation receipt");
        } finally { runtime.shutdown(); }
        helper.succeed();
    }
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
