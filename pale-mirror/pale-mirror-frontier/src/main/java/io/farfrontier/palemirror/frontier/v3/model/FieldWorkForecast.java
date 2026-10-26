package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.RenewableWorkForecast;
import java.util.*;

/** One agriculture-owned estimate for capacity, land comparison and restart reserves.
 * Travel is a geometric cost estimate, never a path or confirmed delivery. */
final class FieldWorkForecast {
    private record PreparationCost(WorkOperation operation, io.farfrontier.palemirror.frontier.v3.model.tooling.ToolRequirement tool, int effortPermille) { }
    private final FrontierBootstrap bootstrap;
    private final SettlementDailySchedule schedule;
    private final List<BlockPosition> stores;
    private final long sowTicks, harvestTicks, distanceFactor;
    private final Map<ResourceFieldCycle.Soil, Long> preparationTicks;

    /** One immutable input view per assessment, shared by every candidate cell. */
    FieldWorkForecast(FrontierWorldState state, SubjectId home, List<ResidentProfile> workers) {
        this(state, home, workers, true);
    }
    FieldWorkForecast(FrontierWorldState state, SubjectId home, List<ResidentProfile> workers, boolean equipmentBacked) {
        bootstrap = state.bootstrap(); schedule = state.humanPopulation().schedule(home);
        sowTicks = duration(state, workers, WorkOperation.SOW, equipmentBacked, null, 1000);
        harvestTicks = duration(state, workers, WorkOperation.HARVEST, equipmentBacked, FieldHarvestExtraction.DEFINITION.toolRequirement(), 1000);
        var stages = new HashMap<PreparationCost, Long>();
        var preparations = new HashMap<ResourceFieldCycle.Soil, Long>();
        for (var soil : ResourceFieldCycle.Soil.catalog().soils()) if (soil.untilled()) {
            long total = ResourceFieldCycle.Soil.catalog().path(soil).stream().mapToLong(step -> stages.computeIfAbsent(new PreparationCost(step.operation(), step.tool(), step.effortPermille()),
                    key -> duration(state, workers, key.operation(), equipmentBacked, key.tool(), key.effortPermille()))).reduce(0L, Math::addExact);
            preparations.put(soil, total);
        }
        preparationTicks = Map.copyOf(preparations);
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        stores = GoodsParticipantStorage.containers(state, participant, FieldHarvestExtraction.ITEM).stream()
                .map(id -> state.inventory().surfaces().get(id).position(state)).toList();
        distanceFactor = bootstrap.ruleset().goodsTrade().supplyPlanning().sourcing()
                .map(rules -> rules.provisionalDistancePermille()).orElse(1000);
    }
    static List<ResidentProfile> workers(FrontierWorldState state, SubjectId home) {
        var definition = state.bootstrap().ruleset().workCatalog().require(FieldHarvestExtraction.ITEM, WorkOperation.HARVEST);
        var assignments = HumanAssignmentProjection.compile(state);
        var candidates = SupplyLabourCapacity.candidates(state, home, ResidentWorkKind.AGRICULTURE, definition.capability(),
                r -> assignments.assignment(r.id()).kind() == HumanAssignmentKind.FIELD_HARVEST).stream()
                .filter(r -> !assignments.assignment(r.id()).active()
                        || assignments.assignment(r.id()).kind() == HumanAssignmentKind.FIELD_HARVEST).toList();
        var equipment = EquipmentProductiveCapacity.items(state, home, candidates.stream().map(ResidentProfile::id)
                .collect(java.util.stream.Collectors.toSet()), FieldEquipment.requirements());
        return candidates.stream().limit(equipment.size()).toList();
    }
    static List<ResidentProfile> valuationWorkers(FrontierWorldState state, SubjectId home) {
        var definition = state.bootstrap().ruleset().workCatalog().require(FieldHarvestExtraction.ITEM, WorkOperation.HARVEST);
        return SupplyLabourCapacity.potential(state, home, ResidentWorkKind.AGRICULTURE, definition.capability());
    }
    RenewableWorkForecast.Source source(FieldSupplyCapacity.Cell cell) {
        var condition = cell.condition();
        boolean planted = condition.crop() == ResourceFieldCycle.Crop.MATURE || condition.crop() == ResourceFieldCycle.Crop.GROWING;
        long growthStage = Math.multiplyExact(FieldAgronomy.growthPeriods(bootstrap, cell.crop()),
                bootstrap.ruleset().cadence().resourceGrowthStageInterval());
        long travel = travel(cell.crop(), FieldAgronomy.yield(bootstrap, cell.crop()));
        long sow = Math.addExact(sowTicks, travel);
        return new RenewableWorkForecast.Source(FieldAgronomy.yield(bootstrap, cell.crop()),
                planted ? 0 : Math.addExact(sow, preparationTicks.getOrDefault(condition.soil(), 0L)),
                Math.addExact(harvestTicks, travel),
                Math.multiplyExact(ResourceSiteLifecycle.MATURE_STAGE - condition.growthStage(), growthStage),
                Math.multiplyExact(ResourceSiteLifecycle.MATURE_STAGE, growthStage), sow, planted, cell.renew());
    }
    long lead(FieldSupplyCapacity.Cell cell) {
        var source = source(cell);
        if (SupplyPlanningWindow.dailyWork(schedule) == 0) return Long.MAX_VALUE;
        return Math.addExact(source.remainingGrowth(), SupplyPlanningWindow.completionDelay(schedule,
                Math.addExact(source.preparation(), source.harvest())));
    }
    private static long duration(FrontierWorldState state, List<ResidentProfile> workers, WorkOperation operation, boolean equipmentBacked,
            io.farfrontier.palemirror.frontier.v3.model.tooling.ToolRequirement requirement, int effortPermille) {
        var catalog = state.bootstrap().ruleset().workCatalog(); var definition = catalog.require(FieldHarvestExtraction.ITEM, operation);
        int speed = workers.stream().mapToInt(r -> ResidentWorkStatistics.speedPermille(r, definition, catalog))
                .min().orElse(catalog.minSpeedPermille());
        if (requirement != null && !workers.isEmpty()) {
            var equipment = EquipmentProductiveCapacity.items(state, workers.getFirst().settlementId(), workers.stream().map(ResidentProfile::id)
                    .collect(java.util.stream.Collectors.toSet()), FieldEquipment.requirements());
            final int workerSpeed = speed;
            speed = equipmentBacked ? equipment.stream().mapToInt(item -> io.farfrontier.palemirror.frontier.v3.model.tooling.ToolWorkRate.speedPermille(
                    workerSpeed, state.bootstrap().ruleset().tools(), item, requirement)).min().orElse(speed)
                    : state.bootstrap().ruleset().tools().definitions().values().stream().filter(tool -> tool.supports(requirement))
                        .mapToInt(tool -> Math.toIntExact(Math.max(1, (long) workerSpeed * tool.speedPermille() / 1000))).min().orElse(speed);
        }
        return Math.ceilDiv(Math.multiplyExact(Math.ceilDiv(Math.multiplyExact(definition.workUnits(), effortPermille), 1000), 1000L), speed);
    }
    private long travel(BlockPosition crop, int yield) {
        long distance = stores.stream()
                .mapToLong(p -> Math.abs((long) p.x() - crop.x()) + Math.abs((long) p.y() - crop.y())
                        + Math.abs((long) p.z() - crop.z())).min().orElse(0);
        long roundTrip = Math.ceilDiv(Math.multiplyExact(Math.multiplyExact(distance, 2), distanceFactor), 1000);
        // Adjacent-cell walking plus the cell's share of a full hand's return trip.
        long edges = Math.addExact(1, Math.ceilDiv(Math.multiplyExact(roundTrip, yield), 64));
        return Math.multiplyExact(edges, bootstrap.ruleset().resourceHarvestColdTravelTicksPerEdge());
    }
}
