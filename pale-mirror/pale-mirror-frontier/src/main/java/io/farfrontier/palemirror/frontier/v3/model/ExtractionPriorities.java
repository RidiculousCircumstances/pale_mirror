package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPriority;
import java.util.*;

/** Extraction translates product demand to finite source/prerequisite priorities, not economic policy. */
public record ExtractionPriorities(Map<String, SupplyPriority> products, Map<Long, SupplyPriority> cells) {
    public ExtractionPriorities { products = Map.copyOf(products); cells = Map.copyOf(cells); }
    public static ExtractionPriorities read(FrontierWorldState state, ExtractionDeposit deposit, Optional<SubjectId> completing, long atTick) {
        var home = deposit.site().settlementId();
        var products = new HashMap<>(ProductionResourcePriorities.read(state, home,
                ExtractionStockDemand.demandedProducts(state, deposit, completing, atTick), completing, atTick));
        var assigned = new HashMap<String, Long>();
        for (var job : state.extractionSites().work().values()) {
            if (completing.filter(job.id()::equals).isPresent()
                    || job.pending().orElse(null) instanceof ExtractionPhysicalStep.BlockWork
                    || job.phase() != ExtractionWork.Phase.EXTRACT && job.phase() != ExtractionWork.Phase.TAKE_TOOL
                    || !ExtractionWorkAuthority.site(state, job).settlementId().equals(home)) continue;
            var source = state.extractionSites().deposits().get(job.siteId());
            job.target().filter(target -> source.cells().get(target.key().cell()).disposition() == ExtractionDeposit.Disposition.PRESENT)
                    .ifPresent(target -> source.site().layout().require(target.key().cell()).definition().coldOutput()
                        .forEach(output -> assigned.merge(output.itemKind(), (long) output.quantity(), Math::addExact)));
        }
        products.replaceAll((kind, priority) -> priority.withAssigned(assigned.getOrDefault(kind, 0L)));
        var priorities = new HashMap<Long, SupplyPriority>();
        for (var cell : deposit.site().layout().cells()) {
            if (deposit.cells().get(cell.id()).disposition() != ExtractionDeposit.Disposition.PRESENT) continue;
            var priority = cell.definition().coldOutput().stream().map(output -> products.get(output.itemKind()))
                    .filter(Objects::nonNull).min(SupplyPriority::compareTo);
            if (priority.isEmpty()) continue;
            var pending = new ArrayDeque<Long>(); pending.add(cell.id());
            while (!pending.isEmpty()) {
                long id = pending.removeFirst();
                if (deposit.cells().get(id).disposition() != ExtractionDeposit.Disposition.PRESENT) continue;
                var prior = priorities.get(id);
                if (prior != null && prior.compareTo(priority.orElseThrow()) <= 0) continue;
                priorities.put(id, priority.orElseThrow()); pending.addAll(deposit.site().layout().require(id).prerequisites());
            }
        }
        return new ExtractionPriorities(products, priorities);
    }
    public SupplyPriority require(long cell) {
        return Objects.requireNonNull(cells.get(cell), "source has no current requested product or prerequisite");
    }
}
