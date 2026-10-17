package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionProductionForecastTest {
    @Test void declaredCoalBeyondTheImmediateWorkFrontIsConditionalCapacityNotNewStock() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:sequential-coal-capacity"), 41,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r17")));
        var deposit = state.extractionSites().deposits().values().stream()
                .filter(d -> d.site().layout().cells().stream().anyMatch(c -> c.definition().before().kind().equals("minecraft:coal_ore")))
                .findFirst().orElseThrow();
        var definition = deposit.site().layout().cells().stream().map(c -> c.definition())
                .filter(d -> d.before().kind().equals("minecraft:coal_ore")).findFirst().orElseThrow();
        var workers = state.humanPopulation().residents().values().stream()
                .filter(r -> r.settlementId().equals(deposit.site().settlementId()) && r.capability(HumanCapability.EXTRACTION) > 0).limit(2).toList();
        var inventory = state.inventory();
        var forecast = ExtractionProductionForecast.assess(state, deposit, definition, workers);
        long exposed = deposit.available(Set.of()).stream().filter(c -> c.definition().equals(definition)).count();
        assertTrue(forecast.outputs() > exposed, "capacity must include the known sequential coal, not only the currently exposed face");
        assertTrue(forecast.labourTicks() >= forecast.firstOutputTicks());
        assertTrue(forecast.firstOutputTicks() > 0);
        assertSame(inventory, state.inventory());
    }
}
