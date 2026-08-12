package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.api.ParcelKind;
import io.farfrontier.palemirror.internal.materialization.ParcelLedger;
import io.farfrontier.palemirror.internal.materialization.ParcelRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
}
