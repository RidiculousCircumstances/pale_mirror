package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceTransition;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceCustody;
import io.farfrontier.palemirror.frontier.v3.model.FungibleResourceLedger;
import io.farfrontier.palemirror.frontier.v3.model.CustodyAccount;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackBinding;
import io.farfrontier.palemirror.frontier.v3.model.ResourceLot;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLease;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaRecord;
import io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody;
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
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Real player clicks into a mixed exact/fungible managed inventory. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ExactContainerIngressGameTests {
    @GameTest(batch = "pm-frontier-v3-player-withdrawal", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void taggedPlayerToolReturnRetiresTheClickAndAllowsTheNextResourceDeposit(GameTestHelper helper) {
        var level = helper.getLevel(); var player = helper.makeMockServerPlayerInLevel();
        var position = interior(helper); var container = new SubjectId("container:test-exact-tool-return");
        var seed = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:exact-tool-click"), 91L).initialState().bootstrap();
        var base = FrontierWorldRuntimeDefinition.configuration(FrontierV3BootstrapGameTestFixtures.translatedBootstrap(seed,
                position.getX(), position.getY() - 65, position.getZ()));
        var owner = new SubjectId("settlement:1");
        var station = io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor.at(position.getX() - 1, position.getY() - 1, position.getZ());
        var endpoint = new io.farfrontier.palemirror.frontier.v3.model.ShipmentEndpoint.Storage(owner, container, station, Optional.empty(), station);
        var record = new io.farfrontier.palemirror.frontier.v3.model.ContainerRecord(container, owner, 27,
                io.farfrontier.palemirror.frontier.v3.model.ContainerPurpose.RESOURCE_STORAGE, Optional.empty(), Optional.of(
                    new io.farfrontier.palemirror.frontier.v3.model.ResourceStoragePolicy(endpoint,
                        io.farfrontier.palemirror.frontier.v3.model.ResourceStorageFilter.all(), 0,
                        io.farfrontier.palemirror.frontier.v3.model.ResourceStorageRole.WAREHOUSE)));
        var initial = new io.farfrontier.palemirror.frontier.v3.model.ResourceStorageDeclared(record,
                new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(position.getX(), position.getY(), position.getZ())).apply(base.initialState(), owner);
        var tool = new ExactItemStack(new SubjectId("item:test-exact-tool-return"), owner, "minecraft:iron_pickaxe", 1,
                new InventoryCustody.Player(player.getUUID()));
        var storedTool = new ExactItemStack(tool.id(), owner, tool.itemKind(), tool.count(), new InventoryCustody.ContainerSlot(container, 0));
        initial = initial.withInventory(initial.inventory().store(storedTool).moveObservedItem(tool.id(), storedTool.custody(), tool.custody())
                .withSurfaceStatus(container, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(container, ContainerSurfaceStatus.ACTIVE));
        var replica = PhysicalReplicaRecord.expected(container, ReferenceContainerCustody.semanticKind(initial, container), 0,
                ReferenceContainerCustody.canonicalFingerprint(initial, container), ReferenceContainerCustody.provenance(container));
        initial = initial.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(initial.replicaCustody().declare(replica)
                .observe(container, 0, 1, replica.fingerprint(), replica.provenance(), 0)
                .acquire(new PhysicalCustodyLease(ReferenceContainerCustody.scopeId(container), container, ReferenceContainerCustody.PROVIDER_ID,
                        1, 0, 2, PhysicalCustodyLeaseStatus.ACQUIRED, null))));
        var config = new FrontierEngineConfiguration<>(base.worldId(), initial, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                base.stateCodec(), base.projectionMapper(), base.limits(), base.initialSchedules(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var runtime = FrontierV3ServerRuntime.start(config, new EphemeralStore(), 10_000);
        level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
        var chest = FrontierV3ContainerSurfaceExecutor.claimFreshChest(level, position, container);
        helper.assertTrue(chest != null, "owned player-accessible physical chest");
        player.teleportTo(position.getX() + 0.5, position.getY() + 1, position.getZ() + 0.5);
        chest.getPersistentData().putString(FrontierV3ReferenceContainerCustodyExecutor.REPLICA_PROVENANCE_KEY, ReferenceContainerCustody.provenance(container));
        var menu = net.minecraft.world.inventory.ChestMenu.threeRows(1, player.getInventory(), chest);
        player.containerMenu = menu;
        player.getInventory().setItem(9, FrontierV3ExactItemPresentation.materializedStack(tool));
        helper.assertTrue(FrontierV3ContainerClickExecutor.before(player, menu, 27, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, runtime)
                == FrontierV3ContainerClickExecutor.Admission.ACCEPTED, "the exact tool is admitted under a durable click witness");
        menu.clicked(27, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
        FrontierV3ContainerClickExecutor.after(player, menu, runtime);
        helper.assertValueEqual(state(runtime).inventory().items().get(tool.id()).custody(), new InventoryCustody.ContainerSlot(container, 0),
                "canonical custody follows the actual tool, not a fungible copy");
        // Retry a persisted pending receipt just as restart observation does; no resource credit or replay.
        FrontierV3ContainerClickExecutor.reconcileOne(level, runtime, state(runtime));
        helper.assertTrue(FrontierV3DepotClickLedger.get(level).pending(container) == null, "the exact ingress closes its shared click fence");
        var completed = new java.util.concurrent.atomic.AtomicBoolean();
        helper.onEachTick(() -> {
        if (completed.get()) return;
        FrontierV3ReferenceContainerCustodyExecutor.tick(level, runtime);
        var lease = state(runtime).replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(container));
        if (lease == null || lease.status() != PhysicalCustodyLeaseStatus.ACQUIRED) return;
        player.getInventory().setItem(9, new ItemStack(Items.BREAD, 3));
        helper.assertTrue(FrontierV3ContainerClickExecutor.before(player, menu, 27, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, runtime)
                == FrontierV3ContainerClickExecutor.Admission.ACCEPTED, "the next bread deposit is not blocked by the tool");
        menu.clicked(27, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
        FrontierV3ContainerClickExecutor.after(player, menu, runtime);
        helper.assertTrue(chest.getItem(1).is(Items.BREAD) && chest.getItem(1).getCount() == 3, "ordinary bread still uses its normal physical slot");
        helper.assertTrue(FrontierV3DepotClickLedger.get(level).pending(container) == null, "the resource deposit also closes");
        helper.assertTrue(FrontierV3ExactItemPresentation.exactMatch(chest.getItem(0), tool), "tool identity survives beside fungible resources");
        completed.set(true); player.closeContainer(); runtime.shutdown(); helper.succeed();
        });
    }
    private static BlockPos interior(GameTestHelper helper) { return helper.absolutePos(new BlockPos(1, 8, 0)); }
    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) { return runtime.decodedState().orElseThrow(); }
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
