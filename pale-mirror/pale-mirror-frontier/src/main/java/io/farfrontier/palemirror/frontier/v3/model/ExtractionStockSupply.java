package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionPhysicalStep;
import java.util.*;

/** A prepared physical extraction still has to settle even if another worker fills the reserve. */
final class ExtractionStockSupply implements StockSupplyPort {
    @Override public Key key() { return Key.EXTRACTION; }
    @Override public long inputDemand(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId owner, String itemKind, long atTick) { return 0; }
    @Override public List<Commitment> pending(FrontierWorldState state) {
        var result = new ArrayList<Commitment>();
        for (var job : state.extractionSites().work().values()) {
            if (job.pending().orElse(null) instanceof ExtractionPhysicalStep.BlockWork block)
                for (var output : block.extraction().output()) result.add(new Commitment(job.id(),
                        ExtractionWorkAuthority.site(state, job).settlementId(), output.itemKind(), output.quantity(), 0));
        }
        return List.copyOf(result);
    }
}
