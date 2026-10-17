package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProcurementSelectionTest {
    @Test void emergencySelectionPrefersUsefulRecoveryRateButKeepsTheOnlySmallDelivery() {
        var planner = new ProcurementSelection();
        var need = List.of(new ProcurementSelection.Requirement("grain", 64, 0));
        var tiny = offer("tiny", 2, 1, 0, 100, 0);
        var useful = offer("useful", 2, 32, 0, 200, 0);
        assertEquals(Optional.of(useful.id()), planner.select(need, List.of(tiny, useful)));
        assertEquals(Optional.of(tiny.id()), planner.select(need, List.of(tiny)));
        var single = List.of(new ProcurementSelection.Requirement("grain", 1, 0));
        assertEquals(Optional.of(tiny.id()), planner.select(single, List.of(tiny, useful)),
                "unneeded surplus cannot inflate recovery value");
    }
    @Test void ownLabourUsesTheSameShadowPriceAsSourcingWithoutCreatingAPayment() {
        var need = List.of(new ProcurementSelection.Requirement("grain", 10, 1000));
        var collection = offer("collect", 1, 10, 0, 100, 100);
        var delivered = offer("delivered", 2, 10, 0, 100, 0);
        assertEquals(Optional.of(delivered.id()), new ProcurementSelection().select(need,
                List.of(collection, delivered), FixedScalar.whole(1).raw()));
        assertEquals(FixedScalar.ZERO, collection.transportCost());
    }
    @Test void fundedRestartWinsAnEqualDeadlineWithoutDependingOnCommoditySpelling() {
        var needs = List.of(new ProcurementSelection.Requirement("a-food", 60, 0),
                new ProcurementSelection.Requirement("z-fuel", 1, 0, true));
        var food = new ProcurementSelection.Offer(new SubjectId("offer:food"),
                Map.of("a-food", new ProcurementSelection.Line(4, FixedScalar.whole(3))), FixedScalar.ZERO, 100, 0);
        var fuel = new ProcurementSelection.Offer(new SubjectId("offer:fuel"),
                Map.of("z-fuel", new ProcurementSelection.Line(1, FixedScalar.whole(8))), FixedScalar.ZERO, 100, 0);
        assertEquals(Optional.of(fuel.id()), new ProcurementSelection().select(needs, List.of(food, fuel)));
    }
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
