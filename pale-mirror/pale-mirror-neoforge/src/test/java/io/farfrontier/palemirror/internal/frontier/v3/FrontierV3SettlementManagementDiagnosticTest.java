package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.JsonParser;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3SettlementManagementDiagnosticTest {
    @Test void registeredDiagnosticExplainsTheSameFoodDecisionWithoutMutation() {
        var configuration = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:management-diagnostic"), 407L);
        var checkpoint = FrontierEngines.create(configuration).checkpoint();
        FrontierWorldState state = configuration.stateCodec().decode(checkpoint.canonicalState());
        assertTrue(FrontierV3DiagnosticView.SETTLEMENT_MANAGEMENT.requiresId());
        String result = FrontierV3DiagnosticJson.render("settlement_management", "settlement:1", checkpoint, state, Optional.empty());
        var json = JsonParser.parseString(result.substring(FrontierV3DiagnosticJson.PREFIX.length())).getAsJsonObject();
        assertEquals("SETTLEMENT_PRODUCE_BREAD", json.get("selected").getAsString());
        assertFalse(json.getAsJsonArray("offers").isEmpty());
        assertTrue(json.getAsJsonArray("commitments").isEmpty());
        assertArrayEquals(checkpoint.canonicalState(), configuration.stateCodec().encode(state));
    }
}
