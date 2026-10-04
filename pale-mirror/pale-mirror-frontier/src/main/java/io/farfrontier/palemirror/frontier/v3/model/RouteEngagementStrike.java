package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One durable, exact COLD combat strike. Damage is checked against pure combat rules on reduction. */
public record RouteEngagementStrike(SubjectId engagementId, SubjectId attackerId, SubjectId targetId, int epoch,
                             FixedScalar damage,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId attackerExecution,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId targetExecution) implements FrontierPayload {
    public RouteEngagementStrike {
        Objects.requireNonNull(engagementId, "engagement id"); Objects.requireNonNull(attackerId, "attacker id");
        Objects.requireNonNull(targetId, "target id"); Objects.requireNonNull(damage, "strike damage");
        if (epoch < 0 || damage.compareTo(FixedScalar.ZERO) <= 0) throw new IllegalArgumentException("invalid COLD strike");
        Objects.requireNonNull(attackerExecution, "strike actor execution"); Objects.requireNonNull(targetExecution, "strike target execution");
        if (!attackerExecution.actorId().equals(attackerId) || !targetExecution.actorId().equals(targetId))
            throw new IllegalArgumentException("strike declaration has a foreign actor or target");
    }
    @Override public String type() { return "frontier.route_engagement_strike"; }
}
