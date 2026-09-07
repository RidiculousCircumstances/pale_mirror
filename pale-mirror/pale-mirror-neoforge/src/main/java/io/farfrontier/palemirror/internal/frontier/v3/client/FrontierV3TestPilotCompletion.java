package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonObject;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;

/** Owns terminal pilot lifecycle transitions after the action interpreter has frozen evidence. */
final class FrontierV3TestPilotCompletion {
    private FrontierV3TestPilotCompletion() { }

    static void complete(int actionCount) {
        String segment = FrontierV3PilotSessionControl.lifecycleSegment();
        if (FrontierV3PilotSessionControl.expectedCrashSegment()) {
            // A declared fault can park after the final ordinary player action.  Keep the same
            // connection idle only for the runner's bounded real-probe/arm wait; publishing a
            // terminal result or replaying actions would both falsify the crash boundary.
            if (!FrontierV3PilotSessionControl.expectedLossArmed()) FrontierV3PilotSessionControl.markAwaitingExpectedLossProbe();
            return;
        }
        if (FrontierV3PilotSessionControl.shouldAwaitNextSegment()) {
            try {
                String runId = FrontierV3PilotSessionControl.runId();
                FrontierV3PilotSessionControl.publishLifecycleSignal("scenario_segment_complete", segment, new JsonObject());
                FrontierV3PilotSessionControl.markAwaitingResume();
                PaleMirrorMod.LOGGER.info("PMV3_PILOT session_segment_complete runId={}", runId);
                // Queue the normal client disconnect after this tick.  Calling it re-entrantly
                // from the scenario action tick can leave the live network connection open
                // until server shutdown, which makes a persistent-client restart slower and
                // fails to prove an ordinary departure.
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.execute(() -> {
                    if (minecraft.getConnection() == null) {
                        throw new IllegalStateException("persistent pilot has no live connection to close");
                    }
                    minecraft.getConnection().getConnection().disconnect(Component.literal("Frontier v3 persistent-pilot restart"));
                });
                return;
            } catch (IOException | IllegalArgumentException failure) {
                throw new IllegalStateException("persistent pilot could not publish its exact restart boundary", failure);
            }
        }
        if (FrontierV3PilotSessionControl.shouldAwaitFinalClose()) {
            try {
                FrontierV3PilotSessionControl.publishLifecycleSignal("scenario_segment_complete", segment, new JsonObject());
                FrontierV3PilotSessionControl.markAwaitingFinalClose();
                PaleMirrorMod.LOGGER.info("PMV3_PILOT final_segment_awaiting_supervisor_close runId={}", FrontierV3PilotSessionControl.runId());
                return;
            } catch (IOException | IllegalArgumentException failure) {
                throw new IllegalStateException("persistent pilot could not publish its final matrix boundary", failure);
            }
        }
        if (FrontierV3PilotSessionControl.shouldAwaitLifecycleFinalClose()) {
            try {
                FrontierV3PilotSessionControl.publishLifecycleSignal("scenario_segment_complete", segment, new JsonObject());
                FrontierV3PilotSessionControl.markAwaitingLifecycleFinalClose();
                PaleMirrorMod.LOGGER.info("PMV3_PILOT completed scenario actions={} awaiting_authenticated_normal_disconnect", actionCount);
                return;
            } catch (IOException | IllegalArgumentException failure) {
                throw new IllegalStateException("pilot could not publish its isolated terminal boundary", failure);
            }
        }
        try {
            FrontierV3PilotSessionControl.publishLifecycleSignal("scenario_segment_complete", segment, new JsonObject());
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("pilot could not acknowledge terminal scenario segment", failure);
        }
        // The outer runner must still receive the server response to the final
        // read-only assertion before it closes this ordinary client.
        PaleMirrorMod.LOGGER.info("PMV3_PILOT completed scenario actions={}", actionCount);
    }
}
