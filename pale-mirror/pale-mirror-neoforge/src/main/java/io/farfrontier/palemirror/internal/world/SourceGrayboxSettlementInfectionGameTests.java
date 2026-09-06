package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Player-visible settlement infection follows the exact canonical territory cell. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SourceGrayboxSettlementInfectionGameTests {
    private SourceGrayboxSettlementInfectionGameTests() { }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void settlementInfectionCoversTheSourceBuildingAndRetreatsWithoutAStaleRoof(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 24);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();

        ReferenceGrayboxSnapshot active = fixture(anchor, baseline, .35d, 0.0d, "a".repeat(64));
        SourceGrayboxPresentationPlan.Desired activeOverlay = overlay(active);
        helper.assertValueEqual(activeOverlay.width(), fixtureRectangle(anchor).width(),
                "active source infection must visibly span the settlement building roofline");
        helper.assertValueEqual(activeOverlay.depth(), 1,
                "active source infection is a roofline fascia, not a silent full replacement");
        materializer.apply(helper.getLevel(), active);
        BlockPos activePosition = position(activeOverlay);
        SourceGrayboxPresentationLedger.Claim activeClaim = materializer.claimAt(helper.getLevel(), activePosition);
        helper.assertValueEqual(helper.getLevel().getBlockState(activePosition).getBlock(), Blocks.RED_WOOL,
                "active infection must turn the real settlement structure visibly red");
        helper.assertTrue(activeClaim.installed() && activeClaim.kind().equals("SETTLEMENT_INFECTION")
                        && activeClaim.subjectId().startsWith("settlement:"),
                "the infection fascia must retain source-settlement provenance rather than becoming decoration");

        ReferenceGrayboxSnapshot severe = fixture(anchor, baseline, .80d, .20d, "b".repeat(64));
        SourceGrayboxPresentationPlan.Desired severeOverlay = overlay(severe);
        helper.assertValueEqual(severeOverlay.depth(), fixtureRectangle(anchor).depth(),
                "severe infection must cover the complete source building roof");
        materializer.apply(helper.getLevel(), severe);
        helper.assertValueEqual(helper.getLevel().getBlockState(position(severeOverlay)).getBlock(), Blocks.MAGENTA_WOOL,
                "a source signal must remain visibly distinct on the infected settlement roof");
        helper.assertValueEqual(helper.getLevel().getBlockState(new BlockPos(severeOverlay.x() + severeOverlay.width() - 1,
                severeOverlay.y(), severeOverlay.z() + severeOverlay.depth() - 1)).getBlock(), Blocks.MAGENTA_WOOL,
                "severe infection must not degrade to one diagnostic point on a settlement object");

        ReferenceGrayboxSnapshot clear = fixture(anchor, baseline, 0.0d, 0.0d, "c".repeat(64));
        materializer.apply(helper.getLevel(), clear);
        helper.assertTrue(SourceGrayboxPresentationPlan.from(clear).get("infection-overlay:facility:fixture-infected") == null,
                "a cleared source cell must retire its settlement infection projection");
        helper.assertValueEqual(helper.getLevel().getBlockState(position(severeOverlay)).getBlock(), Blocks.AIR,
                "retreat must remove only the still-owned infected roof rather than leave a stale crisis scene");
        helper.succeed();
    }

    @GameTest(batch = "pm-source-graybox-materializer", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void settlementInfectionNeverOverwritesOrAdoptsAPlayerRoofBlock(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO).atY(ReferenceGrayboxLayout.GROUND_Y);
        SourceGrayboxMaterializerGameTests.prepareFlatFloor(helper, anchor, 24);
        ReferenceGrayboxSnapshot baseline = ReferenceGrayboxSimulation.create(42L).snapshot();
        ReferenceGrayboxSnapshot infected = fixture(anchor, baseline, .80d, 0.0d, "d".repeat(64));
        SourceGrayboxPresentationPlan.Desired overlay = overlay(infected);
        BlockPos obstruction = position(overlay);
        helper.getLevel().setBlock(obstruction, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        SourceGrayboxMaterializer materializer = new SourceGrayboxMaterializer();

        materializer.apply(helper.getLevel(), infected);
        SourceGrayboxPresentationLedger.Claim conflict = SourceGrayboxPresentationLedger.get(helper.getLevel()).claim(overlay.id());
        helper.assertValueEqual(helper.getLevel().getBlockState(obstruction).getBlock(), Blocks.DIAMOND_BLOCK,
                "source infection may not overwrite a player-owned settlement roof block");
        helper.assertTrue(conflict != null && conflict.conflicted() && !conflict.installed(),
                "a blocked infection mark must remain a visible non-owning conflict");

        materializer.apply(helper.getLevel(), fixture(anchor, baseline, 0.0d, 0.0d, "e".repeat(64)));
        helper.assertValueEqual(helper.getLevel().getBlockState(obstruction).getBlock(), Blocks.DIAMOND_BLOCK,
                "source recovery may not reinterpret a formerly blocked player roof as PM-owned cleanup authority");
        helper.assertTrue(SourceGrayboxPresentationLedger.get(helper.getLevel()).claim(overlay.id()).conflicted(),
                "the cleared source state must retain the historical foreign-obstruction evidence");
        helper.succeed();
    }

    private static ReferenceGrayboxSnapshot fixture(BlockPos anchor, ReferenceGrayboxSnapshot baseline, double infection, double signal,
                                                     String revision) {
        ReferenceGrayboxSnapshot.Settlement settlement = baseline.settlements().getFirst();
        List<ReferenceGrayboxSnapshot.Cell> cells = new ArrayList<>(baseline.cells());
        for (int index = 0; index < cells.size(); index++) {
            ReferenceGrayboxSnapshot.Cell cell = cells.get(index);
            if (cell.x() == settlement.logicalX() && cell.y() == settlement.logicalY()) {
                cells.set(index, new ReferenceGrayboxSnapshot.Cell(cell.x(), cell.y(),
                        new ReferenceGrayboxLayout.Rectangle(anchor.getX() - 8, anchor.getZ() - 8, 16, 16), infection,
                        cell.organicMass(), cell.moisture(), signal, cell.colour()));
                return new ReferenceGrayboxSnapshot(baseline.day(), baseline.profileId(), revision, baseline.bounds(), cells,
                        List.of(settlement), List.of(fixtureFacility(anchor, settlement.id())), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            }
        }
        throw new IllegalStateException("baseline fixture has no source cell for settlement " + settlement.id());
    }

    private static ReferenceGrayboxSnapshot.Facility fixtureFacility(BlockPos anchor, int settlementId) {
        return new ReferenceGrayboxSnapshot.Facility("fixture-infected", settlementId, "clinic", fixtureRectangle(anchor), 1.0d,
                "facility.clinic");
    }

    private static ReferenceGrayboxLayout.Rectangle fixtureRectangle(BlockPos anchor) {
        return new ReferenceGrayboxLayout.Rectangle(anchor.getX() + 2, anchor.getZ() + 2, 6, 5);
    }

    private static SourceGrayboxPresentationPlan.Desired overlay(ReferenceGrayboxSnapshot snapshot) {
        SourceGrayboxPresentationPlan.Desired desired = SourceGrayboxPresentationPlan.from(snapshot).get("infection-overlay:facility:fixture-infected");
        if (desired == null) throw new IllegalStateException("fixture should project a settlement infection overlay");
        return desired;
    }

    private static BlockPos position(SourceGrayboxPresentationPlan.Desired desired) {
        return new BlockPos(desired.x(), desired.y(), desired.z());
    }
}
