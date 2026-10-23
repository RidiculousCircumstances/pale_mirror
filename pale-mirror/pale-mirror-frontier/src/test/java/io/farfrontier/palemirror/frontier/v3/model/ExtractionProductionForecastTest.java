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
        for (var other : state.extractionSites().deposits().values())
            ExtractionProductionForecast.assess(state, other, definition, workers);
        assertSame(forecast, ExtractionProductionForecast.assess(state, deposit, definition, workers),
                "querying another mine must not recompile this unchanged forecast");
        var removed = state.inventory();
        for (var item : List.copyOf(removed.items().values())) {
            if (item.economicOwnerId().equals(deposit.site().settlementId())
                    && state.bootstrap().ruleset().tools().find(item.itemKind()).isPresent())
                removed = removed.moveObservedItem(item.id(), item.custody(),
                        new InventoryCustody.Player(new UUID(0, 234)));
        }
        var withoutEquipment = state.withInventory(removed);
        assertEquals(0, ExtractionProductionForecast.assess(withoutEquipment, deposit, definition, workers).outputs(),
                "withdrawing equipment must invalidate cached productive capacity immediately");
        assertEquals(forecast, ExtractionProductionForecast.assess(state, deposit, definition, workers));
        var cell = deposit.site().layout().cells().stream().filter(c -> c.definition().equals(definition)).findFirst().orElseThrow();
        var changed = deposit.observe(cell.id(), deposit.cells().get(cell.id()).revision(), cell.definition().after());
        var successor = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(changed)));
        var next = ExtractionProductionForecast.assess(successor, changed, definition, workers);
        assertEquals(forecast.outputs() - 1, next.outputs(), "external depletion cannot retain old capacity");
        assertEquals(forecast, ExtractionProductionForecast.assess(state, deposit, definition, workers),
                "revisiting an earlier immutable branch must recover its own forecast");
        assertTrue(deposit.presentMaterials().contains(definition));
        var depletedCells = new HashMap<>(deposit.cells());
        for (long id : deposit.site().layout().materialCells().get(definition)) {
            var prior = depletedCells.get(id);
            depletedCells.put(id, new io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionDeposit.CellState(
                    prior.revision() + 1,
                    io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionDeposit.Disposition.EXTERNALLY_CHANGED,
                    definition.after(), prior.extractionOperation()));
        }
        var depleted = new io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionDeposit(
                deposit.site(), depletedCells, deposit.geometry(), deposit.development());
        assertFalse(depleted.presentMaterials().contains(definition), "a declaration index must not resurrect depleted material");
        assertTrue(deposit.presentMaterials().contains(definition), "immutable branches keep their own depletion facts");
    }
}
