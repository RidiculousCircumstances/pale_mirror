package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import static io.farfrontier.palemirror.internal.frontier.v3.client.FrontierV3TestPilotClient.*;

/** Client-side receipts for bounded canonical time-control commands. */
final class FrontierV3PilotFastForwardActions {
    private FrontierV3PilotFastForwardActions() { }

    static void waitForFastForward(Minecraft minecraft, JsonObject action) {
        if (!fastForwardSent) {
            fastForwardBaseline = diagnostics.get(new DiagnosticIdentity("performance", "")); minecraft.player.connection.sendCommand("pale_mirror v3 advance " + action.get("ticks").getAsInt()); fastForwardSent = true;
        }
        ObservedDiagnostic observed = diagnostics.get(new DiagnosticIdentity("performance", ""));
        if (fresh(observed)) {
            JsonObject receipt = FrontierV3FastForwardReceipt.terminalRelative(observed.value(), fastForwardBaseline == null ? null : fastForwardBaseline.value(), action.get("ticks").getAsInt());
            if (receipt != null) {
                String expected = action.has("expectTerminalStatus") ? action.get("expectTerminalStatus").getAsString() : "COMPLETED"; String actual = receipt.get("status").getAsString();
                if (!expected.equals(actual)) {
                    throw new IllegalStateException("relative canonical advance terminal status=" + actual + " receipt=" + receipt);
                }
                if (action.has("expectReasonContains") && (!receipt.has("reason") || receipt.get("reason").isJsonNull()
                        || !receipt.get("reason").getAsString().contains(action.get("expectReasonContains").getAsString()))) {
                    throw new IllegalStateException("relative canonical advance terminal receipt omitted required owner reason: " + receipt);
                }
                advance("fast_forward"); return;
            }
        }
        if (System.nanoTime() - diagnosticWaitRequestNanos >= 1_000_000_000L) {
            minecraft.player.connection.sendCommand("pale_mirror v3 inspect performance");
            diagnosticWaitRequestNanos = System.nanoTime();
        }
        // Canonical acceleration deliberately changes the client-visible game-tick rate.  This
        // deadline protects the carrier process, so it must remain wall-clock based rather than
        // silently shrink whenever the scenario raises /tick rate.
        if (elapsedWallMillis() >= action.get("timeoutMs").getAsLong()) throw new IllegalStateException("timed out waiting for bounded canonical fast-forward completion");
    }

    /** Waits for one server-held absolute checkpoint; client packet latency cannot add canonical time. */
    static void waitForAbsoluteFastForward(Minecraft minecraft, JsonObject action) {
        long target = action.get("targetInstant").getAsLong();
        if (!fastForwardSent) {
            fastForwardBaseline = diagnostics.get(new DiagnosticIdentity("performance", ""));
            minecraft.player.connection.sendCommand("pale_mirror v3 advance_to " + target);
            fastForwardSent = true;
        }
        ObservedDiagnostic observed = diagnostics.get(new DiagnosticIdentity("performance", ""));
        if (fresh(observed) && observed.value().has("fastForwardTargetOutcome")
                && !observed.value().get("fastForwardTargetOutcome").isJsonNull()) {
            JsonObject outcome = observed.value().getAsJsonObject("fastForwardTargetOutcome");
            long requestId = outcome.get("requestId").getAsLong();
            long baselineRequestId = fastForwardBaseline == null || !fastForwardBaseline.value().has("fastForwardTargetOutcome")
                    || fastForwardBaseline.value().get("fastForwardTargetOutcome").isJsonNull() ? 0L
                    : fastForwardBaseline.value().getAsJsonObject("fastForwardTargetOutcome").get("requestId").getAsLong();
            if (requestId > baselineRequestId && outcome.get("targetInstant").getAsLong() == target) {
                String status = outcome.get("status").getAsString();
                if ("REJECTED".equals(status)) {
                    throw new IllegalStateException("server rejected absolute canonical target: " + outcome.get("failure"));
                }
                if ("HELD".equals(status) && outcome.has("reachedCheckpointInstant") && !outcome.get("reachedCheckpointInstant").isJsonNull()
                        && outcome.get("reachedCheckpointInstant").getAsLong() == target
                        && observed.value().has("instant") && observed.value().get("instant").getAsLong() == target) {
                    advance("fast_forward_to_instant"); return;
                }
            }
        }
        if (System.nanoTime() - diagnosticWaitRequestNanos >= 1_000_000_000L) {
            minecraft.player.connection.sendCommand("pale_mirror v3 inspect performance");
            diagnosticWaitRequestNanos = System.nanoTime();
        }
        // See the relative path above: this is an operator wall-clock guard, not canonical time.
        if (elapsedWallMillis() >= action.get("timeoutMs").getAsLong()) {
            throw new IllegalStateException("timed out waiting for server-held absolute canonical target " + target);
        }
    }
    /** Releases the read-only absolute checkpoint without advancing canonical time. */
    static void releaseAbsoluteFastForwardHold(Minecraft minecraft) {
        if (!fastForwardSent) {
            fastForwardBaseline = diagnostics.get(new DiagnosticIdentity("performance", ""));
            releaseProjectionBaseline = diagnostics.get(new DiagnosticIdentity("projection_work", ""));
            minecraft.player.connection.sendCommand("pale_mirror v3 release_advance_hold");
            // The held-COLD release is the precise physical turn whose cost must be accounted
            // for.  Pair its normal acknowledgement with the read-only projection snapshot so
            // a later INPUT_REFRESH cannot erase the compile trigger that caused the turn.
            minecraft.player.connection.sendCommand("pale_mirror v3 inspect projection_work");
            fastForwardSent = true;
        }
        ObservedDiagnostic observed = diagnostics.get(new DiagnosticIdentity("performance", ""));
        ObservedDiagnostic projection = diagnostics.get(new DiagnosticIdentity("projection_work", ""));
        if (fresh(observed) && observed.value().has("fastForwardTargetOutcome")
                && !observed.value().get("fastForwardTargetOutcome").isJsonNull()) {
            JsonObject outcome = observed.value().getAsJsonObject("fastForwardTargetOutcome");
            long baselineRequestId = fastForwardBaseline == null || !fastForwardBaseline.value().has("fastForwardTargetOutcome")
                    || fastForwardBaseline.value().get("fastForwardTargetOutcome").isJsonNull() ? 0L
                    : fastForwardBaseline.value().getAsJsonObject("fastForwardTargetOutcome").get("requestId").getAsLong();
            if (outcome.get("requestId").getAsLong() > baselineRequestId && "RELEASED".equals(outcome.get("status").getAsString())
                    && projection != null && projection != releaseProjectionBaseline) {
                advance("release_fast_forward_hold"); return;
            }
        }
        long tick = minecraft.level.getGameTime();
        if ((tick - actionStartedTick) % 20L == 0L) {
            minecraft.player.connection.sendCommand("pale_mirror v3 inspect performance");
            minecraft.player.connection.sendCommand("pale_mirror v3 inspect projection_work");
        }
        if (elapsedWallMillis() >= 30_000L) throw new IllegalStateException("timed out releasing absolute canonical checkpoint");
    }
}
