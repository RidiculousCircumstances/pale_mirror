package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;

import java.util.List;
import java.util.Objects;

/** One caller-owned destination; neither a path nor permission to perform work on arrival. */
public record MovementOrder(SubjectId ownerId, SubjectId actorId, long goalOrdinal, long goalRevision,
                            List<SurfaceAnchor> legalStations, TraversalCapability capability,
                            ArrivalPolicy arrivalPolicy) {
    public static final int MAX_LEGAL_STATIONS = 8;
    public enum ArrivalPolicy { EXACT_STATION, ANY_DECLARED_STATION }

    public MovementOrder {
        Objects.requireNonNull(ownerId, "movement owner");
        Objects.requireNonNull(actorId, "moving actor");
        legalStations = List.copyOf(Objects.requireNonNull(legalStations, "movement legal stations"));
        Objects.requireNonNull(capability, "movement capability");
        Objects.requireNonNull(arrivalPolicy, "movement arrival policy");
        if (goalOrdinal < 0 || goalRevision < 1 || legalStations.isEmpty() || legalStations.size() > MAX_LEGAL_STATIONS
                || legalStations.stream().anyMatch(Objects::isNull)
                || legalStations.stream().distinct().count() != legalStations.size()
                || arrivalPolicy == ArrivalPolicy.EXACT_STATION && legalStations.size() != 1)
            throw new IllegalArgumentException("movement order needs a bounded, distinct and versioned destination");
    }

    public boolean arrivedAt(SurfaceAnchor observed) {
        return legalStations.contains(Objects.requireNonNull(observed, "observed movement station"));
    }
}
