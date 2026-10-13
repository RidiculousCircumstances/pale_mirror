package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Connected claim-owner loss boundary shared by menu edits and native chest drops. */
class ResidentMealResourceLossTest {
    @Test void chestDropAndMenuWithdrawalReleaseTheSameMealWithoutConsumingFood() {
        for (boolean groundDrop : List.of(false, true)) {
            var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:meal-source-loss"), 421L));
            var home = state.bootstrap().settlements().getFirst(); var resident = home.residents().getFirst().id();
            var container = new SubjectId("container:meal-source-loss"); var account = ReferenceContainerCustody.scopeId(container);
            var station = SettlementServiceAccessPoints.depotPort(state, home.id()).exteriorApproach();
            var endpoint = new ShipmentEndpoint.Storage(home.id(), container, station, java.util.Optional.empty(), station);
            var policy = new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 100, ResourceStorageRole.WAREHOUSE);
            state = new ResourceStorageDeclared(new ContainerRecord(container, home.id(), 27, ContainerPurpose.RESOURCE_STORAGE,
                    java.util.Optional.empty(), java.util.Optional.of(policy)), station.support().offset(1, 1, 0)).apply(state, home.id());
            var lot = new ResourceLot(new SubjectId("lot:meal-source-loss"), home.id(), "minecraft:bread", 64, "test:meal", List.of());
            var resources = state.inventory().fungibleResources().issue(lot,
                    new CustodyAccount(account, new ResourceCustody.Container(container), Map.of(lot.id(), 64), Map.of()));
            state = state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(resources))
                    .humanPopulation(state.humanPopulation().accrueHunger(resident, 27_000L)));
            var start = ResidentMealProcess.selectSourceAtYield(state, resident, 27_000L).orElseThrow();
            state = ResidentMealProcess.reduceStarted(state, resident, start);
            resources = state.inventory().fungibleResources();
            var layout = List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                    new InventoryCustody.ContainerSlot(container, 0)), "minecraft:bread", 64));
            resources = resources.rebind(account, 1, FungiblePhysicalObservation.bind(resources, account, 1, layout));
            state = ReferenceContainerCustodyFixtures.observedAndHeld(state.withInventory(state.inventory()
                    .withFungibleResources(resources).withSurfaceStatus(container, ContainerSurfaceStatus.PREPARED)
                    .withSurfaceStatus(container, ContainerSurfaceStatus.ACTIVE)), container);
            var before = state;
            FrontierWorldState result;
            if (groundDrop) {
                var entity = java.util.UUID.randomUUID();
                var binding = resources.bindings().values().stream().filter(b -> b.accountId().equals(account)).findFirst().orElseThrow();
                var drop = FungiblePhysicalHandoff.departToNew(resources, account, 1, binding, 0,
                        new SubjectId("custody:meal-drop"), new ResourceCustody.Ground(entity, home.anchor()), 1,
                        new PhysicalStackAddress.WorldEntity(entity)).forfeitAffectedClaims(resources);
                assertTrue(FungibleClaimForfeitureStateSupport.supports(state, drop));
                var plan = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class,
                        FrontierWorldPhysicalObservationProcess.planFungibleHandoff(state, drop, 27_001L));
                assertTrue(plan.events().stream().anyMatch(event -> event.payload() instanceof
                        io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Cancelled));
                result = FungibleResourceHandoffStateSupport.apply(state, drop);
                assertEquals(64, result.inventory().fungibleResources().accounts().get(drop.destinationAccount().id())
                        .lotQuantities().values().stream().mapToInt(Integer::intValue).sum());
                assertThrows(IllegalArgumentException.class, () -> FungibleResourceHandoffStateSupport.apply(result, drop));
            } else {
                var loss = assertInstanceOf(FungibleStockDepartureObserved.class, FungibleContainerPlayerEdit.classify(
                        resources, account, container, home.id(), 1, java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                        List.of(), SourceStockClaimLoss.admissibleClaims(state, account)));
                result = SourceStockClaimLoss.settle(state, loss);
            }
            assertFalse(result.humanPopulation().meals().containsKey(resident));
            assertFalse(result.inventory().fungibleResources().claims().containsKey(start.meal().claimId()));
            assertTrue(result.actorExecutions().actors().get(resident).current().isEmpty());
            assertEquals(before.humanPopulation().nutrition(resident), result.humanPopulation().nutrition(resident));
            var codec = new FrontierWorldStateCodec(); assertEquals(result, codec.decode(codec.encode(result)));
        }
    }
}
