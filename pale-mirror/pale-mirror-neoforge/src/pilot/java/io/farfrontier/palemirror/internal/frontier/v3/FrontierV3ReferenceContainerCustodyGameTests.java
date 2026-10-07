package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import io.farfrontier.palemirror.frontier.v3.process.HiveNutrientTransferProcess;
import io.farfrontier.palemirror.frontier.v3.process.HiveGrowthProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loaded-world negative evidence and bounded multi-scope scheduling for the F0.2B adapter. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ReferenceContainerCustodyGameTests {
    private FrontierV3ReferenceContainerCustodyGameTests() { }

    @GameTest(batch = "pm-frontier-v3-reference-projection", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void declaredActorMaterialOrderTakesAndPartiallyPlacesWithoutLosingRemainder(GameTestHelper helper) {
        SubjectId container = new SubjectId("container:actor-material-test");
        SubjectId actorId = new SubjectId("resident:actor-material-test");
        SubjectId lot = new SubjectId("lot:actor-material-test");
        ChestBlockEntity chest = chest(helper, helper.absolutePos(new BlockPos(2, 2, 2)), container);
        chest.setItem(4, new ItemStack(Items.WHEAT, 20));
        Villager actor = EntityType.VILLAGER.create(helper.getLevel());
        if (actor == null) throw new IllegalStateException("GameTest could not create actor");
        actor.setPos(helper.absolutePos(new BlockPos(2, 3, 2)).getCenter());
        helper.getLevel().addFreshEntity(actor);
        ActorContainerItemOrder take = new ActorContainerItemOrder(new SubjectId("job:actor-material-test"), actorId,
                ActorContainerItemOrder.Direction.TAKE,
                new ActorContainerItemOrder.Portion.Fungible(new SubjectId("custody:actor-source"),
                        new ResourceCustody.Container(container), new SubjectId("custody:actor-hand"),
                        new ResourceCustody.Actor(actorId), Optional.empty(), "minecraft:wheat", Map.of(lot, 20)),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(container),
                SurfaceAnchor.at(2, 2, 2), ActorContainerItemOrder.Hand.MAIN, 1, 1);
        var source = List.of(new MaterialSourceSelection.Slice(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(container, 4)), 20, 20, 1L));
        var pickup = new FrontierV3ActorItemTransfer.FungibleStep(take, chest, actor, actor.getUUID(), source, -1);
        helper.assertTrue(pickup.before() && pickup.apply() && pickup.after()
                        && chest.getItem(4).isEmpty() && actor.getItemBySlot(EquipmentSlot.MAINHAND).getCount() == 20,
                "one declared TAKE must transfer the real stack to the real actor hand");
        ActorContainerItemOrder place = new ActorContainerItemOrder(take.ownerId(), actorId,
                ActorContainerItemOrder.Direction.PLACE,
                new ActorContainerItemOrder.Portion.Fungible(new SubjectId("custody:actor-hand"),
                        new ResourceCustody.Actor(actorId), new SubjectId("custody:actor-destination"),
                        new ResourceCustody.Container(container), Optional.empty(), "minecraft:wheat", Map.of(lot, 12)),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(container),
                take.station(), ActorContainerItemOrder.Hand.MAIN, 2, 1);
        var hand = List.of(new MaterialSourceSelection.Slice(new PhysicalStackAddress.ActorHand(actorId,
                actor.getUUID(), ActorContainerItemOrder.Hand.MAIN), 20, 12, 1L));
        var delivery = new FrontierV3ActorItemTransfer.FungibleStep(place, chest, actor, actor.getUUID(), hand, 6);
        helper.assertTrue(delivery.before() && delivery.apply() && delivery.after()
                        && chest.getItem(6).is(Items.WHEAT) && chest.getItem(6).getCount() == 12
                        && actor.getItemBySlot(EquipmentSlot.MAINHAND).getCount() == 8,
                "declared partial PLACE must retain the actor's eight remaining items");
        chest.getPersistentData().putString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY, "container:foreign");
        boolean rejected = false;
        try { new FrontierV3ActorItemTransfer.FungibleStep(place, chest, actor, actor.getUUID(), hand, 7); }
        catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected && chest.getItem(6).getCount() == 12,
                "an undeclared container owner must be rejected before touching physical stock");
        chest.getPersistentData().putString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY, container.value());
        rejected = false;
        try { new FrontierV3ActorItemTransfer.FungibleStep(take, chest, actor, java.util.UUID.randomUUID(), source, -1); }
        catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected && actor.getItemBySlot(EquipmentSlot.MAINHAND).getCount() == 8,
                "an undeclared actor body must be rejected before touching its physical hand");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-reference-projection", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void insufficientFungibleCapacityNeverWritesAPartialOwnedChest(GameTestHelper helper) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:reference-capacity-atomic"), 91L));
        SubjectId settlement = state.bootstrap().settlements().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement);
        for (int ordinal = 0; state.inventory().firstFreeSlot(depot).isPresent(); ordinal++) {
            int slot = state.inventory().firstFreeSlot(depot).orElseThrow();
            state = state.withInventory(state.inventory().store(new ExactItemStack(
                    new SubjectId("item:capacity-fixture-" + ordinal), settlement, "minecraft:cobblestone", 1,
                    new InventoryCustody.ContainerSlot(depot, slot))));
        }
        // A recovered legacy image can still contain an impossible overcommit. Build that
        // image explicitly: ordinary store now reserves the COLD wheat's physical slot.
        ExactInventory fitted = state.inventory();
        int reservedSlot = java.util.stream.IntStream.range(0, fitted.containers().get(depot).slotCount())
                .filter(slot -> fitted.itemAt(depot, slot).isEmpty()).findFirst().orElseThrow();
        Map<SubjectId, ExactItemStack> overfilledItems = new java.util.HashMap<>(fitted.items());
        SubjectId overfillId = new SubjectId("item:legacy-capacity-overfill");
        overfilledItems.put(overfillId, new ExactItemStack(overfillId, settlement, "minecraft:cobblestone", 1,
                new InventoryCustody.ContainerSlot(depot, reservedSlot)));
        state = state.withInventory(new ExactInventory(fitted.containers(), overfilledItems, fitted.cargo(),
                fitted.playerItems(), fitted.worldCarrierItems(), fitted.conflicts(), fitted.surfaces(),
                fitted.economics(), fitted.fungibleResources()));
        ChestBlockEntity chest = chest(helper, helper.absolutePos(new BlockPos(2, 2, 2)), depot);
        helper.assertTrue(!FrontierV3ContainerSurfaceExecutor.writeCanonicalSlots(chest, state, depot) && chest.isEmpty(),
                "insufficient wheat capacity must reject before writing even the exact-item prefix");
        chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        helper.assertTrue(!FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, state, depot)
                        && chest.getItem(0).is(net.minecraft.world.item.Items.DIAMOND)
                        && chest.getItem(0).getCount() == 3,
                "an impossible replacement must preserve the complete prior physical chest");
        chest.clearContent();
        SubjectId released = state.inventory().itemAt(depot, 0).orElseThrow().id();
        FrontierWorldState fitting = state.withInventory(state.inventory().withoutItem(released));
        helper.assertTrue(FrontierV3ContainerSurfaceExecutor.writeCanonicalSlots(chest, fitting, depot)
                        && chest.getItem(0).is(net.minecraft.world.item.Items.WHEAT) && !chest.isEmpty(),
                "after one real slot is available, the complete canonical chest may be written once");
        chest.clearContent();
        ExactInventory smaller = fitting.inventory().withoutItem(fitting.inventory().itemAt(depot, 1).orElseThrow().id());
        FrontierWorldState invalidExact = fitting.withInventory(smaller.store(new ExactItemStack(
                new SubjectId("item:unstackable-overcount"), settlement, "minecraft:iron_pickaxe", 2,
                new InventoryCustody.ContainerSlot(depot, 1))));
        helper.assertTrue(!FrontierV3ContainerSurfaceExecutor.writeCanonicalSlots(chest, invalidExact, depot)
                        && chest.isEmpty(),
                "an exact stack beyond the real item's stack limit must fail before any physical write");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-reference-projection", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void pendingProjectionUsesActualChestAndRetainsMismatchAcrossRuntimeRecovery(GameTestHelper helper) {
        WorldId world = new WorldId("frontier:reference-pending-projection");
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L));
        SubjectId depot = FrontierWorldState.depotId(initial.bootstrap().settlements().getFirst().id());
        BlockPos local = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPosition original = initial.inventory().surfaces().get(depot).position();
        initial = FrontierWorldState.initial(FrontierV3BootstrapGameTestFixtures.translatedBootstrap(initial.bootstrap(),
                local.getX() - original.x(), local.getY() - original.y(), local.getZ() - original.z()));
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(world, initial);
        helper.assertTrue(runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE, "fixture startup: " + runtime.status());
        helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.prepareInitialProjection(runtime, initial, depot), "durable preparation must succeed");
        FrontierWorldState prepared = runtime.decodedState().orElseThrow();
        SubjectId scope = ReferenceContainerCustody.scopeId(depot);
        PhysicalCustodyLease lease = prepared.replicaCustody().custodyByScope().get(scope);
        helper.assertTrue(lease.status() == PhysicalCustodyLeaseStatus.PREPARING && !ReferenceContainerCustody.hasOperationalCustody(prepared, depot),
                "before-write custody does not grant HOT operation permission");
        ChestBlockEntity chest = chest(helper, local, depot);
        FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, prepared, depot);
        helper.assertTrue(!chest.isEmpty(), "fixture must project real canonical wheat before simulating physical loss");
        var pendingRevision = runtime.canonicalState().orElseThrow().revision();
        helper.assertTrue(!FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedMutation(runtime, depot, chest)
                        && runtime.canonicalState().orElseThrow().revision().equals(pendingRevision),
                "an effect checkpoint cannot reclassify pending projection as provider loss");
        chest.getItem(0).shrink(1); chest.setChanged();
        helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.reconcilePreparedProjection(runtime, prepared, lease, chest),
                "actual mismatching chest must produce a retained local conflict");
        var checkpoint = runtime.checkpointImage().orElseThrow();
        runtime.shutdown();
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> recovered = recovered(world, checkpoint, initial.bootstrap());
        helper.assertTrue(recovered.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE, "fixture recovery: " + recovered.status());
        FrontierWorldState conflicted = recovered.decodedState().orElseThrow();
        PhysicalCustodyLease retained = conflicted.replicaCustody().custodyByScope().get(scope);
        helper.assertTrue(retained.status() == PhysicalCustodyLeaseStatus.UNRESOLVED && retained.authorityEpoch() == lease.authorityEpoch()
                        && conflicted.replicaCustody().replicas().get(depot).observedFingerprint().isPresent(),
                "runtime recovery keeps actual mismatch and the same exclusive epoch");
        helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.eligibleReferenceSurfaces(conflicted,
                        List.of(conflicted.inventory().surfaces().get(depot))).size() == 1,
                "pending-write conflict remains eligible for later exact observation");
        helper.assertTrue(!FrontierV3ReferenceContainerCustodyExecutor.reconcilePreparedProjection(recovered, conflicted, retained, chest)
                        && chest.getItem(0).getCount() == 63 && recovered.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE,
                "a repeated observation neither overwrites loss nor quarantines the instance");
        recovered.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-reference-projection", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void initialChestWriteConfirmsCustodyInItsOwnLifecycleTurn(GameTestHelper helper) {
        WorldId world = new WorldId("frontier:reference-same-turn-confirmation");
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L));
        SubjectId depot = FrontierWorldState.depotId(initial.bootstrap().settlements().getFirst().id());
        BlockPos local = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPosition original = initial.inventory().surfaces().get(depot).position();
        initial = FrontierWorldState.initial(FrontierV3BootstrapGameTestFixtures.translatedBootstrap(initial.bootstrap(),
                local.getX() - original.x(), local.getY() - original.y(), local.getZ() - original.z()));
        var runtime = runtime(world, initial);
        helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.prepareInitialProjection(runtime, initial, depot),
                "initial projection has its durable before-write fence");
        var prepared = runtime.decodedState().orElseThrow();
        var chest = chest(helper, local, depot);
        helper.assertTrue(FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, prepared, depot), "exact initial slots written");
        FrontierV3ContainerSurfaceExecutor.activateAndConfirm(runtime, prepared.inventory().surfaces().get(depot), chest);
        var confirmed = runtime.decodedState().orElseThrow();
        helper.assertTrue(confirmed.inventory().surfaces().get(depot).status() == ContainerSurfaceStatus.ACTIVE
                        && confirmed.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(depot)).status()
                            == PhysicalCustodyLeaseStatus.ACQUIRED,
                "the lifecycle turn itself confirms the real chest, without another player visit or polling turn");
        var checkpoint = runtime.checkpointImage().orElseThrow();
        runtime.shutdown();
        var restored = recovered(world, checkpoint, initial.bootstrap());
        helper.assertTrue(restored.decodedState().orElseThrow().replicaCustody().custodyByScope()
                        .get(ReferenceContainerCustody.scopeId(depot)).status() == PhysicalCustodyLeaseStatus.ACQUIRED,
                "restart cannot recover this completed initial write as PREPARING");
        restored.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-reference-projection", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void releasedProjectionRequiresFreshWriteFenceAndActualConfirmation(GameTestHelper helper) {
        WorldId world = new WorldId("frontier:reference-released-projection");
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L));
        SubjectId depot = FrontierWorldState.depotId(state.bootstrap().settlements().getFirst().id());
        BlockPos local = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPosition original = state.inventory().surfaces().get(depot).position();
        state = FrontierWorldState.initial(FrontierV3BootstrapGameTestFixtures.translatedBootstrap(state.bootstrap(),
                local.getX() - original.x(), local.getY() - original.y(), local.getZ() - original.z()));
        state = held(state.withInventory(state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)), depot);
        SubjectId scope = ReferenceContainerCustody.scopeId(depot);
        state = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(state.replicaCustody()
                .checkpoint(scope, 1L, 0L, 2L).release(scope, 1L, 0L, 2L)));
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(world, state);
        helper.assertTrue(runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE, "released fixture startup: " + runtime.status());
        var prior = state.replicaCustody().replicas().get(depot);
        helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.prepareReleasedProjection(runtime, state, prior),
                "unchanged released slots still acquire a durable pending-write fence");
        FrontierWorldState prepared = runtime.decodedState().orElseThrow();
        PhysicalCustodyLease lease = prepared.replicaCustody().custodyByScope().get(scope);
        helper.assertTrue(lease.authorityEpoch() == 2L && lease.status() == PhysicalCustodyLeaseStatus.PREPARING
                        && !ReferenceContainerCustody.hasOperationalCustody(prepared, depot),
                "catch-up must not grant work permission before actual observation");
        ChestBlockEntity chest = chest(helper, local, depot);
        FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, prepared, depot);
        helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.reconcilePreparedProjection(runtime, prepared, lease, chest),
                "real projected slots confirm the same fresh epoch");
        FrontierWorldState confirmed = runtime.decodedState().orElseThrow();
        helper.assertTrue(ReferenceContainerCustody.hasOperationalCustody(confirmed, depot)
                        && confirmed.replicaCustody().custodyByScope().get(scope).authorityEpoch() == 2L,
                "only actual confirmation enables the next custody cycle");
        runtime.shutdown(); helper.succeed();
    }

    /** Ordinary chest rearrangement changes a HOT binding, never its stock or replica ownership. */
    @GameTest(batch = "pm-frontier-v3-reference-projection", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void movedBulkWheatRebindsWithoutReferenceConflict(GameTestHelper helper) {
        WorldId world = new WorldId("frontier:reference-bulk-rearrangement");
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L));
        SubjectId depot = FrontierWorldState.depotId(initial.bootstrap().settlements().getFirst().id());
        BlockPos local = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPosition original = initial.inventory().surfaces().get(depot).position();
        FrontierWorldState translated = FrontierWorldState.initial(FrontierV3BootstrapGameTestFixtures.translatedBootstrap(
                initial.bootstrap(), local.getX() - original.x(), local.getY() - original.y(), local.getZ() - original.z()));
        SubjectId accountId = translated.inventory().fungibleResources().accounts().values().stream()
                .filter(account -> account.custody().equals(new ResourceCustody.Container(depot)))
                .map(CustodyAccount::id).findFirst().orElseThrow();
        int sourceSlot = java.util.stream.IntStream.range(0, 27)
                .filter(slot -> ReferenceContainerCustody.expectedFungibleSlot(translated, depot, slot).isPresent())
                .findFirst().orElseThrow();
        FungibleResourceLedger cold = translated.inventory().fungibleResources();
        FungibleResourceLedger hot = cold.rebind(accountId, 1L, FungiblePhysicalObservation.bind(cold, accountId, 1L,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(depot, sourceSlot)), "minecraft:wheat", 64))));
        FrontierWorldState active = activated(translated, depot);
        FrontierWorldState state = held(active.withInventory(active.inventory().withFungibleResources(hot)), depot);
        ChestBlockEntity chest = chest(helper, local, depot);
        helper.assertTrue(FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, state, depot), "initial chest projection");
        int destinationSlot = java.util.stream.IntStream.range(0, chest.getContainerSize())
                .filter(slot -> chest.getItem(slot).isEmpty()).findFirst().orElseThrow();
        chest.setItem(destinationSlot, chest.getItem(sourceSlot).copy()); chest.setItem(sourceSlot, ItemStack.EMPTY); chest.setChanged();
        var runtime = runtime(world, state);
        helper.assertTrue(FrontierV3FungibleResourceObservationExecutor.observe(helper.getLevel(), runtime, state,
                hot.accounts().get(accountId), chest, 1L), "same stock at a new slot must publish the current physical binding");
        FrontierWorldState rebound = runtime.decodedState().orElseThrow();
        helper.assertTrue(rebound.inventory().fungibleResources().bindings().values().stream()
                        .anyMatch(binding -> binding.accountId().equals(accountId)
                                && binding.address().equals(new PhysicalStackAddress.ContainerSlot(
                                new InventoryCustody.ContainerSlot(depot, destinationSlot))))
                        && ReferenceContainerCustody.canonicalFingerprint(rebound, depot).equals(
                        FrontierV3ReferenceContainerCustodyExecutor.observed(rebound, depot, chest).fingerprint()),
                "semantic stock and the actual new slot must agree without changing economic custody");
        FrontierV3ReferenceContainerCustodyExecutor.reconcile(helper.getLevel(), runtime, rebound,
                rebound.inventory().surfaces().get(depot));
        FrontierWorldState after = runtime.decodedState().orElseThrow();
        helper.assertTrue(after.replicaCustody().replicas().get(depot).state() == PhysicalReplicaState.OBSERVED_CURRENT
                        && ReferenceContainerCustody.hasOperationalCustody(after, depot)
                        && chest.getItem(destinationSlot).is(net.minecraft.world.item.Items.WHEAT),
                "ordinary slot move must neither conflict nor rewrite the physical chest");
        int splitSlot = java.util.stream.IntStream.range(0, chest.getContainerSize())
                .filter(slot -> chest.getItem(slot).isEmpty()).findFirst().orElseThrow();
        ItemStack split = chest.getItem(destinationSlot).split(24);
        chest.setItem(splitSlot, split); chest.setChanged();
        helper.assertTrue(FrontierV3FungibleResourceObservationExecutor.observe(helper.getLevel(), runtime, after,
                after.inventory().fungibleResources().accounts().get(accountId), chest, 1L),
                "splitting the same owned stock must update only physical bindings");
        FrontierWorldState splitState = runtime.decodedState().orElseThrow();
        FrontierV3ReferenceContainerCustodyExecutor.reconcile(helper.getLevel(), runtime, splitState,
                splitState.inventory().surfaces().get(depot));
        FrontierWorldState splitObserved = runtime.decodedState().orElseThrow();
        helper.assertTrue(splitObserved.replicaCustody().replicas().get(depot).state() == PhysicalReplicaState.OBSERVED_CURRENT
                        && splitObserved.inventory().fungibleResources().bindings().values().stream()
                        .filter(binding -> binding.accountId().equals(accountId)).count() == 2
                        && chest.getItem(destinationSlot).getCount() == 40 && chest.getItem(splitSlot).getCount() == 24,
                "ordinary split must conserve all 64 physical wheat without a false reference conflict");
        var checkpoint = runtime.checkpointImage().orElseThrow();
        runtime.shutdown();
        var recovered = recovered(world, checkpoint, translated.bootstrap());
        helper.assertTrue(recovered.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE,
                "reordered bulk stock must recover under the current fingerprint schema");
        FrontierWorldState resumed = recovered.decodedState().orElseThrow();
        FrontierV3ReferenceContainerCustodyExecutor.reconcile(helper.getLevel(), recovered, resumed,
                resumed.inventory().surfaces().get(depot));
        FrontierWorldState afterRestart = recovered.decodedState().orElseThrow();
        helper.assertTrue(afterRestart.replicaCustody().replicas().get(depot).state() == PhysicalReplicaState.OBSERVED_CURRENT
                        && afterRestart.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(depot)).status()
                        == PhysicalCustodyLeaseStatus.ACQUIRED,
                "restart must retain the same owned chest, split stock and live reference custody");
        recovered.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-reference-projection", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void releasedMutationPreservesBulkStockBeforeRepackingColdLots(GameTestHelper helper) {
        WorldId world = new WorldId("frontier:reference-released-repack");
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L));
        SubjectId settlement = initial.bootstrap().settlements().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement);
        BlockPos local = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPosition original = initial.inventory().surfaces().get(depot).position();
        FrontierWorldState state = activated(FrontierWorldState.initial(FrontierV3BootstrapGameTestFixtures.translatedBootstrap(
                initial.bootstrap(), local.getX() - original.x(), local.getY() - original.y(), local.getZ() - original.z())), depot);
        SubjectId accountId = new SubjectId("custody:reference-repack");
        ResourceLot wheat = new ResourceLot(new SubjectId("lot:field-repack"), settlement, "minecraft:wheat", 64, "field", List.of());
        ResourceLot bread = new ResourceLot(new SubjectId("lot:production-repack"), settlement, "minecraft:bread", 64, "recipe:bread", List.of());
        FungibleResourceLedger cold = new FungibleResourceLedger(Map.of(wheat.id(), wheat, bread.id(), bread), Map.of(),
                Map.of(accountId, new CustodyAccount(accountId, new ResourceCustody.Container(depot),
                        Map.of(wheat.id(), 64, bread.id(), 64), Map.of())), Map.of());
        var breadSlot = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(depot, 0)), bread.itemKind(), 64);
        var wheatSlot = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(depot, 1)), wheat.itemKind(), 64);
        FungibleResourceLedger hot = cold.rebind(accountId, 1L,
                FungiblePhysicalObservation.bind(cold, accountId, 1L, List.of(breadSlot, wheatSlot)));
        state = held(state.withInventory(state.inventory().withFungibleResources(hot)), depot);
        ChestBlockEntity chest = chest(helper, local, depot);
        FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, state, depot);
        chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 64));
        chest.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT, 64));
        chest.setChanged();
        helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.observed(state, depot, chest).fingerprint()
                        .equals(ReferenceContainerCustody.canonicalFingerprint(state, depot)),
                "fixture must retain the observed bread-then-wheat HOT slot order");
        FrontierWorldState released = ReferenceContainerCustody.closeConfirmedMutation(state,
                ReferenceContainerCustody.confirmedMutationTransition(state, depot, 1L));
        var emitted = released.replicaCustody().replicas().get(depot);
        helper.assertTrue(emitted.state() == PhysicalReplicaState.EXPECTED
                        && emitted.fingerprint().equals(ReferenceContainerCustody.canonicalFingerprint(released, depot))
                        && FrontierV3ReferenceContainerCustodyExecutor.observedRetained(released, depot, chest).fingerprint()
                        .equals(emitted.fingerprint()),
                "HOT release preserves the same stock image even when COLD lot packing changes order");
        var runtime = runtime(world, released);
        helper.assertTrue(runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE, "released fixture startup");
        FrontierV3ReferenceContainerCustodyExecutor.reconcile(helper.getLevel(), runtime, released,
                released.inventory().surfaces().get(depot));
        FrontierWorldState observed = runtime.decodedState().orElseThrow();
        helper.assertTrue(observed.replicaCustody().replicas().get(depot).state() == PhysicalReplicaState.OBSERVED_CURRENT
                        && observed.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(depot)).status()
                        == PhysicalCustodyLeaseStatus.RELEASED,
                "first observation must confirm the retained physical image without a false conflict");
        FrontierV3ReferenceContainerCustodyExecutor.reconcile(helper.getLevel(), runtime, observed,
                observed.inventory().surfaces().get(depot));
        FrontierWorldState confirmed = runtime.decodedState().orElseThrow();
        PhysicalCustodyLease fence = confirmed.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(depot));
        helper.assertTrue(fence.authorityEpoch() == 2L
                        && ReferenceContainerCustody.hasOperationalCustody(confirmed, depot),
                "the fenced catch-up write confirms its actual image in the same loaded turn, before unload");
        helper.assertTrue(chest.getItem(0).is(net.minecraft.world.item.Items.WHEAT)
                        && chest.getItem(1).is(net.minecraft.world.item.Items.BREAD),
                "the fenced catch-up write must project the same lots in canonical COLD order");
        helper.assertTrue(!FrontierV3ReferenceContainerCustodyExecutor.retainsUnobservedRestartFungibleHot(
                        Map.of(fence.scopeId(), fence.authorityEpoch()), confirmed.inventory().fungibleResources(), fence),
                "an actually confirmed current-process image must not be retained as unobserved restart custody");
        runtime.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-reference-projection", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void loadedUnchangedCheckpointedChestFinishesItsRecordedDrain(GameTestHelper helper) {
        WorldId world = new WorldId("frontier:reference-loaded-checkpointed");
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L));
        SubjectId depot = FrontierWorldState.depotId(state.bootstrap().settlements().getFirst().id());
        BlockPos local = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPosition original = state.inventory().surfaces().get(depot).position();
        state = FrontierWorldState.initial(FrontierV3BootstrapGameTestFixtures.translatedBootstrap(state.bootstrap(),
                local.getX() - original.x(), local.getY() - original.y(), local.getZ() - original.z()));
        state = held(activated(state, depot), depot);
        SubjectId scope = ReferenceContainerCustody.scopeId(depot);
        state = state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(state.replicaCustody()
                .checkpoint(scope, 1L, 0L, 2L)));
        ChestBlockEntity chest = chest(helper, local, depot);
        FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, state, depot);
        var runtime = runtime(world, state);
        helper.assertTrue(runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE, "checkpoint fixture active");
        var before = FrontierV3ReferenceContainerCustodyExecutor.observed(state, depot, chest);
        FrontierV3ReferenceContainerCustodyExecutor.reconcile(helper.getLevel(), runtime, state, state.inventory().surfaces().get(depot));
        var released = runtime.decodedState().orElseThrow();
        helper.assertTrue(released.replicaCustody().custodyByScope().get(scope).status() == PhysicalCustodyLeaseStatus.RELEASED,
                "a loaded matching chest must finish its prior drain, not strand a live checkpoint");
        helper.assertTrue(before.equals(FrontierV3ReferenceContainerCustodyExecutor.observed(released, depot, chest)),
                "closing retained custody must not overwrite physical contents or provenance");
        runtime.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-reference-conflict-restart", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void foreignAndMissingProvenanceRemainActualEvidence(GameTestHelper helper) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:reference-observation"), 91L));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(position, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(position);
        chest.getPersistentData().putString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY, "container:foreign-depot");
        chest.getPersistentData().putString(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY, "foreign:player");
        FrontierV3ReferenceContainerCustodyExecutor.Observed foreign = FrontierV3ReferenceContainerCustodyExecutor.observed(state, depot, chest);
        helper.assertTrue(foreign.provenance().contains("foreign:container-owner=container:foreign-depot") && foreign.provenance().contains("foreign:player"),
                "a wrong owner and its actual provenance must remain typed evidence, never canonical adoption");

        chest.getPersistentData().putString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY, depot.value());
        chest.getPersistentData().remove(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY);
        FrontierV3ReferenceContainerCustodyExecutor.Observed missingProvenance = FrontierV3ReferenceContainerCustodyExecutor.observed(state, depot, chest);
        helper.assertTrue(missingProvenance.provenance().equals("missing:replica-provenance:" + depot.value()),
                "a legacy owner marker alone cannot manufacture canonical replica provenance");
        helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.observed(state, depot, null).provenance().equals("missing:" + depot.value()),
                "an absent chest remains missing evidence");
        helper.assertTrue(!FrontierV3ReferenceContainerCustodyExecutor.initialDeclarationReady(ContainerSurfaceStatus.PREPARED, chest)
                        && FrontierV3ReferenceContainerCustodyExecutor.initialDeclarationReady(ContainerSurfaceStatus.ACTIVE, chest),
                "a tagged chest cannot become a replica boundary until its surface transition is durably ACTIVE");
        helper.assertTrue(!FrontierV3ReferenceContainerCustodyExecutor.readyForReplicaObservation(ContainerSurfaceStatus.PREPARED)
                        && FrontierV3ReferenceContainerCustodyExecutor.readyForReplicaObservation(ContainerSurfaceStatus.ACTIVE),
                "a retained replica must not classify a generic socket's durable PREPARED recovery window as missing evidence");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-reference-depot-never-visited", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void roundRobinAdvancesDepotAndHiveWhileAConflictIsLocal(GameTestHelper helper) {
        FrontierWorldState baseline = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:reference-fairness"), 91L));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        SubjectId hiveStore = baseline.bootstrap().hive().organs().stream().flatMap(organ -> organ.containerId().stream()).findFirst().orElseThrow();
        List<ContainerSurface> loaded = List.of(baseline.inventory().surfaces().get(depot), baseline.inventory().surfaces().get(hiveStore));
        List<ContainerSurface> eligible = FrontierV3ReferenceContainerCustodyExecutor.eligibleReferenceSurfaces(baseline, loaded);
        helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.selectRoundRobin(eligible, 0L).containerId()
                        != FrontierV3ReferenceContainerCustodyExecutor.selectRoundRobin(eligible, 1L).containerId(),
                "a stable first held scope cannot starve the next naturally loaded depot/store");

        PhysicalReplicaRecord expected = PhysicalReplicaRecord.expected(depot, ReferenceContainerCustody.semanticKind(baseline, depot), 1L,
                ReferenceContainerCustody.canonicalFingerprint(baseline, depot), ReferenceContainerCustody.provenance(depot));
        PhysicalReplicaCustodyState conflicted = PhysicalReplicaCustodyState.empty().declare(expected)
                .observe(depot, 1L, 1L, "sha256:foreign", "foreign:player", 1L);
        FrontierWorldState localConflict = baseline.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(conflicted));
        List<ContainerSurface> remaining = FrontierV3ReferenceContainerCustodyExecutor.eligibleReferenceSurfaces(localConflict, loaded);
        helper.assertTrue(remaining.size() == 1 && remaining.getFirst().containerId().equals(hiveStore),
                "one conflicted depot is excluded locally while the hive store continues");
        helper.succeed();
    }

    /** A formerly visited unloaded depot remains cold until fresh matching evidence exists. */
    @GameTest(batch = "pm-frontier-v3-reference-depot-visited-unloaded", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void releasedDepotDoesNotReemitBeforeRetainedEvidenceMatches(GameTestHelper helper) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:reference-released"), 91L));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        PhysicalReplicaRecord expected = PhysicalReplicaRecord.expected(depot, ReferenceContainerCustody.semanticKind(state, depot), 7L,
                ReferenceContainerCustody.canonicalFingerprint(state, depot), ReferenceContainerCustody.provenance(depot));
        PhysicalReplicaCustodyState retained = PhysicalReplicaCustodyState.empty().declare(expected)
                .observe(depot, 7L, 1L, expected.fingerprint(), expected.provenance(), 7L)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(depot), depot, ReferenceContainerCustody.PROVIDER_ID,
                        1L, 7L, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null))
                .checkpoint(ReferenceContainerCustody.scopeId(depot), 1L, 7L, 2L)
                .release(ReferenceContainerCustody.scopeId(depot), 1L, 7L, 2L);
        helper.assertTrue(!ReferenceContainerCustody.hasLiveCustody(state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(retained)), depot),
                "a released unloaded visit has no write authority before a fresh matching observation");
        helper.succeed();
    }

    /** A naturally loaded world with no player still schedules the independent hive store. */
    @GameTest(batch = "pm-frontier-v3-reference-hive-zero-player", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void zeroPlayerHiveStoreIsAReferenceScopeWithoutDepotAuthority(GameTestHelper helper) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:reference-hive"), 91L));
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        SubjectId hiveStore = state.bootstrap().hive().organs().stream().flatMap(organ -> organ.containerId().stream()).findFirst().orElseThrow();
        helper.assertTrue(ReferenceContainerCustody.isReferenceContainer(state, hiveStore) && !ReferenceContainerCustody.hasLiveCustody(state, depot),
                "the zero-player hive store is independent from an unheld settlement depot");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-reference-conflict-restart", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void confirmedMutationClosesItsReplicaBoundaryBeforeImmediateRestart(GameTestHelper helper) {
        WorldId world = new WorldId("frontier:reference-same-turn-boundary");
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        FrontierWorldState before = base.initialState();
        SubjectId store = new SubjectId("container:hive-east-store");
        ExactItemStack biomass = exactHiveBiomass(before, store);
        before = before.withInventory(before.inventory().withFungibleResources(FungibleResourceLedger.empty()).store(biomass));
        PhysicalReplicaRecord replica = PhysicalReplicaRecord.expected(store, ReferenceContainerCustody.semanticKind(before, store), 0L,
                ReferenceContainerCustody.canonicalFingerprint(before, store), ReferenceContainerCustody.provenance(store));
        PhysicalReplicaCustodyState custody = PhysicalReplicaCustodyState.empty().declare(replica)
                .observe(store, 0L, 1L, replica.fingerprint(), replica.provenance(), 0L)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(store), store, ReferenceContainerCustody.PROVIDER_ID,
                        1L, 0L, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null));
        FrontierWorldState confirmed = before.withInventory(before.inventory().consume(biomass.id(), biomass.count()))
                .withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(base.worldId(), confirmed,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(configuration, new EphemeralStore(), 10_000);

        BlockPos position = helper.absolutePos(new BlockPos(10, 8, 0)); ServerLevel level = helper.getLevel();
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3); level.setBlock(position, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(position);
        chest.getPersistentData().putString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY, store.value());
        chest.getPersistentData().putString(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY, ReferenceContainerCustody.provenance(store));
        FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, confirmed, store);
        helper.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.checkpointConfirmedMutation(runtime, store, chest),
                "a confirmed exact mutation must checkpoint, release, and emit before another tick can unload its chest");
        FrontierWorldState fenced = runtime.decodedState().orElseThrow();
        PhysicalReplicaRecord emitted = fenced.replicaCustody().replicas().get(store);
        helper.assertTrue(emitted.state() == PhysicalReplicaState.EXPECTED && emitted.fingerprint().equals(ReferenceContainerCustody.canonicalFingerprint(fenced, store))
                        && !ReferenceContainerCustody.hasLiveCustody(fenced, store),
                "the same turn leaves a new serialized replica boundary rather than stale live custody");
        helper.assertValueEqual(fenced, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(fenced)),
                "an immediate restart preserves the emitted custody/replica boundary without classifying the owned empty chest as foreign drift");
        runtime.shutdown(); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-reference-conflict-restart", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void nutrientEndpointExecutorClosesAndRecoversItsOwnReferenceBoundary(GameTestHelper helper) {
        WorldId world = new WorldId("frontier:reference-nutrient-endpoint-boundary");
        SubjectId east = new SubjectId("container:hive-east-store");
        FrontierWorldState state = activated(FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L)), east);
        BlockPos position = position(state, east);
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:reference-nutrient"), hive,
                StrategicObjectiveKind.HIVE_GROW_ORGANISM, Optional.empty(), 2, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:reference-nutrient"), objective.id(), hive,
                StrategicTaskKind.GROW_HIVE_ORGANISM, Optional.empty(), List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS), List.of(), StrategicTaskStatus.PENDING);
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
        state = held(state, east);
        ExactItemStack biomass = exactHiveBiomass(state, east);
        state = state.withInventory(state.inventory().withFungibleResources(FungibleResourceLedger.empty()).store(biomass));
        HiveNutrientTransfer transfer = HiveNutrientTransferProcess.create(state, task, biomass, new SubjectId("container:hive-west-store"), 0);
        state = HiveNutrientTransferProcess.reduceStarted(state, hive, transfer);
        var departure = HiveNutrientTransferStateSupport.departureIntent(state, transfer);
        state = state.preparePhysicalIntent(departure);
        state = state.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(
                FencedRecoveryPhysicalIntentSupport.prepared(state.fencedRecovery(), departure, FencedRecoveryAsset.CARGO)));

        ChestBlockEntity chest = chest(helper, position, east);
        FrontierV3ContainerSurfaceExecutor.replaceCanonicalSlots(chest, state, east);
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(world, state);
        helper.assertTrue(runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE,
                "nutrient endpoint fixture startup: " + runtime.status());
        FrontierV3HiveNutrientEndpointExecutor.tick(helper.getLevel(), runtime);
        helper.assertTrue(runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE,
                "nutrient endpoint transition: " + runtime.status());
        FrontierWorldState confirmed = runtime.decodedState().orElseThrow();
        helper.assertTrue(confirmed.physicalIntents().get(departure.id()).status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED
                        && confirmed.inventory().items().get(biomass.id()).custody().equals(new InventoryCustody.Cargo(transfer.cargoId()))
                        && emittedAndReleased(confirmed, east),
                "the nutrient endpoint executor itself records exact cargo and its next released replica boundary");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> recovered = recovered(world, runtime.checkpointImage().orElseThrow());
        helper.assertTrue(emittedAndReleased(recovered.decodedState().orElseThrow(), east),
                "the executor-established nutrient boundary survives persisted recovery");
        recovered.shutdown(); runtime.shutdown(); helper.succeed();
    }

    /** A HOT hive source becomes COLD cargo only after its named physical stack is removed. */
    @GameTest(batch = "pm-frontier-v3-reference-conflict-restart", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void fungibleNutrientEndpointTransfersOneHotSourceToColdCargo(GameTestHelper helper) {
        WorldId world = new WorldId("frontier:reference-fungible-nutrient-boundary");
        SubjectId east = new SubjectId("container:hive-east-store"), west = new SubjectId("container:hive-west-store");
        FrontierWorldState state = activated(FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L)), east);
        SubjectId hive = state.bootstrap().hive().id(), lotId = new SubjectId("lot:reference-hive-biomass");
        SubjectId accountId = new SubjectId("custody:reference-hive-biomass");
        ResourceLot biomass = new ResourceLot(lotId, hive, "minecraft:rotten_flesh", 64, "reference", List.of());
        CustodyAccount account = new CustodyAccount(accountId, new ResourceCustody.Container(east), Map.of(lotId, 64), Map.of());
        FungibleResourceLedger cold = FungibleResourceLedger.empty().issue(biomass, account);
        FungiblePhysicalObservation.Stack stack = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(east, 0)), biomass.itemKind(), 64);
        FungibleResourceLedger hot = cold.rebind(accountId, 1L, FungiblePhysicalObservation.bind(cold, accountId, 1L, List.of(stack)));
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:reference-fungible-nutrient"), hive,
                StrategicObjectiveKind.HIVE_GROW_ORGANISM, Optional.empty(), 2, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:reference-fungible-nutrient"), objective.id(), hive,
                StrategicTaskKind.GROW_HIVE_ORGANISM, Optional.empty(), List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS),
                List.of(), StrategicTaskStatus.PENDING);
        state = held(state.withInventory(state.inventory().withFungibleResources(hot))
                .withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task)), east);
        HiveNutrientTransfer transfer = HiveNutrientTransferProcess.create(state, task,
                new FungibleResourceCustodySupport.LotAtContainer(accountId, biomass, 64), west, 0);
        state = HiveNutrientTransferProcess.reduceStarted(state, hive, transfer);
        var departure = HiveNutrientTransferStateSupport.departureIntent(state, transfer);
        state = state.preparePhysicalIntent(departure);
        state = state.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(
                FencedRecoveryPhysicalIntentSupport.prepared(state.fencedRecovery(), departure, FencedRecoveryAsset.CARGO)));

        ChestBlockEntity chest = chest(helper, position(state, east), east);
        chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH, 64)); chest.setChanged();
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(world, state);
        helper.assertTrue(runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE,
                "fungible nutrient endpoint fixture startup: " + runtime.status());
        FrontierV3HiveNutrientEndpointExecutor.tick(helper.getLevel(), runtime);
        helper.assertTrue(runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE,
                "fungible nutrient endpoint transition: " + runtime.status());
        FrontierWorldState confirmed = runtime.decodedState().orElseThrow();
        helper.assertTrue(chest.getItem(0).isEmpty() && confirmed.physicalIntents().get(departure.id()).status()
                        == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED,
                "the endpoint removes one verified HOT source only after its RUNNING record is durable");
        helper.assertTrue(confirmed.inventory().fungibleResources().accounts().values().stream().anyMatch(value -> value.custody()
                        instanceof ResourceCustody.Cargo cargo && cargo.cargoId().equals(transfer.cargoId())
                        && value.lotQuantities().equals(Map.of(lotId, 64)))
                        && !confirmed.inventory().fungibleResources().accounts().containsKey(accountId)
                        && confirmed.inventory().fungibleResources().bindings().isEmpty(),
                "the exact removed portion has one COLD cargo owner and no surviving source binding");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> recovered = recovered(world, runtime.checkpointImage().orElseThrow());
        helper.assertValueEqual(confirmed, recovered.decodedState().orElseThrow(),
                "the confirmed fungible source departure survives immediate replay without duplicate cargo");
        recovered.shutdown(); runtime.shutdown(); helper.succeed();
    }

    /** A player taking part of a reserved HOT hive input retires only that claimed growth work. */
    @GameTest(batch = "pm-frontier-v3-reference-conflict-restart", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void reservedFungibleHiveWithdrawalForfeitsItsOneOwningGrowthJob(GameTestHelper helper) {
        WorldId world = new WorldId("frontier:reference-fungible-claim-withdrawal");
        SubjectId east = new SubjectId("container:hive-west-store"), hive;
        FrontierWorldState state = activated(FrontierWorldState.initial(FrontierBootstrapper.create(world, 91L)), east);
        hive = state.bootstrap().hive().id(); SubjectId lotId = new SubjectId("lot:reference-hive-claim");
        SubjectId accountId = new SubjectId("custody:reference-hive-claim");
        ResourceLot biomass = new ResourceLot(lotId, hive, "minecraft:rotten_flesh", 64, "reference", List.of());
        CustodyAccount account = new CustodyAccount(accountId, new ResourceCustody.Container(east), Map.of(lotId, 64), Map.of());
        FungibleResourceLedger cold = FungibleResourceLedger.empty().issue(biomass, account);
        FungiblePhysicalObservation.Stack stack = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(east, 0)), biomass.itemKind(), 64);
        FungibleResourceLedger hot = cold.rebind(accountId, 1L, FungiblePhysicalObservation.bind(cold, accountId, 1L, List.of(stack)));
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:reference-fungible-claim"), hive,
                StrategicObjectiveKind.HIVE_GROW_ORGANISM, Optional.empty(), 2, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:reference-fungible-claim"), objective.id(), hive,
                StrategicTaskKind.GROW_HIVE_ORGANISM, Optional.empty(), List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS),
                List.of(), StrategicTaskStatus.PENDING);
        state = held(state.withInventory(state.inventory().withFungibleResources(hot))
                .withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task)), east);
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> start = HiveGrowthProcess.planStart(state, HiveGrowthProcess.start(task, 100L));
        HiveGrowthStarted started = start.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(HiveGrowthStarted.class::isInstance).map(HiveGrowthStarted.class::cast).findFirst().orElseThrow();
        PhysicalIntentPrepared prepared = start.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload)
                .filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        state = state.withStrategicPlans(state.strategicPlans().transitionTask(task.id(), StrategicTaskStatus.ACTIVE));
        state = HiveGrowthProcess.reduceStarted(state, hive, started).preparePhysicalIntent(prepared.intent());

        ChestBlockEntity chest = chest(helper, position(state, east), east);
        chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH, 32)); chest.setChanged();
        var player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH, 32));
        helper.assertTrue(helper.getLevel().players().stream().flatMap(value -> java.util.stream.IntStream.range(0,
                        value.getInventory().getContainerSize()).mapToObj(value.getInventory()::getItem))
                        .filter(value -> !value.isEmpty() && value.getItem() == net.minecraft.world.item.Items.ROTTEN_FLESH
                        && value.getCount() == 32).count() == 1,
                "the observed departure must have exactly one unambiguous matching player stack");
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = runtime(world, state);
        helper.assertTrue(!FrontierV3FungibleResourceObservationExecutor.observe(helper.getLevel(), runtime,
                        runtime.decodedState().orElseThrow(), account, chest, 1L),
                "a committed handoff consumes this adapter turn rather than treating the old layout as current");
        FrontierWorldState forfeited = runtime.decodedState().orElseThrow();
        SubjectId claimId = ((HiveGrowthInputHold.FungibleCold) started.job().inputHold()).claimId();
        helper.assertTrue(!forfeited.hiveColony().growthJobs().containsKey(started.job().id())
                        && forfeited.strategicPlans().tasks().get(task.id()).status() == StrategicTaskStatus.BLOCKED
                        && !forfeited.physicalIntents().containsKey(prepared.intent().id())
                        && !forfeited.inventory().fungibleResources().claims().containsKey(claimId),
                "the exact owning growth job is retired atomically and no reserved allocation survives the physical loss");
        helper.assertTrue(forfeited.inventory().fungibleResources().accounts().values().stream().anyMatch(value -> value.custody()
                        .equals(new ResourceCustody.Player(player.getUUID())) && value.lotQuantities().equals(Map.of(lotId, 32))
                        && value.claimQuantities().isEmpty())
                        && forfeited.inventory().fungibleResources().totalQuantity(hive, "minecraft:rotten_flesh") == 64,
                "the observed player portion remains one unclaimed HOT custody account with the conserved total");
        runtime.shutdown(); helper.succeed();
    }

    private static FrontierWorldState activated(FrontierWorldState state, SubjectId containerId) {
        java.util.Map<SubjectId, ContainerSurface> surfaces = new LinkedHashMap<>(state.inventory().surfaces());
        surfaces.put(containerId, new ContainerSurface(containerId, surfaces.get(containerId).position(), ContainerSurfaceStatus.ACTIVE));
        ExactInventory inventory = new ExactInventory(state.inventory().containers(), state.inventory().items(), state.inventory().cargo(),
                state.inventory().playerItems(), state.inventory().worldCarrierItems(), state.inventory().conflicts(), surfaces, state.inventory().economics());
        return state.withInventory(inventory);
    }

    private static BlockPos position(FrontierWorldState state, SubjectId containerId) {
        BlockPosition value = state.inventory().surfaces().get(containerId).position();
        return new BlockPos(value.x(), value.y(), value.z());
    }

    private static ExactItemStack exactHiveBiomass(FrontierWorldState state, SubjectId store) {
        return new ExactItemStack(new SubjectId("item:bootstrap-hive-biomass"), state.bootstrap().hive().id(), "minecraft:rotten_flesh", 64,
                new InventoryCustody.ContainerSlot(store, 0));
    }

    private static FrontierWorldState held(FrontierWorldState state, SubjectId containerId) {
        PhysicalReplicaRecord expected = PhysicalReplicaRecord.expected(containerId, ReferenceContainerCustody.semanticKind(state, containerId), 0L,
                ReferenceContainerCustody.canonicalFingerprint(state, containerId), ReferenceContainerCustody.provenance(containerId));
        PhysicalReplicaCustodyState custody = PhysicalReplicaCustodyState.empty().declare(expected)
                .observe(containerId, 0L, 1L, expected.fingerprint(), expected.provenance(), 0L)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(containerId), containerId, ReferenceContainerCustody.PROVIDER_ID,
                        1L, 0L, 2L, PhysicalCustodyLeaseStatus.ACQUIRED, null));
        return state.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(custody));
    }

    static ChestBlockEntity chest(GameTestHelper helper, BlockPos position, SubjectId containerId) {
        ServerLevel level = helper.getLevel(); level.setBlock(position, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(position);
        chest.getPersistentData().putString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY, containerId.value());
        chest.getPersistentData().putString(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY, ReferenceContainerCustody.provenance(containerId));
        return chest;
    }

    static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime(WorldId world, FrontierWorldState state) {
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(base.worldId(), state,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(state.bootstrap()), base.projectionMapper(), base.limits(),
                List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        return FrontierV3ServerRuntime.start(configuration, new EphemeralStore(), 10_000);
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> recovered(WorldId world,
                                                                                                     io.farfrontier.palemirror.frontier.v3.api.CheckpointImage checkpoint) {
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        return FrontierV3ServerRuntime.start(configuration, new SnapshotStore(world, checkpoint), 10_000);
    }

    private static FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> recovered(WorldId world,
            io.farfrontier.palemirror.frontier.v3.api.CheckpointImage checkpoint, FrontierBootstrap bootstrap) {
        var base = FrontierWorldRuntimeDefinition.configuration(world, 91L);
        var configuration = new FrontierEngineConfiguration<>(world, FrontierWorldState.initial(bootstrap), base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(bootstrap), base.projectionMapper(),
                base.limits(), List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        return FrontierV3ServerRuntime.start(configuration, new SnapshotStore(world, checkpoint), 10_000);
    }

    private static boolean emittedAndReleased(FrontierWorldState state, SubjectId containerId) {
        PhysicalReplicaRecord replica = state.replicaCustody().replicas().get(containerId);
        return replica != null && replica.state() == PhysicalReplicaState.EXPECTED && !ReferenceContainerCustody.hasLiveCustody(state, containerId)
                && replica.fingerprint().equals(ReferenceContainerCustody.canonicalFingerprint(state, containerId));
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

    private record SnapshotStore(WorldId world, io.farfrontier.palemirror.frontier.v3.api.CheckpointImage checkpoint) implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) {
            if (!world.equals(worldId)) throw new IllegalArgumentException("recovery world mismatch");
            return new RecoveryImage(world, Optional.of(new SnapshotRecord(checkpoint, checkpoint.revision().value())), List.of());
        }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) { throw new UnsupportedOperationException("recovered GameTest store is read-only"); }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("recovered GameTest store is read-only"); }
        @Override public CompactionReceipt compact(WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision coveredRevision) { throw new UnsupportedOperationException("recovered GameTest store is read-only"); }
    }
}
