package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.agriculture.*;
import io.farfrontier.palemirror.frontier.v3.model.economy.EconomicRequirements;
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
        var possible = new ArrayList<FieldSupplyCapacity.Cell>();
        for (var field : fields) for (var cell : field.layout().cells()) {
            var condition = sites.cycle(field.id()).cell(cell.id());
            if (!sites.hasPendingCellMutation(field.id(), cell.id())) possible.add(new FieldSupplyCapacity.Cell(cell.crop(), condition, true));
        }
        for (var cell : land) possible.add(new FieldSupplyCapacity.Cell(cell.crop(),
                new ResourceFieldCycle.CellState(cell.condition(), ResourceFieldCycle.Crop.ABSENT, 0, false, false), true));
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        var view = GoodsParticipantView.observe(state, participant);
        var option = SettlementSupplyPlanning.assessCultivation(state, home, view.residents(), view.stocks(), possible, true, atTick);
        var economy = option.economy().orElseThrow();
        // Current funded interest may use already developed soil. Only development
        // of new land needs a sustained signal, not ordinary sowing or production.
        var existingCells = possible.subList(0, possible.size() - land.size());
        var current = SettlementSupplyPlanning.assessCultivation(state, home, view.residents(), view.stocks(), existingCells, atTick);
        long horizon = economy.horizonTicks();
        var basis = SettlementReservePlanning.basis(state, home, atTick);
        var reserves = SettlementReservePlanning.read(state, home, atTick);
        long restart = reserves.get(FieldHarvestExtraction.ITEM).timing().leadTicks();
        var arrivals = SettlementDeliveryForecast.read(state, participant);
        var required = new TreeMap<String, Long>(); var covered = new TreeMap<String, Long>();
        basis.needs().forEach((item, need) -> {
            required.put(item, Math.addExact(need.immediate(), Math.addExact(need.consumption().over(restart), reserves.get(item).protectedQuantity())));
            covered.put(item, Math.addExact(view.stocks().get(item).unclaimedInStorage(), arrivals.getOrDefault(item, List.of()).stream()
                    .filter(arrival -> arrival.afterTicks() <= restart).mapToLong(arrival -> arrival.quantity()).sum()));
        });
        long safety = new EconomicRequirements().net(required, covered, basis.flows(), Map.of()).missing().get(FieldHarvestExtraction.ITEM);
        boolean safe = basis.needs().entrySet().stream().filter(e -> e.getValue().consumption().numerator().signum() > 0
                        || e.getValue().immediate() > 0).allMatch(e ->
                io.farfrontier.palemirror.frontier.v3.model.supply.DatedStockCoverage.assess(
                        view.stocks().get(e.getKey()).unclaimedInStorage(), e.getValue(), reserves.get(e.getKey()).protectedQuantity(),
                        arrivals.getOrDefault(e.getKey(), List.of()), restart).covered());
        // A missing non-food input cannot itself justify erasing the restart crop reserve.
        if (!safe && safety == 0) safety = Math.max(1, reserves.get(FieldHarvestExtraction.ITEM).consumption().over(restart));
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
        long existingOutput = candidates.stream().filter(cell -> cell.harvestAfterTicks() <= horizon)
                .mapToLong(CultivationAreaPlanner.Cell::yield).sum();
        long desired = Math.max(economy.additionalProduction().get(FieldHarvestExtraction.ITEM), Math.min(existingOutput,
                current.economy().orElseThrow().additionalProduction().get(FieldHarvestExtraction.ITEM)));
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
