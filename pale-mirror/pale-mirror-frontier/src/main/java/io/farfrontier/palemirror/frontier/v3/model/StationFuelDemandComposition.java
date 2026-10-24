package io.farfrontier.palemirror.frontier.v3.model;
import java.util.*;

/** Closed demand-owner composition. Logistics only transports the requested fuel. */
public final class StationFuelDemandComposition {
    @FunctionalInterface private interface Demand { long heat(FrontierWorldState state, ProductionStationSpec station, long tick); }
    @FunctionalInterface private interface Backing { long heat(FrontierWorldState state, ProductionStationSpec station); }
    private record Policy(Demand demand, Backing backing) { }
    private static final Map<ProductionStationSpec.Capability, Policy> OWNERS = Map.of(
            ProductionStationSpec.Capability.BAKING, new Policy(StationFuelDemandComposition::baking, StationFuelDemandComposition::workerBacking),
            ProductionStationSpec.Capability.SMELTING, new Policy(StationFuelDemandComposition::automatic, StationFuelDemandComposition::automaticBacking),
            ProductionStationSpec.Capability.FABRICATION, new Policy(StationFuelDemandComposition::equipment, StationFuelDemandComposition::workerBacking));
    public static long heat(FrontierWorldState state, ProductionStationSpec station, long tick) {
        long backed = backedHeat(state, station);
        return backed == 0 ? 0 : Math.min(backed, Objects.requireNonNull(OWNERS.get(station.capability()),
                "unregistered station fuel policy").demand().heat(state, station, tick));
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
    private static long automaticBacking(FrontierWorldState state, ProductionStationSpec station) {
        var policy = AutomaticStationProduction.policy(state, station);
        if (policy.isEmpty()) return 0;
        var recipe = state.bootstrap().ruleset().production().require(policy.orElseThrow().recipeId());
        return Math.multiplyExact(recipe.heatPerBatch(), Math.min(ProductionBatchSizing.maximumRecipeBatches(recipe),
                ProductionIngredientAvailability.forStation(state, station, recipe) / recipe.ingredient().quantity()));
    }
    private static long workerBacking(FrontierWorldState state, ProductionStationSpec station) {
        long heat = 0;
        for (var recipe : state.bootstrap().ruleset().production().recipes()) {
            if (recipe.capability() != station.capability()) continue;
            long inputs = ProductionIngredientAvailability.forStation(state, station, recipe);
            for (var job : state.productionJobs().values()) {
                var work = job.stationWork().orElse(null);
                if (work == null || !work.stationId().equals(station.id()) || !work.recipeId().equals(recipe.id())
                        || StationWorkCargo.output(job) || work.phase() == StationWorkState.Phase.PROCESSING) continue;
                inputs = Math.addExact(inputs, job.inputCount());
            }
            heat = Math.max(heat, Math.multiplyExact(recipe.heatPerBatch(), Math.min(
                    ProductionBatchSizing.maximumRecipeBatches(recipe), inputs / recipe.ingredient().quantity())));
        }
        return heat;
    }
    private static long baking(FrontierWorldState state, ProductionStationSpec station, long tick) {
        return state.bootstrap().ruleset().production().recipes().stream().filter(recipe -> recipe.capability() == station.capability())
                .mapToLong(recipe -> Math.multiplyExact(recipe.heatPerBatch(), ProductionBatchSizing.maximumRecipeBatches(recipe) * 2L)).max().orElse(0);
    }
    private static long automatic(FrontierWorldState state, ProductionStationSpec station, long tick) {
        var policy = AutomaticStationProduction.policy(state, station);
        if (policy.isEmpty()) return 0;
        var recipe = state.bootstrap().ruleset().production().require(policy.orElseThrow().recipeId());
        var owner = ProductionStationEnergy.record(state, station.id()).ownerId();
        long missing = ProductionStockDemand.read(state, owner, recipe.product().itemKind(), tick).missing();
        return Math.multiplyExact(Math.min(ProductionBatchSizing.maximumRecipeBatches(recipe),
                Math.ceilDiv(missing, recipe.product().quantity())), recipe.heatPerBatch());
    }
    private static long equipment(FrontierWorldState state, ProductionStationSpec station, long tick) {
        var owner = ProductionStationEnergy.record(state, station.id()).ownerId();
        long retained = state.productionJobs().values().stream().filter(job -> job.stationWork().filter(work ->
                work.stationId().equals(station.id()) && !StationWorkCargo.output(job)).isPresent())
                .mapToLong(job -> state.bootstrap().ruleset().production().require(job.stationWork().orElseThrow().recipeId()).heatPerBatch()).sum();
        var planned = EquipmentProductionPlanning.planned(state, owner, tick);
        // One planned operation per free station; a long forecast must not fill every fuel port.
        long next = planned.stream().filter(plan -> AutomaticStationProduction.stations(state, owner, plan.request().recipe()).stream()
                .map(record -> record.productionStation().orElseThrow().id()).sorted().findFirst().filter(station.id()::equals).isPresent())
                .mapToLong(plan -> plan.request().recipe().heatPerBatch()).max().orElse(0);
        return Math.addExact(retained, next);
    }
    private StationFuelDemandComposition() { }
}
