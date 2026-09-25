package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ResourceSiteReceiptLifecycleTest {
    @Test void formerAirPrefixLedgerCannotBeReinterpretedAsReplantedCells() {
        var old = FrontierV3ResourceSiteLedger.fixture().save(new CompoundTag(), null);
        old.putInt("format", 6);
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceSiteLedger.load(old, null));
    }

    @Test void newGrowthRetiresOldReceiptButSameHarvestCannotReplaceItsOutput() {
        var site = new SubjectId("site:1-wheat-field");
        var ledger = FrontierV3ResourceSiteLedger.fixture();
        ledger.reserve(site, new PhysicalIntentId("intent:site-projection-1-wheat-field"));
        ledger.activate(site); ledger.updateStage(site, 7);
        for (int cell = 1; cell <= 64; cell++) ledger.harvestOne(site, cell);
        var first = output("item:first-harvest", 0);
        var second = output("item:second-harvest", 1);
        assertTrue(ledger.recordHarvestReceipt(site, first));
        ledger = reload(ledger);
        assertTrue(ledger.hasHarvestReceipt(site, first));
        assertFalse(ledger.recordHarvestReceipt(site, first));
        var sameHarvest = ledger;
        assertThrows(IllegalStateException.class, () -> sameHarvest.recordHarvestReceipt(site, second));
        assertThrows(IllegalArgumentException.class, () -> sameHarvest.updateStage(site, -1));
        assertTrue(ledger.hasHarvestReceipt(site, first), "invalid transition cannot discard the retained receipt");
        ledger.updateStage(site, 7);
        assertTrue(ledger.hasHarvestReceipt(site, first), "same-stage replay must retain the anti-duplication fence");

        // Ordinary completed bounded projection, unlike mature restoreOne(), uses this owner boundary.
        ledger.updateStage(site, 0);
        ledger = reload(ledger);
        assertFalse(ledger.hasHarvestReceipt(site, first));
        assertEquals(0, ledger.claim(site).harvestedCropSlots());
        var growing = ledger;
        assertThrows(IllegalStateException.class, () -> growing.recordHarvestReceipt(site, second));
        for (int stage = 1; stage <= 7; stage++) ledger.updateStage(site, stage);
        for (int cell = 1; cell <= 64; cell++) ledger.harvestOne(site, cell);
        assertTrue(ledger.recordHarvestReceipt(site, second));
        ledger = reload(ledger);
        assertTrue(ledger.hasHarvestReceipt(site, second));
        assertFalse(ledger.hasHarvestReceipt(site, first));
        assertFalse(ledger.recordHarvestReceipt(site, second));
    }

    private static ExactItemStack output(String id, int slot) {
        return new ExactItemStack(new SubjectId(id), new SubjectId("settlement:1"), "minecraft:wheat", 64,
                new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), slot));
    }
    private static FrontierV3ResourceSiteLedger reload(FrontierV3ResourceSiteLedger ledger) {
        return FrontierV3ResourceSiteLedger.load(ledger.save(new CompoundTag(), null), null);
    }
}
