package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.api.ParcelKind;
import io.farfrontier.palemirror.internal.materialization.ParcelLedger;
import io.farfrontier.palemirror.internal.materialization.ParcelRecord;
import io.farfrontier.palemirror.api.SemanticSlotKey;
import io.farfrontier.palemirror.internal.materialization.MaterializationGateway;
import io.farfrontier.palemirror.internal.materialization.SemanticCellRecord;
import io.farfrontier.palemirror.internal.materialization.SemanticSlotLedger;
import io.farfrontier.palemirror.internal.materialization.SemanticSlotRecord;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.SettlementTerritoryPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;

@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaterializationFrameworkGameTests {
    private MaterializationFrameworkGameTests() { }

    @GameTest(batch = "pm-parcel-ownership", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void playerPlotRequiresExplicitCommissioningBeforePmCanManageIt(GameTestHelper helper) {
        ParcelLedger ledger = new ParcelLedger();
        BlockPos min = helper.absolutePos(new BlockPos(0, 2, 0));
        BlockPos position = min.offset(2, 2, 2);
        String dimension = helper.getLevel().dimension().location().toString();
        ledger.register(new ParcelRecord("pale_mirror:test_influence", "pale_mirror:test_region", dimension,
                min, min.offset(8, 8, 8), "pale_mirror:test", ParcelKind.INFLUENCE, null, 0, ""));
        helper.assertTrue(ledger.managedAt(dimension, position).isEmpty(),
                "settlement influence must never grant PM block-mutation authority");

        ParcelRecord plot = new ParcelRecord("pale_mirror:test_plot", "pale_mirror:test_region", dimension,
                min, min.offset(8, 8, 8), "pale_mirror:test_plot", ParcelKind.RESERVED, null, 0, "");
        ledger.register(plot);
        plot.leaseTo(java.util.UUID.randomUUID());
        helper.assertTrue(ledger.managedAt(dimension, position).isEmpty(),
                "a player lease must remain outside PM ownership");
        plot.commissionCommunity("development-intent:test");
        helper.assertTrue(ledger.managedAt(dimension, position).isPresent(),
                "an explicit commissioning permit may convert the plot into a community-managed parcel");
        helper.assertValueEqual(plot.commissioningPermit(), "development-intent:test",
                "commissioning provenance must remain persisted on the parcel");
        helper.succeed();
    }

    @GameTest(batch = "pm-resident-identity", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void retiredStableResidentCannotBeResurrectedByRestart(GameTestHelper helper) {
        var data = new io.farfrontier.palemirror.internal.world.PaleMirrorSavedData();
        String residentId = java.util.UUID.randomUUID().toString();
        data.residentIdentities().retire(residentId);
        var snapshot = data.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess());
        var reloaded = io.farfrontier.palemirror.internal.world.PaleMirrorSavedData.load(
                snapshot, helper.getLevel().registryAccess());
        helper.assertTrue(reloaded.residentIdentities().retired(residentId),
                "confirmed stable-identity retirement must survive restart");
        helper.succeed();
    }

    @GameTest(batch = "pm-materialization-rollback", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void failedPostconditionRollsBackInsideExactParcel(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlock(position, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        String dimension = helper.getLevel().dimension().location().toString();
        ParcelLedger parcels = new ParcelLedger();
        ParcelRecord parcel = new ParcelRecord("pale_mirror:exact", "pale_mirror:test_region", dimension,
                position, position, "test", ParcelKind.COMMUNITY, null, 0, "");
        parcels.register(parcel);
        SemanticSlotKey key = new SemanticSlotKey("pale_mirror:test", "module", "slot");
        SemanticSlotLedger slots = new SemanticSlotLedger();
        slots.register(new SemanticSlotRecord(key, parcel.id(), ParcelKind.COMMUNITY,
                java.util.List.of(new SemanticCellRecord(position,
                        net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
                        net.minecraft.world.level.block.Blocks.STONE.defaultBlockState())), false, "", ""));
        var result = new MaterializationGateway(helper.getLevel(), slots, parcels).setBlock(key, position,
                net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3, ignored -> false);

        helper.assertTrue(result.status() == io.farfrontier.palemirror.api.GuardedWorldAccess.Status.BLOCKED,
                "failed postcondition must block the operation");
        helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.STONE, new BlockPos(1, 2, 1));
        helper.succeed();
    }

    @GameTest(batch = "pm-materialization-connectivity", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void derivedFenceConnectionsDoNotBlockAnAuthoredSlot(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos neighbour = position.east();
        var authored = net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState();
        helper.getLevel().setBlock(position, authored, 3);
        helper.getLevel().setBlock(neighbour, authored, 3);
        String dimension = helper.getLevel().dimension().location().toString();
        ParcelLedger parcels = new ParcelLedger();
        ParcelRecord parcel = new ParcelRecord("pale_mirror:fence", "pale_mirror:test_region", dimension,
                position, position, "test", ParcelKind.COMMUNITY, null, 0, "");
        parcels.register(parcel);
        SemanticSlotKey key = new SemanticSlotKey("pale_mirror:test", "module", "fence");
        SemanticSlotLedger slots = new SemanticSlotLedger();
        slots.register(new SemanticSlotRecord(key, parcel.id(), ParcelKind.COMMUNITY,
                java.util.List.of(new SemanticCellRecord(position, authored, authored)), false, "", ""));

        var result = new MaterializationGateway(helper.getLevel(), slots, parcels)
                .setBlock(key, position, authored, 3);

        helper.assertTrue(result.status() == io.farfrontier.palemirror.api.GuardedWorldAccess.Status.UNCHANGED,
                "Minecraft-derived fence connections must not look like a player conflict");
        helper.succeed();
    }

    @GameTest(batch = "pm-settlement-territory", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void settlementColumnRejectsTreesAndBackgroundHostiles(GameTestHelper helper) {
        BlockPos inside = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos outside = helper.absolutePos(new BlockPos(12, 2, 12));
        String dimension = helper.getLevel().dimension().location().toString();
        PaleMirrorSavedData data = PaleMirrorSavedData.get(helper.getLevel().getServer().overworld());
        data.parcels().register(new ParcelRecord("pale_mirror:test_influence:" + java.util.UUID.randomUUID(),
                "pale_mirror:test_region", dimension, inside.offset(-2, -1, -2), inside.offset(2, 1, 2),
                "settlement", ParcelKind.INFLUENCE, null, 0, ""));

        var hostile = new MobSpawnEvent.SpawnPlacementCheck(EntityType.ZOMBIE, helper.getLevel(),
                MobSpawnType.NATURAL, inside.above(200), helper.getLevel().getRandom(), true);
        helper.assertTrue(SettlementTerritoryPolicy.evaluate(hostile),
                "natural hostile spawn must be denied across the full settlement column");
        helper.assertValueEqual(hostile.getResult(), MobSpawnEvent.SpawnPlacementCheck.Result.FAIL,
                "settlement hostile policy must fail the placement check");

        var explicit = new MobSpawnEvent.SpawnPlacementCheck(EntityType.ZOMBIE, helper.getLevel(),
                MobSpawnType.SPAWNER, inside, helper.getLevel().getRandom(), true);
        helper.assertTrue(!SettlementTerritoryPolicy.evaluate(explicit),
                "explicit spawner mechanics must remain outside the settlement background policy");

        var insideGrowth = new BlockGrowFeatureEvent(helper.getLevel(), helper.getLevel().getRandom(), inside, null);
        SettlementTerritoryPolicy.evaluate(insideGrowth);
        helper.assertTrue(insideGrowth.isCanceled(), "tree-like growth inside settlement influence must be canceled");

        var outsideGrowth = new BlockGrowFeatureEvent(helper.getLevel(), helper.getLevel().getRandom(), outside, null);
        SettlementTerritoryPolicy.evaluate(outsideGrowth);
        helper.assertTrue(!outsideGrowth.isCanceled(), "tree-like growth outside settlement influence must remain unchanged");
        helper.succeed();
    }
}
