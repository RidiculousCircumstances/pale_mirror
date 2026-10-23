package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockClearance;
import io.farfrontier.palemirror.frontier.v3.model.supply.FiniteWorkForecast;
import java.util.*;

/** The extraction owner describes known sequential work, never hypothetical unexplored geology. */
final class ExtractionProductionForecast {
    private static final ImmutableKeyedInputView<io.farfrontier.palemirror.frontier.v3.api.SubjectId,
            Map<BlockExtraction.Definition, FiniteWorkForecast.Estimate>> ESTIMATES = new ImmutableKeyedInputView<>(256);
    static FiniteWorkForecast.Estimate assess(FrontierWorldState state, ExtractionDeposit deposit,
            BlockExtraction.Definition product, List<ResidentProfile> workers) {
        // Reservations of trade stock/cash do not change geology, extraction
        // commitments or worker capabilities. Key only the actual owner inputs.
        var inputs = new ArrayList<Object>();
        inputs.add(state.bootstrap().ruleset()); inputs.add(state.extractionSites().work()); inputs.add(deposit);
        inputs.add(state.inventory().items()); inputs.add(state.inventory().surfaces());
        inputs.add(WorkEquipmentClaimComposition.reserved(state));
        inputs.addAll(workers);
        synchronized (ESTIMATES) {
            var estimates = ESTIMATES.get(state.bootstrap(), deposit.site().id(), inputs, HashMap::new);
            return estimates.computeIfAbsent(product, ignored -> project(state, deposit, product, workers));
        }
    }
    private static FiniteWorkForecast.Estimate project(FrontierWorldState state, ExtractionDeposit deposit,
            BlockExtraction.Definition product, List<ResidentProfile> workers) {
        var reserved = state.extractionSites().reservedCells(deposit.site().id());
        var catalog = state.bootstrap().ruleset().workCatalog();
        var nodes = new ArrayList<FiniteWorkForecast.Node>();
        var workerIds = workers.stream().map(ResidentProfile::id).collect(java.util.stream.Collectors.toSet());
        var equipment = new HashMap<io.farfrontier.palemirror.frontier.v3.model.tooling.ToolRequirement, List<ExactItemStack>>();
        for (var cell : deposit.site().layout().cells()) {
            var known = deposit.cells().get(cell.id());
            if (known.knownBlock().equals(cell.definition().after()) || KnownBlockClearance.emptyCollision(known.knownBlock().kind())) {
                nodes.add(new FiniteWorkForecast.Node(cell.id(), Set.of(), OptionalLong.of(0), false)); continue;
            }
            var tools = state.bootstrap().ruleset().tools();
            var usable = equipment.computeIfAbsent(cell.definition().toolRequirement(), requirement ->
                    EquipmentProductiveCapacity.items(state, deposit.site().settlementId(), workerIds,
                        io.farfrontier.palemirror.frontier.v3.model.tooling.EquipmentRequirements.allOf(Set.of(requirement))));
            boolean eligible = known.disposition() == ExtractionDeposit.Disposition.PRESENT && !usable.isEmpty()
                    && (deposit.development().openedCells().contains(cell.id())
                        || state.bootstrap().ruleset().extraction().developmentBatchCells() > 0);
            OptionalLong work = OptionalLong.empty();
            if (eligible) {
                var definition = catalog.require(cell.definition().before().kind(), WorkOperation.EXTRACT);
                if (workers.stream().allMatch(r -> r.capability(definition.capability()) > 0)) {
                    int workerSpeed = workers.stream().mapToInt(r -> ResidentWorkStatistics.speedPermille(r, definition, catalog)).min().orElseThrow();
                    int speed = usable.stream().mapToInt(tool -> io.farfrontier.palemirror.frontier.v3.model.tooling.ToolWorkRate.speedPermille(
                            workerSpeed, tools, tool, cell.definition().toolRequirement())).min().orElseThrow();
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
