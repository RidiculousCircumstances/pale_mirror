package io.farfrontier.palemirror.frontier.v3.model;

/** Production owns the exact retained job/station/workplace relationship. The shared
 * facility evaluator receives only physical requirements and never inspects a job. */
public final class ProductionFacilityAvailability {
    private ProductionFacilityAvailability() { }
    public static boolean mayUseJob(FrontierWorldState state, ProductionJob job) {
        return job.stationWork().map(work -> mayUseStation(state, ProductionStationDirectory
                .require(state.inventory(), work.stationId()).productionStation().orElseThrow()))
                .orElseGet(() -> FacilityFunctionAvailability.mayUse(state, job.facilityId()));
    }
    public static boolean mayUseStation(FrontierWorldState state, ProductionStationSpec station) {
        var building = FrontierWorldStateSupport.structureById(state.bootstrap(), station.facilityId());
        var work = building.definition().workplaces().get(station.workplaceId().value());
        if (work == null || !building.placement().support(work.worker()).equals(station.workerStation()))
            throw new IllegalArgumentException("station lost its exact declared function workplace");
        return FacilityFunctionAvailability.supportMayExist(state, building.id(), station.workerStation().support());
    }
}
