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
    private static final BlockPos HANDOFF = new BlockPos(-360, 64, -352);

    @Test
    void localDimensionAndChunkReadinessCannotAdvanceBeforeAFreshServerReceipt() {
        assertFalse(FrontierV3PilotDemandHandshake.mayAdvance(true, false, admitted(), REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
        assertFalse(FrontierV3PilotDemandHandshake.mayAdvance(false, true, admitted(), REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
        assertTrue(FrontierV3PilotDemandHandshake.mayAdvance(true, true, admitted(), REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
    }

    @Test
    void freshReceiptMustBindTheExactRequestDimensionCandidatePlayerAndObserver() {
        assertFalse(FrontierV3PilotDemandHandshake.mayAdvance(true, true, with("id", "other"), REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
        assertFalse(FrontierV3PilotDemandHandshake.mayAdvance(true, true, with("destinationDimension", "minecraft:overworld"), REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
        JsonObject wrongHandoff = admitted(); wrongHandoff.getAsJsonObject("candidateHandoff").addProperty("y", 65);
        assertFalse(FrontierV3PilotDemandHandshake.mayAdvance(true, true, wrongHandoff, REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
        assertFalse(FrontierV3PilotDemandHandshake.mayAdvance(true, true, with("playerId", "00000000-0000-0000-0000-000000000036"), REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
        JsonObject noObserver = admitted(); noObserver.getAsJsonArray("sceneDemandObserverIds").remove(0); noObserver.addProperty("requestedObserverPresent", false);
        assertFalse(FrontierV3PilotDemandHandshake.mayAdvance(true, true, noObserver, REQUEST, ASSAULT, DIMENSION, HANDOFF, PLAYER));
    }

    private static JsonObject with(String field, String value) { JsonObject receipt = admitted(); receipt.addProperty(field, value); return receipt; }

    private static JsonObject admitted() {
        return JsonParser.parseString("""
                {"schema":1,"kind":"demand_handshake","id":"settlement-assault-visit",
                "assault":"assault:development-settlement-assault","destinationDimension":"pale_mirror:frontier_graybox",
                "travelAnchor":{"x":-360,"y":65,"z":-352},"candidateHandoff":{"x":-360,"y":64,"z":-352},
                "playerId":"00000000-0000-0000-0000-000000000035","serverPlayerPosition":{"x":-360,"y":65,"z":-352},
                "destinationObserved":true,"destinationPlayerTicket":true,"destinationHolder":true,"providerIdentity":"projection-snapshot",
                "exactCandidateCount":1,"sceneDemandChunkLoaded":true,"sceneDemandObserverIds":["00000000-0000-0000-0000-000000000035"],
                "requestedObserverPresent":true,"reason":"ADMITTED"}""").getAsJsonObject();
    }
}
