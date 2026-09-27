package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FungibleResourceLedgerTest {
    private static final SubjectId OWNER = new SubjectId("settlement:one");
    private static final SubjectId LOT = new SubjectId("lot:bread-genesis");
    private static final SubjectId DEPOT = new SubjectId("container:depot");
    private static final SubjectId DEPOT_ACCOUNT = new SubjectId("custody:depot");

    @Test
    void bakerColdOrderCarriesClaimedWheatThroughRecipeAndReturnsUnclaimedBread() {
        SubjectId worker = new SubjectId("resident:baker");
        SubjectId job = new SubjectId("job:bakery-one");
        SubjectId actorAccount = new SubjectId("custody:bakery-worker");
        SubjectId station = new SubjectId("container:bakery-station");
        SubjectId stationAccount = new SubjectId("custody:bakery-station");
        ProductionStationSpec machine = new ProductionStationSpec(new SubjectId("station:bakery-one"),
                new SubjectId("structure:bakery-one"), station, ProductionStationSpec.Capability.BAKING,
                SurfaceAnchor.at(2, 64, 2), SurfaceAnchor.at(3, 64, 2), 0, 1);
        SubjectId wheatId = new SubjectId("lot:bakery-wheat");
        SubjectId claimId = new SubjectId("claim:bakery-wheat");
        SubjectId breadId = new SubjectId("lot:bakery-bread");
        ResourceLot wheat = new ResourceLot(wheatId, OWNER, "minecraft:wheat", 64, "harvest:one", List.of());
        FungibleResourceLedger reserved = FungibleResourceLedger.empty().issue(wheat,
                new CustodyAccount(DEPOT_ACCOUNT, new ResourceCustody.Container(DEPOT), Map.of(wheatId, 64), Map.of()))
                .reserve(new ClaimAllocation(claimId, job, OWNER, "minecraft:wheat", 64,
                        Map.of(wheatId, 64), ClaimPurpose.PRODUCTION_WORK), DEPOT_ACCOUNT);
        InventoryCustody.ContainerSlot slot = new InventoryCustody.ContainerSlot(DEPOT, 0);
        ActorContainerItemOrder take = new ActorContainerItemOrder(job, worker, ActorContainerItemOrder.Direction.TAKE,
                new ActorContainerItemOrder.Portion.Fungible(DEPOT_ACCOUNT, new ResourceCustody.Container(DEPOT),
                        actorAccount, new ResourceCustody.Actor(worker), Optional.of(claimId), "minecraft:wheat",
                        Map.of(wheatId, 64)), new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(DEPOT),
                SurfaceAnchor.at(1, 64, 1), ActorContainerItemOrder.Hand.MAIN, 0, 1);
        FungibleResourceLedger carried = reserved.transferActorOrderCold(take);
        assertEquals(Map.of(wheatId, 64), carried.accounts().get(actorAccount).lotQuantities());
        assertEquals(Map.of(claimId, 64), carried.accounts().get(actorAccount).claimQuantities());
        assertEquals(false, carried.accounts().containsKey(DEPOT_ACCOUNT));
        ActorContainerItemOrder load = new ActorContainerItemOrder(job, worker, ActorContainerItemOrder.Direction.PLACE,
                new ActorContainerItemOrder.Portion.Fungible(actorAccount, new ResourceCustody.Actor(worker),
                        stationAccount, new ResourceCustody.Container(station), Optional.of(claimId), "minecraft:wheat",
                        Map.of(wheatId, 64)), new ActorContainerItemOrder.ContainerEndpoint.FungibleStation(machine, ActorContainerItemOrder.StationPort.INPUT),
                SurfaceAnchor.at(2, 64, 2), ActorContainerItemOrder.Hand.MAIN, 1, 1);
        FungibleResourceLedger loaded = carried.transferActorOrderCold(load);
        assertEquals(false, loaded.accounts().containsKey(actorAccount));
        assertEquals(Map.of(wheatId, 64), loaded.accounts().get(stationAccount).lotQuantities());
        ResourceLot bread = new ResourceLot(breadId, OWNER, "minecraft:bread", 64, "recipe:bread", List.of(wheatId));
        FungibleResourceLedger baked = loaded.transformCold(stationAccount, Map.of(wheatId, 64), Map.of(claimId, 64), bread);
        assertEquals(Map.of(breadId, 64), baked.accounts().get(stationAccount).lotQuantities());
        ActorContainerItemOrder unload = new ActorContainerItemOrder(job, worker, ActorContainerItemOrder.Direction.TAKE,
                new ActorContainerItemOrder.Portion.Fungible(stationAccount, new ResourceCustody.Container(station),
                        actorAccount, new ResourceCustody.Actor(worker), Optional.empty(), "minecraft:bread",
                        Map.of(breadId, 64)), new ActorContainerItemOrder.ContainerEndpoint.FungibleStation(machine, ActorContainerItemOrder.StationPort.OUTPUT),
                SurfaceAnchor.at(2, 64, 2), ActorContainerItemOrder.Hand.MAIN, 2, 1);
        FungibleResourceLedger collected = baked.transferActorOrderCold(unload);
        assertEquals(false, collected.accounts().containsKey(stationAccount));
        ActorContainerItemOrder place = new ActorContainerItemOrder(job, worker, ActorContainerItemOrder.Direction.PLACE,
                new ActorContainerItemOrder.Portion.Fungible(actorAccount, new ResourceCustody.Actor(worker),
                        DEPOT_ACCOUNT, new ResourceCustody.Container(DEPOT), Optional.empty(), "minecraft:bread",
                        Map.of(breadId, 64)), new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(DEPOT),
                SurfaceAnchor.at(1, 64, 1), ActorContainerItemOrder.Hand.MAIN, 3, 1);
        FungibleResourceLedger delivered = collected.transferActorOrderCold(place);
        assertEquals(Map.of(breadId, 64), delivered.accounts().get(DEPOT_ACCOUNT).lotQuantities());
        assertEquals(false, delivered.accounts().containsKey(actorAccount));
        assertEquals(0, delivered.totalQuantity(OWNER, "minecraft:wheat"));
        assertEquals(64, delivered.totalQuantity(OWNER, "minecraft:bread"));
        assertThrows(IllegalArgumentException.class, () -> reserved.transferActorOrderCold(new ActorContainerItemOrder(
                new SubjectId("job:foreign"), worker, ActorContainerItemOrder.Direction.TAKE, take.portion(), take.containerEndpoint(),
                take.station(), take.hand(), take.goalOrdinal(), take.goalRevision())));
        assertThrows(IllegalArgumentException.class, () -> delivered.transferActorOrderCold(place),
                "a completed delivery cannot spend the actor-held output again");
    }

    @Test
    void bakerObservedOrderFencesSourceAndHandEpochs() {
        SubjectId worker = new SubjectId("resident:hot-baker");
        SubjectId job = new SubjectId("job:hot-bakery");
        SubjectId actorAccount = new SubjectId("custody:hot-baker");
        SubjectId station = new SubjectId("container:hot-bakery-station");
        SubjectId stationAccount = new SubjectId("custody:hot-bakery-station");
        ProductionStationSpec machine = new ProductionStationSpec(new SubjectId("station:hot-bakery"),
                new SubjectId("structure:hot-bakery"), station, ProductionStationSpec.Capability.BAKING,
                SurfaceAnchor.at(2, 64, 2), SurfaceAnchor.at(3, 64, 2), 0, 1);
        SubjectId wheatId = new SubjectId("lot:hot-bakery-wheat");
        SubjectId claimId = new SubjectId("claim:hot-bakery-wheat");
        ResourceLot wheat = new ResourceLot(wheatId, OWNER, "minecraft:wheat", 64, "harvest:hot", List.of());
        FungibleResourceLedger cold = FungibleResourceLedger.empty().issue(wheat,
                new CustodyAccount(DEPOT_ACCOUNT, new ResourceCustody.Container(DEPOT), Map.of(wheatId, 64), Map.of()))
                .reserve(new ClaimAllocation(claimId, job, OWNER, "minecraft:wheat", 64,
                        Map.of(wheatId, 64), ClaimPurpose.PRODUCTION_WORK), DEPOT_ACCOUNT);
        InventoryCustody.ContainerSlot slot = new InventoryCustody.ContainerSlot(DEPOT, 0);
        FungibleResourceLedger hot = cold.rebind(DEPOT_ACCOUNT, 7,
                FungiblePhysicalObservation.bind(cold, DEPOT_ACCOUNT, 7, List.of(
                        new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(slot), "minecraft:wheat", 64))));
        ActorContainerItemOrder take = new ActorContainerItemOrder(job, worker, ActorContainerItemOrder.Direction.TAKE,
                new ActorContainerItemOrder.Portion.Fungible(DEPOT_ACCOUNT, new ResourceCustody.Container(DEPOT),
                        actorAccount, new ResourceCustody.Actor(worker), Optional.of(claimId), "minecraft:wheat",
                        Map.of(wheatId, 64)), new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(DEPOT),
                SurfaceAnchor.at(1, 64, 1), ActorContainerItemOrder.Hand.MAIN, 0, 1);
        PhysicalStackAddress.ActorHand hand = new PhysicalStackAddress.ActorHand(worker, uuid(6));
        PhysicalStackBinding handWheat = new PhysicalStackBinding(new SubjectId("binding:hot-baker-wheat"), actorAccount,
                hand, 3, "minecraft:wheat", Map.of(wheatId, 64), Map.of(claimId, 64));
        assertThrows(IllegalArgumentException.class, () -> hot.transferActorOrderObserved(take, 8, 3, List.of(), List.of(handWheat)));
        FungibleResourceLedger carried = hot.transferActorOrderObserved(take, 7, 3, List.of(), List.of(handWheat));
        assertEquals(hand, carried.bindings().get(handWheat.id()).address());
        assertEquals(false, carried.accounts().containsKey(DEPOT_ACCOUNT));
        assertThrows(IllegalArgumentException.class, () -> carried.transferActorOrderObserved(take, 7, 3,
                List.of(), List.of(handWheat)), "the same source may not be picked up twice");
        ActorContainerItemOrder load = new ActorContainerItemOrder(job, worker, ActorContainerItemOrder.Direction.PLACE,
                new ActorContainerItemOrder.Portion.Fungible(actorAccount, new ResourceCustody.Actor(worker),
                        stationAccount, new ResourceCustody.Container(station), Optional.of(claimId), "minecraft:wheat",
                        Map.of(wheatId, 64)), new ActorContainerItemOrder.ContainerEndpoint.FungibleStation(machine, ActorContainerItemOrder.StationPort.INPUT),
                SurfaceAnchor.at(2, 64, 2), ActorContainerItemOrder.Hand.MAIN, 1, 1);
        InventoryCustody.ContainerSlot stationSlot = new InventoryCustody.ContainerSlot(station, 0);
        PhysicalStackBinding stationWheat = new PhysicalStackBinding(new SubjectId("binding:station-wheat"), stationAccount,
                new PhysicalStackAddress.ContainerSlot(stationSlot), 4, "minecraft:wheat", Map.of(wheatId, 64), Map.of(claimId, 64));
        FungibleResourceLedger loaded = carried.transferActorOrderObserved(load, 3, 4, List.of(), List.of(stationWheat));
        assertEquals(false, loaded.accounts().containsKey(actorAccount));
        SubjectId breadId = new SubjectId("lot:hot-bakery-bread");
        ResourceLot bread = new ResourceLot(breadId, OWNER, "minecraft:bread", 64, "recipe:bread", List.of(wheatId));
        FungibleResourceLedger baked = loaded.transformObserved(stationAccount, 4, Map.of(wheatId, 64),
                Map.of(claimId, 64), bread,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(station, machine.outputSlot())), "minecraft:bread", 64)));
        assertEquals(Map.of(breadId, 64), baked.accounts().get(stationAccount).lotQuantities());
        ActorContainerItemOrder unload = new ActorContainerItemOrder(job, worker, ActorContainerItemOrder.Direction.TAKE,
                new ActorContainerItemOrder.Portion.Fungible(stationAccount, new ResourceCustody.Container(station),
                        actorAccount, new ResourceCustody.Actor(worker), Optional.empty(), "minecraft:bread",
                        Map.of(breadId, 64)), new ActorContainerItemOrder.ContainerEndpoint.FungibleStation(machine, ActorContainerItemOrder.StationPort.OUTPUT),
                SurfaceAnchor.at(2, 64, 2), ActorContainerItemOrder.Hand.MAIN, 2, 1);
        PhysicalStackBinding handBread = new PhysicalStackBinding(new SubjectId("binding:hand-bread"), actorAccount,
                hand, 5, "minecraft:bread", Map.of(breadId, 64), Map.of());
        FungibleResourceLedger wrongOutputPort = loaded.transformObserved(stationAccount, 4, Map.of(wheatId, 64),
                Map.of(claimId, 64), bread,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(stationSlot), "minecraft:bread", 64)));
        assertThrows(IllegalArgumentException.class, () -> wrongOutputPort.transferActorOrderObserved(unload, 4, 5,
                List.of(), List.of(handBread)), "finished bread cannot be unloaded from the machine input port");
        FungibleResourceLedger collected = baked.transferActorOrderObserved(unload, 4, 5, List.of(), List.of(handBread));
        assertEquals(false, collected.accounts().containsKey(stationAccount));
        ActorContainerItemOrder place = new ActorContainerItemOrder(job, worker, ActorContainerItemOrder.Direction.PLACE,
                new ActorContainerItemOrder.Portion.Fungible(actorAccount, new ResourceCustody.Actor(worker),
                        DEPOT_ACCOUNT, new ResourceCustody.Container(DEPOT), Optional.empty(), "minecraft:bread",
                        Map.of(breadId, 64)), new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(DEPOT),
                SurfaceAnchor.at(1, 64, 1), ActorContainerItemOrder.Hand.MAIN, 3, 1);
        PhysicalStackBinding returnedBread = new PhysicalStackBinding(new SubjectId("binding:returned-bread"),
                DEPOT_ACCOUNT, new PhysicalStackAddress.ContainerSlot(slot), 8, "minecraft:bread",
                Map.of(breadId, 64), Map.of());
        assertThrows(IllegalArgumentException.class, () -> collected.transferActorOrderObserved(place, 4, 8,
                List.of(), List.of(returnedBread)), "a different actor-hand epoch cannot authorize delivery");
        FungibleResourceLedger delivered = collected.transferActorOrderObserved(place, 5, 8,
                List.of(), List.of(returnedBread));
        assertEquals(false, delivered.accounts().containsKey(actorAccount));
        assertEquals(Map.of(breadId, 64), delivered.accounts().get(DEPOT_ACCOUNT).lotQuantities());
        assertEquals(0, delivered.totalQuantity(OWNER, "minecraft:wheat"));
        assertEquals(64, delivered.totalQuantity(OWNER, "minecraft:bread"));
    }

    @Test
    void coldActorHarvestAccruesOneObservedUnitIntoOneRetainedPart() {
        SubjectId farmer = new SubjectId("resident:field-worker");
        SubjectId account = new SubjectId("custody:field-worker-harvest");
        ResourceLot first = new ResourceLot(new SubjectId("lot:field-part"), OWNER,
                "minecraft:wheat", 1, "field:one:epoch-1:part-0", List.of());
        FungibleResourceLedger one = FungibleResourceLedger.empty()
                .accrueColdActorHarvestPart(first, account, farmer);
        ResourceLot second = first.withQuantity(2);
        FungibleResourceLedger two = one.accrueColdActorHarvestPart(second, account, farmer);
        assertEquals(2, two.totalQuantity(OWNER, "minecraft:wheat"));
        assertEquals(Map.of(first.id(), 2), two.accounts().get(account).lotQuantities());
        assertThrows(IllegalArgumentException.class, () -> one.accrueColdActorHarvestPart(first, account, farmer),
                "replaying one cell cannot credit the same unit again");
        assertThrows(IllegalArgumentException.class, () -> one.accrueColdActorHarvestPart(first.withQuantity(3), account, farmer),
                "a missing cell receipt cannot jump the held amount");
        assertThrows(IllegalArgumentException.class, () -> one.accrueColdActorHarvestPart(
                new ResourceLot(first.id(), OWNER, first.itemKind(), 2, "field:forged-source", List.of()), account, farmer));
        assertThrows(IllegalArgumentException.class, () -> one.accrueColdActorHarvestPart(second, account,
                new SubjectId("resident:other-farmer")));
        assertThrows(IllegalArgumentException.class, () -> one.accrueColdActorHarvestPart(
                new ResourceLot(new SubjectId("lot:another-field-part"), OWNER, "minecraft:wheat", 1,
                        "field:one:epoch-1:part-1", List.of()), new SubjectId("custody:second-harvest"), farmer),
                "one farmer cannot open a second live cargo part before the first is handed off");
    }

    @Test
    void hotActorHarvestRequiresTheSameObservedHandAndAuthorityEpochForEveryUnit() {
        SubjectId farmer = new SubjectId("resident:hot-field-worker");
        SubjectId account = new SubjectId("custody:hot-field-worker-harvest");
        ResourceLot first = new ResourceLot(new SubjectId("lot:hot-field-part"), OWNER,
                "minecraft:wheat", 1, "field:hot:epoch-1:part-0", List.of());
        PhysicalStackAddress.ActorHand hand = new PhysicalStackAddress.ActorHand(farmer, uuid(7));
        FungiblePhysicalObservation.Stack observedOne = new FungiblePhysicalObservation.Stack(hand, "minecraft:wheat", 1);
        FungibleResourceLedger one = FungibleResourceLedger.empty().accrueObservedActorHarvestPart(
                first, account, farmer, 4L, observedOne);
        ResourceLot second = first.withQuantity(2);
        FungiblePhysicalObservation.Stack observedTwo = new FungiblePhysicalObservation.Stack(hand, "minecraft:wheat", 2);
        FungibleResourceLedger two = one.accrueObservedActorHarvestPart(second, account, farmer, 4L, observedTwo);
        assertEquals(2, two.totalQuantity(OWNER, "minecraft:wheat"));
        assertEquals(hand, two.bindings().values().iterator().next().address());
        assertThrows(IllegalArgumentException.class, () -> one.accrueObservedActorHarvestPart(
                first, account, farmer, 4L, observedOne), "one observed cell must not be credited twice");
        assertThrows(IllegalArgumentException.class, () -> one.accrueObservedActorHarvestPart(
                second, account, farmer, 5L, observedTwo), "a stale or invented authority epoch cannot add wheat");
        assertThrows(IllegalArgumentException.class, () -> one.accrueObservedActorHarvestPart(
                second, account, farmer, 4L, new FungiblePhysicalObservation.Stack(
                        new PhysicalStackAddress.ActorHand(farmer, uuid(8)), "minecraft:wheat", 2)),
                "a different body requires an explicit handoff, not a crop receipt");
        assertThrows(IllegalArgumentException.class, () -> one.accrueObservedActorHarvestPart(
                second, account, farmer, 4L, new FungiblePhysicalObservation.Stack(
                        new PhysicalStackAddress.PlayerSlot(uuid(7), 0), "minecraft:wheat", 2)));
    }

    @Test
    void physicalAddressMustBelongToTheExactCustodyOwner() {
        FungibleResourceLedger depot = issue(10);
        assertThrows(IllegalArgumentException.class, () -> depot.rebind(DEPOT_ACCOUNT, 1L, List.of(
                binding("binding:foreign-depot", 1L, 10,
                        new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(
                                new SubjectId("container:other-depot"), 0))))));
        assertThrows(IllegalArgumentException.class, () -> depot.rebind(DEPOT_ACCOUNT, 1L, List.of(
                binding("binding:depot-as-player", 1L, 10, new PhysicalStackAddress.PlayerSlot(uuid(9), 0)))));

        SubjectId playerId = new SubjectId("custody:player-owner");
        CustodyAccount player = new CustodyAccount(playerId, new ResourceCustody.Player(uuid(4)),
                Map.of(LOT, 10), Map.of());
        FungibleResourceLedger held = depot.transferToNewAccount(DEPOT_ACCOUNT, player);
        assertThrows(IllegalArgumentException.class, () -> held.rebind(playerId, 1L, List.of(
                new PhysicalStackBinding(new SubjectId("binding:wrong-player"), playerId,
                        new PhysicalStackAddress.PlayerSlot(uuid(5), 0), 1L, "minecraft:bread",
                        Map.of(LOT, 10), Map.of()))));

        SubjectId carrierAccountId = new SubjectId("custody:world-owner");
        CustodyAccount carrier = new CustodyAccount(carrierAccountId, new ResourceCustody.WorldCarrier(uuid(7)),
                Map.of(LOT, 10), Map.of());
        FungibleResourceLedger worldHeld = depot.transferToNewAccount(DEPOT_ACCOUNT, carrier);
        assertEquals(1, worldHeld.rebind(carrierAccountId, 1L, List.of(new PhysicalStackBinding(
                new SubjectId("binding:world-owner"), carrierAccountId,
                new PhysicalStackAddress.WorldEntity(uuid(7)), 1L, "minecraft:bread",
                Map.of(LOT, 10), Map.of()))).bindings().size());
        assertThrows(IllegalArgumentException.class, () -> worldHeld.rebind(carrierAccountId, 1L, List.of(
                new PhysicalStackBinding(new SubjectId("binding:wrong-world-owner"), carrierAccountId,
                        new PhysicalStackAddress.WorldEntity(uuid(8)), 1L, "minecraft:bread",
                        Map.of(LOT, 10), Map.of()))));
    }

    @Test
    void splitPartialMoveAndMergePreserveOneExactFungibleTotalWithoutStackIdentity() {
        FungibleResourceLedger issued = issue(10);
        ResourceLot child = new ResourceLot(new SubjectId("lot:bread-child"), OWNER, "minecraft:bread", 4, "bootstrap", List.of(LOT));
        FungibleResourceLedger split = issued.split(DEPOT_ACCOUNT, LOT, child, 4);
        CustodyAccount player = new CustodyAccount(new SubjectId("custody:player"), new ResourceCustody.Player(uuid(2)),
                Map.of(child.id(), 4), Map.of());
        FungibleResourceLedger moved = split.transferToNewAccount(DEPOT_ACCOUNT, player);

        assertEquals(10, moved.totalQuantity(OWNER, "minecraft:bread"));
        assertEquals(6, moved.accounts().get(DEPOT_ACCOUNT).lotQuantities().get(LOT));
        assertEquals(4, moved.accounts().get(player.id()).lotQuantities().get(child.id()));

        ResourceLot merged = new ResourceLot(new SubjectId("lot:bread-merged"), OWNER, "minecraft:bread", 10, "bootstrap", List.of(LOT, child.id()));
        FungibleResourceLedger returned = moved.transfer(player.id(), DEPOT_ACCOUNT, Map.of(child.id(), 4), Map.of()).merge(DEPOT_ACCOUNT, LOT, child.id(), merged);
        assertEquals(10, returned.totalQuantity(OWNER, "minecraft:bread"));
        assertEquals(Map.of(merged.id(), 10), returned.accounts().get(DEPOT_ACCOUNT).lotQuantities());
    }

    @Test
    void reservedPortionMovesExactlyAndCannotBeDoubleAllocatedOrOverConsumed() {
        FungibleResourceLedger reserved = issue(10).reserve(new ClaimAllocation(new SubjectId("claim:provision"), new SubjectId("process:provision"), OWNER,
                "minecraft:bread", 4, Map.of(), ClaimPurpose.EXTERNAL_RESERVATION), DEPOT_ACCOUNT);
        CustodyAccount player = new CustodyAccount(new SubjectId("custody:player"), new ResourceCustody.Player(uuid(3)), Map.of(LOT, 4),
                Map.of(new SubjectId("claim:provision"), 4));
        FungibleResourceLedger stolen = reserved.transferToNewAccount(DEPOT_ACCOUNT, player);

        assertEquals(10, stolen.totalQuantity(OWNER, "minecraft:bread"));
        assertEquals(4, stolen.accounts().get(player.id()).claimQuantities().get(new SubjectId("claim:provision")));
        assertThrows(IllegalArgumentException.class, () -> reserved.reserve(new ClaimAllocation(new SubjectId("claim:duplicate"), new SubjectId("process:other"), OWNER,
                "minecraft:bread", 8, Map.of(), ClaimPurpose.EXTERNAL_RESERVATION), DEPOT_ACCOUNT));
        assertThrows(IllegalArgumentException.class, () -> stolen.destroy(player.id(), Map.of(LOT, 5), Map.of(new SubjectId("claim:provision"), 4)));
    }

    @Test
    void partialColdRecipeConsumesOnlyItsReservedInputPortionBeforeItCreatesOutput() {
        SubjectId claimId = new SubjectId("claim:bakery");
        FungibleResourceLedger reserved = issue(10).reserve(new ClaimAllocation(claimId, new SubjectId("process:bakery"), OWNER,
                "minecraft:bread", 4, Map.of(), ClaimPurpose.EXTERNAL_RESERVATION), DEPOT_ACCOUNT);
        ResourceLot output = new ResourceLot(new SubjectId("lot:toast"), OWNER, "minecraft:toast", 4, "recipe:toast", List.of(LOT));

        FungibleResourceLedger transformed = reserved.transformCold(DEPOT_ACCOUNT, Map.of(LOT, 4), Map.of(claimId, 4), output);

        assertEquals(Map.of(LOT, 6, output.id(), 4), transformed.accounts().get(DEPOT_ACCOUNT).lotQuantities());
        assertEquals(6, transformed.totalQuantity(OWNER, "minecraft:bread"));
        assertEquals(4, transformed.totalQuantity(OWNER, "minecraft:toast"));
        assertThrows(IllegalArgumentException.class, () -> reserved.transformCold(DEPOT_ACCOUNT, Map.of(LOT, 3), Map.of(claimId, 4), output));
        assertThrows(IllegalStateException.class, () -> issue(10).rebind(DEPOT_ACCOUNT, 3L, List.of(binding("binding:recipe", 3L, 10,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0))))).transformCold(DEPOT_ACCOUNT,
                Map.of(LOT, 4), Map.of(), output));
    }

    @Test
    void coldRecipeCanConsumeTwoDistinctProvenanceLotsUnderOneClaim() {
        SubjectId first = new SubjectId("lot:wheat-field-one");
        SubjectId second = new SubjectId("lot:wheat-field-two");
        SubjectId claimId = new SubjectId("claim:bread-from-two-fields");
        ResourceLot firstLot = new ResourceLot(first, OWNER, "minecraft:wheat", 32, "harvest:field-one", List.of());
        ResourceLot secondLot = new ResourceLot(second, OWNER, "minecraft:wheat", 32, "harvest:field-two", List.of());
        FungibleResourceLedger source = new FungibleResourceLedger(Map.of(first, firstLot, second, secondLot), Map.of(),
                Map.of(DEPOT_ACCOUNT, new CustodyAccount(DEPOT_ACCOUNT, new ResourceCustody.Container(DEPOT),
                        Map.of(first, 32, second, 32), Map.of())), Map.of());
        ClaimAllocation claim = new ClaimAllocation(claimId, new SubjectId("job:two-field-bread"), OWNER, "minecraft:wheat", 64,
                Map.of(), ClaimPurpose.EXTERNAL_RESERVATION);
        FungibleResourceLedger reserved = source.reserve(claim, DEPOT_ACCOUNT);
        ResourceLot output = new ResourceLot(new SubjectId("lot:two-field-bread"), OWNER, "minecraft:bread", 64,
                "recipe:bread", List.of(first, second));

        FungibleResourceLedger transformed = reserved.transformCold(DEPOT_ACCOUNT,
                Map.of(first, 32, second, 32), Map.of(claimId, 64), output);

        assertEquals(Map.of(output.id(), output), transformed.lots());
        assertEquals(Map.of(output.id(), 64), transformed.accounts().get(DEPOT_ACCOUNT).lotQuantities());
        assertEquals(Map.of(), transformed.claims());
        assertEquals(List.of(first, second), transformed.lots().get(output.id()).lineage());
        assertThrows(IllegalArgumentException.class, () -> reserved.transformCold(DEPOT_ACCOUNT,
                Map.of(first, 32), Map.of(claimId, 64), output));
    }

    @Test
    void pinnedRecipeClaimProtectsBothLotsFromAnotherConsumerAndBindsBothPhysicalStacks() {
        SubjectId first = new SubjectId("lot:two-source-first");
        SubjectId second = new SubjectId("lot:two-source-second");
        SubjectId pinId = new SubjectId("claim:two-source-recipe");
        Map<SubjectId, Integer> portions = Map.of(first, 32, second, 32);
        FungibleResourceLedger source = new FungibleResourceLedger(Map.of(
                first, new ResourceLot(first, OWNER, "minecraft:wheat", 32, "harvest:first", List.of()),
                second, new ResourceLot(second, OWNER, "minecraft:wheat", 32, "harvest:second", List.of())), Map.of(),
                Map.of(DEPOT_ACCOUNT, new CustodyAccount(DEPOT_ACCOUNT, new ResourceCustody.Container(DEPOT), portions, Map.of())), Map.of());
        ClaimAllocation pin = new ClaimAllocation(pinId, new SubjectId("job:two-source-recipe"), OWNER, "minecraft:wheat", 64,
                portions, ClaimPurpose.EXTERNAL_RESERVATION);
        FungibleResourceLedger cold = source.reserve(pin, DEPOT_ACCOUNT);
        assertEquals(portions, cold.claims().get(pinId).lotQuantities());
        assertThrows(IllegalArgumentException.class, () -> cold.destroy(DEPOT_ACCOUNT, Map.of(first, 1), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> cold.destroy(DEPOT_ACCOUNT, Map.of(first, 1), Map.of(pinId, 1)));
        assertThrows(IllegalArgumentException.class, () -> cold.reserve(new ClaimAllocation(new SubjectId("claim:other"),
                new SubjectId("work:other"), OWNER, "minecraft:wheat", 1, Map.of(), ClaimPurpose.EXTERNAL_RESERVATION), DEPOT_ACCOUNT));

        var stacks = List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(DEPOT, 0)), "minecraft:wheat", 32),
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(DEPOT, 1)), "minecraft:wheat", 32));
        FungibleResourceLedger bound = source.rebind(DEPOT_ACCOUNT, 7L, FungiblePhysicalObservation.bind(source, DEPOT_ACCOUNT, 7L, stacks))
                .reserveBound(pin, DEPOT_ACCOUNT, 7L);
        assertEquals(2, bound.bindings().values().stream().filter(binding -> binding.claimQuantities().containsKey(pinId)).count());
        assertEquals(64, bound.bindings().values().stream().mapToInt(binding -> binding.claimQuantities().getOrDefault(pinId, 0)).sum());
        assertEquals(cold, bound.releaseBindings(DEPOT_ACCOUNT, 7L));
    }

    @Test
    void movingPinnedLotForfeitsItsClaimEvenWhenIndependentStackClaimColumnStayedBehind() {
        SubjectId unclaimed = new SubjectId("lot:a-unclaimed-wheat");
        SubjectId pinned = new SubjectId("lot:z-pinned-wheat");
        SubjectId claimId = new SubjectId("claim:pinned-work");
        var source = new FungibleResourceLedger(Map.of(
                unclaimed, new ResourceLot(unclaimed, OWNER, "minecraft:wheat", 32, "harvest:unclaimed", List.of()),
                pinned, new ResourceLot(pinned, OWNER, "minecraft:wheat", 32, "harvest:pinned", List.of())), Map.of(),
                Map.of(DEPOT_ACCOUNT, new CustodyAccount(DEPOT_ACCOUNT, new ResourceCustody.Container(DEPOT),
                        Map.of(unclaimed, 32, pinned, 32), Map.of())), Map.of());
        var claim = new ClaimAllocation(claimId, new SubjectId("job:pinned-work"), OWNER, "minecraft:wheat", 32,
                Map.of(pinned, 32), ClaimPurpose.EXTERNAL_RESERVATION);
        var reserved = source.reserve(claim, DEPOT_ACCOUNT);
        var binding = new PhysicalStackBinding(new SubjectId("binding:mixed-wheat"), DEPOT_ACCOUNT,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0)), 7L,
                "minecraft:wheat", Map.of(unclaimed, 32, pinned, 32), Map.of(claimId, 32));
        var hot = reserved.rebind(DEPOT_ACCOUNT, 7L, List.of(binding));
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000081");
        var observed = FungiblePhysicalHandoff.departToNew(hot, DEPOT_ACCOUNT, 7L, binding, 32,
                new SubjectId("custody:player-pinned-wheat"), new ResourceCustody.Player(player), 1L,
                new PhysicalStackAddress.PlayerSlot(player, 0));
        assertEquals(Map.of(pinned, 32), observed.lotQuantities());
        assertEquals(Map.of(), observed.claimQuantities(), "old independent claim split retained the column on the source stack");
        var forfeited = observed.forfeitAffectedClaims(hot);
        assertEquals(java.util.Set.of(claimId), forfeited.forfeitedClaimIds());
        var effective = forfeited.withoutForfeitedClaims();
        var released = hot.releaseClaims(forfeited.forfeitedClaimIds());
        var transferred = released.transferObservedToNewAccount(effective.sourceAccountId(), effective.destinationAccount(),
                effective.sourceEpoch(), effective.destinationEpoch(), effective.lotQuantities(), effective.claimQuantities(),
                effective.remainingSource(), effective.destinationBindings());
        assertEquals(32, transferred.accounts().get(effective.destinationAccount().id()).lotQuantities().get(pinned));
        assertEquals(Map.of(), transferred.claims());
    }

    @Test
    void observedLayoutAlignsPinnedClaimWithItsLotAndRepositionsOlderGenericClaim() {
        SubjectId first = new SubjectId("lot:a-field-wheat");
        SubjectId second = new SubjectId("lot:z-field-wheat");
        SubjectId genericId = new SubjectId("claim:a-generic-work");
        SubjectId pinnedId = new SubjectId("claim:z-pinned-work");
        var source = new FungibleResourceLedger(Map.of(
                first, new ResourceLot(first, OWNER, "minecraft:wheat", 32, "harvest:first", List.of()),
                second, new ResourceLot(second, OWNER, "minecraft:wheat", 32, "harvest:second", List.of())), Map.of(),
                Map.of(DEPOT_ACCOUNT, new CustodyAccount(DEPOT_ACCOUNT, new ResourceCustody.Container(DEPOT),
                        Map.of(first, 32, second, 32), Map.of())), Map.of());
        var stacks = List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(DEPOT, 0)), "minecraft:wheat", 32),
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(DEPOT, 1)), "minecraft:wheat", 32));
        var generic = new ClaimAllocation(genericId, new SubjectId("work:generic"), OWNER, "minecraft:wheat", 32,
                Map.of(), ClaimPurpose.EXTERNAL_RESERVATION);
        var pinned = new ClaimAllocation(pinnedId, new SubjectId("work:pinned"), OWNER, "minecraft:wheat", 32,
                Map.of(first, 32), ClaimPurpose.EXTERNAL_RESERVATION);
        var hot = source.rebind(DEPOT_ACCOUNT, 7L, FungiblePhysicalObservation.bind(source, DEPOT_ACCOUNT, 7L, stacks))
                .reserveBound(generic, DEPOT_ACCOUNT, 7L).reserveBound(pinned, DEPOT_ACCOUNT, 7L);
        var firstStack = hot.bindings().values().stream().filter(binding -> binding.lotQuantities().containsKey(first)).findFirst().orElseThrow();
        var secondStack = hot.bindings().values().stream().filter(binding -> binding.lotQuantities().containsKey(second)).findFirst().orElseThrow();
        assertEquals(Map.of(pinnedId, 32), firstStack.claimQuantities());
        assertEquals(Map.of(genericId, 32), secondStack.claimQuantities());
        var rebound = hot.releaseBindings(DEPOT_ACCOUNT, 7L);
        var restored = rebound.rebind(DEPOT_ACCOUNT, 8L, FungiblePhysicalObservation.bind(rebound, DEPOT_ACCOUNT, 8L, stacks));
        assertEquals(Map.of(pinnedId, 32), restored.bindings().values().stream()
                .filter(binding -> binding.lotQuantities().containsKey(first)).findFirst().orElseThrow().claimQuantities());
    }

    @Test
    void transientPhysicalBindingsAcceptARealSplitButFenceStaleOrDuplicateStackEvidence() {
        FungibleResourceLedger issued = issue(10);
        PhysicalStackBinding first = binding("binding:one", 7L, 6, new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0)));
        PhysicalStackBinding second = binding("binding:two", 7L, 4, new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 1)));
        FungibleResourceLedger split = issued.rebind(DEPOT_ACCOUNT, 7L, List.of(first, second));

        assertEquals(10, split.bindings().values().stream().mapToInt(PhysicalStackBinding::quantity).sum());
        assertThrows(IllegalStateException.class, () -> split.destroy(DEPOT_ACCOUNT, Map.of(LOT, 1), Map.of()),
                "an observer-free HOT hopper/container binding must fence concurrent COLD spending");
        assertThrows(IllegalArgumentException.class, () -> split.releaseBindings(DEPOT_ACCOUNT, 6L));
        FungibleResourceLedger released = split.releaseBindings(DEPOT_ACCOUNT, 7L);
        assertEquals(9, released.destroy(DEPOT_ACCOUNT, Map.of(LOT, 1), Map.of()).totalQuantity(OWNER, "minecraft:bread"));
        assertThrows(IllegalArgumentException.class, () -> split.rebind(DEPOT_ACCOUNT, 8L, List.of(first)));
        assertThrows(IllegalArgumentException.class, () -> issued.rebind(DEPOT_ACCOUNT, 7L, List.of(first,
                binding("binding:duplicate-address", 7L, 4, new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0))))));
        assertThrows(IllegalArgumentException.class, () -> issued.rebind(DEPOT_ACCOUNT, 7L, List.of(binding("binding:over", 7L, 11,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0))))));
    }

    @Test
    void boundReservationExtendsTheCurrentLeaseLayoutWithoutOpeningColdCustody() {
        FungibleResourceLedger hot = issue(10).rebind(DEPOT_ACCOUNT, 7L, List.of(
                binding("binding:one", 7L, 6, new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0))),
                binding("binding:two", 7L, 4, new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 1)))));
        ClaimAllocation claim = new ClaimAllocation(new SubjectId("claim:hot-production"), new SubjectId("job:production-hot"), OWNER,
                "minecraft:bread", 7, Map.of(), ClaimPurpose.EXTERNAL_RESERVATION);

        FungibleResourceLedger reserved = hot.reserveBound(claim, DEPOT_ACCOUNT, 7L);

        assertEquals(Map.of(claim.id(), 7), reserved.accounts().get(DEPOT_ACCOUNT).claimQuantities());
        assertEquals(7L, reserved.bindings().get(new SubjectId("binding:one")).authorityEpoch());
        assertEquals(Map.of(claim.id(), 6), reserved.bindings().get(new SubjectId("binding:one")).claimQuantities());
        assertEquals(Map.of(claim.id(), 1), reserved.bindings().get(new SubjectId("binding:two")).claimQuantities());
        assertThrows(IllegalStateException.class, () -> reserved.destroy(DEPOT_ACCOUNT, Map.of(LOT, 1), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> hot.reserveBound(claim, DEPOT_ACCOUNT, 6L));
        assertThrows(IllegalArgumentException.class, () -> hot.reserveBound(new ClaimAllocation(new SubjectId("claim:over-hot"),
                new SubjectId("job:over-hot"), OWNER, "minecraft:bread", 11, Map.of(), ClaimPurpose.EXTERNAL_RESERVATION), DEPOT_ACCOUNT, 7L));
    }

    @Test
    void boundReservationSkipsEarlierStacksOfAnotherKindOrEconomicOwner() {
        var wheat = new ResourceLot(new SubjectId("lot:wheat"), OWNER, "minecraft:wheat", 10, "bootstrap", List.of());
        var foreignBread = new ResourceLot(new SubjectId("lot:foreign-bread"), new SubjectId("settlement:other"), "minecraft:bread", 10, "bootstrap", List.of());
        var bread = new ResourceLot(LOT, OWNER, "minecraft:bread", 10, "bootstrap", List.of());
        var account = new CustodyAccount(DEPOT_ACCOUNT, new ResourceCustody.Container(DEPOT),
                Map.of(wheat.id(), 10, foreignBread.id(), 10, bread.id(), 10), Map.of());
        var cold = new FungibleResourceLedger(Map.of(wheat.id(), wheat, foreignBread.id(), foreignBread, bread.id(), bread),
                Map.of(), Map.of(account.id(), account), Map.of());
        var bindings = List.of(
                new PhysicalStackBinding(new SubjectId("binding:a-wheat"), account.id(), new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0)),
                        7L, wheat.itemKind(), Map.of(wheat.id(), 10), Map.of(), ""),
                new PhysicalStackBinding(new SubjectId("binding:b-foreign"), account.id(), new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 1)),
                        7L, foreignBread.itemKind(), Map.of(foreignBread.id(), 10), Map.of(), ""),
                new PhysicalStackBinding(new SubjectId("binding:c-own"), account.id(), new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 2)),
                        7L, bread.itemKind(), Map.of(bread.id(), 10), Map.of(), ""));
        var hot = cold.rebind(account.id(), 7L, bindings);
        var claim = new ClaimAllocation(new SubjectId("claim:own-bread"), new SubjectId("job:own-bread"), OWNER, bread.itemKind(), 7,
                Map.of(), ClaimPurpose.EXTERNAL_RESERVATION);
        var reserved = hot.reserveBound(claim, account.id(), 7L);
        assertEquals(Map.of(), reserved.bindings().get(bindings.get(0).id()).claimQuantities());
        assertEquals(Map.of(), reserved.bindings().get(bindings.get(1).id()).claimQuantities());
        assertEquals(Map.of(claim.id(), 7), reserved.bindings().get(bindings.get(2).id()).claimQuantities());
        assertEquals(hot.lots(), reserved.lots());
    }

    @Test
    void hotVanillaSplitAndMergeRebindOneLotWithoutStackIdentityOrQuantityDrift() {
        FungibleResourceLedger issued = issue(10);
        List<FungiblePhysicalObservation.Stack> splitStacks = List.of(
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0)), "minecraft:bread", 6),
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 1)), "minecraft:bread", 4));
        FungibleResourceLedger split = issued.rebind(DEPOT_ACCOUNT, 9L,
                FungiblePhysicalObservation.bind(issued, DEPOT_ACCOUNT, 9L, splitStacks));
        FungibleResourceLedger merged = split.rebind(DEPOT_ACCOUNT, 9L, FungiblePhysicalObservation.bind(split, DEPOT_ACCOUNT, 9L, List.of(
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 3)), "minecraft:bread", 10))));

        assertEquals(10, merged.totalQuantity(OWNER, "minecraft:bread"));
        assertEquals(1, merged.bindings().size());
        assertThrows(IllegalArgumentException.class, () -> FungiblePhysicalObservation.bind(issued, DEPOT_ACCOUNT, 9L, List.of(
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0)), "minecraft:carrot", 10))));
    }

    @Test
    void observedPartialHandoffMovesOneBoundedPortionWithoutAnInterimColdSpendingWindow() {
        FungibleResourceLedger hot = issue(10).rebind(DEPOT_ACCOUNT, 4L, List.of(binding("binding:source", 4L, 10,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0)))));
        SubjectId playerAccountId = new SubjectId("custody:player-handoff");
        CustodyAccount player = new CustodyAccount(playerAccountId, new ResourceCustody.Player(uuid(4)), Map.of(LOT, 4), Map.of());
        PhysicalStackBinding remaining = binding("binding:source-remainder", 4L, 6,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0)));
        PhysicalStackBinding held = new PhysicalStackBinding(new SubjectId("binding:player-handoff"), playerAccountId,
                new PhysicalStackAddress.PlayerSlot(uuid(4), 0), 5L, "minecraft:bread", Map.of(LOT, 4), Map.of());

        FungibleResourceLedger moved = hot.transferObservedToNewAccount(DEPOT_ACCOUNT, player, 4L, 5L, Map.of(LOT, 4), Map.of(), List.of(remaining), List.of(held));
        assertEquals(Map.of(LOT, 6), moved.accounts().get(DEPOT_ACCOUNT).lotQuantities());
        assertEquals(Map.of(LOT, 4), moved.accounts().get(playerAccountId).lotQuantities());
        assertEquals(10, moved.totalQuantity(OWNER, "minecraft:bread"));
        assertThrows(IllegalArgumentException.class, () -> hot.transferObservedToNewAccount(DEPOT_ACCOUNT, player, 3L, 5L,
                Map.of(LOT, 4), Map.of(), List.of(remaining), List.of(held)));
    }

    @Test
    void observedHotDepartureCreatesColdCargoOnlyAfterItFencesTheSourceRemainder() {
        FungibleResourceLedger hot = issue(10).rebind(DEPOT_ACCOUNT, 4L, List.of(binding("binding:source", 4L, 10,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0)))));
        SubjectId cargoId = new SubjectId("cargo:hot-departure");
        SubjectId cargoAccountId = new SubjectId("custody:cargo-hot-departure");
        CustodyAccount cargo = new CustodyAccount(cargoAccountId, new ResourceCustody.Cargo(cargoId), Map.of(LOT, 4), Map.of());
        PhysicalStackBinding remainder = binding("binding:source-remainder", 4L, 6,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0)));

        FungibleResourceLedger moved = hot.transferObservedToColdNewAccount(DEPOT_ACCOUNT, cargo, 4L, Map.of(LOT, 4), Map.of(), List.of(remainder));

        assertEquals(Map.of(LOT, 6), moved.accounts().get(DEPOT_ACCOUNT).lotQuantities());
        assertEquals(Map.of(LOT, 4), moved.accounts().get(cargoAccountId).lotQuantities());
        assertEquals(10, moved.totalQuantity(OWNER, "minecraft:bread"));
        assertEquals(List.of(remainder), moved.bindings().values().stream().toList());
        assertThrows(IllegalArgumentException.class, () -> hot.transferObservedToColdNewAccount(DEPOT_ACCOUNT, cargo, 3L,
                Map.of(LOT, 4), Map.of(), List.of(remainder)));
    }

    @Test
    void observedHandoffIntoAnExistingContainerRetainsBothBalancesBehindOneFreshBinding() {
        SubjectId sourceLotId = new SubjectId("lot:player-bread");
        SubjectId destinationLotId = new SubjectId("lot:depot-bread");
        SubjectId sourceAccountId = new SubjectId("custody:player-source");
        SubjectId destinationAccountId = new SubjectId("custody:depot-existing");
        SubjectId destinationContainer = new SubjectId("container:depot-existing");
        CustodyAccount source = new CustodyAccount(sourceAccountId, new ResourceCustody.Player(uuid(5)), Map.of(sourceLotId, 4), Map.of());
        CustodyAccount destination = new CustodyAccount(destinationAccountId, new ResourceCustody.Container(destinationContainer), Map.of(destinationLotId, 6), Map.of());
        PhysicalStackBinding sourceBinding = new PhysicalStackBinding(new SubjectId("binding:player-source"), sourceAccountId,
                new PhysicalStackAddress.PlayerSlot(uuid(5), 0), 7L, "minecraft:bread", Map.of(sourceLotId, 4), Map.of());
        FungibleResourceLedger ledger = new FungibleResourceLedger(
                Map.of(sourceLotId, new ResourceLot(sourceLotId, OWNER, "minecraft:bread", 4, "player", List.of()),
                        destinationLotId, new ResourceLot(destinationLotId, OWNER, "minecraft:bread", 6, "depot", List.of())),
                Map.of(), Map.of(sourceAccountId, source, destinationAccountId, destination), Map.of(sourceBinding.id(), sourceBinding));
        PhysicalStackBinding destinationBinding = new PhysicalStackBinding(new SubjectId("binding:depot-existing"), destinationAccountId,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(destinationContainer, 0)), 8L,
                "minecraft:bread", Map.of(sourceLotId, 4, destinationLotId, 6), Map.of());

        FungibleResourceLedger deposited = ledger.transferObservedToExistingAccount(sourceAccountId, destinationAccountId, 7L, 8L,
                Map.of(sourceLotId, 4), Map.of(), List.of(), List.of(destinationBinding));

        assertEquals(Map.of(destinationLotId, 6, sourceLotId, 4), deposited.accounts().get(destinationAccountId).lotQuantities());
        assertEquals(10, deposited.totalQuantity(OWNER, "minecraft:bread"));
        assertEquals(List.of(destinationBinding), deposited.bindings().values().stream().toList());
        assertThrows(IllegalArgumentException.class, () -> ledger.transferObservedToExistingAccount(sourceAccountId, destinationAccountId, 6L, 8L,
                Map.of(sourceLotId, 4), Map.of(), List.of(), List.of(destinationBinding)));
    }

    @Test
    void cargoDeliveryRetitlesOnlyAnIsolatedLotAndDischargesItsShipmentClaim() {
        SubjectId cargoId = new SubjectId("cargo:bread-shipment");
        SubjectId cargoAccountId = new SubjectId("custody:cargo-bread-shipment");
        SubjectId receiver = new SubjectId("container:hive-store");
        SubjectId hive = new SubjectId("hive:one");
        SubjectId claimId = new SubjectId("claim:bread-shipment");
        ResourceLot child = new ResourceLot(new SubjectId("lot:bread-shipment"), OWNER, "minecraft:bread", 4, "bootstrap", List.of(LOT));
        FungibleResourceLedger loaded = issue(10).split(DEPOT_ACCOUNT, LOT, child, 4).reserve(new ClaimAllocation(claimId,
                new SubjectId("contract:bread-shipment"), OWNER, "minecraft:bread", 4, Map.of(), ClaimPurpose.EXTERNAL_RESERVATION), DEPOT_ACCOUNT)
                .transferToNewAccount(DEPOT_ACCOUNT, new CustodyAccount(cargoAccountId, new ResourceCustody.Cargo(cargoId), Map.of(child.id(), 4), Map.of(claimId, 4)));
        CustodyAccount destination = new CustodyAccount(new SubjectId("custody:hive-store"), new ResourceCustody.Container(receiver), Map.of(child.id(), 4), Map.of());

        FungibleResourceLedger delivered = loaded.deliverCargoToContainer(cargoAccountId, destination, hive);

        assertEquals(hive, delivered.lots().get(child.id()).economicOwnerId());
        assertEquals(Map.of(child.id(), 4), delivered.accounts().get(destination.id()).lotQuantities());
        assertEquals(6, delivered.totalQuantity(OWNER, "minecraft:bread"));
        assertEquals(4, delivered.totalQuantity(hive, "minecraft:bread"));
        assertEquals(Map.of(), delivered.claims());
        assertThrows(IllegalArgumentException.class, () -> issue(10).deliverCargoToContainer(DEPOT_ACCOUNT, destination, hive));
    }

    @Test
    void oneContainerAccountReconcilesSeveralResourceKindsWithoutCrossKindMerging() {
        SubjectId carrotId = new SubjectId("lot:carrot");
        ResourceLot carrot = new ResourceLot(carrotId, OWNER, "minecraft:carrot", 4, "bootstrap", List.of());
        CustodyAccount account = new CustodyAccount(DEPOT_ACCOUNT, new ResourceCustody.Container(DEPOT),
                Map.of(LOT, 10, carrotId, 4), Map.of());
        FungibleResourceLedger ledger = new FungibleResourceLedger(
                Map.of(LOT, new ResourceLot(LOT, OWNER, "minecraft:bread", 10, "bootstrap", List.of()), carrotId, carrot),
                Map.of(), Map.of(DEPOT_ACCOUNT, account), Map.of());
        List<PhysicalStackBinding> bindings = FungiblePhysicalObservation.bind(ledger, DEPOT_ACCOUNT, 2L, List.of(
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 0)), "minecraft:bread", 10),
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(DEPOT, 1)), "minecraft:carrot", 4)));

        assertEquals(2, bindings.size());
        assertEquals("minecraft:bread", bindings.getFirst().itemKind());
        assertEquals("minecraft:carrot", bindings.getLast().itemKind());
    }

    @Test
    void snapshotRoundTripPreservesLotsClaimsAccountsAndTransientBindingsExactly() {
        WorldId world = new WorldId("frontier:fungible-ledger-round-trip");
        FrontierWorldState baseline = new FrontierWorldStateCodec().decode(FrontierEngines
                .create(FrontierWorldRuntimeDefinition.configuration(world, 17L)).checkpoint().canonicalState());
        SubjectId containerId = new SubjectId("container:1-depot"); SubjectId owner = new SubjectId("settlement:1");
        SubjectId lotId = new SubjectId("lot:snapshot"); SubjectId accountId = new SubjectId("custody:snapshot");
        ResourceLot lot = new ResourceLot(lotId, owner, "minecraft:bread", 10, "snapshot", List.of());
        CustodyAccount account = new CustodyAccount(accountId, new ResourceCustody.Container(containerId), Map.of(lotId, 10), Map.of());
        PhysicalStackBinding binding = new PhysicalStackBinding(new SubjectId("binding:snapshot"), accountId,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(containerId, 1)), 3L, "minecraft:bread",
                Map.of(lotId, 10), Map.of(new SubjectId("claim:snapshot"), 4));
        FungibleResourceLedger resources = FungibleResourceLedger.empty().issue(lot, account)
                .reserve(new ClaimAllocation(new SubjectId("claim:snapshot"), new SubjectId("process:snapshot"), owner, "minecraft:bread", 4,
                        Map.of(), ClaimPurpose.EXTERNAL_RESERVATION), accountId)
                .rebind(accountId, 3L, List.of(binding));
        FrontierWorldState retained = baseline.withInventory(baseline.inventory().withFungibleResources(resources));

        assertEquals(resources, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(retained)).inventory().fungibleResources());
    }

    private static FungibleResourceLedger issue(int quantity) {
        ResourceLot lot = new ResourceLot(LOT, OWNER, "minecraft:bread", quantity, "bootstrap", List.of());
        CustodyAccount account = new CustodyAccount(DEPOT_ACCOUNT, new ResourceCustody.Container(DEPOT), Map.of(LOT, quantity), Map.of());
        return FungibleResourceLedger.empty().issue(lot, account);
    }

    private static PhysicalStackBinding binding(String id, long epoch, int quantity, PhysicalStackAddress address) {
        return new PhysicalStackBinding(new SubjectId(id), DEPOT_ACCOUNT, address, epoch, "minecraft:bread", Map.of(LOT, quantity), Map.of());
    }

    private static UUID uuid(int tail) { return UUID.fromString("00000000-0000-0000-0000-00000000000" + tail); }
}
