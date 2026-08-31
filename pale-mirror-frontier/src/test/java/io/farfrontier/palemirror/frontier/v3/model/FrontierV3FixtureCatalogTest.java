package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3FixtureCatalogTest {
    @Test
    void everyDeclaredFixtureProfileHasExactlyOneLoadedProviderAndRequiredEvidenceContract() {
        List<FrontierV3FixtureCatalog.Profile> profiles = FrontierV3FixtureCatalog.profiles();
        assertEquals(16, profiles.size());
        assertEquals(profiles.size(), profiles.stream().map(FrontierV3FixtureCatalog.Profile::id).distinct().count());
        for (int index = 0; index < profiles.size(); index++) {
            FrontierV3FixtureCatalog.Profile profile = profiles.get(index);
            assertTrue(profile.provider().length() > 0);
            assertEquals("production", profile.rulesetId());
            assertTrue(profile.sourceProfile().length() > 0);
            assertTrue(profile.requiredAssertion().length() > 0);
            var configuration = FrontierV3FixtureCatalog.configuration(profile.id(), new WorldId("frontier:catalog-" + index), 41L);
            assertEquals("frontier:catalog-" + index, configuration.worldId().value());
            assertEquals(FrontierRulesets.production(), configuration.initialState().bootstrap().ruleset(),
                    "a fixture may vary canonical state, but it must not silently vary production balance rules");
        }
    }

    @Test
    void unknownAndDuplicateFixtureProfilesFailBeforeAScenarioCanStart() {
        assertThrows(IllegalArgumentException.class, () -> FrontierV3FixtureCatalog.profile("not-a-profile"));
        FrontierV3FixtureCatalog.Profile duplicate = new FrontierV3FixtureCatalog.Profile("duplicate", "world", "production", "normal-world", "unit",
                "terminal", FrontierWorldRuntimeDefinition::configuration);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3FixtureCatalog.catalog(duplicate, duplicate));
    }
}
