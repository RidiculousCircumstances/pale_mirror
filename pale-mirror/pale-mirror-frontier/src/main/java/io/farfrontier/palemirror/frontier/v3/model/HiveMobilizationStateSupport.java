package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

/**
 * Pure cross-owner invariants for the task-to-cocoon-to-assault custody chain.
 *
 * <p>{@link HiveColony} owns mobilisation lifecycle, while {@link StrategicPlanState}
 * owns task and assault lifecycle. This support is deliberately the only place that
 * reconciles those records, so {@link FrontierWorldState} remains an aggregate boundary
 * rather than a second hive-operation coordinator.</p>
 */
final class HiveMobilizationStateSupport {
    private HiveMobilizationStateSupport() { }

    static void validateTaskCustody(FrontierBootstrap bootstrap, HiveColony colony, StrategicPlanState plans) {
        for (HiveMobilization mobilization : colony.mobilizations().values()) {
            StrategicTask task = plans.tasks().get(mobilization.taskId());
            boolean knownSettlement = bootstrap.settlements().stream()
                    .anyMatch(settlement -> settlement.id().equals(mobilization.settlementId()));
            if (task == null || task.kind() != StrategicTaskKind.ASSAULT_SETTLEMENT
                    || !task.ownerId().equals(bootstrap.hive().id()) || !knownSettlement) {
                throw new IllegalArgumentException("hive mobilization must retain one exact assault task and settlement target");
            }
            if (mobilization.status() != HiveMobilizationStatus.DEPARTED && mobilization.status() != HiveMobilizationStatus.RETURNING
                    && mobilization.status() != HiveMobilizationStatus.COMPLETED) {
                if (task.status() != StrategicTaskStatus.ACTIVE) {
                    throw new IllegalArgumentException("an un-departed hive mobilization must retain one active exact assault task");
                }
                continue;
            }
            List<SettlementAssault> departures = plans.settlementAssaults().values().stream()
                    .filter(assault -> assault.taskId().equals(mobilization.taskId()) && assault.hiveId().equals(mobilization.hiveId())
                            && assault.sighting().equals(mobilization.sighting()))
                    .toList();
            if (departures.size() != 1) {
                throw new IllegalArgumentException("a departed hive mobilization must retain exactly one same-sighting settlement assault");
            }
            SettlementAssault departure = departures.getFirst();
            if (mobilization.status() == HiveMobilizationStatus.DEPARTED && departure.status() == SettlementAssaultStatus.RESOLVED) {
                throw new IllegalArgumentException("a resolved expedition child must atomically retain a return or casualty-only completion");
            }
            if (departure.status() != SettlementAssaultStatus.RESOLVED && task.status() != StrategicTaskStatus.ACTIVE) {
                throw new IllegalArgumentException("an unresolved departed assault must retain its active exact task");
            }
            if (departure.status() == SettlementAssaultStatus.RESOLVED) {
                if (mobilization.status() != HiveMobilizationStatus.RETURNING && mobilization.status() != HiveMobilizationStatus.COMPLETED) {
                    throw new IllegalArgumentException("a terminal expedition child requires its retained return or completed parent receipt");
                }
                StrategicTaskStatus expected = departure.outcome().orElseThrow() == SettlementAssaultOutcome.HIVE_VICTORY
                        ? StrategicTaskStatus.COMPLETED : StrategicTaskStatus.BLOCKED;
                if (task.status() != expected) {
                    throw new IllegalArgumentException("a resolved departed assault task must retain its matching terminal outcome");
                }
            }
            if (mobilization.status() == HiveMobilizationStatus.RETURNING || mobilization.returnAssembly().isPresent()) {
                HiveReturnAssembly returning = mobilization.returnAssembly().orElseThrow();
                if (!returning.members().keySet().stream().allMatch(mobilization.memberIds()::contains)) {
                    throw new IllegalArgumentException("a return cursor may name only the original expedition roster");
                }
                if (mobilization.status() == HiveMobilizationStatus.COMPLETED && !returning.complete()) {
                    throw new IllegalArgumentException("a completed expedition retains unfinished survivor return cursors");
                }
            }
        }
    }
}
