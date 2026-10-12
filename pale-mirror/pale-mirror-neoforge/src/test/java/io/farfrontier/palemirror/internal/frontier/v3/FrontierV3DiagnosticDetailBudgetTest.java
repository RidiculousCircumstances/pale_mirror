package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.JsonParser;
import io.farfrontier.palemirror.frontier.v3.api.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Optional diagnostics may not hide the operator's terminal request outcome. */
class FrontierV3DiagnosticDetailBudgetTest {
    @Test void optionalPressureCannotHideTheTerminalTimeControlReceipt() {
        var checkpoint = new CheckpointImage(new WorldId("frontier:pressure-budget"), new Revision(3),
                new SimInstant(12000), new byte[]{1}, List.of(), List.of());
        var receipt = new FrontierV3ServerLifecycle.FastForwardRequestOutcome(1,
                "RELATIVE", 12000, 12000L, 0L, 12000L, "COMPLETED", null);
        String value = FrontierV3PerformanceDiagnostic.render(checkpoint,
                new FrontierV3PerformanceMetrics().snapshot(), null, 0, null, null, null, null,
                List.of(receipt), null, "{\"depth\":0}", "[\"" + "ж".repeat(8192) + "\"]");
        var json = JsonParser.parseString(value.substring(FrontierV3DiagnosticJson.PREFIX.length())).getAsJsonObject();
        assertEquals("ok", json.get("status").getAsString());
        assertEquals("COMPLETED", json.getAsJsonArray("fastForwardRequests").get(0)
                .getAsJsonObject().get("status").getAsString());
        assertEquals("physicalJournal", json.getAsJsonArray("omittedSections").get(0).getAsString());
        assertFalse(json.has("physicalJournal"));
        assertTrue(value.substring(FrontierV3DiagnosticJson.PREFIX.length()).getBytes(StandardCharsets.UTF_8).length
                <= FrontierV3DiagnosticJson.MAX_BYTES);
    }
}
