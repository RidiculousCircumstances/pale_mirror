package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One COLD-only movement, transfer or station-work fact for the currently retained bread job. */
public record BakeryColdStep(SubjectId jobId, BakeryWorkState.Phase expectedPhase, Action action,
                             SurfaceAnchor nextSurface) implements FrontierPayload {
    public enum Action {
        MOVE(1), PICKUP(2), LOAD(3), WORK_TICK(4), RECIPE(5), UNLOAD(6), DELIVER(7), FINALIZE(8);
        private final int wireTag;
        Action(int wireTag) { this.wireTag = wireTag; }
        public int wireTag() { return wireTag; }
        public static Action fromWireTag(int tag) {
            for (Action action : values()) if (action.wireTag == tag) return action;
            throw new IllegalArgumentException("unknown bakery step action tag: " + tag);
        }
    }

    public BakeryColdStep {
        Objects.requireNonNull(jobId, "bakery cold job");
        Objects.requireNonNull(expectedPhase, "bakery expected phase");
        Objects.requireNonNull(action, "bakery cold action");
        Objects.requireNonNull(nextSurface, "bakery next or current surface");
    }

    @Override public String type() { return "frontier.bakery_cold_step"; }
}
