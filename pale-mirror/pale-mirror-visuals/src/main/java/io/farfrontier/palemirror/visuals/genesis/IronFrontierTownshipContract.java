package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredSettlementSitePlan;
import io.farfrontier.palemirror.api.DevelopmentReservationKind;
import io.farfrontier.palemirror.api.SettlementDevelopmentStage;

/** Iron-frontier-specific constraints kept outside the reusable settlement manifest API. */
final class IronFrontierTownshipContract {
    private IronFrontierTownshipContract() { }

    static void validate(AuthoredSettlementSitePlan plan) {
        if (plan.buildings().size() != 20 || plan.openSpaces().size() != 6) {
            throw new IllegalStateException("iron_frontier Township requires 20 buildings and six open spaces");
        }
        long parcels = plan.developmentReservations().stream()
                .filter(value -> value.kind() == DevelopmentReservationKind.PARCEL).count();
        long annexes = plan.developmentReservations().stream()
                .filter(value -> value.kind() == DevelopmentReservationKind.ANNEX).count();
        if (parcels != 5 || annexes != 2) {
            throw new IllegalStateException("iron_frontier Mining Town reserve requires five parcels and two annexes");
        }
        if (plan.developmentReservations().stream()
                .anyMatch(value -> value.targetStage() != SettlementDevelopmentStage.MINING_TOWN)) {
            throw new IllegalStateException("iron_frontier v40 reserves only the Mining Town snapshot");
        }
    }
}
