package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

/** Immutable client-side receipt predicate for the pilot-only server acknowledgement. */
final class FrontierV3PilotDemandHandshake {
    private FrontierV3PilotDemandHandshake() { }

    /** The actual visit transition: both independently retained client facts need a fresh exact server receipt. */
    static boolean mayAdvance(FrontierV3PilotVisitIngress ingress, boolean freshReceipt, JsonObject value, String request, String assault,
                              String dimension, BlockPos expectedHandoff, String expectedPlayerId) {
        return ingress.serverReceiptRequested() && ingress.targetDimensionSeen() && ingress.targetChunkSeen() && freshReceipt
                && admitted(value, request, assault, dimension, expectedHandoff, expectedPlayerId);
    }

    static boolean admitted(JsonObject value, String request, String assault, String dimension, BlockPos expectedHandoff, String expectedPlayerId) {
        if (!"demand_handshake".equals(string(value, "kind")) || !request.equals(string(value, "id")) || !assault.equals(string(value, "assault"))
                || !dimension.equals(string(value, "destinationDimension")) || !"ADMITTED".equals(string(value, "reason"))
                || !value.has("candidateHandoff") || !value.get("candidateHandoff").isJsonObject()) return false;
        return point(value.getAsJsonObject("candidateHandoff"), expectedHandoff) && expectedPlayerId.equals(string(value, "playerId"))
                && value.has("serverPlayerPosition") && value.get("serverPlayerPosition").isJsonObject()
                && value.has("destinationObserved") && value.get("destinationObserved").getAsBoolean()
                && value.has("providerIdentity") && !value.get("providerIdentity").isJsonNull()
                && value.has("exactCandidateCount") && value.get("exactCandidateCount").getAsInt() == 1
                && value.has("destinationPlayerTicket") && value.get("destinationPlayerTicket").getAsBoolean()
                && value.has("destinationHolder") && value.get("destinationHolder").getAsBoolean()
                && value.has("sceneDemandChunkLoaded") && value.get("sceneDemandChunkLoaded").getAsBoolean()
                && value.has("requestedObserverPresent") && value.get("requestedObserverPresent").getAsBoolean()
                && value.has("sceneDemandObserverIds") && value.getAsJsonArray("sceneDemandObserverIds").asList().stream()
                .anyMatch(observer -> expectedPlayerId.equals(observer.getAsString()));
    }

    private static boolean point(JsonObject value, BlockPos expected) {
        return value.has("x") && value.has("y") && value.has("z") && value.get("x").getAsInt() == expected.getX()
                && value.get("y").getAsInt() == expected.getY() && value.get("z").getAsInt() == expected.getZ();
    }

    private static String string(JsonObject value, String field) {
        return value.has(field) && value.get(field).isJsonPrimitive() ? value.get(field).getAsString() : "";
    }
}
