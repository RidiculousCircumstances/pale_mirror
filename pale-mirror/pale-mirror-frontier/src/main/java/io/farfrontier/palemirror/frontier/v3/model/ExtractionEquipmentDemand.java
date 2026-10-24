package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import java.util.*;

/** Known deposit access requires ready equipment; active products forecast renewal over the shared horizon. */
final class ExtractionEquipmentDemand implements EquipmentDemandPort {
    @Override public Key key() { return Key.EXTRACTION; }
    private static final ImmutableKeyedInputView<SubjectId, List<EquipmentWorkDemand>> VIEWS = new ImmutableKeyedInputView<>(256);
    @Override public List<EquipmentWorkDemand> read(FrontierWorldState state, SubjectId home) {
        return VIEWS.get(state.bootstrap(), home, List.of(state.extractionSites().deposits(), state.extractionSites().work(),
                state.humanPopulation().schedule(home)), () -> project(state, home));
    }
    private static List<EquipmentWorkDemand> project(FrontierWorldState state, SubjectId home) {
        var staffing = state.bootstrap().ruleset().labour().entries().get(ResidentWorkKind.EXTRACTION);
        if (staffing == null || staffing.targetWorkers() == 0) return List.of();
        var labour = new HashMap<ToolRequirement, Long>();
        var wear = new HashMap<ToolRequirement, Long>(); var activeWork = new HashMap<ToolRequirement, Long>(); var cells = new HashMap<ToolRequirement, Long>();
        for (var deposit : state.extractionSites().deposits().values()) {
            if (!deposit.site().settlementId().equals(home)) continue;
            var activeProducts = state.extractionSites().work().values().stream()
                    .filter(w -> w.siteId().equals(deposit.site().id()) && !w.terminal())
                    .map(ExtractionWork::outputKind).collect(java.util.stream.Collectors.toSet());
            for (var cell : deposit.site().layout().cells()) {
                if (deposit.cells().get(cell.id()).disposition() != ExtractionDeposit.Disposition.PRESENT) continue;
                var requirement = cell.definition().toolRequirement(); cells.merge(requirement, 1L, Math::addExact);
                labour.merge(requirement, state.bootstrap().ruleset().workCatalog()
                        .require(cell.definition().before().kind(), WorkOperation.EXTRACT).workUnits(), Math::addExact);
                if (cell.definition().coldOutput().stream().anyMatch(product -> activeProducts.contains(product.itemKind()))
                        && (deposit.development().openedCells().contains(cell.id())
                            || state.bootstrap().ruleset().extraction().developmentBatchCells() > 0)) {
                    wear.merge(requirement, (long) requirement.wear(), Math::addExact);
                    activeWork.merge(requirement, state.bootstrap().ruleset().workCatalog()
                            .require(cell.definition().before().kind(), WorkOperation.EXTRACT).workUnits(), Math::addExact);
                }
            }
        }
        var abilities = new TreeMap<String, List<ToolRequirement>>();
        cells.keySet().forEach(requirement -> abilities.computeIfAbsent(requirement.ability(), ignored -> new ArrayList<>()).add(requirement));
        return abilities.entrySet().stream().map(entry -> {
            var variants = entry.getValue();
            var required = new ToolRequirement(entry.getKey(), variants.stream().mapToInt(ToolRequirement::minimumGrade).max().orElseThrow(),
                    variants.stream().mapToInt(ToolRequirement::wear).max().orElseThrow());
            int count = Math.toIntExact(Math.min(variants.stream().mapToLong(cells::get).sum(), staffing.targetWorkers()));
            long availableWear = variants.stream().mapToLong(r -> wear.getOrDefault(r, 0L)).sum();
            long work = variants.stream().mapToLong(r -> activeWork.getOrDefault(r, 0L)).sum();
            long horizonWork = Math.multiplyExact(count, SupplyPlanningWindow.forSettlement(state, home, List.of())
                    .workTicks(state.humanPopulation().schedule(home)));
            // An opened batch is an execution bound, not the whole demand for
            // replacement picks. Keep the forecast finite in both known geology
            // and the workers' calendar; idle deposits create no wear demand.
            long plannedWear = availableWear == 0 ? 0 : Math.min(availableWear,
                    java.math.BigInteger.valueOf(horizonWork).multiply(java.math.BigInteger.valueOf(availableWear))
                            .divide(java.math.BigInteger.valueOf(work)).longValueExact());
            return new EquipmentWorkDemand("extraction/" + entry.getKey(), List.of(required), count,
                    Math.max(count, plannedWear),
                    Math.max(1, Math.ceilDiv(variants.stream().mapToLong(labour::get).sum(),
                            variants.stream().mapToLong(r -> Math.multiplyExact(cells.get(r), r.wear())).sum())));
        }).toList();
    }
}
