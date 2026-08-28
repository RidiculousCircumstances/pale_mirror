package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.model.InfectionCell;
import io.farfrontier.palemirror.frontier.v3.model.InfectionOverlayStage;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Materialized persistence boundary for managed v3 blast candidates, before the executor confirms a receipt. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ExplosionGameTests {
    private FrontierV3ExplosionGameTests() { }

    @GameTest(batch = "pm-frontier-v3-explosion", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void managedBlastRetainsEntityEvidenceAcrossSavedDataReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(44, 8, 0)); level.setBlock(position, Blocks.STONE.defaultBlockState(), 3);
        Zombie zombie = EntityType.ZOMBIE.create(level); zombie.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D); level.addFreshEntity(zombie);
        PhysicalIntentId intent = new PhysicalIntentId("intent:managed-explosion-entity-test"); FrontierV3ManagedExplosionLedger ledger = FrontierV3ManagedExplosionLedger.get(level);
        helper.assertTrue(ledger.capture(level, level.getGameTime(), intent, java.util.List.of(position), java.util.List.of(zombie), null,
                FrontierV3GrayboxLedger.get(level), FrontierV3InfectionOverlayLedger.get(level), ignored -> true), "entity UUID/type are retained before post-impact inspection");
        ledger = FrontierV3ManagedExplosionLedger.load(ledger.save(new CompoundTag(), level.registryAccess()), level.registryAccess()); zombie.discard(); level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        FrontierV3ManagedExplosionLedger.EntityReady entity = ledger.nextEntity(intent, level.getGameTime() + 1L).orElseThrow(); ledger.resolveEntity(entity, true);
        FrontierV3ManagedExplosionLedger.BlockReady block = ledger.nextBlock(intent, level.getGameTime() + 1L).orElseThrow(); ledger.resolveBlock(block, true);
        FrontierV3ManagedExplosionLedger.Completion completion = ledger.completeIfResolved(intent, level.getGameTime() + 1L).orElseThrow();
        helper.assertValueEqual(completion.entityImpacts().getFirst().entityId(), zombie.getUUID(), "reload preserves the exact observed entity identity");
        helper.assertTrue(completion.entityImpacts().getFirst().removed(), "post-impact absence is explicit evidence, not a respawn request"); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-explosion", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void managedBlastRetainsExactWorldDropEvidenceAcrossSavedDataReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(46, 8, 0));
        SubjectId itemId = new SubjectId("item:bootstrap-1-wheat");
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:explosion-world-drop"), 91L));
        var expected = initial.inventory().items().get(itemId); InventoryCustody.ContainerSlot source = (InventoryCustody.ContainerSlot) expected.custody();
        java.util.UUID carrierId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000079");
        var physical = FrontierV3CargoHandoffExecutor.materializedStack(expected); FrontierV3CargoHandoffExecutor.bindWorldCarrier(physical, carrierId);
        ItemEntity drop = new ItemEntity(level, position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D,
                physical); drop.setUUID(carrierId);
        InventoryCustody.WorldCarrier carrier = new InventoryCustody.WorldCarrier(carrierId);
        helper.assertTrue(FrontierV3CargoHandoffExecutor.exactMatch(drop.getItem(), expected), "the physical drop retains its exact stack tag");
        helper.assertValueEqual(FrontierV3CargoHandoffExecutor.worldCarrierId(drop.getItem()).orElseThrow(), carrierId,
                "the physical drop retains its exact world-carrier tag");
        FrontierWorldState state = initial.withInventory(initial.inventory().moveObservedItem(itemId, source, carrier));
        PhysicalIntentId intent = new PhysicalIntentId("intent:managed-explosion-world-drop-test"); FrontierV3ManagedExplosionLedger ledger = FrontierV3ManagedExplosionLedger.get(level);
        helper.assertTrue(ledger.capture(level, level.getGameTime(), intent, java.util.List.of(), java.util.List.of(drop), state,
                FrontierV3GrayboxLedger.get(level), FrontierV3InfectionOverlayLedger.get(level), ignored -> true),
                "an exact drop is retained by its canonical world-carrier UUID before blast reconciliation");
        ledger = FrontierV3ManagedExplosionLedger.load(ledger.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        FrontierV3ManagedExplosionLedger.ItemReady ready = ledger.nextItem(intent, level.getGameTime() + 1L).orElseThrow();
        helper.assertValueEqual(ready.candidate().source(), carrier, "reload preserves the unique physical drop custody");
        ledger.resolveItem(ready, io.farfrontier.palemirror.frontier.v3.model.ExplosionItemImpact.Outcome.DESTROYED);
        FrontierV3ManagedExplosionLedger.Completion completion = ledger.completeIfResolved(intent, level.getGameTime() + 1L).orElseThrow();
        helper.assertValueEqual(completion.itemImpacts().getFirst().itemId(), itemId, "receipt retains the exact canonical stack identity");
        helper.assertValueEqual(completion.itemImpacts().getFirst().outcome(), io.farfrontier.palemirror.frontier.v3.model.ExplosionItemImpact.Outcome.DESTROYED,
                "only the observed world drop becomes a destruction candidate"); helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-explosion", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void managedBlastRetainsChangedInfectionEvidenceAcrossSavedDataReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(48, 8, 0));
        InfectionCell cell = new InfectionCell(12, 13); FrontierV3InfectionOverlayLedger infection = FrontierV3InfectionOverlayLedger.get(level);
        level.setBlock(position, FrontierV3InfectionOverlayExecutor.material(InfectionOverlayStage.BLOOM), 3); infection.applied(cell, position, InfectionOverlayStage.BLOOM);
        PhysicalIntentId intent = new PhysicalIntentId("intent:managed-explosion-infection-test"); FrontierV3ManagedExplosionLedger ledger = FrontierV3ManagedExplosionLedger.get(level);
        helper.assertTrue(ledger.capture(level, level.getGameTime(), intent, java.util.List.of(position), java.util.List.of(), null,
                FrontierV3GrayboxLedger.get(level), infection, ignored -> true), "an owned infection marker is retained before blast reconciliation");
        ledger = FrontierV3ManagedExplosionLedger.load(ledger.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        FrontierV3ManagedExplosionLedger.BlockReady block = ledger.nextBlock(intent, level.getGameTime() + 1L).orElseThrow(); ledger.resolveBlock(block, true);
        FrontierV3ManagedExplosionLedger.Completion completion = ledger.completeIfResolved(intent, level.getGameTime() + 1L).orElseThrow();
        helper.assertValueEqual(completion.affectedInfectionOverlayCount(), 1, "receipt retains the pre-impact owned marker count");
        helper.assertValueEqual(completion.changedInfectionOverlayCount(), 1, "receipt retains the destroyed owned marker count"); helper.succeed();
    }
}
