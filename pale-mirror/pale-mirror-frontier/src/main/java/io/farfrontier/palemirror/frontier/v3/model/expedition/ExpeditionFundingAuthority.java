package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.TransportMission;
import java.util.Optional;

/** Funding owner for admitted transport itineraries, independent of the transported goods. */
public final class ExpeditionFundingAuthority {
    private ExpeditionFundingAuthority() { }

    public static FixedScalar allocation(FrontierWorldState state, TransportMission mission) {
        if (state.bootstrap().ruleset().schemaVersion() >= 17 && mission.supplies().isEmpty())
            throw new IllegalArgumentException("expedition funding requires its full round-trip supply declaration");
        // Current itineraries provision the entire round trip before departure. They do not
        // plan purchases en route. A future purchase itinerary must declare its required cost,
        // not repurpose the optional replenishment ceiling as that requirement.
        return ExpeditionFundingPolicy.allocation(FixedScalar.ZERO,
                state.bootstrap().ruleset().expedition().replenishmentBudget(),
                state.inventory().economics().availableToReserve(mission.organization().sponsor().id())).orElseThrow();
    }

    public static TransportMission declare(FrontierWorldState state, TransportMission mission) {
        var amount = allocation(state, mission);
        return mission.withFinancialBudget(amount.raw() == 0 ? Optional.empty()
                : Optional.of(new SubjectId("budget:" + mission.id().value().replace(':', '-'))));
    }
}
