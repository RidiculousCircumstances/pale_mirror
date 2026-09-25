package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ManagedSoilPolicyTest {
    @Test void onlyActiveDeclaredSoilIsProtectedIncludingTheHarvestToGrowthBoundary() {
        var id = new SubjectId("site:1-wheat-field");
        var ledger = FrontierV3ResourceSiteLedger.fixture();
        assertFalse(FrontierV3ResourceSiteExecutor.blocksNativeSoilReversion(null, true));
        ledger.reserve(id, new PhysicalIntentId("intent:site-projection-1-wheat-field"));
        assertFalse(FrontierV3ResourceSiteExecutor.blocksNativeSoilReversion(ledger.claim(id), true));
        ledger.activate(id);
        assertTrue(FrontierV3ResourceSiteExecutor.blocksNativeSoilReversion(ledger.claim(id), true));
        assertFalse(FrontierV3ResourceSiteExecutor.blocksNativeSoilReversion(ledger.claim(id), false),
                "a neighbouring natural farm or crop cell is not owned soil");
        ledger.updateStage(id, 7);
        for (int cursor = 1; cursor <= 64; cursor++) ledger.harvestOne(id, cursor);
        assertTrue(FrontierV3ResourceSiteExecutor.blocksNativeSoilReversion(ledger.claim(id), true),
                "empty harvested field still owns its soil");
        ledger.updateStage(id, 0);
        ledger = FrontierV3ResourceSiteLedger.load(ledger.save(new CompoundTag(), null), null);
        assertTrue(FrontierV3ResourceSiteExecutor.blocksNativeSoilReversion(ledger.claim(id), true));
    }
}
