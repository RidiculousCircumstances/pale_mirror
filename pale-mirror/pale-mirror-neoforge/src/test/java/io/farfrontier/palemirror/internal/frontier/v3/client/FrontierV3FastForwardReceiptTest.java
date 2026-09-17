package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierV3FastForwardReceiptTest {
    @Test
    void selectsTheNewRelativeTerminalReceiptAndRetainsItsPhysicalStopBoundary() {
        JsonObject performance = JsonParser.parseString("""
                {"fastForwardRequests":[
                  {"requestId":1,"kind":"RELATIVE","requestedTicks":10000,"targetInstant":15310,"admittedCheckpointInstant":5310,"reachedCheckpointInstant":5342,"status":"REJECTED","reason":"resource-site-projection:site:7-wheat-field"}
                ]}
                """).getAsJsonObject();

        JsonObject receipt = FrontierV3FastForwardReceipt.terminalRelative(performance, null, 10_000);

        assertEquals(1L, receipt.get("requestId").getAsLong());
        assertEquals("REJECTED", receipt.get("status").getAsString());
        assertEquals(5_342L, receipt.get("reachedCheckpointInstant").getAsLong());
    }

    @Test
    void rejectsATerminalReceiptThatErasesItsStopBoundary() {
        JsonObject performance = JsonParser.parseString("""
                {"fastForwardRequests":[
                  {"requestId":1,"kind":"RELATIVE","requestedTicks":10000,"targetInstant":15310,"admittedCheckpointInstant":5310,"reachedCheckpointInstant":null,"status":"REJECTED","reason":"resource-site-projection:site:7-wheat-field"}
                ]}
                """).getAsJsonObject();

        assertThrows(IllegalStateException.class, () -> FrontierV3FastForwardReceipt.terminalRelative(performance, null, 10_000));
    }
}
