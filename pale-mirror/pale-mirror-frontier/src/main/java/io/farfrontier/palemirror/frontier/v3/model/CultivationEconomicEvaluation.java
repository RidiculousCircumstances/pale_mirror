package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.economy.EconomicRequirements;
import java.util.*;

/** Explicit hypothetical land option of the economic review owner, not a query or reducer. */
public final class CultivationEconomicEvaluation {
    public static Optional<CultivationEconomicIntent> evaluate(FrontierWorldState state, SubjectId home, long atTick) {
        if (state.bootstrap().ruleset().fields().cultivation().isEmpty()
                || state.bootstrap().ruleset().goodsTrade().supplyPlanning().economics().isEmpty()) return Optional.empty();
        var rules = state.bootstrap().ruleset().fields().cultivation().orElseThrow();
        var sites = state.resourceSites();
        var fields = state.resourceSiteDescriptors().values().stream().filter(field -> field.settlementId().equals(home))
                .filter(field -> state.structureConditions().get(field.facilityId()) == StructureCondition.INTACT)
                .filter(field -> { var phase = sites.site(field.id()).phase(); return phase == ResourceSitePhase.GROWING
                        || phase == ResourceSitePhase.READY || phase == ResourceSitePhase.HARVESTING; })
                .sorted(Comparator.comparing(ResourceSite::id)).toList();
        if (fields.isEmpty()) return Optional.empty();
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
        var forecast = new FieldWorkForecast(state, home, FieldWorkForecast.workers(state, home));
        long existingOutput = possible.subList(0, possible.size() - land.size()).stream()
                .filter(cell -> forecast.lead(cell) <= horizon)
                .mapToLong(cell -> FieldAgronomy.yield(state.bootstrap(), cell.crop())).sum();
        long desired = Math.max(economy.additionalProduction().get(FieldHarvestExtraction.ITEM), Math.min(existingOutput,
                current.economy().orElseThrow().additionalProduction().get(FieldHarvestExtraction.ITEM)));
        return Optional.of(new CultivationEconomicIntent(desired, safety, horizon, safe));
    }
    private CultivationEconomicEvaluation() { }
}
