package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Field owner supplies finite biological capacity and labour separately; never starts work or routes. */
final class FieldSupplyCapacity implements SupplyCapacityPort {
    /** A considered cultivation option is not admitted land, incoming stock or a production commitment. */
    record Cell(BlockPosition crop, ResourceFieldCycle.CellState condition, boolean renew) {
        Cell { Objects.requireNonNull(crop); Objects.requireNonNull(condition); }
    }
    @Override public Key key() { return Key.FIELD; }
    @Override public List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks,
                                                    SupplyPlanningWindow window) {
        var cells = new ArrayList<Cell>();
        for (var site : state.resourceSites().sites().values()) {
            if (!state.resourceSite(site.siteId()).settlementId().equals(home)
                    || site.phase() == ResourceSitePhase.DESTROYED || site.phase() == ResourceSitePhase.CONFLICT) continue;
            var cycle = state.resourceSites().cycle(site.siteId());
            for (var cell : cycle.layout().cells()) {
                var value = cycle.cell(cell.id());
                if (value.workAccessBlocked() || state.resourceSites().hasPendingCellMutation(site.siteId(), cell.id())) continue;
                cells.add(new Cell(cell.crop(), value, cycle.sowing().cells().contains(cell.id())));
            }
        }
        return assess(state, home, stocks, window, cells);
    }

    static List<SupplyPlanner.Process> assess(FrontierWorldState state, SubjectId home,
            Map<String, SupplyPlanner.Stock> stocks, SupplyPlanningWindow window, List<Cell> cells) {
        if (cells.size() > ResourceFieldLayout.MAX_CELLS || cells.stream().map(Cell::crop).distinct().count() != cells.size())
            throw new IllegalArgumentException("field capacity option has duplicate or unbounded land");
        if (state.bootstrap().ruleset().fields().agriculture().isEmpty()) return List.of();
        var workers = FieldWorkForecast.workers(state, home);
        if (workers.isEmpty()) return List.of();
        var schedule = state.humanPopulation().schedule(home);
        long workWindow = SupplyPlanningWindow.dailyWork(schedule);
        if (workWindow == 0) return List.of();
        var estimate = new FieldWorkForecast(state, home, workers);
        var sources = cells.stream().filter(cell -> {
            var value = cell.condition();
            return !value.workAccessBlocked() && value.soil().cultivable()
                    && (value.crop() == ResourceFieldCycle.Crop.MATURE || value.crop() == ResourceFieldCycle.Crop.GROWING
                        || cell.renew() && value.crop() == ResourceFieldCycle.Crop.ABSENT);
        }).sorted(Comparator.comparingInt((Cell c) -> c.condition().crop() == ResourceFieldCycle.Crop.MATURE ? 0
                : c.condition().crop() == ResourceFieldCycle.Crop.GROWING ? 1 : 2)
                .thenComparingInt(c -> c.crop().x()).thenComparingInt(c -> c.crop().z()).thenComparingInt(c -> c.crop().y()))
                .map(estimate::source).toList();
        if (sources.isEmpty()) return List.of();
        long labour = sources.stream().mapToLong(source -> Math.ceilDiv(Math.addExact(source.harvest(),
                Math.max(source.preparation(), source.renew() ? source.repeatPreparation() : 0)), source.yield())).max().orElseThrow();
        long period = Math.max(1, Math.ceilDiv(Math.multiplyExact(labour, schedule.dayTicks()),
                Math.multiplyExact(workWindow, workers.size())));
        var forecast = io.farfrontier.palemirror.frontier.v3.model.supply.RenewableWorkForecast.assess(sources, workers.size(),
                schedule.dayTicks(), workWindow, window.ticks());
        var equipment = EquipmentProductiveCapacity.items(state, home, workers.stream().map(ResidentProfile::id)
                .collect(java.util.stream.Collectors.toSet()), FieldEquipment.requirements());
        long operations = EquipmentProductiveCapacity.remainingOperations(state, equipment, FieldHarvestExtraction.DEFINITION.toolRequirement());
        long tilling = cells.stream().filter(cell -> cell.renew() && cell.condition().soil().untilled()).count();
        long yieldPerUse = cells.stream().mapToLong(cell -> FieldAgronomy.yield(state.bootstrap(), cell.crop())).min().orElse(1);
        long equipmentOutput = Math.multiplyExact(Math.max(0, operations - tilling), yieldPerUse);
        var releases = new ArrayList<SupplyPlanner.OutputRelease>();
        long promised = stocks.get(FieldHarvestExtraction.ITEM).promisedProduction();
        for (var release : forecast) {
            long available = Math.min(release.recipes(), equipmentOutput) - promised;
            if (available > 0 && (releases.isEmpty() || available > releases.getLast().recipes())) releases.add(new SupplyPlanner.OutputRelease(release.afterTicks(), Math.toIntExact(available)));
        }
        if (releases.isEmpty()) return List.of();
        return List.of(new SupplyPlanner.Process("field-capacity:" + home.value(), FieldHarvestExtraction.ITEM, 1,
                Map.of(), 0, Map.of(), 0, releases.getFirst().afterTicks(), period,
                releases.getLast().recipes(), labour, SupplyLabourCapacity.over(state, home, workers.stream().map(ResidentProfile::id).toList(), window),
                1, releases));
    }
    @Override public List<SupplyPlanner.Dependency> dependencies(FrontierWorldState state, SubjectId home,
            Map<String, SupplyPlanner.Stock> stocks) { return List.of(); }

}
