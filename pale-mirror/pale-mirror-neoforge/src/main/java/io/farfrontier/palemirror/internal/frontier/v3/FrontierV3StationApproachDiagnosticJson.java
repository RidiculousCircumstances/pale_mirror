package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.StationApproachState;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;

/** Read-only formatting of an owner's explicit spatial continuation, never a lifecycle decision. */
final class FrontierV3StationApproachDiagnosticJson {
    private FrontierV3StationApproachDiagnosticJson() { }
    static String write(StationApproachState state, SurfaceAnchor checkpoint, SurfaceAnchor goal) {
        return "{\"revision\":" + state.revision() + ",\"pending\":" + state.pending()
                + ",\"origin\":" + FrontierV3DiagnosticJson.position(state.current(checkpoint).support())
                + ",\"semanticGoal\":" + FrontierV3DiagnosticJson.position(goal.support())
                + ",\"waitReason\":\"" + (state.waitingOrigin().isPresent() ? "KNOWN_APPROACH_UNAVAILABLE" : "NONE")
                + "\",\"cursor\":" + state.approach().map(value -> value.cursor()).orElse(-1)
                + ",\"nodes\":" + state.approach().map(value -> value.path().size()).orElse(0) + "}";
    }
}
