package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The former settlement-wide ration loop is not an executable fresh-world owner. */
class SettlementProvisionRetirementTest {
    @Test void freshWorldHasOnlyExactResidentNeedAuthority() {
        var configuration = FrontierWorldRuntimeDefinition.configuration(
                new WorldId("frontier:retired-settlement-provision"), 91L);
        assertTrue(configuration.initialState().humanPopulation().provisions().isEmpty());
        assertTrue(configuration.initialSchedules().stream().anyMatch(action ->
                action.kind().equals("frontier.resident.need.review")));
        assertFalse(configuration.initialSchedules().stream().anyMatch(action ->
                action.kind().startsWith("frontier.settlement.provision")));
        assertFalse(FrontierV3FixtureCatalog.profiles().stream().anyMatch(profile ->
                profile.id().equals("settlement-provision")));
    }
}
