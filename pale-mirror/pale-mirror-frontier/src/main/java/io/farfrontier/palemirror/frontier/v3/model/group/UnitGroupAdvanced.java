package io.farfrontier.palemirror.frontier.v3.model.group;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import java.util.Optional;

/** Exact group transition; journey data is accepted geometry, not a command to write actor positions. */
public record UnitGroupAdvanced(SubjectId groupId, long expectedRevision, Change change, long goalOrdinal,
                                Optional<UnitGroup.Journey> journey, Optional<SubjectId> departureActor) implements FrontierPayload {
    public enum Change { START, FRAME, ARRIVE, CLOSE }
    public UnitGroupAdvanced {
        Objects.requireNonNull(groupId); Objects.requireNonNull(change); Objects.requireNonNull(journey);
        Objects.requireNonNull(departureActor);
        if (expectedRevision < 1 || goalOrdinal < 0 || ((change == Change.START || change == Change.FRAME) != journey.isPresent())
                || (change == Change.START) != departureActor.isPresent())
            throw new IllegalArgumentException("group transition has incomplete nominal evidence");
    }
    @Override public String type() { return "frontier.unit_group_advanced"; }
}
