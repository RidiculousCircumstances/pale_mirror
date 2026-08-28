package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/**
 * One bounded exact hive interception of a named human route operation.
 *
 * <p>The future state owner retains this independently of a Minecraft scene. It deliberately
 * does not contain hit points, drops or block outcomes: those remain actor state, custody and
 * physical observations respectively.</p>
 */
record RouteEngagement(SubjectId id, SubjectId taskId, SubjectId operationId, SubjectId hiveId,
                       List<SubjectId> attackerIds, BlockPosition intercept, RouteEngagementStatus status) {
    RouteEngagement {
        Objects.requireNonNull(id, "engagement id"); Objects.requireNonNull(taskId, "engagement task");
        Objects.requireNonNull(operationId, "engagement operation"); Objects.requireNonNull(hiveId, "engagement hive");
        attackerIds = List.copyOf(attackerIds); Objects.requireNonNull(intercept, "engagement intercept");
        Objects.requireNonNull(status, "engagement status");
        if (attackerIds.isEmpty() || attackerIds.size() > 16 || attackerIds.stream().distinct().count() != attackerIds.size()) {
            throw new IllegalArgumentException("engagement must retain one to sixteen distinct attackers");
        }
    }

    RouteEngagement withStatus(RouteEngagementStatus next) {
        return new RouteEngagement(id, taskId, operationId, hiveId, attackerIds, intercept, next);
    }
}
