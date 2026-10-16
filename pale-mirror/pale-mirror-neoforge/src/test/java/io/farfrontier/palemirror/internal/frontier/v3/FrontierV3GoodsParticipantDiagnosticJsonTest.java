package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3GoodsParticipantDiagnosticJsonTest {
    @Test void reserveReportSurvivesLargeOptionalTradeDetailWithoutClaimingItIsEmpty() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:reserve-diagnostic"),
                20260918065L, FrontierRulesets.marketQuarryGraybox()));
        var checkpoint = new CheckpointImage(state.bootstrap().worldId(), new Revision(0), new SimInstant(0), new byte[]{1}, List.of(), List.of());
        var participant = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:6"));
        String rendered = FrontierV3GoodsParticipantDiagnosticJson.render(checkpoint, state, participant);
        var parsed = JsonParser.parseString(rendered).getAsJsonObject();
        assertEquals("ok", parsed.get("status").getAsString());
        assertEquals(4, parsed.getAsJsonArray("reserves").size());
        assertTrue(rendered.getBytes(StandardCharsets.UTF_8).length <= FrontierV3DiagnosticJson.MAX_BYTES);
        var bounded = FrontierV3DiagnosticDetailBudget.finish("{\"status\":\"ok\",\"reserves\":" + parsed.get("reserves"),
                List.of(new FrontierV3DiagnosticDetailBudget.Section("orders", "[\"" + "x".repeat(9000) + "\"]")));
        var result = JsonParser.parseString(bounded).getAsJsonObject();
        assertFalse(result.has("orders")); assertEquals("orders", result.getAsJsonArray("omittedSections").get(0).getAsString());
        assertEquals(parsed.get("reserves"), result.get("reserves"));
    }
}
