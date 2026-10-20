package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionStockDemandTest {
    @Test void withdrawnMarginalDemandDoesNotCancelAnAcceptedPhysicalSource() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:admitted-mining-commitment"), 41,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r8")));
        var deposit = state.extractionSites().deposits().values().iterator().next();
        var home = deposit.site().settlementId();
        var permissions = SettlementWorkPolicy.permissions(state, home);
        var resident = state.humanPopulation().residents().values().stream().filter(r -> r.settlementId().equals(home))
                .filter(r -> permissions.permits(ResidentWorkKind.EXTRACTION, r.id()))
                .findFirst().orElseThrow();
        var offered = new ExtractionWorkAdmission(deposit.site().id()).discover(state, resident, 100).orElseThrow().execution();
        state = ExtractionWorkAdmission.start(state, offered, 100);
        for (var settlement : state.bootstrap().settlements()) {
            var lot = new SubjectId("lot:admitted-mining-reserve-" + settlement.id().value().replace(':', '-'));
            var account = new SubjectId("custody:admitted-mining-reserve-" + settlement.id().value().replace(':', '-'));
            int quantity = state.bootstrap().ruleset().extraction().reserveItems();
            var ledger = state.inventory().fungibleResources().issue(new ResourceLot(lot, settlement.id(), "minecraft:cobblestone", quantity,
                    "fixture:reserve-arrived", List.of()), new CustodyAccount(account, new ResourceCustody.Container(FrontierWorldState.depotId(settlement.id())), Map.of(lot, quantity), Map.of()));
            ledger = ledger.transfer(account, ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(settlement.id())), Map.of(lot, quantity), Map.of());
            state = state.withInventory(state.inventory().withFungibleResources(ledger));
        }
        var codec = new FrontierWorldStateCodec(); var restored = codec.decode(codec.encode(state));
        assertFalse(ExtractionWorkPolicy.requested(restored, offered.work(), 100L));
        assertTrue(ExtractionWorkPolicy.mayFinishTarget(restored, offered.work(), 100L));
        assertThrows(IllegalArgumentException.class, () -> ExtractionColdWork.apply(restored, offered.work().id(),
                new ExtractionWorkProgressed(offered.work().id(), offered.work().revision(), ExtractionWorkProgressed.Operation.END_WORK), 101));
    }
    @Test void anAdmittedCoalSourceCoversOnlyItsOwnYieldAndLeavesTheNextMinerForNeededStone() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:assigned-mining-demand"), 41,
                FrontierRulesets.fuelledQuarryGraybox()));
        var home = new SubjectId("settlement:1");
        // Fill peer reserves too: an actual export opportunity is additional
        // demand, so it would invalidate a fixture claiming a one-unit deficit.
        for (var settlement : state.bootstrap().settlements()) for (var entry : Map.of(
                "minecraft:coal", settlement.id().equals(home) ? 3 : 4,
                "minecraft:cobblestone", settlement.id().equals(home) ? 191 : 192).entrySet()) {
            var lot = new SubjectId("lot:test-priority-" + settlement.id().value().replace(':', '-') + "-" + entry.getKey().replace(':', '-'));
            var account = new SubjectId("custody:test-priority-" + settlement.id().value().replace(':', '-') + "-" + entry.getKey().replace(':', '-'));
            var ledger = state.inventory().fungibleResources().issue(new ResourceLot(lot, settlement.id(), entry.getKey(), entry.getValue(),
                    "fixture:owned-stock", List.of()), new CustodyAccount(account,
                            new ResourceCustody.Container(FrontierWorldState.depotId(settlement.id())), Map.of(lot, entry.getValue()), Map.of()));
            state = state.withInventory(state.inventory().withFungibleResources(ledger.transfer(account,
                    ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(settlement.id())), Map.of(lot, entry.getValue()), Map.of())));
        }
        var deposit = state.extractionSites().deposits().values().stream().filter(value -> value.site().settlementId().equals(home)).findFirst().orElseThrow();
        var coal = deposit.site().layout().cells().stream().filter(cell -> cell.definition().before().kind().equals("minecraft:coal_ore")).findFirst().orElseThrow();
        var opened = new ExtractionDeposit(deposit.site(), deposit.cells(), deposit.geometry(), new WorkAreaDevelopment(2, deposit.cells().keySet()));
        // Prior excavation geometry is input only; clearing access cannot mint stock.
        var access = new HashSet<>(coal.prerequisites()); var queue = new ArrayDeque<>(access);
        while (!queue.isEmpty()) for (long id : deposit.site().layout().require(queue.removeFirst()).prerequisites())
            if (access.add(id)) queue.add(id);
        for (long id : access) opened = opened.extracted(id, 1, "fixture:prior-access-" + id);
        state = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(opened)));
        var worker = state.humanPopulation().resident(new SubjectId("resident:1-10"));
        var event = new ExtractionWorkAdmission(deposit.site().id()).discover(state, worker, 100).orElseThrow().execution();
        assertEquals("minecraft:coal", event.work().outputKind());
        var admitted = ExtractionWorkAdmission.start(state, event, 100);
        assertEquals(state.inventory(), admitted.inventory(), "assignment is neither a resource grant nor a transfer");
        var priorities = ExtractionPriorities.read(admitted, opened, Optional.empty(), 100L);
        assertEquals(0, priorities.products().get("minecraft:coal").missing());
        assertEquals(1, priorities.products().get("minecraft:cobblestone").missing());
        assertEquals(1, ExtractionPriorities.read(admitted, opened, Optional.of(event.work().id()), 100L)
                .products().get("minecraft:coal").missing(), "an execution cannot count its own target as already completed");
        var successor = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(admitted));
        var second = successor.humanPopulation().resident(new SubjectId("resident:1-11"));
        assertEquals("minecraft:cobblestone", ExtractionWorkAdmission.opportunity(successor, second, deposit.site().id(), 100L).orElseThrow()
                .cell().definition().coldOutput().getFirst().itemKind());
    }
    @Test void toolAdmissionRejectsPhysicallyUnreachableStorageWithoutAcquiringWorkerOrStock() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:tool-route"), 41,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r13")));
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
        // Bounded navigation may retain an unresolved search instead of proving NO_PATH.
        // Neither result permits worker/resource acquisition.
        assertFalse(ExtractionWorkPlanning.availableWork(blocked, resident, 100));
        assertEquals(state.inventory(), blocked.inventory());
        assertEquals(state.actorExecutions(), blocked.actorExecutions());
    }
    @Test void fulfilledReserveWithdrawsStaffingAndAdmissionThenConsumptionReopensBothAfterRecovery() {
        // This fixture deliberately has a stone reserve. Current dynamic-reserve content
        // has no stone consumer and must not request stone simply because a stack was removed.
        String profile = "frontier-v3-quarry-graybox-r8";
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:extraction-demand"), 41,
                FrontierRulesets.installed(profile)));
        int reserve = state.bootstrap().ruleset().extraction().reserveItems();
        // All known partners have their local stone target; there is no outstanding export demand.
        for (var home : state.bootstrap().settlements()) {
            var lot = new SubjectId("lot:test-stone-" + home.id().value().replace(':', '-'));
            var temporary = new SubjectId("custody:test-stone-" + home.id().value().replace(':', '-'));
            var ledger = state.inventory().fungibleResources().issue(new ResourceLot(lot, home.id(), "minecraft:cobblestone", reserve,
                    "test:reserve", List.of()), new CustodyAccount(temporary, new ResourceCustody.WorldCarrier(
                    UUID.nameUUIDFromBytes(home.id().value().getBytes(java.nio.charset.StandardCharsets.UTF_8))), Map.of(lot, reserve), Map.of()));
            ledger = ledger.transfer(temporary, ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(home.id())), Map.of(lot, reserve), Map.of());
            state = state.withInventory(state.inventory().withFungibleResources(ledger));
        }
        var deposit = state.extractionSites().deposits().values().iterator().next(); var home = deposit.site().settlementId();
        var rules = state.bootstrap().ruleset().labour().entries().get(ResidentWorkKind.EXTRACTION);
        var resident = state.humanPopulation().residents().values().stream().filter(value -> value.settlementId().equals(home)).findFirst().orElseThrow();
        assertFalse(ExtractionWorkPlanning.demanded(state, home, 100L));
        assertEquals(0, ExtractionWorkPlanning.staffingDemand(state, home, rules, 100L).targetWorkers());
        assertFalse(ExtractionWorkPlanning.availableWork(state, resident, 100));
        var lot = new SubjectId("lot:test-stone-" + home.value().replace(':', '-'));
        var ledger = state.inventory().fungibleResources().destroy(ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(home)), Map.of(lot, 1), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        state = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertTrue(ExtractionWorkPlanning.demanded(state, home, 100L));
        assertEquals(rules.targetWorkers(), ExtractionWorkPlanning.staffingDemand(state, home, rules, 100L).targetWorkers());
        assertTrue(ExtractionWorkPlanning.availableWork(state, resident, 100));
    }
}
