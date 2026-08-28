package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One bounded exact hive interception of a named human route operation.
 *
 * <p>The future state owner retains this independently of a Minecraft scene. It deliberately
 * does not contain hit points, drops or block outcomes: those remain actor state, custody and
 * physical observations respectively.</p>
 */
record RouteEngagement(SubjectId id, SubjectId taskId, SubjectId operationId, SubjectId hiveId,
                       List<EngagementAttacker> attackers, BlockPosition intercept, RouteEngagementStatus status,
                       int nextStrikeEpoch, Optional<RouteEngagementOutcome> outcome) {
    RouteEngagement {
        Objects.requireNonNull(id, "engagement id"); Objects.requireNonNull(taskId, "engagement task");
        Objects.requireNonNull(operationId, "engagement operation"); Objects.requireNonNull(hiveId, "engagement hive");
        attackers = List.copyOf(attackers); Objects.requireNonNull(intercept, "engagement intercept");
        Objects.requireNonNull(status, "engagement status"); outcome = Objects.requireNonNull(outcome, "engagement outcome");
        if (attackers.isEmpty() || attackers.size() > 16 || attackers.stream().map(EngagementAttacker::actorId).distinct().count() != attackers.size()) {
            throw new IllegalArgumentException("engagement must retain one to sixteen distinct attackers");
        }
        if (attackers.stream().anyMatch(attacker -> !attacker.route().getLast().equals(intercept))) {
            throw new IllegalArgumentException("engagement attacker route must end at its intercept");
        }
        if (nextStrikeEpoch < 0) throw new IllegalArgumentException("engagement strike epoch cannot be negative");
        if (status == RouteEngagementStatus.RESOLVED != outcome.isPresent()) {
            throw new IllegalArgumentException("only resolved engagements retain one outcome");
        }
    }

    List<SubjectId> attackerIds() { return attackers.stream().map(EngagementAttacker::actorId).toList(); }
    boolean allAttackersAtIntercept() { return attackers.stream().allMatch(EngagementAttacker::atDestination); }

    RouteEngagement withStatus(RouteEngagementStatus next) {
        if (next == RouteEngagementStatus.RESOLVED) throw new IllegalArgumentException("resolved engagement requires an exact outcome");
        return new RouteEngagement(id, taskId, operationId, hiveId, attackers, intercept, next, nextStrikeEpoch, Optional.empty());
    }

    RouteEngagement advanceAttacker(SubjectId actorId, int nextRouteIndex) {
        if (status != RouteEngagementStatus.APPROACHING) throw new IllegalArgumentException("only approaching engagement may advance");
        boolean found = false; java.util.ArrayList<EngagementAttacker> next = new java.util.ArrayList<>(attackers.size());
        for (EngagementAttacker attacker : attackers) {
            if (attacker.actorId().equals(actorId)) { next.add(attacker.advance(nextRouteIndex)); found = true; }
            else next.add(attacker);
        }
        if (!found) throw new IllegalArgumentException("engagement has no named attacker");
        return new RouteEngagement(id, taskId, operationId, hiveId, next, intercept, status, nextStrikeEpoch, outcome);
    }

    RouteEngagement afterStrike(int expectedEpoch) {
        if (status != RouteEngagementStatus.COLD_COMBAT || nextStrikeEpoch != expectedEpoch) {
            throw new IllegalArgumentException("route engagement strike does not match its current COLD epoch");
        }
        return new RouteEngagement(id, taskId, operationId, hiveId, attackers, intercept, status,
                Math.addExact(nextStrikeEpoch, 1), Optional.empty());
    }

    RouteEngagement resolve(RouteEngagementOutcome result) {
        if (status == RouteEngagementStatus.RESOLVED || status == RouteEngagementStatus.HOT
                || result != RouteEngagementOutcome.ABORTED && status != RouteEngagementStatus.COLD_COMBAT) {
            throw new IllegalArgumentException("only COLD combat may choose a combat outcome");
        }
        return new RouteEngagement(id, taskId, operationId, hiveId, attackers, intercept, RouteEngagementStatus.RESOLVED,
                nextStrikeEpoch, Optional.of(Objects.requireNonNull(result, "engagement outcome")));
    }
}
