package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Compare-and-set transition of one engagement's retained command signal. */
public record RouteEngagementCommandAuthorityChanged(SubjectId engagementId, HiveOperationCommandAuthority expected,
                                                      HiveOperationCommandAuthority next) implements FrontierPayload {
    public RouteEngagementCommandAuthorityChanged {
        Objects.requireNonNull(engagementId, "engagement id"); Objects.requireNonNull(expected, "expected authority"); Objects.requireNonNull(next, "next authority");
        if (expected.equals(next)) throw new IllegalArgumentException("command authority transition must change state");
    }
    @Override public String type() { return "frontier.route_engagement_command_authority_changed"; }
}
