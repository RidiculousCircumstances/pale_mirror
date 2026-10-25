package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.process.ExactItemDepartureComposition.Owner;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExactItemDepartureCompositionTest {
    @Test void departureFactConsumersMustDeclareTheirClosedOwnerOnce() {
        var forestry = new ForestryToolDeparture();
        assertThrows(IllegalArgumentException.class, () -> ExactItemDepartureReactions.closed(List.of()));
        assertThrows(IllegalArgumentException.class, () -> ExactItemDepartureReactions.closed(List.of(forestry, forestry)));
        org.junit.jupiter.api.Assertions.assertEquals(List.of(forestry), ExactItemDepartureReactions.closed(List.of(forestry)));
    }
    @Test void missingDuplicateAndForeignCommitmentDeclarationsFailClosed() {
        var production = policy(Owner.PRODUCTION_WORK);
        assertThrows(IllegalArgumentException.class, () -> new ExactItemDepartureComposition(List.of(production)));
        assertThrows(IllegalArgumentException.class, () -> new ExactItemDepartureComposition(List.of(production, production)));
        assertThrows(IllegalArgumentException.class, () -> new ExactItemDepartureComposition(
                List.of(production, policy(Owner.STATION_OPERATION))));
    }

    private static ExactItemDepartureComposition.Policy policy(Owner owner) {
        return new ExactItemDepartureComposition.Policy(owner, (state, item) -> false,
                (state, command, item, observation) -> List.of(observation),
                (state, item, source, inventory) -> state.withInventory(inventory));
    }
}
