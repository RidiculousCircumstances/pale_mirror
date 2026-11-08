package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Retained purpose of the journey, independent of the portion's source/custody. */
public sealed interface MealDestination permits MealDestination.Here, MealDestination.DiningArea, MealDestination.Seat {
    record Seat(io.farfrontier.palemirror.frontier.v3.model.seating.SeatPlace.Id id) implements MealDestination {
        public Seat { Objects.requireNonNull(id); }
    }
    record Here(SurfaceAnchor station) implements MealDestination {
        public Here { Objects.requireNonNull(station); }
    }
    record DiningArea(SubjectId facilityId, String zoneId) implements MealDestination {
        public DiningArea {
            Objects.requireNonNull(facilityId); Objects.requireNonNull(zoneId);
            if (!zoneId.matches("[a-z0-9_./-]{1,96}")) throw new IllegalArgumentException("invalid dining area identity");
        }
    }
}
