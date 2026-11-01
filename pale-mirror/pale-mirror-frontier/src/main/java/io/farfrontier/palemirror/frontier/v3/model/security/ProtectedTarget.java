package io.farfrontier.palemirror.frontier.v3.model.security;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;
/** Nominal protection subject, not an execution owner or a resource claim. */
public record ProtectedTarget(Kind kind, SubjectId id, SubjectId home, SurfaceAnchor observationAnchor, int importance) {
    public enum Kind {
        SETTLEMENT(1), CULTIVATION_SITE(2), EXTRACTION_SITE(3), FOREST_AREA(4), UNIT_GROUP(5);
        private final int tag; Kind(int tag) { this.tag=tag; } public int wireTag() { return tag; }
        public static Kind require(int tag) { for (var kind : values()) if (kind.tag==tag) return kind;
            throw new IllegalArgumentException("unknown protected target kind"); }
    }
    public ProtectedTarget { Objects.requireNonNull(kind); Objects.requireNonNull(id); Objects.requireNonNull(home); Objects.requireNonNull(observationAnchor);
        if (importance < 1 || importance > 255) throw new IllegalArgumentException("invalid protection importance"); }
}
