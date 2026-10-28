package io.farfrontier.palemirror.frontier.v3.model;
import java.util.*;

/** Closed demand-owner composition. Logistics only transports the requested fuel. */
public final class StationFuelDemandComposition {
    @FunctionalInterface private interface Demand { long heat(FrontierWorldState state, ProductionStationSpec station, long tick, long backed); }
    @FunctionalInterface private interface Backing { long heat(FrontierWorldState state, ProductionStationSpec station); }
    private record Policy(Demand demand, Backing backing) { }
    private static final Map<ProductionStationSpec.Capability, Policy> OWNERS = Map.of(
            ProductionStationSpec.Capability.WOODWORKING, new Policy((state, station, tick, backed) -> 0, (state, station) -> 0),
            ProductionStationSpec.Capability.BAKING, new Policy(StationFuelDemandComposition::baking, StationFuelDemandComposition::workerBacking),
            ProductionStationSpec.Capability.SMELTING, new Policy(StationFuelDemandComposition::automatic, StationFuelDemandComposition::automaticBacking),
            ProductionStationSpec.Capability.FABRICATION, new Policy(StationFuelDemandComposition::equipment, StationFuelDemandComposition::workerBacking));
    public static long heat(FrontierWorldState state, ProductionStationSpec station, long tick) {
        long backed = Math.max(backedHeat(state, station), activeBurnTicks(state, station, tick));
        return backed == 0 ? 0 : Math.max(activeBurnTicks(state, station, tick), Math.min(backed, Objects.requireNonNull(OWNERS.get(station.capability()),
                "unregistered station fuel policy").demand().heat(state, station, tick, backed)));
    }
    /** The production owner supplies priority; logistics never classifies bread, metal or jobs. */
    public static long deadline(FrontierWorldState state, ProductionStationSpec station, long tick) {
        var owner = ProductionStationEnergy.record(state, station.id()).ownerId();
        var participant = Objects.requireNonNull(state.companies().goodsTrade().participants().participants().get(owner));
        var plan = GoodsParticipantAssessment.view(state, participant, tick).supplyPlan();
        return state.bootstrap().ruleset().production().recipes().stream()
                .filter(recipe -> recipe.capability() == station.capability())
                .mapToLong(recipe -> plan.map(value -> value.require(recipe.product().itemKind()).deadlineTicks())
                        .orElse(Long.MAX_VALUE)).min().orElse(Long.MAX_VALUE);
    }
    /** No heat is immobilized on the strength of an output target alone. Available
     * ingredients and exact accepted input orders bound the next machine load. */
    public static long backedHeat(FrontierWorldState state, ProductionStationSpec station) {
        return Objects.requireNonNull(OWNERS.get(station.capability()), "unregistered station fuel policy").backing().heat(state, station);
    }
    /** Remaining admitted work cannot disappear when its input claims move into the machine. */
    public static long activeBurnTicks(FrontierWorldState state, ProductionStationSpec station, long tick) {
        var operation = state.productionStations().require(station.id()).operation().orElse(null);
        if (operation == null || operation.transformed()) return 0;
        var recipe = state.bootstrap().ruleset().production().require(operation.recipeId());
        if (!recipe.requiresCombustion()) return 0;
        long total = Math.multiplyExact(operation.inputQuantity() / recipe.inputUnitsPerBatch(),
                recipe.nominalBurnTicksPerBatch(state.bootstrap().ruleset().workCatalog()));
        var clock = operation.progress().orElse(null);
        return clock == null ? total : Math.ceilDiv(clock.requiredMilliWork() - clock.completedAt(Math.max(tick, clock.evaluatedAtTick())),
                operation.attendance().map(ProductionStationClock.Attendance::speedPermille).orElse(1000));
    }
    private static long automaticBacking(FrontierWorldState state, ProductionStationSpec station) {
        var policy = AutomaticStationProduction.policy(state, station);
        if (policy.isEmpty()) return 0;
        var recipe = state.bootstrap().ruleset().production().require(policy.orElseThrow().recipeId());
        return Math.multiplyExact(recipe.nominalBurnTicksPerBatch(state.bootstrap().ruleset().workCatalog()), Math.min(ProductionBatchSizing.maximumRecipeBatches(recipe),
                ProductionIngredientAvailability.batches(state, station, recipe)));
    }
    private static long workerBacking(FrontierWorldState state, ProductionStationSpec station) {
        long heat = 0;
        for (var recipe : state.bootstrap().ruleset().production().recipes()) {
            if (recipe.capability() != station.capability()) continue;
            long batches = ProductionIngredientAvailability.batches(state, station, recipe);
            for (var job : state.productionJobs().values()) {
                var work = job.stationWork().orElse(null);
                if (work == null || !work.stationId().equals(station.id()) || !work.recipeId().equals(recipe.id())
                        || StationWorkCargo.output(job) || work.phase() == StationWorkState.Phase.PROCESSING) continue;
                batches = Math.addExact(batches, StationWorkMaterialAccounting.plannedUnits(job) / recipe.inputUnitsPerBatch());
            }
            heat = Math.max(heat, Math.multiplyExact(recipe.nominalBurnTicksPerBatch(state.bootstrap().ruleset().workCatalog()), Math.min(
                    ProductionBatchSizing.maximumRecipeBatches(recipe), batches)));
        }
        return heat;
    }
    private static long baking(FrontierWorldState state, ProductionStationSpec station, long tick, long backed) {
        return state.bootstrap().ruleset().production().recipes().stream().filter(recipe -> recipe.capability() == station.capability())
                .mapToLong(recipe -> Math.multiplyExact(recipe.nominalBurnTicksPerBatch(state.bootstrap().ruleset().workCatalog()), ProductionBatchSizing.maximumRecipeBatches(recipe) * 2L)).max().orElse(0);
    }
    private static long automatic(FrontierWorldState state, ProductionStationSpec station, long tick, long backed) {
        var policy = AutomaticStationProduction.policy(state, station);
        if (policy.isEmpty()) return 0;
        var recipe = state.bootstrap().ruleset().production().require(policy.orElseThrow().recipeId());
        var owner = ProductionStationEnergy.record(state, station.id()).ownerId();
        long missing = ProductionStockDemand.read(state, owner, recipe.product().itemKind(), tick).missing();
        return Math.multiplyExact(Math.min(ProductionBatchSizing.maximumRecipeBatches(recipe),
                Math.ceilDiv(missing, recipe.product().quantity())), recipe.nominalBurnTicksPerBatch(state.bootstrap().ruleset().workCatalog()));
    }
    private static long equipment(FrontierWorldState state, ProductionStationSpec station, long tick, long backed) {
        var owner = ProductionStationEnergy.record(state, station.id()).ownerId();
        long retained = state.productionJobs().values().stream().filter(job -> job.stationWork().filter(work ->
                work.stationId().equals(station.id()) && !StationWorkCargo.output(job)).isPresent())
                .mapToLong(job -> state.bootstrap().ruleset().production().require(job.stationWork().orElseThrow().recipeId()).nominalBurnTicksPerBatch(state.bootstrap().ruleset().workCatalog())).sum();
        // heat() clips policy demand to the actual material/active-operation
        // backing. Admitted work already covers that bound; a market portfolio
        // can only increase a number which the same bound will discard.
        if (retained >= backed) return retained;
        var planned = EquipmentProductionPlanning.planned(state, owner, tick);
        // One planned operation per free station; a long forecast must not fill every fuel port.
        long next = planned.stream().filter(plan -> AutomaticStationProduction.stations(state, owner, plan.request().recipe()).stream()
                .map(record -> record.productionStation().orElseThrow().id()).sorted().findFirst().filter(station.id()::equals).isPresent())
                .mapToLong(plan -> plan.request().recipe().nominalBurnTicksPerBatch(state.bootstrap().ruleset().workCatalog())).max().orElse(0);
        return Math.addExact(retained, next);
    }
    private StationFuelDemandComposition() { }
}
