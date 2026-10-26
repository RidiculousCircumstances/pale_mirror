package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.economy.EconomicPlan;
import io.farfrontier.palemirror.frontier.v3.model.market.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EconomicCashCycleCodecTest {
    @Test void operatingEnvelopeAndExplicitRecoveryPurposeRoundTripAndRejectUnknownOrUnfundedScope() throws IOException {
        var amounts = Map.of("fuel", 10L);
        var plan = new EconomicPlan(1000, amounts, amounts, amounts, 0, 5, 2, List.of(), List.of(), Set.of(),
                amounts, Set.of("fuel"), amounts, Map.of("fuel", 15L));
        var bytes = new ByteArrayOutputStream();
        EconomicPlanCodec.write(new DataOutputStream(bytes), plan);
        assertEquals(plan, EconomicPlanCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        var quote = new MarketPricePolicy.Quote(new FixedScalar(2), new FixedScalar(3), new FixedScalar(2), 10, 83, Optional.empty(),
                new StockSalePlan(StockSalePlan.Kind.OPERATING_RECOVERY, 83, 100));
        bytes.reset(); ParticipantPriceCodec.write(new DataOutputStream(bytes), Map.of("fuel", quote));
        var encoded = bytes.toByteArray();
        assertEquals(Map.of("fuel", quote), ParticipantPriceCodec.read(new DataInputStream(new ByteArrayInputStream(encoded))));
        encoded[encoded.length - 17] = (byte) 99;
        assertThrows(IllegalArgumentException.class, () -> ParticipantPriceCodec.read(new DataInputStream(new ByteArrayInputStream(encoded))));
        assertThrows(IllegalArgumentException.class, () -> new MarketPricePolicy.Quote(new FixedScalar(1), new FixedScalar(2),
                new FixedScalar(1), 0, 83, Optional.empty(), new StockSalePlan(StockSalePlan.Kind.OPERATING_RECOVERY, 83, 100)));
    }
}
