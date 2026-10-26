package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import java.util.*;

/** Only not-yet-issued tree loot is incoming; actual carried logs already belong to the inventory ledger. */
final class ForestryStockSupply implements StockSupplyPort {
    public Key key() { return Key.FORESTRY; }
    public long inputDemand(FrontierWorldState state, SubjectId owner, String kind, long tick) { return 0; }
    public List<Commitment> pending(FrontierWorldState state) {
        return state.forest().work().values().stream().filter(job -> job.target().isPresent()).map(job -> {
            long remaining = job.felling().map(batch -> (long) batch.batch().effects().size() - job.appliedEffects())
                    .orElseGet(() -> (long) ForestKnowledge.require(state, job.target().orElseThrow().tree()).remaining());
            return new Commitment(job.id(), job.home(), job.outputKind(), remaining, 0);
        }).toList();
    }
}
