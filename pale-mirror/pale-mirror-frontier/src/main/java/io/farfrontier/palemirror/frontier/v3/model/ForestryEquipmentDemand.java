package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.tooling.EquipmentWorkDemand;
import java.util.*;

/** Forestry supplies capability/wear demand; the shared equipment economy chooses manufacture or repair. */
final class ForestryEquipmentDemand implements EquipmentDemandPort {
    @Override public Key key() { return Key.FORESTRY; }
    private static final ImmutableKeyedInputView<SubjectId, List<EquipmentWorkDemand>> VIEWS = new ImmutableKeyedInputView<>(256);
    @Override public List<EquipmentWorkDemand> read(FrontierWorldState state, SubjectId home) {
        if (!ForestryStockDemand.enabled(state, home)) return List.of();
        return VIEWS.get(state.bootstrap(), home, List.of(state.inventory(), state.forest().work(), state.forest().regions()), () -> {
            if (!io.farfrontier.palemirror.frontier.v3.model.forestry.ForestKnowledge.harvestable(state, home)) return List.of();
            var rules = state.bootstrap().ruleset().forestry();
            var kinds = rules.harvests().stream().map(profile -> profile.outputItem()).collect(java.util.stream.Collectors.toSet());
            long missing = kinds.stream().mapToLong(kind -> ForestryStockDemand.requested(state, home, kind, Optional.empty())
                    ? Math.max(0, rules.reserveItems() - ForestryStockDemand.held(state, home, kind)) : 0).sum();
            if (missing == 0) return List.of();
            int concurrent = rules.workersPerSettlement();
            long wear = Math.max(concurrent, Math.multiplyExact(missing, rules.tool().wear()));
            long workPerWear = rules.harvests().stream().mapToLong(profile -> state.bootstrap().ruleset().workCatalog()
                    .require(profile.logKind(), WorkOperation.EXTRACT).workUnits()).max().orElseThrow();
            return List.of(new EquipmentWorkDemand("forestry", List.of(rules.tool()), concurrent, wear,
                    Math.max(1, Math.ceilDiv(workPerWear, rules.tool().wear()))));
        });
    }
}
