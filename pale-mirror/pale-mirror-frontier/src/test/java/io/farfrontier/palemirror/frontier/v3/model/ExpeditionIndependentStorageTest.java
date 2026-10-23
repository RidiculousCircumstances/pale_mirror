package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.expedition.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpeditionIndependentStorageTest {
    @Test void retainedTripSelectsSeparateFoodWarehouseAndRetainsItsExactPickupAcrossRecovery() {
        var state = ConsolidatedTradeFixture.initial(new WorldId("frontier:independent-provisions"), 20260918065L, false);
        var seller = new SubjectId("settlement:1");
        var buyer = state.companies().goodsTrade().participants().participants().get(seller).known().getFirst().party().id();
        state = review(state, seller, 400); state = review(state, buyer, 401);
        var original = state.shipments().missions().values().stream().findFirst().orElseThrow();
        var home = original.organization().sponsor().id();
        var cargo = original.organization().home().containerId();
        var port = GoodsParticipantStorage.endpoint(state, cargo);
        var foodStore = new SubjectId("container:provisions-only");
        var exterior = ShipmentEndpointComposition.exteriorApproach(state, port);
        var station = exterior;
        var foodEndpoint = new ShipmentEndpoint.Storage(home, foodStore, station, Optional.empty(), station);
        state = new ResourceStorageDeclared(new ContainerRecord(foodStore, home, 27, ContainerPurpose.RESOURCE_STORAGE,
                Optional.empty(), Optional.of(new ResourceStoragePolicy(foodEndpoint,
                    new ResourceStorageFilter(false, Set.of("minecraft:bread"), Map.of()), 100, ResourceStorageRole.WAREHOUSE))),
                station.support().offset(0, 1, 1)).apply(state, home);
        var ledger = state.inventory().fungibleResources();
        var account = FungibleResourceCustodySupport.accountAtContainer(state, cargo).orElseThrow();
        int totalFood = ledger.totalQuantity(home, "minecraft:bread");
        var selection = FungibleResourceCustodySupport.selectAtAccount(ledger, account.id(), home, "minecraft:bread", 16).orElseThrow();
        ledger = ledger.transferToNewAccount(account.id(),
                new CustodyAccount(ReferenceContainerCustody.scopeId(foodStore), new ResourceCustody.Container(foodStore),
                    selection.lotQuantities(), Map.of()));
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        var replacement = ExpeditionSupplyAuthority.reconsider(state, original, 402).orElseThrow();
        state = ExpeditionSupplyAuthority.replanned(state, new ExpeditionSupplyReplanned(original.id(),
                original.supplies().orElseThrow().revision(), replacement), 402);
        var mission = state.shipments().missions().values().stream().findFirst().orElseThrow();
        assertEquals(cargo, mission.organization().home().containerId());
        var load = mission.supplies().orElseThrow();
        assertFalse(load.allocations().isEmpty());
        assertTrue(load.allocations().stream().allMatch(a -> a.source().containerId().equals(foodStore)), load.allocations().toString());
        assertTrue(TransportMissionProcess.wakeDependencies(state, TransportMissionProcess.progress(mission.id(), 402)).contains(foodStore));
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        var first = load.next().orElseThrow();
        var wrong = state.withActorBody(first.actorId(), port.station().standingBody());
        assertThrows(IllegalArgumentException.class, () -> ExpeditionSupplyAuthority.coldLoaded(wrong, mission.id(), first.claimId()));
        var atSource = state.withActorBody(first.actorId(), first.source().station().standingBody());
        var received = ExpeditionSupplyAuthority.coldLoaded(atSource, mission.id(), first.claimId());
        assertEquals(totalFood, received.inventory().fungibleResources().totalQuantity(home, "minecraft:bread"));
        assertTrue(received.shipments().missions().get(mission.id()).supplies().orElseThrow().allocations().getFirst().loaded());
        assertEquals(received, codec.decode(codec.encode(received)));
        var forged = new ExpeditionSupplyLoad.Allocation(first.claimId(), first.actorId(), port, first.sourceAccountId(),
                first.destinationAccountId(), first.slot(), first.lots(), first.outcome(), first.pending());
        assertThrows(IllegalArgumentException.class, () -> load.replace(first, forged), "an existing promise cannot swap its source port");
    }

    private static FrontierWorldState review(FrontierWorldState state, SubjectId owner, long now) {
        for (var event : GoodsParticipantProcess.plan(state, GoodsParticipantProcess.review(owner, now), new SimInstant(now))) {
            if (event.payload() instanceof ScheduleEffect) continue;
            var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
            var payload = codecs.decode(event.payload().type(), codecs.encode(event.payload()));
            state = FrontierWorldRuntimeDefinition.reduce(state, new FrontierEvent(FrontierEvent.SCHEMA_VERSION,
                    new EventId("event:independent-provisions"), new TransactionId("transaction:independent-provisions"),
                    state.bootstrap().worldId(), new Revision(1), new SimInstant(now), event.subject(),
                    CauseChain.root(new CommandId("command:independent-provisions")), payload));
        }
        return state;
    }
}
