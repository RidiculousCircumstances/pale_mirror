package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Stream;

/** Production's reservation projection. Execution/cargo retention is not station occupancy. */
public final class ProductionFacilityReservations {
    private ProductionFacilityReservations() { }

    public static Stream<SubjectId> committed(Map<SubjectId, ProductionJob> jobs) {
        return jobs.values().stream().filter(ProductionJob::reservesFacility).map(ProductionJob::facilityId);
    }

    public static boolean available(Map<SubjectId, ProductionJob> jobs, SubjectId facilityId) {
        return committed(jobs).noneMatch(facilityId::equals);
    }

    /** Same closure applies to hydration and ordinary transitions; no second reservation store. */
    public static void validate(Map<SubjectId, ProductionJob> jobs) {
        var facilities = new HashSet<SubjectId>();
        committed(jobs).forEach(facility -> {
            if (!facilities.add(facility))
                throw new IllegalArgumentException("production station has multiple reservation owners");
        });
    }
}
