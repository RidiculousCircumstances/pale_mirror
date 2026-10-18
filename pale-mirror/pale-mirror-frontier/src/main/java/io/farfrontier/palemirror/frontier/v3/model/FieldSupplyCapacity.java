package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;

/** Field owner supplies finite biological capacity and labour separately; never starts work or routes. */
final class FieldSupplyCapacity implements SupplyCapacityPort {
    @Override public Key key() { return Key.FIELD; }
    @Override public List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks,
                                                    SupplyPlanningWindow window) {
        if (state.bootstrap().ruleset().fields().agriculture().isEmpty()) return List.of();
        var definition = state.bootstrap().ruleset().workCatalog().require(FieldHarvestExtraction.ITEM, WorkOperation.HARVEST);
        var assignments = HumanAssignmentProjection.compile(state);
        var workers = SupplyLabourCapacity.candidates(state, home, ResidentWorkKind.AGRICULTURE, definition.capability(),
                        r -> assignments.assignment(r.id()).kind() == HumanAssignmentKind.FIELD_HARVEST).stream()
                .filter(r -> !assignments.assignment(r.id()).active()
                        || assignments.assignment(r.id()).kind() == HumanAssignmentKind.FIELD_HARVEST).toList();
        if (workers.isEmpty()) return List.of();
        int speed = workers.stream().mapToInt(r -> ResidentWorkStatistics.speedPermille(r, definition,
                state.bootstrap().ruleset().workCatalog())).min().orElseThrow();
        long cellWork = Math.ceilDiv(Math.multiplyExact(definition.workUnits(), 1000L), speed);
        long output = 0, cells = 0, growth = 0, fullGrowth = 0;
        for (var site : state.resourceSites().sites().values()) {
            if (!state.resourceSite(site.siteId()).settlementId().equals(home)
                    || site.phase() == ResourceSitePhase.DESTROYED || site.phase() == ResourceSitePhase.CONFLICT) continue;
            var cycle = state.resourceSites().cycle(site.siteId());
            for (var cell : cycle.layout().cells()) {
                var value = cycle.cell(cell.id());
                // Unprepared or damaged soil is not an executable growing crop.
                if (value.soil() != ResourceFieldCycle.Soil.FARMLAND || value.workAccessBlocked()
                        || value.crop() != ResourceFieldCycle.Crop.MATURE && value.crop() != ResourceFieldCycle.Crop.GROWING) continue;
                output = Math.addExact(output, FieldAgronomy.yield(state.bootstrap(), cell.crop())); cells++;
                growth = Math.max(growth, Math.multiplyExact(ResourceSiteLifecycle.MATURE_STAGE - value.growthStage(),
                        Math.multiplyExact(FieldAgronomy.growthPeriods(state.bootstrap(), cell.crop()),
                                state.bootstrap().ruleset().cadence().resourceGrowthStageInterval())));
                fullGrowth = Math.max(fullGrowth, Math.multiplyExact(ResourceSiteLifecycle.MATURE_STAGE,
                        Math.multiplyExact(FieldAgronomy.growthPeriods(state.bootstrap(), cell.crop()),
                                state.bootstrap().ruleset().cadence().resourceGrowthStageInterval())));
            }
        }
        if (output == 0) return List.of();
        long remaining = Math.max(0, output - stocks.get(FieldHarvestExtraction.ITEM).promisedProduction());
        var schedule = state.humanPopulation().schedule(home);
        long workWindow = schedule.segments().stream().filter(s -> s.window() == SettlementDailySchedule.Window.WORK)
                .mapToLong(s -> s.endExclusive() - s.startInclusive()).sum();
        if (workWindow == 0) return List.of();
        long labour = Math.max(1, Math.ceilDiv(Math.multiplyExact(cells, cellWork), output));
        long period = Math.max(1, Math.ceilDiv(Math.multiplyExact(labour, schedule.dayTicks()),
                Math.multiplyExact(workWindow, workers.size())));
        // Repeated harvests are dated waves, not a continuous stream that invents
        // wheat during regrowth. Waiting for the entire wave is conservative for
        // staggered cells; committed current output is subtracted only once.
        long harvest = SupplyPlanningWindow.completionDelay(schedule,
                Math.ceilDiv(Math.multiplyExact(cells, cellWork), workers.size()));
        long first = Math.addExact(growth, harvest);
        long cycleTicks = Math.addExact(fullGrowth, harvest);
        var releases = new ArrayList<SupplyPlanner.OutputRelease>();
        long quantity = remaining;
        if (remaining > 0) releases.add(new SupplyPlanner.OutputRelease(first, Math.toIntExact(remaining)));
        for (long at = Math.addExact(first, cycleTicks); at <= window.ticks(); at = Math.addExact(at, cycleTicks)) {
            quantity = Math.addExact(quantity, output);
            releases.add(new SupplyPlanner.OutputRelease(at, Math.toIntExact(quantity)));
        }
        if (releases.isEmpty()) return List.of();
        return List.of(new SupplyPlanner.Process("field-capacity:" + home.value(), FieldHarvestExtraction.ITEM, 1,
                Map.of(), 0, Map.of(), 0, releases.getFirst().afterTicks(), period,
                Math.toIntExact(quantity), labour, SupplyLabourCapacity.over(state, home, workers.stream().map(ResidentProfile::id).toList(), window),
                1, releases));
    }
    @Override public List<SupplyPlanner.Dependency> dependencies(FrontierWorldState state, SubjectId home,
            Map<String, SupplyPlanner.Stock> stocks) { return List.of(); }
}
