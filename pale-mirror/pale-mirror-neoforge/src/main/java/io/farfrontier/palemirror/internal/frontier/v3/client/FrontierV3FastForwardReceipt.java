package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Parses one bounded server-owned fast-forward result for the visible pilot. */
final class FrontierV3FastForwardReceipt {
    private FrontierV3FastForwardReceipt() { }

    static JsonObject terminalRelative(JsonObject performance, JsonObject baseline, int ticks) {
        if (!performance.has("fastForwardRequests") || !performance.get("fastForwardRequests").isJsonArray()) return null;
        long baselineId = lastRequestId(baseline);
        JsonArray requests = performance.getAsJsonArray("fastForwardRequests");
        for (int index = requests.size() - 1; index >= 0; index--) {
            JsonObject receipt = requests.get(index).getAsJsonObject();
            if (receipt.get("requestId").getAsLong() <= baselineId || !"RELATIVE".equals(receipt.get("kind").getAsString())) continue;
            if (receipt.get("requestedTicks").getAsInt() != ticks || !hasNumber(receipt, "targetInstant")
                    || !hasNumber(receipt, "admittedCheckpointInstant")) {
                throw new IllegalStateException("relative canonical advance receipt lost its admitted request identity: " + receipt);
            }
            if ("QUEUED".equals(receipt.get("status").getAsString())) return null;
            if (!hasNumber(receipt, "reachedCheckpointInstant")) {
                throw new IllegalStateException("relative canonical advance terminal receipt omitted its stop boundary: " + receipt);
            }
            return receipt;
        }
        return null;
    }

    private static long lastRequestId(JsonObject performance) {
        if (performance == null || !performance.has("fastForwardRequests") || !performance.get("fastForwardRequests").isJsonArray()) return 0L;
        JsonArray requests = performance.getAsJsonArray("fastForwardRequests");
        return requests.isEmpty() ? 0L : requests.get(requests.size() - 1).getAsJsonObject().get("requestId").getAsLong();
    }

    private static boolean hasNumber(JsonObject value, String field) {
        return value.has(field) && !value.get(field).isJsonNull() && value.get(field).isJsonPrimitive()
                && value.get(field).getAsJsonPrimitive().isNumber();
    }
}
