package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3PilotDemandHandshakeTest {
    private static final String REQUEST = "settlement-assault-visit";
    private static final String ASSAULT = "assault:development-settlement-assault";
    private static final String DIMENSION = "pale_mirror:frontier_graybox";
    private static final String PLAYER = "00000000-0000-0000-0000-000000000035";
    private static final FrontierV3PilotDemandReceiptTransition.Correlation CORRELATION = new FrontierV3PilotDemandReceiptTransition.Correlation(
            "00000000-0000-0000-0000-000000000031", 1, "00000000-0000-0000-0000-000000000032");
    /** Candidate handoff is Northwatch's anchor, not the nearby travel coordinate. */
    private static final BlockPos HANDOFF = new BlockPos(-360, 64, -340);

    @Test
    void localDimensionAndChunkReadinessCannotAdvanceBeforeAFreshServerReceipt() {
        FrontierV3PilotVisitIngress dimensionOnly = ingress(DIMENSION, false);
        FrontierV3PilotVisitIngress allGreen = ingress(DIMENSION, true);
        assertFalse(FrontierV3PilotDemandHandshake.mayAdvance(allGreen, true, admitted(), REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
        assertTrue(allGreen.requestServerReceipt(CORRELATION));
        assertFalse(FrontierV3PilotDemandHandshake.mayAdvance(allGreen, false, admitted(), REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
        assertTrue(dimensionOnly.requestServerReceipt(CORRELATION));
        assertFalse(FrontierV3PilotDemandHandshake.mayAdvance(dimensionOnly, true, admitted(), REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
        assertTrue(FrontierV3PilotDemandHandshake.mayAdvance(allGreen, true, admitted(), REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
    }

    @Test
    void freshReceiptMustBindTheExactRequestDimensionCandidatePlayerAndObserver() {
        FrontierV3PilotVisitIngress ingress = ingress(DIMENSION, true); ingress.requestServerReceipt(CORRELATION);
        assertFalse(FrontierV3PilotDemandHandshake.mayAdvance(ingress, true, with("id", "other"), REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
        assertFalse(FrontierV3PilotDemandHandshake.mayAdvance(ingress, true, with("destinationDimension", "minecraft:overworld"), REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
        JsonObject wrongHandoff = admitted(); wrongHandoff.getAsJsonObject("candidateHandoff").addProperty("y", 65);
        assertFalse(FrontierV3PilotDemandHandshake.mayAdvance(ingress, true, wrongHandoff, REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
        assertFalse(FrontierV3PilotDemandHandshake.mayAdvance(ingress, true, with("playerId", "00000000-0000-0000-0000-000000000036"), REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
        assertFalse(FrontierV3PilotDemandHandshake.mayAdvance(ingress, true, with("pilotRunId", "00000000-0000-0000-0000-000000000033"), REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
        assertFalse(FrontierV3PilotDemandHandshake.mayAdvance(ingress, true, with("pilotActionAttempt", "00000000-0000-0000-0000-000000000033"), REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
        JsonObject noObserver = admitted(); noObserver.getAsJsonArray("sceneDemandObserverIds").remove(0); noObserver.addProperty("requestedObserverPresent", false);
        assertFalse(FrontierV3PilotDemandHandshake.mayAdvance(ingress, true, noObserver, REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
    }

    private static FrontierV3PilotVisitIngress ingress(String dimension, boolean chunk) {
        FrontierV3PilotVisitIngress value = new FrontierV3PilotVisitIngress(DIMENSION);
        value.observe(dimension, chunk, new BlockPos(-360, 65, -352));
        return value;
    }

    private static JsonObject with(String field, String value) { JsonObject receipt = admitted(); receipt.addProperty(field, value); return receipt; }

    private static JsonObject admitted() {
        return JsonParser.parseString("""
                {"schema":1,"kind":"demand_handshake","id":"settlement-assault-visit",
                "assault":"assault:development-settlement-assault","destinationDimension":"pale_mirror:frontier_graybox",
                "travelAnchor":{"x":-360,"y":65,"z":-352},"candidateHandoff":{"x":-360,"y":64,"z":-340},
                "pilotRunId":"00000000-0000-0000-0000-000000000031","pilotActionStep":1,"pilotActionAttempt":"00000000-0000-0000-0000-000000000032",
                "playerId":"00000000-0000-0000-0000-000000000035","serverPlayerPosition":{"x":-360,"y":65,"z":-352},
                "destinationObserved":true,"destinationPlayerTicket":true,"destinationHolder":true,"providerIdentity":"projection-snapshot",
                "exactCandidateCount":1,"sceneDemandChunkLoaded":true,"sceneDemandObserverIds":["00000000-0000-0000-0000-000000000035"],
                "requestedObserverPresent":true,"reason":"ADMITTED"}""").getAsJsonObject();
    }
}
