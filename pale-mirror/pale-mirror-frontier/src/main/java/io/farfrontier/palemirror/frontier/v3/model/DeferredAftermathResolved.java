package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** One observed local resolution; conflict is terminal only for this exact cell. */
public record DeferredAftermathResolved(SubjectId aftermathId, long expectedEpoch, long observationAt, int expectedCursor, long authorityRevision,
                                        DeferredAftermathCellStatus result) implements FrontierPayload {
    public DeferredAftermathResolved(SubjectId aftermathId, long expectedEpoch, long observationAt, int expectedCursor,
                                     DeferredAftermathCellStatus result) {
        this(aftermathId, expectedEpoch, observationAt, expectedCursor, 0L, result);
    }
    public DeferredAftermathResolved {
        Objects.requireNonNull(aftermathId, "aftermath id"); Objects.requireNonNull(result, "aftermath resolution");
        if (expectedEpoch < 0 || observationAt < 0 || expectedCursor < 0 || result == DeferredAftermathCellStatus.PENDING) throw new IllegalArgumentException("invalid aftermath resolution");
    }
    @Override public String type() { return "frontier.deferred_aftermath_resolved"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
