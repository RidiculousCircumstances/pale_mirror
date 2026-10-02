package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Read-only admission contract. The execution/resource owner persists it, never a second reservation store. */
public sealed interface WorkReservationClaim permits WorkReservationClaim.Cell,
        WorkReservationClaim.StationPlace, WorkReservationClaim.ContainerCapacity,
        WorkReservationClaim.ExactResource, WorkReservationClaim.LotQuantity {
    record Cell(ResourceFieldWorkTarget target) implements WorkReservationClaim {
        public Cell { Objects.requireNonNull(target); }
    }
    record StationPlace(SubjectId stationId) implements WorkReservationClaim {
        public StationPlace { Objects.requireNonNull(stationId); }
    }
    record ContainerCapacity(InventoryCustody.ContainerSlot slot) implements WorkReservationClaim {
        public ContainerCapacity { Objects.requireNonNull(slot); }
    }
    record ExactResource(SubjectId itemId, int quantity) implements WorkReservationClaim {
        public ExactResource { Objects.requireNonNull(itemId); requireQuantity(quantity); }
    }
    record LotQuantity(SubjectId accountId, SubjectId lotId, int quantity) implements WorkReservationClaim {
        public LotQuantity { Objects.requireNonNull(accountId); Objects.requireNonNull(lotId); requireQuantity(quantity); }
    }
    private static void requireQuantity(int quantity) {
        if (quantity < 1) throw new IllegalArgumentException("work resource claim must be positive");
    }
}
