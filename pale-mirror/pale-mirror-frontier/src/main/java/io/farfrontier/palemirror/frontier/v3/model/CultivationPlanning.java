package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.agriculture.*;
import java.util.*;

/** Economic/land composition. Only the registry accepts its proposal; this owner never writes blocks or assigns workers. */
public final class CultivationPlanning {
    public record Plan(ResourceSiteState sites, List<SubjectId> declared) {
        public Plan { Objects.requireNonNull(sites); declared = List.copyOf(declared); }
    }
    private record Key(SubjectId home, long atTick) { }
    private static final ImmutableResultView<Key, Plan> CACHE = new ImmutableResultView<>(8);
    public static Plan read(FrontierWorldState state, SubjectId home, long atTick) {
        return CACHE.get(List.of(state), new Key(home, atTick), () -> plan(state, home, atTick));
    }
    private static Plan plan(FrontierWorldState state, SubjectId home, long atTick) {
        var rules = state.bootstrap().ruleset().fields().cultivation().orElseThrow();
        var sites = state.resourceSites();
        var fields = state.resourceSiteDescriptors().values().stream().filter(field -> field.settlementId().equals(home))
                .filter(field -> state.structureConditions().get(field.facilityId()) == StructureCondition.INTACT)
                .filter(field -> { var phase = sites.site(field.id()).phase(); return phase == ResourceSitePhase.GROWING
                        || phase == ResourceSitePhase.READY || phase == ResourceSitePhase.HARVESTING; })
                .sorted(Comparator.comparing(ResourceSite::id)).toList();
        if (fields.isEmpty()) return new Plan(sites, List.of());
        int area = state.resourceSiteDescriptors().values().stream().filter(field -> field.settlementId().equals(home))
                .mapToInt(field -> field.layout().cells().size()).sum();
        var land = FieldDevelopmentLand.read(state, home, fields, Math.max(0, rules.maximumCells() - area));
        var accepted = state.companies().goodsTrade().participants().economicState(home);
        if (accepted.accepted().isEmpty() || accepted.awaitingResources(atTick,
                state.bootstrap().ruleset().goodsTrade().supplyPlanning().commodities().keySet()).contains(FieldHarvestExtraction.ITEM))
            return new Plan(sites, List.of());
        var economic = accepted.accepted().orElseThrow().cultivation().orElse(null);
        if (economic == null) return new Plan(sites, List.of());
        long horizon = economic.horizonTicks(), desired = economic.desiredOutput(), safety = economic.safetyOutput();
        boolean safe = economic.safe();
        var candidates = new ArrayList<CultivationAreaPlanner.Cell>();
        var estimate = new FieldWorkForecast(state, home, FieldWorkForecast.workers(state, home));
        for (var field : fields) {
            var cycle = sites.cycle(field.id());
            for (var cell : field.layout().cells()) {
                var value = cycle.cell(cell.id());
                if (!value.soil().cultivable() || value.crop() == ResourceFieldCycle.Crop.OBSTRUCTED || value.workAccessBlocked()
                        || sites.hasPendingCellMutation(field.id(), cell.id())) continue;
                boolean planted = value.crop() == ResourceFieldCycle.Crop.GROWING || value.crop() == ResourceFieldCycle.Crop.MATURE;
                candidates.add(new CultivationAreaPlanner.Cell(cell.crop(), planted ? CultivationAreaPlanner.Land.PLANTED : CultivationAreaPlanner.Land.RESERVE,
                        FieldAgronomy.yield(state.bootstrap(), cell.crop()), estimate.lead(
                                new FieldSupplyCapacity.Cell(cell.crop(), value, true)),
                        cycle.sowing().cells().contains(cell.id()), !planted && cycle.sowing().cells().contains(cell.id())));
            }
        }
        for (var cell : land) candidates.add(new CultivationAreaPlanner.Cell(cell.crop(), CultivationAreaPlanner.Land.NEW,
                FieldAgronomy.yield(state.bootstrap(), cell.crop()), estimate.lead(
                        new FieldSupplyCapacity.Cell(cell.crop(), new ResourceFieldCycle.CellState(cell.condition(),
                                ResourceFieldCycle.Crop.ABSENT, 0, false, false), true)), false, false));
        var decision = new CultivationAreaPlanner().plan(new CultivationAreaPlanner.Request(
                desired, safety, horizon, !safe, safe, rules, candidates));
        var next = sites;
        // Existing plants and current effects survive contraction. Only their next sowing permission changes.
        for (var field : fields) next = next.planSowing(field.id(), field.layout().cells().stream()
                .filter(cell -> decision.sow().contains(cell.crop())).map(ResourceFieldLayout.Cell::id).collect(java.util.stream.Collectors.toSet()));
        var additions = land.stream().filter(cell -> decision.sow().contains(cell.crop())).toList();
        var admitted = FieldLandAdmission.admit(state, next, home, fields, additions, rules);
        next = admitted.sites();
        int selected = 0, planted = 0, reserve = 0; long output = 0, first = Long.MAX_VALUE;
        for (var declaration : next.declarations().values()) if (declaration.settlementId().equals(home)) {
            var cycle = next.cycle(declaration.id()); selected += cycle.sowing().cells().size();
            for (var cell : cycle.layout().cells()) {
                var value = cycle.cell(cell.id());
                boolean plant = value.crop() == ResourceFieldCycle.Crop.GROWING || value.crop() == ResourceFieldCycle.Crop.MATURE;
                if (plant) planted++; else if (value.soil().cultivable()) reserve++;
                if (cycle.sowing().cells().contains(cell.id())) {
                    output += FieldAgronomy.yield(state.bootstrap(), cell.crop());
                    first = Math.min(first, estimate.lead(new FieldSupplyCapacity.Cell(cell.crop(), value, true)));
                }
            }
        }
        var reason = admitted.deferred() ? CultivationAreaPlanner.Reason.DEVELOPMENT_LIMIT : decision.reason();
        next = next.reviewed(home, new CultivationReview(atTick, decision.targetOutput(), output, first, horizon,
                selected, planted, reserve, reason));
        return new Plan(next, admitted.declared());
    }
    private CultivationPlanning() { }
}
