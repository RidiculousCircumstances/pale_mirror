package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResidentMealHotServiceTurnTest {
    @Test
    void secondHotArrivalWaitsForTheFirstResidentInsteadOfChangingItsMeal() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:resident-hot-service-turn"), 421L));
        Settlement settlement = initial.bootstrap().settlements().getFirst();
        SubjectId first = settlement.residents().get(0).id();
        SubjectId second = settlement.residents().get(1).id();
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        var bread = initial.inventory().fungibleResources().transformCold(
                ReferenceContainerCustody.scopeId(depot),
                Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), Map.of(),
                new ResourceLot(new SubjectId("lot:hot-service-bread"), settlement.id(),
                        ResidentMeal.BREAD_KIND, 64, "test", List.of()));
        FrontierWorldState state = initial.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(initial.inventory().withFungibleResources(bread))
                .humanPopulation(initial.humanPopulation().accrueHunger(first, 27_000L)
                        .accrueHunger(second, 27_000L)));
        state = ResidentMealProcess.reduceStarted(state, first,
                ResidentMealProcess.selectSourceAtYield(state, first, 27_000L).orElseThrow());
        state = ResidentMealProcess.reduceStarted(state, second,
                ResidentMealProcess.selectSourceAtYield(state, second, 27_000L).orElseThrow());
        for (SubjectId resident : List.of(first, second)) {
            AmbientActorLease lease = AmbientActorProcess.nextLease(state, resident, new SimInstant(27_001L));
            state = AmbientLeaseStateProcess.prepare(state, lease);
            state = AmbientLeaseStateProcess.transition(state, resident, AmbientLeaseStatus.HOT);
        }
        var service = SettlementDepotServicePort.forDepot(settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow()).serviceSurface();
        ResidentMealHotArrived firstArrival = new ResidentMealHotArrived(first,
                state.ambientLeases().get(first).revision(), service.standingBody());
        assertTrue(ResidentMealProcess.hotArrivalHasServiceTurn(state, first, firstArrival));
        state = ResidentMealProcess.reduceHotArrived(state, first, firstArrival);
        ResidentMealHotArrived secondArrival = new ResidentMealHotArrived(second,
                state.ambientLeases().get(second).revision(), service.standingBody());
        assertFalse(ResidentMealProcess.hotArrivalHasServiceTurn(state, second, secondArrival));
        assertEquals(ResidentMeal.Phase.MOVE, state.humanPopulation().meals().get(second).phase());
        FrontierWorldState occupied = state;
        assertThrows(IllegalArgumentException.class, () -> ResidentMealProcess.reduceHotArrived(
                occupied, second, secondArrival));
    }
}
