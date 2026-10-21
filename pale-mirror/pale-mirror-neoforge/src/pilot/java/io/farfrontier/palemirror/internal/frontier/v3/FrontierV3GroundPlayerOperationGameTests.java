package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied;
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
    public static void personalStockDeathUsesOneNativeTransferAndNeverInfersAnotherFromCounts(GameTestHelper helper) {
        var level = helper.getLevel(); var point = helper.absolutePos(new BlockPos(1, 1, 1));
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:entity-resource-drop"), 95L);
        var initial = base.initialState(); var actor = initial.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var execution = initial.actorExecutions().next(actor, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.PRESENCE, actor);
        initial = ActorExecutionComposition.LIFECYCLE
                .prepareVacant(initial, execution).commit(initial, FrontierWorldStateUpdate.begin());
        initial = ActorBodyAuthority.demand(initial, actor); var id = ActorBodyAuthority.current(initial, actor);
        initial = ActorBodyAuthority.running(initial, id);
        var body = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        body.setUUID(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(initial.bootstrap().worldId(), actor));
        body.setPos(point.getX() + .5, point.getY(), point.getZ() + .5); body.setNoAi(true);
        FrontierV3ActorCarrierComposition.stamp(body, FrontierV3ActorCarrierComposition.fromCanonical(initial, actor,
                ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, body.getUUID(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, id.physicalEpoch()));
        helper.assertTrue(level.addFreshEntity(body), "native source admitted");
        body.getInventory().setItem(3, new ItemStack(Items.COBBLESTONE, 7));
        var account = new SubjectId("custody:entity-resource-drop");
        var lot = new ResourceLot(new SubjectId("lot:entity-resource-drop"), initial.humanPopulation().resident(actor).settlementId(),
                "minecraft:cobblestone", 7, "fixture:personal-stock", List.of());
        var resources = initial.inventory().fungibleResources().issue(lot, new CustodyAccount(account, new ResourceCustody.Actor(actor),
                Map.of(lot.id(), 7), Map.of(), Optional.of(new ActorItemSlot.Pocket(3))));
        initial = initial.withInventory(initial.inventory().withFungibleResources(resources));
        var config = new FrontierEngineConfiguration<>(base.worldId(), initial, base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var runtime = FrontierV3ServerRuntime.start(config, new FrontierV3AmbientActorGameTests.EphemeralStore(), 10_000);
        try {
            body.setHealth(0);
            var settlement = FrontierV3UnitInventoryDeathResources.prepare(level, runtime, body, id);
            var current = runtime.decodedState().orElseThrow(); var location = current.actorLocations().get(actor);
            FrontierV3CommandSubmission.submit(runtime, "fixture-death", actor.value(), new ActorBodyDied(id, location.body(),
                    location.condition().health(), Optional.empty(), current.actorExecutions().actors().get(actor).current(), "test:pre-loot"));
            settlement.settle();
            var after = runtime.decodedState().orElseThrow(); var stock = after.inventory().fungibleResources();
            var destination = stock.accounts().values().stream().filter(a -> a.lotQuantities().containsKey(lot.id())).findFirst().orElseThrow();
            helper.assertTrue(!stock.accounts().containsKey(account) && destination.custody() instanceof ResourceCustody.WorldCarrier
                    && destination.lotQuantities().equals(Map.of(lot.id(), 7)) && stock.lots().get(lot.id()).equals(lot),
                    "one confirmed death transfer preserves quantity and title without retaining dead-actor stock");
            var drop = (ItemEntity) level.getEntity(((ResourceCustody.WorldCarrier) destination.custody()).carrierId());
            helper.assertTrue(drop != null && drop.getItem().getCount() == 7 && body.getInventory().getItem(3).isEmpty(),
                    "physical pocket emptied exactly once into the accounted native entity");
            var journal = FrontierV3ResourceHandoffLedger.get(level); var operations = FrontierV3OperationLedger.get(level);
            helper.assertTrue(journal.entityDrops().isEmpty() && FrontierV3NativeOperationMarkers.read(body.getPersistentData()).size() == 1,
                    "owner outbox closes through the shared operation, not a private item tag");
            settlement.settle(); helper.assertTrue(runtime.decodedState().orElseThrow().equals(after), "repeated death settlement cannot duplicate stock");
            var source = FrontierV3OperationEntityParticipant.nativeObject(body).identity();
            var birth = FrontierV3OperationEntityParticipant.nativeObject(drop).identity();
            var outcome = new UnitInventoryDispositionObserved(id, account, 1, UnitInventoryDispositionObserved.Outcome.WORLD_DROP, Optional.of(drop.getUUID()));
            var unproved = operations.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.ENTITY_RESOURCE_DROP,
                    FrontierExecutionSubjects.PHYSICAL_EXECUTOR, UUID.randomUUID().toString(), FrontierV3OperationConfirmation.fingerprint(outcome), Set.of(source, birth)));
            var transaction = new FrontierV3EntityResourceDropTransaction(source, birth, unproved, outcome);
            journal.prepareEntityDrop(transaction); journal.persist(level); operations.persist(level);
            helper.assertTrue(!FrontierV3EntityResourceDropOperation.reconcile(level, runtime, transaction)
                    && runtime.decodedState().orElseThrow().equals(after), "matching empty pocket and dropped quantity do not prove another operation");
            helper.assertTrue(operations.cancelUnattempted(unproved.ticket()), "unattempted effect is explicitly cancelled"); operations.persist(level);
            helper.assertTrue(FrontierV3EntityResourceDropOperation.reconcile(level, runtime, transaction)
                    && runtime.decodedState().orElseThrow().equals(after) && journal.entityDrops().isEmpty(),
                    "cancel-before-owner-retire cut neither repeats the physical effect nor submits its unperformed consequence");
        } finally { runtime.shutdown(); }
        helper.succeed();
    }
    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void nativeContainerDropSplitsOnceAndConfirmsTheWholeResourceOutcome(GameTestHelper helper) {
        var level = helper.getLevel(); var point = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(point, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(point);
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:container-drop-operation"), 94L);
        var owner = base.initialState().bootstrap().settlements().getFirst().id(); var container = FrontierWorldState.depotId(owner);
        chest.getPersistentData().putString(FrontierV3ExactItemPresentation.CONTAINER_ID_KEY, container.value());
        chest.setItem(26, new ItemStack(Items.BREAD, 64));
        var account = ReferenceContainerCustody.scopeId(container);
        var lot = new ResourceLot(new SubjectId("lot:container-drop-operation"), owner, "minecraft:bread", 64, "test:drop", List.of());
        var resources = FungibleResourceLedger.empty().issue(lot,
                new CustodyAccount(account, new ResourceCustody.Container(container), Map.of(lot.id(), 64), Map.of()));
        resources = resources.rebind(account, 1, FungiblePhysicalObservation.bind(resources, account, 1,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(container, 26)),
                        lot.itemKind(), 64))));
        var initial = base.initialState().withInventory(base.initialState().inventory().withFungibleResources(resources));
        var config = new FrontierEngineConfiguration<>(base.worldId(), initial, base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                base.initialSchedules(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var runtime = FrontierV3ServerRuntime.start(config, new FrontierV3AmbientActorGameTests.EphemeralStore(), 10_000);
        try {
            FrontierV3ContainerDropOperation.perform(level, runtime, FrontierV3PhysicalContainer.declaredChest(container, chest), 26,
                    chest.getItem(26), point.getX(), point.getY(), point.getZ());
            var next = runtime.decodedState().orElseThrow().inventory().fungibleResources();
            var drops = next.accounts().values().stream().filter(a -> a.custody() instanceof ResourceCustody.Ground).toList();
            helper.assertTrue(chest.getItem(26).isEmpty() && !next.accounts().containsKey(account) && drops.size() >= 3,
                    "actual vanilla callback splits the original stack and retires its source");
            helper.assertTrue(drops.stream().flatMap(a -> a.lotQuantities().values().stream()).mapToInt(Integer::intValue).sum() == 64
                    && next.lots().get(lot.id()).equals(lot), "fragmented drops preserve the original lot and all 64 resources");
            helper.assertTrue(drops.stream().allMatch(a -> level.getEntity(((ResourceCustody.Ground) a.custody()).entityId()) instanceof ItemEntity e
                    && e.getItem().getCount() == a.lotQuantities().get(lot.id())), "every canonical fragment is the actual native drop");
            helper.assertTrue(FrontierV3ResourceHandoffLedger.get(level).containerDrops().isEmpty(), "one owner outcome closes the whole native split");
            helper.assertTrue(FrontierV3NativeOperationMarkers.read(chest.getPersistentData()).size() == 1,
                    "one operation covers the original stack, not one operation per fragment");
            var fragment = (ItemEntity) level.getEntity(((ResourceCustody.Ground) drops.getFirst().custody()).entityId());
            var operations = FrontierV3OperationLedger.get(level); var journal = FrontierV3ResourceHandoffLedger.get(level);
            var unproved = operations.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.CONTAINER_DROP,
                    FrontierExecutionSubjects.PHYSICAL_EXECUTOR, UUID.randomUUID().toString(), "unproved-drop",
                    Set.of(FrontierV3OperationBlockEntityParticipant.current(chest).identity(),
                            FrontierV3OperationEntityParticipant.nativeObject(fragment).identity())));
            var exact = new ExactItemCustodyChanged(new SubjectId("item:unproved-drop"), new InventoryCustody.ContainerSlot(container, 26),
                    new InventoryCustody.WorldCarrier(fragment.getUUID()));
            var retained = new FrontierV3ContainerDropTransaction(container, 26, unproved, List.of(), List.of(exact));
            journal.prepareDrop(retained); journal.persist(level); operations.persist(level);
            var canonical = runtime.decodedState().orElseThrow();
            helper.assertTrue(!FrontierV3ContainerDropOperation.reconcile(level, runtime, retained)
                    && runtime.decodedState().orElseThrow().equals(canonical), "existing destination and empty source never prove another drop");
            helper.assertTrue(operations.cancelUnattempted(unproved.ticket()), "only unattempted callback is cancellable"); operations.persist(level);
            helper.assertTrue(FrontierV3ContainerDropOperation.reconcile(level, runtime, retained)
                    && runtime.decodedState().orElseThrow().equals(canonical) && journal.containerDrops().isEmpty(),
                    "cancel-before-owner-retire recovery neither spawns nor submits a new outcome");
        } finally { runtime.shutdown(); }
        helper.succeed();
    }
    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void nativeMergeConservesBothLotsAndClosesOneCausalHandoff(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); var point = helper.absolutePos(new BlockPos(1, 1, 1));
        var source = new ItemEntity(level, point.getX() + .5, point.getY(), point.getZ() + .5, new ItemStack(Items.BREAD, 5));
        var destination = new ItemEntity(level, point.getX() + .5, point.getY(), point.getZ() + .5, new ItemStack(Items.BREAD, 10));
        source.setNoGravity(true); destination.setNoGravity(true);
        helper.assertTrue(level.addFreshEntity(source) && level.addFreshEntity(destination), "both native participants admitted");
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:ground-merge-operation"), 93L);
        var owner = base.initialState().bootstrap().settlements().getFirst().id();
        var resources = FungibleResourceLedger.empty(); var accounts = new ArrayList<SubjectId>();
        for (var entity : List.of(source, destination)) {
            var account = new SubjectId("custody:merge-" + entity.getUUID()); accounts.add(account);
            var lot = new ResourceLot(new SubjectId("lot:merge-" + entity.getUUID()), owner, "minecraft:bread", entity.getItem().getCount(), "test:merge", List.of());
            resources = resources.issue(lot, new CustodyAccount(account,
                    new ResourceCustody.Ground(entity.getUUID(), new BlockPosition(point.getX(), point.getY(), point.getZ())),
                    Map.of(lot.id(), lot.quantity()), Map.of()));
            resources = resources.rebind(account, 1, FungiblePhysicalObservation.bind(resources, account, 1,
                    List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.WorldEntity(entity.getUUID()), lot.itemKind(), lot.quantity()))));
        }
        var initial = base.initialState().withInventory(base.initialState().inventory().withFungibleResources(resources));
        var config = new FrontierEngineConfiguration<>(base.worldId(), initial, base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                base.initialSchedules(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var runtime = FrontierV3ServerRuntime.start(config, new FrontierV3AmbientActorGameTests.EphemeralStore(), 10_000);
        var nativeMerge = ItemEntity.class.getDeclaredMethod("merge", ItemEntity.class, ItemStack.class, ItemEntity.class, ItemStack.class);
        nativeMerge.setAccessible(true);
        try {
            FrontierV3OperationEntityColumns.forRemoval(level, source.chunkPosition());
            var from = resources.bindings().values().stream().filter(b -> b.accountId().equals(accounts.getFirst())).findFirst().orElseThrow();
            var to = resources.bindings().values().stream().filter(b -> b.accountId().equals(accounts.getLast())).findFirst().orElseThrow();
            var receipt = FungiblePhysicalHandoff.returnToExisting(resources, from.accountId(), 1, from, to.accountId(), 1, to);
            var column = FrontierV3OperationEntityColumns.current(level, source.chunkPosition()).orElseThrow();
            var operations = FrontierV3OperationLedger.get(level); var journal = FrontierV3ResourceHandoffLedger.get(level);
            var cancelled = operations.prepare(new FrontierV3OperationReceipt.Declaration(FrontierV3OperationReceipt.Protocol.GROUND_MERGE,
                    FrontierExecutionSubjects.PHYSICAL_EXECUTOR, UUID.randomUUID().toString(), FrontierV3OperationConfirmation.fingerprint(receipt),
                    Set.of(column.identity(), FrontierV3OperationEntityParticipant.nativeObject(destination).identity())));
            journal.prepareNative(destination.getUUID(), receipt, cancelled); journal.persist(level); operations.persist(level);
            helper.assertTrue(operations.cancelUnattempted(cancelled.ticket()), "only an unattempted operation can be cancelled"); operations.persist(level);
            helper.assertTrue(FrontierV3NativeHandoffOperation.reconcile(level, runtime, destination.getUUID(), receipt)
                    && runtime.decodedState().orElseThrow().equals(initial), "cancel-before-owner-retire cut never invents a canonical transfer");
            helper.assertTrue(!FrontierV3GroundItemMerge.before(level, runtime, destination, destination.getItem(), source, source.getItem(), () -> {
                try { nativeMerge.invoke(null, destination, destination.getItem(), source, source.getItem()); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            }), "managed merge wraps the real native method");
            helper.assertTrue(source.isRemoved() && destination.getItem().getCount() == 15, "Vanilla merged and removed the actual source");
            var next = runtime.decodedState().orElseThrow().inventory().fungibleResources();
            helper.assertTrue(!next.accounts().containsKey(accounts.getFirst()) && next.accounts().get(accounts.getLast()).lotQuantities().size() == 2
                    && next.accounts().get(accounts.getLast()).lotQuantities().values().stream().mapToInt(Integer::intValue).sum() == 15,
                    "canonical merger conserves both original lots");
            helper.assertTrue(FrontierV3ResourceHandoffLedger.get(level).pending().isEmpty(), "handoff owner closes through the shared operation");
        } finally { runtime.shutdown(); }
        helper.succeed();
    }
    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void destructionNeedsAnActualRemovalReceiptNotAnAbsentEntity(GameTestHelper helper) {
        var level = helper.getLevel(); var point = helper.absolutePos(new BlockPos(1, 1, 1));
        var item = new ItemEntity(level, point.getX() + .5, point.getY(), point.getZ() + .5, new ItemStack(Items.BREAD, 2));
        item.setNoGravity(true); helper.assertTrue(level.addFreshEntity(item), "source admitted");
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:ground-destruction-operation"), 92L);
        var owner = base.initialState().bootstrap().settlements().getFirst().id(); var account = new SubjectId("custody:ground-destruction");
        var ground = new ResourceCustody.Ground(item.getUUID(), new BlockPosition(point.getX(), point.getY(), point.getZ()));
        var lot = new ResourceLot(new SubjectId("lot:ground-destruction"), owner, "minecraft:bread", 2, "test:destruction", List.of());
        var resources = FungibleResourceLedger.empty().issue(lot, new CustodyAccount(account, ground, Map.of(lot.id(), 2), Map.of()));
        resources = resources.rebind(account, 1, FungiblePhysicalObservation.bind(resources, account, 1,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.WorldEntity(item.getUUID()), lot.itemKind(), 2))));
        var initial = base.initialState().withInventory(base.initialState().inventory().withFungibleResources(resources));
        var config = new FrontierEngineConfiguration<>(base.worldId(), initial, base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                base.initialSchedules(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var runtime = FrontierV3ServerRuntime.start(config, new FrontierV3AmbientActorGameTests.EphemeralStore(), 10_000);
        try {
            var journal = FrontierV3ResourceHandoffLedger.get(level);
            var unproved = new GroundResourceDestroyed(account, ground, 1, owner, UUID.randomUUID(), "DISCARDED", Map.of(lot.id(), 2), Set.of());
            journal.prepareLoss(unproved); journal.persist(level);
            helper.assertTrue(!FrontierV3GroundItemDestruction.reconcile(level, runtime, unproved), "a witness is not proof of execution");
            journal.retireLoss(unproved); journal.persist(level);
            helper.assertTrue(FrontierV3GroundItemDestruction.before(level, item,
                    net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK, runtime), "unloading never consumes resource stock");
            helper.assertTrue(!FrontierV3GroundItemDestruction.before(level, item,
                    net.minecraft.world.entity.Entity.RemovalReason.DISCARDED, runtime), "outer removal is replaced by exactly one native callback");
            helper.assertTrue(item.isRemoved() && item.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.DISCARDED,
                    "native removal actually occurred");
            helper.assertTrue(!runtime.decodedState().orElseThrow().inventory().fungibleResources().accounts().containsKey(account)
                    && journal.losses().isEmpty(), "confirmed destruction retires stock and its owner witness");
            var bag = FrontierV3OperationEntityColumns.current(level, item.chunkPosition()).orElseThrow().persistent();
            helper.assertTrue(FrontierV3NativeOperationMarkers.read(bag).stream()
                    .anyMatch(r -> FrontierV3OperationLedger.get(level).require(r.ticket()).declaration().protocol()
                            == FrontierV3OperationReceipt.Protocol.GROUND_DESTRUCTION),
                    "exact native column receipt survives destruction of the source entity");
            // Even an absent entity and absent canonical stock cannot prove an unrelated operation.
            journal.prepareLoss(unproved); journal.persist(level);
            helper.assertTrue(!FrontierV3GroundItemDestruction.reconcile(level, runtime, unproved), "absence cannot forge a retirement receipt");
            journal.retireLoss(unproved); journal.persist(level);
        } finally { runtime.shutdown(); }
        helper.succeed();
    }
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
