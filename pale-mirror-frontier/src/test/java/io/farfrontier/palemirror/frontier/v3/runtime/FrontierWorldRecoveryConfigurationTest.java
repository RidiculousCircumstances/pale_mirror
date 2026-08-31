package io.farfrontier.palemirror.frontier.v3.runtime;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierWorldRecoveryConfigurationTest {
    @Test
    void emptyRecoveryRetainsTheExplicitTestFixtureRatherThanSilentlyBootstrappingNormalWorld() {
        var selected = FrontierV3FixtureCatalog.defenderEquipmentConfiguration(new WorldId("frontier:fixture-recovery"), 41L);

        var recovered = FrontierWorldRecoveryConfiguration.select(selected, Optional.empty());

        assertSame(selected, recovered);
        assertTrue(recovered.initialState().inventory().items().containsKey(
                new io.farfrontier.palemirror.frontier.v3.api.SubjectId("item:development-defender-sword")));
    }
}
