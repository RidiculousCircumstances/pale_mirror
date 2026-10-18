package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockClearance;
import io.farfrontier.palemirror.frontier.v3.model.supply.FiniteWorkForecast;
import java.util.*;

/** The extraction owner describes known sequential work, never hypothetical unexplored geology. */
final class ExtractionProductionForecast {
    private static final ImmutableInputView<Map<BlockExtraction.Definition, FiniteWorkForecast.Estimate>> ESTIMATES = new ImmutableInputView<>();
    static FiniteWorkForecast.Estimate assess(FrontierWorldState state, ExtractionDeposit deposit,
            BlockExtraction.Definition product, List<ResidentProfile> workers) {
        // Reservations of trade stock/cash do not change geology, extraction
        // commitments or worker capabilities. Key only the actual owner inputs.
        var inputs = new ArrayList<Object>();
        inputs.add(state.bootstrap().ruleset()); inputs.add(state.extractionSites()); inputs.add(deposit);
        inputs.addAll(workers);
        synchronized (ESTIMATES) {
            var estimates = ESTIMATES.get(inputs, HashMap::new);
            return estimates.computeIfAbsent(product, ignored -> project(state, deposit, product, workers));
        }
    }
    private static FiniteWorkForecast.Estimate project(FrontierWorldState state, ExtractionDeposit deposit,
            BlockExtraction.Definition product, List<ResidentProfile> workers) {
        var reserved = state.extractionSites().reservedCells(deposit.site().id());
        var catalog = state.bootstrap().ruleset().workCatalog();
        var nodes = new ArrayList<FiniteWorkForecast.Node>();
        for (var cell : deposit.site().layout().cells()) {
            var known = deposit.cells().get(cell.id());
            if (known.knownBlock().equals(cell.definition().after()) || KnownBlockClearance.emptyCollision(known.knownBlock().kind())) {
                nodes.add(new FiniteWorkForecast.Node(cell.id(), Set.of(), OptionalLong.of(0), false)); continue;
            }
            boolean eligible = known.disposition() == ExtractionDeposit.Disposition.PRESENT
                    && cell.definition().toolKind().equals(product.toolKind())
                    && (deposit.development().openedCells().contains(cell.id())
                        || state.bootstrap().ruleset().extraction().developmentBatchCells() > 0);
            OptionalLong work = OptionalLong.empty();
            if (eligible) {
                var definition = catalog.require(cell.definition().before().kind(), WorkOperation.EXTRACT);
                if (workers.stream().allMatch(r -> r.capability(definition.capability()) > 0)) {
                    int speed = workers.stream().mapToInt(r -> ResidentWorkStatistics.speedPermille(r, definition, catalog)).min().orElseThrow();
                    work = OptionalLong.of(Math.ceilDiv(Math.multiplyExact(definition.workUnits(), 1000L), speed));
                }
            }
            nodes.add(new FiniteWorkForecast.Node(cell.id(), cell.prerequisites(), work,
                    eligible && !reserved.contains(cell.id()) && cell.definition().equals(product)));
        }
        return FiniteWorkForecast.assess(nodes);
    }
    private ExtractionProductionForecast() { }
}
