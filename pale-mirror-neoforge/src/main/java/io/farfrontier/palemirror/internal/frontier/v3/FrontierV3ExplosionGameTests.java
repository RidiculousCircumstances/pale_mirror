package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.model.InfectionCell;
import io.farfrontier.palemirror.frontier.v3.model.InfectionOverlayStage;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
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
