package io.farfrontier.palemirror.frontier.v3.model;
import java.util.*;

/** Closed demand-owner composition. Logistics only transports the requested fuel. */
public final class StationFuelDemandComposition {
    @FunctionalInterface private interface Demand { long heat(FrontierWorldState state, ProductionStationSpec station, long tick); }
    private static final Map<ProductionStationSpec.Capability, Demand> OWNERS = Map.of(
            ProductionStationSpec.Capability.BAKING, StationFuelDemandComposition::baking,
            ProductionStationSpec.Capability.SMELTING, StationFuelDemandComposition::automatic,
            ProductionStationSpec.Capability.FABRICATION, StationFuelDemandComposition::equipment);
    public static long heat(FrontierWorldState state, ProductionStationSpec station, long tick) {
        return Objects.requireNonNull(OWNERS.get(station.capability()), "unregistered station fuel policy").heat(state, station, tick);
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
