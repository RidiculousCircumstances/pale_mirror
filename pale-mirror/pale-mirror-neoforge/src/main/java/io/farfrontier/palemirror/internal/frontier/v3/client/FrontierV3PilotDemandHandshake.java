package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

/** Immutable client-side receipt predicate for the pilot-only server acknowledgement. */
final class FrontierV3PilotDemandHandshake {
    private FrontierV3PilotDemandHandshake() { }

    static boolean admitted(JsonObject value, String request, String assault, String dimension, BlockPos target) {
        if (!"demand_handshake".equals(string(value, "kind")) || !request.equals(string(value, "id")) || !assault.equals(string(value, "assault"))
                || !dimension.equals(string(value, "destinationDimension")) || !"ADMITTED".equals(string(value, "reason"))
                || !value.has("anchor") || !value.get("anchor").isJsonObject()) return false;
        JsonObject anchor = value.getAsJsonObject("anchor");
        return anchor.has("x") && anchor.has("y") && anchor.has("z") && anchor.get("x").getAsInt() == target.getX()
                && anchor.get("y").getAsInt() == target.getY() && anchor.get("z").getAsInt() == target.getZ()
                && value.has("playerId") && !string(value, "playerId").isBlank() && value.has("providerIdentity") && !value.get("providerIdentity").isJsonNull()
                && value.has("exactCandidateCount") && value.get("exactCandidateCount").getAsInt() > 0
                && value.has("destinationPlayerTicket") && value.get("destinationPlayerTicket").getAsBoolean()
                && value.has("destinationHolder") && value.get("destinationHolder").getAsBoolean()
                && value.has("sceneDemandChunkLoaded") && value.get("sceneDemandChunkLoaded").getAsBoolean()
                && value.has("requestedObserverPresent") && value.get("requestedObserverPresent").getAsBoolean();
    }

    private static String string(JsonObject value, String field) {
        return value.has(field) && value.get(field).isJsonPrimitive() ? value.get(field).getAsString() : "";
    }
}
