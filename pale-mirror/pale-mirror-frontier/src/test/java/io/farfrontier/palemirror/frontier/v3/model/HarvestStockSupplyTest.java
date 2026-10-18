package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HarvestStockSupplyTest {
    @Test void quotedHarvestUsesCurrentCellFactsAcrossUnrelatedDraftsAndRecovery() {
        var fixture = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var before = fixture.state(); var port = new HarvestStockSupply();
        var original = port.pending(before);
        var cycle = before.resourceSites().cycle(fixture.site());
        var cell = cycle.layout().cells().stream().filter(c -> !c.id().equals(fixture.job().target().cellId())).findFirst().orElseThrow();
        var changed = before.withResourceSites(before.resourceSites().replace(before.resourceSites().site(fixture.site()), cycle.harvested(cell.id())));
        var after = port.pending(changed);
        assertTrue(after.stream().mapToLong(StockSupplyPort.Commitment::incoming).sum()
                < original.stream().mapToLong(StockSupplyPort.Commitment::incoming).sum());
        assertEquals(original, port.pending(before), "branching immutable drafts cannot reuse changed crop facts");
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        assertEquals(after, port.pending(codec.decode(codec.encode(changed))));
    }
}
