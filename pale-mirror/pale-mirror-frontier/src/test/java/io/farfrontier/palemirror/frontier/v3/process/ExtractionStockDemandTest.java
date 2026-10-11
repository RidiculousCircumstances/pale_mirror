package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionStockDemandTest {
    @Test void toolAdmissionRejectsPhysicallyUnreachableStorageWithoutAcquiringWorkerOrStock() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:tool-route"), 41,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r4")));
        var home = state.extractionSites().deposits().values().iterator().next().site().settlementId();
        var resident = state.humanPopulation().residents().values().stream()
                .filter(value -> value.settlementId().equals(home)).findFirst().orElseThrow();
        assertTrue(ExtractionToolSelection.select(state, resident).isPresent());
        var blocked = state;
        for (var container : state.inventory().containers().values()) {
            if (!container.ownerId().equals(home) || container.storage().isEmpty()) continue;
            blocked = blocked.recordPhysicalDelta(new PhysicalDelta(container.storage().orElseThrow().endpoint().station().support().offset(0, 1, 0),
                    PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:blocked-tool-access"));
        }
        assertTrue(ExtractionToolSelection.select(blocked, resident).isEmpty());
        assertFalse(ExtractionWorkPlanning.availableWork(blocked, resident, 100));
        assertEquals(state.inventory(), blocked.inventory());
        assertEquals(state.actorExecutions(), blocked.actorExecutions());
    }
    @Test void fulfilledReserveWithdrawsStaffingAndAdmissionThenConsumptionReopensBothAfterRecovery() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:extraction-demand"), 41,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r4")));
        // All known partners have their local stone target; there is no outstanding export demand.
        for (var home : state.bootstrap().settlements()) {
            var lot = new SubjectId("lot:test-stone-" + home.id().value().replace(':', '-'));
            var temporary = new SubjectId("custody:test-stone-" + home.id().value().replace(':', '-'));
            var ledger = state.inventory().fungibleResources().issue(new ResourceLot(lot, home.id(), "minecraft:cobblestone", 64,
                    "test:reserve", List.of()), new CustodyAccount(temporary, new ResourceCustody.WorldCarrier(
                    UUID.nameUUIDFromBytes(home.id().value().getBytes(java.nio.charset.StandardCharsets.UTF_8))), Map.of(lot, 64), Map.of()));
            ledger = ledger.transfer(temporary, ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(home.id())), Map.of(lot, 64), Map.of());
            state = state.withInventory(state.inventory().withFungibleResources(ledger));
        }
        var deposit = state.extractionSites().deposits().values().iterator().next(); var home = deposit.site().settlementId();
        var rules = state.bootstrap().ruleset().labour().entries().get(ResidentWorkKind.EXTRACTION);
        var resident = state.humanPopulation().residents().values().stream().filter(value -> value.settlementId().equals(home)).findFirst().orElseThrow();
        assertFalse(ExtractionWorkPlanning.demanded(state, home));
        assertEquals(0, ExtractionWorkPlanning.staffingDemand(state, home, rules).targetWorkers());
        assertFalse(ExtractionWorkPlanning.availableWork(state, resident, 100));
        var lot = new SubjectId("lot:test-stone-" + home.value().replace(':', '-'));
        var ledger = state.inventory().fungibleResources().destroy(ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(home)), Map.of(lot, 1), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertTrue(ExtractionWorkPlanning.demanded(state, home));
        assertEquals(rules.targetWorkers(), ExtractionWorkPlanning.staffingDemand(state, home, rules).targetWorkers());
        assertTrue(ExtractionWorkPlanning.availableWork(state, resident, 100));
    }
}
