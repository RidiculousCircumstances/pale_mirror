package io.farfrontier.palemirror.gametest;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.api.ParcelKind;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.economy.SettlementDepotGeometry;
import io.farfrontier.palemirror.internal.materialization.ParcelRecord;
import io.farfrontier.palemirror.internal.world.CampaignRegionPresentationStatus;
import io.farfrontier.palemirror.internal.world.CampaignRegionRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Cross-layer recovery coverage for authored depot ownership and persisted anchors. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DepotGeometryGameTests {
    private DepotGeometryGameTests() { }

    @GameTest(batch = "pm-living-region", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void functionalGeometryRejectsRailheadOnlyParcel(GameTestHelper helper) {
        BlockPos core = new BlockPos(3256, 64, -10018);
        var functionalParcel = new ParcelRecord(SettlementDepotGeometry.parcelId("pale_mirror:test_region"),
                "pale_mirror:test_region", "minecraft:overworld", SettlementDepotGeometry.parcelMin(core),
                SettlementDepotGeometry.parcelMax(core), "pale_mirror:test_supply_depot",
                ParcelKind.COMMUNITY, null, 0, "");
        helper.assertValueEqual(SettlementDepotGeometry.positions(core).size(), 27,
                "depot functional geometry must retain its bounded exact cell count");
        helper.assertTrue(SettlementDepotGeometry.fits(functionalParcel, SettlementDepotGeometry.positions(core)),
                "the explicit functional parcel must own every mutable depot cell");

        BlockPos railhead = new BlockPos(3253, 65, -10014);
        var moduleParcel = new ParcelRecord("pale_mirror:test_region:parcel:module_5", "pale_mirror:test_region",
                "minecraft:overworld", new BlockPos(3252, 65, -10024), new BlockPos(3259, 70, -10013),
                "pale_mirror_visuals:temperate/receiving_depot", ParcelKind.COMMUNITY, null, 0, "");
        helper.assertTrue(moduleParcel.contains(railhead),
                "live-crash fixture must contain the railway hand-off center");
        helper.assertTrue(!SettlementDepotGeometry.fits(moduleParcel,
                        SettlementDepotGeometry.positions(railhead)),
                "a parcel containing only the railhead must never capture the functional slot");

        var persisted = new CampaignRegionRecord("pale_mirror:test_region", "pale_mirror:iron_frontier", 2,
                "Test Region", "minecraft:overworld", new WorldObjectId("pale_mirror:test_place"),
                new BlockPos(0, 64, 0), new BlockPos(0, 70, 200), new BlockPos(0, 70, 400),
                new BlockPos(0, 65, 84), railhead, new BlockPos(0, 70, 200),
                new BlockPos(0, 70, 400), CampaignRegionPresentationStatus.MATERIALIZED,
                "", 2, 11, 12, 18, 18, "train-a", "train-a");
        helper.assertTrue(persisted.reconcileAuthoredDepotAnchors(railhead, core),
                "the immutable manifest must correct persisted presentation anchors in place");
        helper.assertValueEqual(persisted.depotAnchor(), core,
                "reconciliation must restore the authored functional core");
        helper.assertValueEqual(persisted.certifiedRouteCapacity(12, 8), 18,
                "presentation-anchor recovery must preserve logistics observations");
        helper.succeed();
    }
}
