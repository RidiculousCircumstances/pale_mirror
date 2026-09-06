package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonObject;

/** Pure timeout vocabulary shared by the visible pilot and its fast parser regression. */
final class FrontierV3TestPilotTimeouts {
    private FrontierV3TestPilotTimeouts() { }

    /** Dynamic visit anchors inherit visit's bounded travel timeout when no action timeout exists. */
    static long resolutionTimeoutMillis(JsonObject action) {
        return action.has("timeoutMs") ? action.get("timeoutMs").getAsLong() : 120_000L;
    }
}
