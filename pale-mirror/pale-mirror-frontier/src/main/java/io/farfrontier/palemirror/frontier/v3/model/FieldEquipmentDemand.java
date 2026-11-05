package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.RenewableWorkForecast;
import java.util.*;

/** Equipment for admitted cultivation over the common horizon, not hypothetical expansion. */
final class FieldEquipmentDemand implements EquipmentDemandPort {
    @Override public Key key() { return Key.FIELD; }
    private static final ImmutableKeyedInputView<SubjectId, List<EquipmentDemand>> VIEWS = new ImmutableKeyedInputView<>(256);
    @Override public List<EquipmentDemand> read(FrontierWorldState state, SubjectId home) {
        var inputs = FieldWorkForecast.permittedInputs(state, home);
        if (inputs.workers().isEmpty()) return List.of();
        return VIEWS.get(state.bootstrap(), home, inputs.keys(), () -> project(state, home, inputs.workers()));
    }
    private static List<EquipmentDemand> project(FrontierWorldState state, SubjectId home, List<ResidentProfile> workers) {
        var forecasts = new EnumMap<ResourceSiteKind, FieldWorkForecast>(ResourceSiteKind.class);
        var sources = new LinkedHashMap<ToolRequirement, List<RenewableWorkForecast.Source>>();
        var tilling = new HashMap<ToolRequirement, Long>(); var cells = new HashMap<ToolRequirement, Long>();
        var effort = new HashMap<ToolRequirement, Long>();
        for (var id : state.resourceSites().siteIdsIn(home)) {
            var site = state.resourceSites().site(id);
            if (site.phase() == ResourceSitePhase.DESTROYED || site.phase() == ResourceSitePhase.CONFLICT) continue;
            var kind = state.resourceSite(id).kind(); var tool = kind.crop().harvestTool();
            var estimate = forecasts.computeIfAbsent(kind, key -> new FieldWorkForecast(state, home, workers, key, true,
                    FieldWorkForecast.storagePositions(state, home, key)));
            var cycle = state.resourceSites().cycle(id);
            for (var cell : cycle.layout().cells()) {
                var condition = cycle.cell(cell.id());
                if (!condition.soil().cultivable() && condition.soil() != ResourceFieldCycle.Soil.UNKNOWN) continue;
                if (condition.crop() != ResourceFieldCycle.Crop.MATURE && condition.crop() != ResourceFieldCycle.Crop.GROWING
                        && !cycle.sowing().cells().contains(cell.id())) continue;
                cells.merge(tool, 1L, Math::addExact);
                var source = estimate.source(new FieldSupplyCapacity.Cell(cell.crop(), condition,
                        cycle.sowing().cells().contains(cell.id()), kind));
                sources.computeIfAbsent(tool, ignored -> new ArrayList<>()).add(new RenewableWorkForecast.Source(tool.wear(),
                        source.preparation(), source.harvest(), source.remainingGrowth(), source.fullGrowth(),
                        source.repeatPreparation(), source.planted(), source.renew()));
                if (condition.soil().untilled() || condition.soil() == ResourceFieldCycle.Soil.UNKNOWN)
                    tilling.merge(tool, 1L, Math::addExact);
                effort.merge(tool, state.bootstrap().ruleset().workCatalog().require(kind.resourceKind(), WorkOperation.HARVEST).workUnits(), Math::max);
            }
        }
        var schedule = state.humanPopulation().schedule(home);
        if (SupplyPlanningWindow.dailyWork(schedule) == 0) return List.of();
        var demands = new ArrayList<EquipmentDemand>();
        for (var tool : sources.keySet().stream().sorted(Comparator.comparing(Object::toString)).toList()) {
            int concurrency = Math.toIntExact(Math.min(cells.get(tool), Math.min(workers.size(), state.bootstrap().ruleset().labour().entries()
                    .get(ResidentWorkKind.AGRICULTURE).targetWorkers())));
            if (concurrency == 0) continue;
            var forecast = RenewableWorkForecast.assess(sources.get(tool), concurrency, schedule.dayTicks(),
                    SupplyPlanningWindow.dailyWork(schedule), SupplyPlanningWindow.forSettlement(state, home, List.of()).ticks());
            long wear = Math.addExact(tilling.getOrDefault(tool, 0L), forecast.isEmpty() ? 0 : forecast.getLast().recipes());
            demands.add(new EquipmentDemand(sources.size() == 1 ? "field" : "field:" + tool.ability(),
                    List.of(tool, new ToolRequirement(ToolAbilities.TILL, 0, 1)), concurrency,
                    Math.max(concurrency, wear), effort.get(tool)));
        }
        return List.copyOf(demands);
    }
}
