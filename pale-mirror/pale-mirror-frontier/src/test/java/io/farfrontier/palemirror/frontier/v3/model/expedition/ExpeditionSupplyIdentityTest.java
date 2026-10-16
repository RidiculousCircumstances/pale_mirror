package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpeditionSupplyIdentityTest {
    @Test void maximumLengthParentsProduceStableDistinctBoundedAllocations() {
        var mission = new SubjectId("transport:" + "m".repeat(128));
        var resident = new SubjectId("resident:" + "r".repeat(128));
        var claim = ExpeditionSupplyPlanning.supplyClaim(mission, Long.MAX_VALUE, resident.value(), 0);
        assertEquals(claim, ExpeditionSupplyPlanning.supplyClaim(mission, Long.MAX_VALUE, resident.value(), 0));
        assertNotEquals(claim, ExpeditionSupplyPlanning.supplyClaim(mission, Long.MAX_VALUE, resident.value(), 1));
        assertNotEquals(claim, ExpeditionSupplyPlanning.supplyClaim(mission, 1, resident.value(), 0));
        assertNotEquals(claim, ExpeditionSupplyPlanning.supplyClaim(mission, Long.MAX_VALUE, "shared", 0));
        assertNotEquals(claim, ExpeditionSupplyPlanning.supplyClaim(new SubjectId("transport:other"), Long.MAX_VALUE, resident.value(), 0));
        assertNotEquals(claim, ExpeditionSupplyPlanning.supplyClaim(mission, Long.MAX_VALUE, "resident:other", 0));
        assertEquals(ExpeditionSupplyPlanning.supplyAccount(claim), ExpeditionSupplyPlanning.supplyAccount(claim));
        assertNotEquals(ExpeditionSupplyPlanning.supplyAccount(claim), ExpeditionSupplyPlanning.supplyAccount(
                ExpeditionSupplyPlanning.supplyClaim(mission, 1, resident.value(), 0)));
    }
}
