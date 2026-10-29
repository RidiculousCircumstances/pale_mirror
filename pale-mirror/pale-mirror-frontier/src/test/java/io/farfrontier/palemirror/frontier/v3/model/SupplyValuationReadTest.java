package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupplyValuationReadTest {
    @Test void fieldCostCannotSurviveLossOfItsProductiveFieldAndRecoveryRebuildsIt() {
        var state = initial(); var id = state.resourceSites().sites().keySet().iterator().next();
        var events = ResourceSiteProcess.planPreparation(state, ResourceSiteProcess.preparation(id, 4000));
        state = ResourceSiteProcess.reducePreparationStarted(state, id, (ResourceSitePreparationStarted) events.getFirst().payload());
        state = ResourceSiteProcess.reducePrepared(state, id, (ResourceSitePrepared) events.get(1).payload());
        var field = state.resourceSite(id); var port = new FieldSupplyCapacity();
        var costs = port.valuation(state, field.settlementId(), Map.of());
        assertFalse(costs.isEmpty());
        var wearOwner = new FieldEquipmentDemand(); var wear = wearOwner.read(state, field.settlementId());
        var reserveOwner = new FieldReserveSupply(); var reserve = reserveOwner.read(state, field.settlementId());
        assertFalse(wear.isEmpty()); assertFalse(reserve.timing().isEmpty());
        var foreign = state.bootstrap().settlements().stream().filter(home -> !home.id().equals(field.settlementId()))
                .findFirst().orElseThrow().residents().getFirst().id();
        var body = state.actorLocations().get(foreign).body();
        var moved = state.withActorBody(foreign, body.offset(1, 0, 0));
        assertSame(costs, port.valuation(moved, field.settlementId(), Map.of()),
                "another resident's walk cannot reprice unchanged local fields and warehouse positions");
        assertSame(wear, wearOwner.read(moved, field.settlementId()));
        assertSame(reserve, reserveOwner.read(moved, field.settlementId()));
        var otherField = state.resourceSiteDescriptors().values().stream()
                .filter(candidate -> !candidate.settlementId().equals(field.settlementId())).findFirst().orElseThrow();
        var foreignDamage = state.withStructureCondition(otherField.facilityId(), StructureCondition.DESTROYED);
        assertSame(wear, wearOwner.read(foreignDamage, field.settlementId()));
        assertSame(reserve, reserveOwner.read(foreignDamage, field.settlementId()));
        var destroyed = state.withStructureCondition(field.facilityId(), StructureCondition.DESTROYED);
        assertTrue(port.valuation(destroyed, field.settlementId(), Map.of()).isEmpty());
        assertTrue(wearOwner.read(destroyed, field.settlementId()).isEmpty());
        assertTrue(reserveOwner.read(destroyed, field.settlementId()).timing().isEmpty());
        assertEquals(costs, port.valuation(state, field.settlementId(), Map.of()));
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(state));
        assertEquals(costs, port.valuation(recovered, field.settlementId(), Map.of()));
        assertEquals(wear, wearOwner.read(recovered, field.settlementId()));
        assertEquals(reserve, reserveOwner.read(recovered, field.settlementId()));
    }

    @Test void extractionCostTracksProductScopeDepletionAndLivingWorkforce() {
        var state = initial(); var home = state.extractionSites().deposits().values().iterator().next().site().settlementId();
        var rules = state.bootstrap().ruleset().goodsTrade();
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        var stocks = new TreeMap<String, SupplyPlanner.Stock>();
        rules.policies().get(participant.policy()).forEach(c -> stocks.put(c.itemKind(),
                new SupplyPlanner.Stock(0, 0, 0, 0, 0, 0, 1, rules.supplyPlanning().require(c.itemKind()))));
        var port = new ExtractionSupplyCapacity(); var costs = port.valuation(state, home, stocks);
        assertFalse(costs.isEmpty());
        var productionWorkers = SupplyLabourCapacity.potential(state, home, ResidentWorkKind.PRODUCTION, HumanCapability.INDUSTRY);
        assertFalse(productionWorkers.isEmpty());
        assertTrue(port.valuation(state, home, Map.of()).isEmpty());
        assertEquals(costs, port.valuation(state, home, stocks));
        var sites = state.extractionSites();
        for (var deposit : state.extractionSites().deposits().values()) if (deposit.site().settlementId().equals(home)) {
            var depleted = deposit;
            for (var cell : deposit.site().layout().cells())
                depleted = depleted.observe(cell.id(), depleted.cells().get(cell.id()).revision(), cell.definition().after());
            sites = sites.replace(depleted);
        }
        var depleted = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(sites));
        assertTrue(port.valuation(depleted, home, stocks).isEmpty(), "external source removal cannot retain its former production cost");
        assertEquals(costs, port.valuation(state, home, stocks));
        var actors = new LinkedHashMap<>(state.actorLocations());
        state.humanPopulation().residentsIn(home).forEach(person -> {
            var actor = actors.get(person.id()); actors.put(person.id(), actor.deadAt(actor.body()));
        });
        // Read-model adversarial fixture, not an authorized native death transition.
        var unavailable = FrontierWorldState.duringUnpublishedTransition(() -> state.withChanges(
                FrontierWorldStateUpdate.begin().actorLocations(actors)));
        assertTrue(port.valuation(unavailable, home, stocks).isEmpty());
        assertTrue(SupplyLabourCapacity.potential(unavailable, home, ResidentWorkKind.PRODUCTION, HumanCapability.INDUSTRY).isEmpty(),
                "a warmed manufacturing estimate cannot retain dead workers");
        assertEquals(productionWorkers, SupplyLabourCapacity.potential(state, home, ResidentWorkKind.PRODUCTION, HumanCapability.INDUSTRY),
                "a changed workforce cannot alter the earlier immutable branch");
        assertEquals(costs, port.valuation(state, home, stocks));
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:valuation-reads"),
                125, FrontierRulesets.marketQuarryGraybox()));
    }
}
