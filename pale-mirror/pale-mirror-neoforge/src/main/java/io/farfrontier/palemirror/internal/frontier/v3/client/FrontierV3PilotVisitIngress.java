package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

import java.util.Objects;

/**
 * Action-local client ingress observations.  These are evidence of the visible client only;
 * they neither request nor authorize the server-side demand receipt.
 */
final class FrontierV3PilotVisitIngress {
    private final String targetDimension;
    private boolean serverReceiptRequested;
    private FrontierV3PilotDemandReceiptTransition.Correlation correlation;
    private boolean targetDimensionSeen;
    private boolean targetChunkSeen;
    private String finalClientDimension = "";
    private BlockPos finalClientPosition;

    FrontierV3PilotVisitIngress(String targetDimension) {
        this.targetDimension = Objects.requireNonNull(targetDimension, "target dimension");
    }

    /** Claims the one correlated server receipt request without consulting local visibility. */
    boolean requestServerReceipt(FrontierV3PilotDemandReceiptTransition.Correlation requestedCorrelation) {
        Objects.requireNonNull(requestedCorrelation, "receipt correlation");
        if (serverReceiptRequested) return false;
        serverReceiptRequested = true; correlation = requestedCorrelation;
        return true;
    }

    boolean serverReceiptRequested() { return serverReceiptRequested; }
    FrontierV3PilotDemandReceiptTransition.Correlation correlation() { return Objects.requireNonNull(correlation, "receipt correlation"); }

    void observe(String clientDimension, boolean targetChunkPresent, BlockPos clientPosition) {
        finalClientDimension = Objects.requireNonNull(clientDimension, "client dimension");
        finalClientPosition = Objects.requireNonNull(clientPosition, "client position");
        if (!targetDimension.equals(clientDimension)) return;
        targetDimensionSeen = true;
        if (targetChunkPresent) targetChunkSeen = true;
    }

    boolean targetDimensionSeen() { return targetDimensionSeen; }
    boolean targetChunkSeen() { return targetChunkSeen; }
    static String timeoutDetail(String dimension, FrontierV3PilotVisitTarget target, FrontierV3PilotVisitIngress ingress) {
        return "timed out visiting naturally loaded " + dimension + " at anchor=" + target.teleportAnchor()
                + " expectedClientFeet=" + target.expectedClientFeet() + "; targetDimensionSeen=" + ingress.targetDimensionSeen()
                + " targetChunkSeen=" + ingress.targetChunkSeen() + " finalClientDimension=" + ingress.finalClientDimension()
                + " finalClientPosition=" + ingress.finalClientPosition();
    }
    /** A loaded target chunk is not a player arrival; ordinary visit evidence needs both. */
    boolean arrivedAt(BlockPos target) {
        return targetDimensionSeen && targetChunkSeen && Objects.requireNonNull(target, "target").equals(finalClientPosition);
    }
    String finalClientDimension() { return finalClientDimension; }
    BlockPos finalClientPosition() { return finalClientPosition; }

    JsonObject receipt(String request) {
        JsonObject value = new JsonObject(); value.addProperty("schema", 1); value.addProperty("kind", "visit_ingress"); value.addProperty("id", request);
        value.addProperty("pilotRunId", correlation().runId()); value.addProperty("pilotActionStep", correlation().actionStep()); value.addProperty("pilotActionAttempt", correlation().actionAttempt());
        value.addProperty("targetDimensionSeen", targetDimensionSeen); value.addProperty("targetChunkSeen", targetChunkSeen); value.addProperty("finalClientDimension", finalClientDimension);
        JsonObject point = new JsonObject(); point.addProperty("x", finalClientPosition.getX()); point.addProperty("y", finalClientPosition.getY()); point.addProperty("z", finalClientPosition.getZ());
        value.add("finalClientPosition", point); return value;
    }

}
