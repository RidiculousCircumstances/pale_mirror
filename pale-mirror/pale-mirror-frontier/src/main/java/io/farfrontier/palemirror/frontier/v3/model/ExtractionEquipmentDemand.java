package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import java.util.*;

/** Known deposit access requires ready equipment; only opened, actively requested fronts forecast wear. */
final class ExtractionEquipmentDemand implements EquipmentDemandPort {
    @Override public Key key() { return Key.EXTRACTION; }
    @Override public List<EquipmentWorkDemand> read(FrontierWorldState state, SubjectId home) {
        var staffing = state.bootstrap().ruleset().labour().entries().get(ResidentWorkKind.EXTRACTION);
        if (staffing == null || staffing.targetWorkers() == 0) return List.of();
        var labour = new HashMap<ToolRequirement, Long>();
        var wear = new HashMap<ToolRequirement, Long>(); var cells = new HashMap<ToolRequirement, Long>();
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
                if (deposit.development().openedCells().contains(cell.id())
                        && cell.definition().coldOutput().stream().anyMatch(product -> activeProducts.contains(product.itemKind())))
                    wear.merge(requirement, (long) requirement.wear(), Math::addExact);
            }
        }
        var abilities = new TreeMap<String, List<ToolRequirement>>();
        cells.keySet().forEach(requirement -> abilities.computeIfAbsent(requirement.ability(), ignored -> new ArrayList<>()).add(requirement));
        return abilities.entrySet().stream().map(entry -> {
            var variants = entry.getValue();
            var required = new ToolRequirement(entry.getKey(), variants.stream().mapToInt(ToolRequirement::minimumGrade).max().orElseThrow(),
                    variants.stream().mapToInt(ToolRequirement::wear).max().orElseThrow());
            int count = Math.toIntExact(Math.min(variants.stream().mapToLong(cells::get).sum(), staffing.targetWorkers()));
            return new EquipmentWorkDemand("extraction/" + entry.getKey(), List.of(required), count,
                    Math.max(count, variants.stream().mapToLong(r -> wear.getOrDefault(r, 0L)).sum()),
                    Math.max(1, Math.ceilDiv(variants.stream().mapToLong(labour::get).sum(),
                            variants.stream().mapToLong(r -> Math.multiplyExact(cells.get(r), r.wear())).sum())));
        }).toList();
    }
}
