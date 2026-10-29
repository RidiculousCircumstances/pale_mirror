package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.stream.Stream;

/** Individual order commitments before machine admission and after transformation. */
public final class StationWorkItemDeparture {
    public static Stream<SubjectId> reserved(FrontierWorldState state) {
        return state.productionJobs().values().stream().flatMap(job -> reserved(state, job).stream());
    }
    static java.util.Set<SubjectId> reserved(FrontierWorldState state, ProductionJob job) {
        if (job.stationWork().isEmpty() || job.stationWork().orElseThrow().phase() == StationWorkState.Phase.DELIVERED) return java.util.Set.of();
        var work = job.stationWork().orElseThrow();
        if (work.maintenanceTarget().isPresent()) return java.util.Set.of(work.maintenanceTarget().orElseThrow().itemId());
        return StationWorkCargo.output(job) && StationWorkProduct.individual(state, job) ? java.util.Set.of(job.outputItemId()) : java.util.Set.of();
    }
    public static boolean committed(FrontierWorldState state, SubjectId item) {
        return !StationExactItemDeparture.committed(state, item) && job(state, item) != null;
    }
    private static ProductionJob job(FrontierWorldState state, SubjectId item) {
        return state.productionJobs().values().stream().filter(value -> value.stationWork().isPresent())
                .filter(value -> value.stationWork().orElseThrow().phase() != StationWorkState.Phase.DELIVERED)
                .filter(value -> value.stationWork().orElseThrow().maintenanceTarget().filter(target -> target.itemId().equals(item)).isPresent()
                        || StationWorkCargo.output(value) && StationWorkProduct.individual(state, value) && value.outputItemId().equals(item))
                .reduce((a, b) -> { throw new IllegalArgumentException("individual has competing station orders"); }).orElse(null);
    }
    public static FrontierWorldState observe(FrontierWorldState state, SubjectId item, InventoryCustody source, ExactInventory inventory) {
        var job = job(state, item);
        if (job == null || !committed(state, item) || !state.inventory().items().get(item).custody().equals(source))
            throw new IllegalArgumentException("individual departure lacks its exact order/source");
        var work = job.stationWork().orElseThrow();
        var transaction = FungibleForfeitureSettlement.from(state, inventory);
        if (work.phase() == StationWorkState.Phase.STATION_UNLOAD) {
            var abandoned = ProductionStationProcessing.abandon(state, StationWorkOperation.require(state, job));
            transaction = FungibleForfeitureSettlement.from(state,
                    inventory.withFungibleResources(abandoned.inventory().fungibleResources())).withStations(abandoned.machines());
        }
        return state.withChanges(StationWorkCancellation.cancelJob(state, job, transaction).contribution());
    }
    private StationWorkItemDeparture() { }
}
