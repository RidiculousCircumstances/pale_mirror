package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.market.MarketDemandHistory;
import java.io.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarketDemandHistoryCodecTest {
    @Test void rollingWindowSurvivesRecoveryAndTruncatedOrUnboundedSamplesAreRejected() throws Exception {
        var buyer = new SubjectId("settlement:2"); var history = MarketDemandHistory.empty();
        for (int tick = 0; tick <= 256; tick += 4) history = history.observe(buyer, tick, 128,
                tick == 128 ? Map.of() : Map.of("minecraft:wheat", new MarketDemandHistory.Quote(64, 500)));
        var bytes = new ByteArrayOutputStream(); MarketDemandHistoryCodec.write(new DataOutputStream(bytes), history);
        assertEquals(history, MarketDemandHistoryCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        var truncated = java.util.Arrays.copyOf(bytes.toByteArray(), bytes.size() - 1);
        assertThrows(EOFException.class, () -> MarketDemandHistoryCodec.read(new DataInputStream(new ByteArrayInputStream(truncated))));
        bytes.reset(); var out = new DataOutputStream(bytes); out.writeInt(1); out.writeUTF(buyer.value()); out.writeUTF("minecraft:wheat");
        out.writeLong(0); out.writeLong(128); out.writeLong(4); out.writeInt(999);
        assertThrows(IllegalArgumentException.class, () -> MarketDemandHistoryCodec.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }
}
