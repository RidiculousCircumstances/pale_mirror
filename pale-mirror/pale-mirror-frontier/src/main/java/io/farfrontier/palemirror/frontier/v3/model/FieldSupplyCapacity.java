package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import io.farfrontier.palemirror.frontier.v3.model.supply.ProductionCost;
import io.farfrontier.palemirror.frontier.v3.model.supply.RenewableWorkForecast;
import java.util.*;

/** Field owner supplies finite biological capacity and labour separately; never starts work or routes. */
final class FieldSupplyCapacity implements SupplyCapacityPort {
    private static final ImmutableKeyedInputView<SubjectId, List<Cell>> CELLS =
            new ImmutableKeyedInputView<>(SimulationCapacity.MAX_SETTLEMENTS);
    private static final ImmutableKeyedInputView<SubjectId, List<ProductionCost>> VALUATIONS =
            new ImmutableKeyedInputView<>(SimulationCapacity.MAX_SETTLEMENTS);
    private static final ImmutableKeyedInputView<SubjectId, io.farfrontier.palemirror.frontier.v3.model.supply.RenewableWorkForecast.Memo>
            FORECASTS = new ImmutableKeyedInputView<>(SimulationCapacity.MAX_SETTLEMENTS);
    /** A considered cultivation option is not admitted land, incoming stock or a production commitment. */
    record Cell(BlockPosition crop, ResourceFieldCycle.CellState condition, boolean renew, ResourceSiteKind kind) {
        Cell { Objects.requireNonNull(crop); Objects.requireNonNull(condition); Objects.requireNonNull(kind); }
    }
    @Override public Key key() { return Key.FIELD; }
    @Override public List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks,
                                                    SupplyPlanningWindow window) {
        return assess(state, home, stocks, window, cells(state, home));
    }
    @Override public List<ProductionCost> valuation(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks) {
        if (state.bootstrap().ruleset().fields().agriculture().isEmpty()
                || SupplyPlanningWindow.dailyWork(state.humanPopulation().schedule(home)) == 0) return List.of();
        if (state.bootstrap().ruleset().tools().definitions().values().stream().noneMatch(tool ->
                FieldEquipment.requirements(state).alternatives().stream().anyMatch(group -> group.stream().allMatch(requirement -> requirement.supports(tool))))) return List.of();
        var workers = state.bootstrap().ruleset().fields().crops().stream()
                .flatMap(policy -> FieldWorkForecast.valuationWorkers(state, home, policy.kind()).stream()).distinct().toList();
        if (workers.isEmpty()) return List.of();
        var stores = state.bootstrap().ruleset().fields().crops().stream()
                .flatMap(policy -> FieldWorkForecast.storagePositions(state, home, policy.kind()).stream()).distinct().toList();
        var inputs = new ArrayList<>(SupplyLabourCapacity.potentialInputs(state, home));
        var sites = state.resourceSites(); var local = sites.siteIdsIn(home);
        inputs.add(local);
        for (var id : local) {
            inputs.add(sites.site(id)); inputs.add(sites.cycle(id)); inputs.addAll(sites.cellMutationInputs(id));
        }
        // The provider resolves current fixed/mobile positions and eligibility.
        // Other residents' movement and foreign market prices are not travel costs.
        inputs.addAll(stores);
        return VALUATIONS.getPublished(state.bootstrap(), home, inputs, () -> valueInstalled(state, home));
    }
    private static List<ProductionCost> valueInstalled(FrontierWorldState state, SubjectId home) {
        var result = new ArrayList<ProductionCost>();
        for (var policy : state.bootstrap().ruleset().fields().crops()) {
            var kind = policy.kind(); var workers = FieldWorkForecast.valuationWorkers(state, home, kind);
            if (workers.isEmpty()) continue;
            var estimate = new FieldWorkForecast(state, home, workers, kind, false, FieldWorkForecast.storagePositions(state, home, kind));
            var labour = cells(state, home).stream().filter(cell -> cell.kind() == kind).filter(FieldSupplyCapacity::productive)
                    .mapToLong(estimate::unitLabour).max();
            if (labour.isPresent()) result.add(new ProductionCost("field-capacity:" + home.value() + ":" + kind.wireTag(),
                    kind.resourceKind(), 1, Map.of(), 0, Map.of(), labour.getAsLong(), 1));
        }
        return List.copyOf(result);
    }
    private static List<Cell> cells(FrontierWorldState state, SubjectId home) {
        var sites = state.resourceSites(); var local = sites.siteIdsIn(home);
        var inputs = new ArrayList<Object>(); inputs.add(local);
        for (var id : local) {
            inputs.add(sites.site(id).phase()); inputs.add(sites.cycle(id)); inputs.addAll(sites.cellMutationInputs(id));
        }
        return CELLS.get(state.bootstrap(), home, inputs, () -> readCells(state, home));
    }
    private static List<Cell> readCells(FrontierWorldState state, SubjectId home) {
        var cells = new ArrayList<Cell>();
        for (var id : state.resourceSites().siteIdsIn(home)) {
            var site = state.resourceSites().site(id);
            if (site.phase() == ResourceSitePhase.DESTROYED || site.phase() == ResourceSitePhase.CONFLICT) continue;
            var cycle = state.resourceSites().cycle(site.siteId());
            for (var cell : cycle.layout().cells()) {
                var value = cycle.cell(cell.id());
                if (value.workAccessBlocked() || state.resourceSites().hasPendingCellMutation(site.siteId(), cell.id())) continue;
                cells.add(new Cell(cell.crop(), value, cycle.sowing().cells().contains(cell.id()), state.resourceSite(id).kind()));
            }
        }
        return List.copyOf(cells);
    }

    static List<SupplyPlanner.Process> assess(FrontierWorldState state, SubjectId home,
            Map<String, SupplyPlanner.Stock> stocks, SupplyPlanningWindow window, List<Cell> cells) {
        var result = new ArrayList<SupplyPlanner.Process>();
        for (var policy : state.bootstrap().ruleset().fields().crops()) {
            var selected = cells.stream().filter(cell -> cell.kind() == policy.kind()).toList();
            if (!selected.isEmpty()) result.addAll(assessCrop(state, home, stocks, window, selected, policy.kind()));
        }
        return List.copyOf(result);
    }
    private static List<SupplyPlanner.Process> assessCrop(FrontierWorldState state, SubjectId home,
            Map<String, SupplyPlanner.Stock> stocks, SupplyPlanningWindow window, List<Cell> cells, ResourceSiteKind kind) {
        var workers = FieldWorkForecast.workers(state, home, kind);
        if (cells.size() > ResourceFieldLayout.MAX_CELLS || cells.stream().map(Cell::crop).distinct().count() != cells.size())
            throw new IllegalArgumentException("field capacity option has duplicate or unbounded land");
        if (state.bootstrap().ruleset().fields().agriculture().isEmpty()) return List.of();
        if (workers.isEmpty()) return List.of();
        var schedule = state.humanPopulation().schedule(home);
        long workWindow = SupplyPlanningWindow.dailyWork(schedule);
        if (workWindow == 0) return List.of();
        var estimate = new FieldWorkForecast(state, home, workers, kind, true, FieldWorkForecast.storagePositions(state, home, kind));
        var sources = sources(cells, estimate);
        if (sources.isEmpty()) return List.of();
        long labour = labour(sources);
        long period = Math.max(1, Math.ceilDiv(Math.multiplyExact(labour, schedule.dayTicks()),
                Math.multiplyExact(workWindow, workers.size())));
        var memo = FORECASTS.get(state.bootstrap(), home, List.of(state.bootstrap()),
                () -> new io.farfrontier.palemirror.frontier.v3.model.supply.RenewableWorkForecast.Memo(4));
        var forecast = memo.assess(sources, workers.size(), schedule.dayTicks(), workWindow, window.ticks());
        var equipment = EquipmentProductiveCapacity.items(state, home, workers.stream().map(ResidentProfile::id)
                .collect(java.util.stream.Collectors.toSet()), FieldEquipment.requirements(kind));
        long operations = EquipmentProductiveCapacity.remainingOperations(state, equipment, kind.crop().harvestTool());
        long tilling = cells.stream().filter(cell -> cell.renew() && cell.condition().soil().untilled()).count();
        long yieldPerUse = cells.stream().mapToLong(cell -> FieldAgronomy.yield(state.bootstrap(), kind, cell.crop())).min().orElse(1);
        long equipmentOutput = Math.multiplyExact(Math.max(0, operations - tilling), yieldPerUse);
        var releases = new ArrayList<SupplyPlanner.OutputRelease>();
        long promised = stocks.get(kind.resourceKind()).promisedProduction();
        for (var release : forecast) {
            long available = Math.min(release.recipes(), equipmentOutput) - promised;
            if (available > 0 && (releases.isEmpty() || available > releases.getLast().recipes())) releases.add(new SupplyPlanner.OutputRelease(release.afterTicks(), Math.toIntExact(available)));
        }
        if (releases.isEmpty()) return List.of();
        return List.of(new SupplyPlanner.Process("field-capacity:" + home.value() + ":" + kind.wireTag(), kind.resourceKind(), 1,
                Map.of(), 0, Map.of(), 0, releases.getFirst().afterTicks(), period,
                releases.getLast().recipes(), labour, SupplyLabourCapacity.over(state, home, workers.stream().map(ResidentProfile::id).toList(), window),
                1, releases));
    }
    private static List<RenewableWorkForecast.Source> sources(List<Cell> cells, FieldWorkForecast estimate) {
        return cells.stream().filter(FieldSupplyCapacity::productive).sorted(Comparator.comparingInt((Cell c) -> c.condition().crop() == ResourceFieldCycle.Crop.MATURE ? 0
                : c.condition().crop() == ResourceFieldCycle.Crop.GROWING ? 1 : 2)
                .thenComparingInt(c -> c.crop().x()).thenComparingInt(c -> c.crop().z()).thenComparingInt(c -> c.crop().y()))
                .map(estimate::source).toList();
    }
    private static boolean productive(Cell cell) {
        var value = cell.condition();
        return !value.workAccessBlocked() && value.soil().cultivable()
                && (value.crop() == ResourceFieldCycle.Crop.MATURE || value.crop() == ResourceFieldCycle.Crop.GROWING
                    || cell.renew() && value.crop() == ResourceFieldCycle.Crop.ABSENT);
    }
    private static long labour(List<RenewableWorkForecast.Source> sources) {
        return sources.stream().mapToLong(source -> Math.ceilDiv(Math.addExact(source.harvest(),
                Math.max(source.preparation(), source.renew() ? source.repeatPreparation() : 0)), source.yield())).max().orElseThrow();
    }
    @Override public List<SupplyPlanner.Dependency> dependencies(FrontierWorldState state, SubjectId home,
            Map<String, SupplyPlanner.Stock> stocks) { return List.of(); }

}
