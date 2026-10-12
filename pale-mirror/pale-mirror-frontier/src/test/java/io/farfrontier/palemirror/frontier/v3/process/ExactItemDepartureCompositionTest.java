package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExactItemDepartureCompositionTest {
    @Test void missingDuplicateAndForeignCommitmentDeclarationsFailClosed() {
        var production = policy(PhysicalIntentLifecycleOwner.PRODUCTION_WORK);
        assertThrows(IllegalArgumentException.class, () -> new ExactItemDepartureComposition(List.of(production)));
        assertThrows(IllegalArgumentException.class, () -> new ExactItemDepartureComposition(List.of(production, production)));
        assertThrows(IllegalArgumentException.class, () -> new ExactItemDepartureComposition(
                List.of(production, policy(PhysicalIntentLifecycleOwner.HIVE_GROWTH))));
    }

    private static ExactItemDepartureComposition.Policy policy(PhysicalIntentLifecycleOwner owner) {
        return new ExactItemDepartureComposition.Policy(owner, (state, item) -> false,
                (state, command, item, observation) -> List.of(observation),
                (state, item, source, inventory) -> state.withInventory(inventory));
    }
}
