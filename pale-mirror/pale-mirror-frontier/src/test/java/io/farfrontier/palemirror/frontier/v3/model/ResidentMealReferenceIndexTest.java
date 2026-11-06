package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResidentMealReferenceIndexTest {
    @Test void populationValidationRejectsChangedResidentRelationsAndForgedKeysAfterWarmup() {
        var state = meal(); var population = state.humanPopulation();
        var meal = population.meals().values().iterator().next();
        var actor = meal.residentId(); var resident = population.resident(actor);
        ResidentMealExecutionAuthority.validate(population, state.actorExecutions());
        assertThrows(IllegalArgumentException.class, () -> ResidentMealExecutionAuthority.validate(population,
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState.empty()));
        var abandoned = population.abandonMealSource(meal);
        assertThrows(IllegalArgumentException.class, () -> ResidentMealExecutionAuthority.validate(abandoned,
                state.actorExecutions()), "an execution without its exact meal cannot survive the reverse check");
        ResidentMealExecutionAuthority.validate(population, state.actorExecutions());
        var other = population.residents().values().stream()
                .filter(person -> !person.settlementId().equals(resident.settlementId())).findFirst().orElseThrow();
        assertEquals(population, populationWith(population, population.residents(), population.households(),
                population.nutrition(), population.meals()));
        var relocated = ImmutableLookupMap.withEntry(population.residents(), actor,
                resident.relocated(other.settlementId(), other.householdId()));
        assertThrows(IllegalArgumentException.class, () -> populationWith(population, relocated,
                population.households(), population.nutrition(), population.meals()),
                "an unchanged meal must be checked when its exact resident moves to another settlement");
        var wrongKey = ImmutableLookupMap.withEntry(ImmutableLookupMap.withoutEntry(population.meals(), actor), other.id(), meal);
        assertThrows(IllegalArgumentException.class, () -> populationWith(population, population.residents(),
                population.households(), population.nutrition(), wrongKey));
        assertThrows(IllegalArgumentException.class, () -> populationWith(population, population.residents(),
                ImmutableLookupMap.withoutEntry(population.households(), resident.householdId()), population.nutrition(), population.meals()));
        assertThrows(IllegalArgumentException.class, () -> populationWith(population, population.residents(),
                population.households(), ImmutableLookupMap.withoutEntry(population.nutrition(), actor), population.meals()));
        assertEquals(population, ImmutableInvariantCheck.full(() -> populationWith(population,
                population.residents(), population.households(), population.nutrition(), population.meals())),
                "failed derived updates cannot poison ordinary or forced full validation");
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        assertEquals(state, codec.decode(codec.encode(state)));
    }
    private static HumanPopulation populationWith(HumanPopulation source, Map<SubjectId, ResidentProfile> residents,
            Map<SubjectId, Household> households, Map<SubjectId, ResidentNutrition> nutrition, Map<SubjectId, ResidentMeal> meals) {
        return new HumanPopulation(households, residents, source.birthJobs(), source.health(), source.quarantines(),
                source.migrations(), source.provisions(), nutrition, source.medicalOperations(), source.schedules(), meals,
                source.mealResourceObligations());
    }
    @Test void warmedMealStillRejectsLostAllocationBodyAndEndpointAndRecoversAfterFailure() {
        var state = meal(); var meal = state.humanPopulation().meals().values().iterator().next();
        ResidentMealReferenceClosure.validateTransition(state, state);
        var resources = state.inventory().fungibleResources().releaseClaim(meal.sourceAccountId(), meal.claimId());
        var missingClaim = FrontierWorldState.duringUnpublishedTransition(() -> state.withInventory(state.inventory().withFungibleResources(resources)));
        assertThrows(IllegalArgumentException.class, () -> ResidentMealReferenceClosure.validateTransition(state, missingClaim));
        ResidentMealReferenceClosure.validateTransition(state, state);
        var body = state.actorLocations().get(meal.residentId());
        var dead = FrontierWorldState.duringUnpublishedTransition(() -> state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(
                ImmutableLookupMap.withEntry(state.actorLocations(), meal.residentId(), body.deadAt(body.body())))));
        assertThrows(IllegalArgumentException.class, () -> ResidentMealReferenceClosure.validateTransition(state, dead));
        ResidentMealReferenceClosure.validateTransition(state, state);
        var inventory = state.inventory(); var surface = inventory.surfaces().get(meal.depotId());
        var changedSurfaces = ImmutableLookupMap.withEntry(inventory.surfaces(), surface.containerId(),
                new ContainerSurface(surface.containerId(), surface.position().offset(20, 0, 0), surface.status()));
        var wrongEndpoint = FrontierWorldState.duringUnpublishedTransition(() -> state.withInventory(new ExactInventory(
                inventory.containers(), inventory.items(), inventory.cargo(), inventory.playerItems(), inventory.worldCarrierItems(),
                inventory.conflicts(), changedSurfaces, inventory.economics(), inventory.fungibleResources())));
        assertThrows(IllegalArgumentException.class, () -> ResidentMealReferenceClosure.validateTransition(state, wrongEndpoint));
        ImmutableInvariantCheck.full(() -> { ResidentMealReferenceClosure.validateTransition(state, state); return null; });
        ResidentMealReferenceClosure.validate(state);
    }
    private static FrontierWorldState meal() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:meal-read-index"), 421L));
        var home = state.bootstrap().settlements().getFirst(); var resident = home.residents().getFirst().id();
        var container = new SubjectId("container:meal-read-index"); var account = ReferenceContainerCustody.scopeId(container);
        var kitchen = home.structures().stream().filter(b -> b.kind() == StructureKind.KITCHEN).findFirst().orElseThrow();
        var station = io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingSpatialPlan.of(kitchen).entrances().values().iterator().next().interiorConnector();
        var endpoint = new ShipmentEndpoint.Storage(home.id(), container, station, Optional.of(kitchen.id()), station);
        var policy = new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 100, ResourceStorageRole.WAREHOUSE);
        state = new ResourceStorageDeclared(ContainerRecord.storage(container, home.id(), 27, io.farfrontier.palemirror.frontier.v3.model.StorageBlockKind.CHEST, policy), station.support().offset(1, 1, 0)).apply(state, home.id());
        var lot = new ResourceLot(new SubjectId("lot:meal-read-index"), home.id(), "minecraft:bread", 64, "test:meal", List.of());
        var resources = state.inventory().fungibleResources().issue(lot,
                new CustodyAccount(account, new ResourceCustody.Container(container), Map.of(lot.id(), 64), Map.of()));
        state = state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(resources))
                .humanPopulation(state.humanPopulation().accrueHunger(resident, 27_000L)));
        var start = ResidentMealProcess.selectSourceAtYield(state, resident, 27_000L).orElseThrow();
        return ResidentMealProcess.reduceStarted(state, resident, start);
    }
}
