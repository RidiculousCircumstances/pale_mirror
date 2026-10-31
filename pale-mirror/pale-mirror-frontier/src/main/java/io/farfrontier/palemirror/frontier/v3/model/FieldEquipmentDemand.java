package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import java.util.*;

/** Equipment for admitted cultivation over the common planning horizon, not hypothetical expansion. */
final class FieldEquipmentDemand implements EquipmentDemandPort {
    @Override public Key key() { return Key.FIELD; }
    private static final ImmutableKeyedInputView<SubjectId, List<EquipmentDemand>> VIEWS = new ImmutableKeyedInputView<>(256);
    @Override public List<EquipmentDemand> read(FrontierWorldState state, SubjectId home) {
        var inputs = FieldWorkForecast.permittedInputs(state, home);
        if (inputs.workers().isEmpty()) return List.of();
        return VIEWS.get(state.bootstrap(), home, inputs.keys(), () -> project(state, home, inputs.workers(), inputs.stores()));
    }
    private static List<EquipmentDemand> project(FrontierWorldState state, SubjectId home,
            List<ResidentProfile> workers, List<BlockPosition> stores) {
        long tilling = 0; long cells = 0;
        if (workers.isEmpty()) return List.of();
        var estimate = new FieldWorkForecast(state, home, workers, true, stores);
        var sources = new ArrayList<io.farfrontier.palemirror.frontier.v3.model.supply.RenewableWorkForecast.Source>();
        for (var id : state.resourceSites().siteIdsIn(home)) {
            var site = state.resourceSites().site(id);
            if (site.phase() == ResourceSitePhase.DESTROYED || site.phase() == ResourceSitePhase.CONFLICT) continue;
            var cycle = state.resourceSites().cycle(site.siteId());
            for (var cell : cycle.layout().cells()) {
                var condition = cycle.cell(cell.id());
                if (!condition.soil().cultivable() && condition.soil() != ResourceFieldCycle.Soil.UNKNOWN) continue;
                if (condition.crop() == ResourceFieldCycle.Crop.MATURE || condition.crop() == ResourceFieldCycle.Crop.GROWING
                        || cycle.sowing().cells().contains(cell.id())) {
                    cells++;
                    var source = estimate.source(new FieldSupplyCapacity.Cell(cell.crop(), condition,
                            cycle.sowing().cells().contains(cell.id())));
                    // The same bounded biological/work forecast as supply, but
                    // count tool use rather than crop yield. A fertile cell does
                    // not wear a tool twice simply because it yields two items.
                    sources.add(new io.farfrontier.palemirror.frontier.v3.model.supply.RenewableWorkForecast.Source(
                            FieldHarvestExtraction.DEFINITION.toolRequirement().wear(), source.preparation(), source.harvest(),
                            source.remainingGrowth(), source.fullGrowth(), source.repeatPreparation(), source.planted(), source.renew()));
                    if (condition.soil().untilled() || condition.soil() == ResourceFieldCycle.Soil.UNKNOWN) tilling++;
                }
            }
        }
        int concurrency = Math.toIntExact(Math.min(cells, Math.min(workers.size(), state.bootstrap().ruleset().labour().entries()
                .get(ResidentWorkKind.AGRICULTURE).targetWorkers())));
        var schedule = state.humanPopulation().schedule(home);
        if (concurrency == 0 || SupplyPlanningWindow.dailyWork(schedule) == 0) return List.of();
        var forecast = io.farfrontier.palemirror.frontier.v3.model.supply.RenewableWorkForecast.assess(sources,
                concurrency, schedule.dayTicks(), SupplyPlanningWindow.dailyWork(schedule),
                SupplyPlanningWindow.forSettlement(state, home, List.of()).ticks());
        long wear = Math.addExact(tilling, forecast.isEmpty() ? 0 : forecast.getLast().recipes());
        return List.of(new EquipmentDemand("field", List.of(
                FieldHarvestExtraction.DEFINITION.toolRequirement(), new ToolRequirement(ToolAbilities.TILL, 0, 1)), concurrency,
                Math.max(concurrency, wear), state.bootstrap().ruleset().workCatalog()
                        .require(FieldHarvestExtraction.ITEM, WorkOperation.HARVEST).workUnits()));
    }
}
