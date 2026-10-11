package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpeditionFundingPolicyTest {
    @Test void optionalCashNeverBlocksProvisionedTravelOrCreatesMoney() {
        assertEquals(FixedScalar.ZERO, ExpeditionFundingPolicy.allocation(FixedScalar.ZERO,
                FixedScalar.whole(20), FixedScalar.ZERO).orElseThrow());
        assertEquals(FixedScalar.whole(7), ExpeditionFundingPolicy.allocation(FixedScalar.ZERO,
                FixedScalar.whole(20), FixedScalar.whole(7)).orElseThrow());
        assertEquals(FixedScalar.whole(20), ExpeditionFundingPolicy.allocation(FixedScalar.ZERO,
                FixedScalar.whole(20), FixedScalar.whole(30)).orElseThrow());
    }
    @Test void requiredPurchasesMustBeFullyFundedBeforeContingency() {
        assertTrue(ExpeditionFundingPolicy.allocation(FixedScalar.whole(8),
                FixedScalar.whole(20), FixedScalar.whole(7)).isEmpty());
        assertEquals(FixedScalar.whole(8), ExpeditionFundingPolicy.allocation(FixedScalar.whole(8),
                FixedScalar.whole(20), FixedScalar.whole(8)).orElseThrow());
        assertEquals(FixedScalar.whole(28), ExpeditionFundingPolicy.allocation(FixedScalar.whole(8),
                FixedScalar.whole(20), FixedScalar.whole(40)).orElseThrow());
    }
    @Test void negativeFundingDeclarationsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> ExpeditionFundingPolicy.allocation(
                new FixedScalar(-1), FixedScalar.ZERO, FixedScalar.ZERO));
    }
}
