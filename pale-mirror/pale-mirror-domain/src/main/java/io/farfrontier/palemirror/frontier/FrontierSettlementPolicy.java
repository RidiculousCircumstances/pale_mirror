package io.farfrontier.palemirror.frontier;

import java.util.Comparator;
import java.util.List;

/**
 * Deterministic civilian policy over an immutable daily threat/reserve view.  The policy does
 * not manufacture guards, food or an outcome: it selects rationing and the civic regime which
 * the existing operations and the materialized settlement must visibly obey.
 */
final class FrontierSettlementPolicy {
    private static final int WATCH_THREAT = 180;
    private static final int EMERGENCY_THREAT = 420;
    private static final int SIEGE_THREAT = 700;
    private static final int WATCH_RESERVE_MILLI = 4_000;
    private static final int EMERGENCY_RESERVE_MILLI = 2_000;
    private static final int RECOVERY_RESERVE_MILLI = 6_000;
    private static final int SIEGE_RATION_PERMILLE = 820;
    private static final int EMERGENCY_RATION_PERMILLE = 920;

    void advance(FrontierWorldState state, String causationId, List<FrontierEvent> events) {
        state.settlements().stream().sorted(Comparator.comparing(FrontierSettlement::id)).forEach(settlement -> {
            FrontierCivicState previous = settlement.civicState();
            int reserve = reserveDaysMilli(state, settlement);
            int threat = threatPermille(state, settlement);
            FrontierCivicState next = decide(state, settlement, previous, threat, reserve);
            int ration = switch (next) {
                case SIEGE -> SIEGE_RATION_PERMILLE;
                case EMERGENCY -> EMERGENCY_RATION_PERMILLE;
                default -> 1_000;
            };
            settlement.reconcileCivic(next, threat, reserve, ration);
            if (previous != next) {
                events.add(state.event(FrontierEvent.Type.SETTLEMENT_CIVIC_CHANGED, settlement.id(), causationId));
            }
        });
    }

    private static int reserveDaysMilli(FrontierWorldState state, FrontierSettlement settlement) {
        long dailyNeed = FrontierBalance.civilianFoodNeed(state.alivePopulation(settlement.id()));
        long daysMilli = Math.min(32_000L, settlement.stock(FrontierResource.FOOD) * 1_000L / dailyNeed);
        return (int) daysMilli;
    }

    private static int threatPermille(FrontierWorldState state, FrontierSettlement settlement) {
        int pressure = state.assaults().stream().filter(value -> !value.terminal())
                .filter(value -> value.targetSettlementId().equals(settlement.id()))
                .mapToInt(value -> switch (value.state()) {
                    case ASSEMBLING -> 420;
                    case EN_ROUTE -> 620;
                    case ENGAGING -> 900;
                    case RETURNING -> 180;
                    case COMPLETED, ABORTED -> 0;
                }).max().orElse(0);
        int hivePressure = state.hives().stream().filter(value -> value.state() == FrontierHive.State.ACTIVE)
                .mapToInt(value -> Math.max(0, 240 - 6 * distance(value.anchor(), settlement.center())))
                .max().orElse(0);
        int damagedFacilities = (int) settlement.facilityIds().stream().map(state::facility).flatMap(java.util.Optional::stream)
                .filter(value -> value.state() != FrontierFacility.State.OPERATIONAL).count();
        return Math.min(1_000, pressure + hivePressure + damagedFacilities * 100);
    }

    private static FrontierCivicState decide(FrontierWorldState state, FrontierSettlement settlement,
                                              FrontierCivicState previous, int threat, int reserve) {
        if (state.alivePopulation(settlement.id()) == 0) return FrontierCivicState.DECLINED;
        if (threat >= SIEGE_THREAT) return FrontierCivicState.SIEGE;
        if (threat >= EMERGENCY_THREAT || reserve < EMERGENCY_RESERVE_MILLI) return FrontierCivicState.EMERGENCY;
        if (threat >= WATCH_THREAT || reserve < WATCH_RESERVE_MILLI) return FrontierCivicState.WATCH;
        if ((previous == FrontierCivicState.SIEGE || previous == FrontierCivicState.EMERGENCY
                || previous == FrontierCivicState.WATCH) && reserve >= RECOVERY_RESERVE_MILLI) {
            return FrontierCivicState.RECOVERY;
        }
        return FrontierCivicState.NORMAL;
    }

    private static int distance(FrontierPoint left, FrontierPoint right) {
        return Math.max(Math.abs(left.x() - right.x()), Math.abs(left.z() - right.z()));
    }
}
