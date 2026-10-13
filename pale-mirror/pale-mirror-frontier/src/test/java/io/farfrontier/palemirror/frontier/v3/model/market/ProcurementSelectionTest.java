package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProcurementSelectionTest {
    @Test void buyerChoosesDeliveredCostRatherThanCheapestGoodsOrInputOrder() {
        var request = List.of(new ProcurementSelection.Requirement("grain", 64, 100));
        var near = offer("near", 8, 64, 64, 50, 0);
        var far = offer("far", 7, 64, 256, 80, 0);
        var other = offer("other", 9, 64, 32, 40, 0);
        var planner = new ProcurementSelection();
        assertEquals(Optional.of(near.id()), planner.select(request, List.of(far, other, near)));
        assertEquals(Optional.of(near.id()), planner.select(request, List.of(near, other, far)));
    }
    @Test void shipmentOverheadAndOwnLabourMatterWhileLateOffersCannotDisplaceTimelySupply() {
        var planner = new ProcurementSelection();
        var request = List.of(new ProcurementSelection.Requirement("grain", 128, 100));
        var small = offer("small", 1, 1, 20, 20, 0);
        var bulk = offer("bulk", 2, 64, 20, 30, 0);
        var late = offer("late", 1, 128, 0, 101, 0);
        assertEquals(Optional.of(bulk.id()), planner.select(request, List.of(small, late, bulk)));
        var own = offer("own", 2, 64, 20, 30, 200);
        assertEquals(Optional.of(bulk.id()), planner.select(request, List.of(own, bulk)));
        assertEquals(Optional.of(late.id()), planner.select(request, List.of(late)), "late recovery remains a real option");
    }
    private static ProcurementSelection.Offer offer(String id, long unit, int quantity, long freight, long arrival, long labour) {
        return new ProcurementSelection.Offer(new SubjectId("offer:" + id),
                Map.of("grain", new ProcurementSelection.Line(quantity, FixedScalar.whole(unit))), FixedScalar.whole(freight), arrival, labour);
    }
}
