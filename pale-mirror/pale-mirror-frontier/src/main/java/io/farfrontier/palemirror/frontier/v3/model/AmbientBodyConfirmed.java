package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Exact physical handoff/occupancy evidence, never a work or inventory receipt. */
public record AmbientBodyConfirmed(SubjectId actorId, long leaseRevision, Boundary boundary,
                                   BodyPosition previousBody, BodyPosition observedBody) implements FrontierPayload {
    public enum Boundary {
        ADMISSION(0), SERVICE_OCCUPANCY(1);
        private final int tag;
        Boundary(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Boundary fromWireTag(int tag) {
            return switch (tag) {
                case 0 -> ADMISSION;
                case 1 -> SERVICE_OCCUPANCY;
                default -> throw new IllegalArgumentException("unknown ambient body boundary");
            };
        }
    }
    public AmbientBodyConfirmed {
        Objects.requireNonNull(actorId); Objects.requireNonNull(boundary);
        Objects.requireNonNull(previousBody); Objects.requireNonNull(observedBody);
        if (leaseRevision < 1) throw new IllegalArgumentException("body confirmation needs an exact lease revision");
    }
    @Override public String type() { return "frontier.ambient_body_confirmed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
