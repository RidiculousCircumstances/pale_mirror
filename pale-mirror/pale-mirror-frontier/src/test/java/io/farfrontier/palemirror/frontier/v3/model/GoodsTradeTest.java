package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Canonical commercial/ledger boundary, not native delivery or visual acceptance. */
class GoodsTradeTest {
    private static final SubjectId SELLER = id("settlement:1"), BUYER = id("settlement:2");
    private static final SubjectId SOURCE = FrontierWorldState.depotId(SELLER), RECEIVER = FrontierWorldState.depotId(BUYER);
    private static final SubjectId SOURCE_ACCOUNT = ReferenceContainerCustody.scopeId(SOURCE), RECEIVER_ACCOUNT = ReferenceContainerCustody.scopeId(RECEIVER);
    private static final SubjectId LOT = id("lot:bootstrap-1-wheat"), CLAIM = id("claim:goods-wheat"), CONTRACT = id("contract:goods-wheat");
    private static final SubjectId HOLD = id("reservation:goods-wheat"), SELL_ORDER = id("order:goods-sell"), BUY_ORDER = id("order:goods-buy");
    private static final GoodsTradeParty SELLER_PARTY = new GoodsTradeParty(SELLER, EconomicOwnerKind.SETTLEMENT_TREASURY);
    private static final GoodsTradeParty BUYER_PARTY = new GoodsTradeParty(BUYER, EconomicOwnerKind.SETTLEMENT_TREASURY);
    private static final FixedScalar PRICE = FixedScalar.whole(1);

    @Test void bothOwnersConsentAndPartialAcceptancePaysOnlyDeliveredRightsAcrossRecovery() {
        FrontierWorldState state = reserved();
        FixedScalar initialBuyer = state.inventory().economics().require(BUYER).balance();
        FixedScalar initialSeller = state.inventory().economics().require(SELLER).balance();
        assertEquals(FixedScalar.whole(60), state.inventory().economics().reservations().get(HOLD).amount());
        state = arrive(state);
        state = fact(state, BUYER, new GoodsTradeAccepted(receipt("receipt:goods-one", 0, 20, "lot:goods-one")));
        assertEquals(initialBuyer.minus(FixedScalar.whole(20)), state.inventory().economics().require(BUYER).balance());
        assertEquals(initialSeller.plus(FixedScalar.whole(20)), state.inventory().economics().require(SELLER).balance());
        assertEquals(40, state.inventory().fungibleResources().claims().get(CLAIM).quantity());
        assertEquals(FixedScalar.whole(40), state.inventory().economics().reservations().get(HOLD).amount());
        assertEquals(44, state.inventory().fungibleResources().lots().get(LOT).quantity());
        assertEquals(SELLER, state.inventory().fungibleResources().lots().get(LOT).economicOwnerId());
        assertEquals(BUYER, state.inventory().fungibleResources().lots().get(id("lot:goods-one")).economicOwnerId());
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        state = fact(state, BUYER, new GoodsTradeAccepted(receipt("receipt:goods-two", 1, 40, "lot:goods-two")));
        assertTrue(state.companies().goodsTrade().contracts().get(CONTRACT).fulfilled());
        assertFalse(state.inventory().economics().reservations().containsKey(HOLD));
        assertFalse(state.inventory().fungibleResources().claims().containsKey(CLAIM));
        assertEquals(4, state.inventory().fungibleResources().lots().get(LOT).quantity());
        assertEquals(initialBuyer.minus(FixedScalar.whole(60)), state.inventory().economics().require(BUYER).balance());
        assertEquals(state, codec.decode(codec.encode(state)));
        FrontierWorldState completed = state;
        assertThrows(IllegalArgumentException.class, () -> fact(completed, BUYER,
                new GoodsTradeAccepted(receipt("receipt:goods-two", 1, 40, "lot:goods-two"))));
    }

    @Test void sellerCannotInventBuyerConsentOrSpendSomeoneElsesBalance() {
        FrontierWorldState state = initial();
        state = fact(state, SELLER, new GoodsTradeOrderPlaced(sellOrder()));
        FrontierWorldState offered = state;
        assertThrows(IllegalArgumentException.class, () -> fact(offered, SELLER, reservation()));
        assertThrows(IllegalArgumentException.class, () -> fact(offered, SELLER, new GoodsTradeOrderPlaced(buyOrder())));
        assertTrue(state.inventory().economics().reservations().isEmpty());
    }

    @Test void receivingPositionOrContractAloneCannotAwardDelivery() {
        FrontierWorldState state = reserved();
        var premature = receipt("receipt:premature", 0, 20, "lot:premature");
        assertThrows(IllegalArgumentException.class, () -> fact(state, BUYER, new GoodsTradeAccepted(premature)));
        assertEquals(0, state.companies().goodsTrade().contracts().get(CONTRACT).revision());
        assertEquals(FixedScalar.whole(60), state.inventory().economics().reservations().get(HOLD).amount());
    }

    @Test void wrongPriceOvercommitAndForgedParticipantKindAreRejected() {
        FrontierWorldState state = fact(fact(initial(), SELLER, new GoodsTradeOrderPlaced(sellOrder())), BUYER, new GoodsTradeOrderPlaced(buyOrder()));
        GoodsTradeContract priced = new GoodsTradeContract(CONTRACT, SELL_ORDER, BUY_ORDER, SELLER_PARTY, BUYER_PARTY,
                SOURCE, RECEIVER, "minecraft:wheat", 60, FixedScalar.whole(4), HOLD, 0, Map.of(CLAIM, 60), Map.of());
        FrontierWorldState offered = state;
        assertThrows(IllegalArgumentException.class, () -> fact(offered, SELLER, new GoodsTradeReserved(priced, reservation().allocations())));
        FrontierWorldState held = fact(state, SELLER, reservation());
        assertThrows(IllegalArgumentException.class, () -> fact(held, SELLER, reservation()));
        var forged = new GoodsTradeOrder(id("order:forged"), new GoodsTradeParty(SELLER, EconomicOwnerKind.COMPANY), BUYER_PARTY,
                GoodsTradeOrder.Side.SELL, SOURCE, "minecraft:wheat", 10, 0, PRICE, 10_000);
        assertThrows(IllegalArgumentException.class, () -> fact(initial(), SELLER, new GoodsTradeOrderPlaced(forged)));
    }

    @Test void breadPromisedForDeliveryIsNotCountedAsAvailablePublicFood() {
        FrontierWorldState state = initial(); var resources = state.inventory().fungibleResources();
        resources = resources.destroy(SOURCE_ACCOUNT, Map.of(LOT, 64), Map.of());
        SubjectId bread = id("lot:trade-bread");
        resources = resources.issue(new ResourceLot(bread, SELLER, "minecraft:bread", 64, "fixture:trade-bread", List.of()),
                new CustodyAccount(SOURCE_ACCOUNT, new ResourceCustody.Container(SOURCE), Map.of(bread, 64), Map.of()));
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        state = fact(state, SELLER, new GoodsTradeOrderPlaced(new GoodsTradeOrder(SELL_ORDER, SELLER_PARTY, BUYER_PARTY,
                GoodsTradeOrder.Side.SELL, SOURCE, "minecraft:bread", 60, 0, PRICE, 10_000)));
        state = fact(state, BUYER, new GoodsTradeOrderPlaced(new GoodsTradeOrder(BUY_ORDER, BUYER_PARTY, SELLER_PARTY,
                GoodsTradeOrder.Side.BUY, RECEIVER, "minecraft:bread", 60, 0, PRICE, 10_000)));
        var contract = new GoodsTradeContract(CONTRACT, SELL_ORDER, BUY_ORDER, SELLER_PARTY, BUYER_PARTY,
                SOURCE, RECEIVER, "minecraft:bread", 60, PRICE, HOLD, 0, Map.of(CLAIM, 60), Map.of());
        state = fact(state, SELLER, new GoodsTradeReserved(contract, List.of(new GoodsTradeStockAllocation(SOURCE_ACCOUNT,
                new ClaimAllocation(CLAIM, CONTRACT, SELLER, "minecraft:bread", 60, Map.of(bread, 60), ClaimPurpose.GOODS_TRADE),
                GoodsTradeStockAllocation.Authority.COLD, 0))));
        assertEquals(64, SettlementFoodPolicy.breadStock(state, SELLER));
        assertEquals(4, SettlementFoodPolicy.reserveCoverageBread(state, SELLER));
        assertEquals(4, SettlementFoodPolicy.coldUsableBread(state, SELLER));
        assertFalse(SettlementFoodPolicy.allowsPopulationGrowth(state, SELLER));
    }

    @Test void inboundCommitmentUsesTheExistingStorageOwnerAndStopsDoubleCountingAfterArrival() {
        FrontierWorldState state = reserved();
        assertEquals(Map.of("minecraft:wheat", 60L), ContainerStorageAdmission.inbound(state, RECEIVER, java.util.Optional.empty()));
        FrontierWorldState arrived = arrive(state);
        assertTrue(ContainerStorageAdmission.inbound(arrived, RECEIVER, java.util.Optional.empty()).isEmpty());
        assertEquals(60, arrived.companies().goodsTrade().contracts().get(CONTRACT).remainingQuantity());
    }

    @Test void activeContractAndReservedStockCannotBeErasedAtPublication() {
        FrontierWorldState state = reserved();
        FrontierWorldState erased = state.withCompanies(state.companies().withGoodsTrade(GoodsTradeState.empty()));
        assertThrows(IllegalArgumentException.class, () -> FrontierReferenceClosure.validateTransition(state, erased, List.of()));
        var resources = state.inventory().fungibleResources().releaseClaim(SOURCE_ACCOUNT, CLAIM);
        FrontierWorldState unreserved = state.withInventory(state.inventory().withFungibleResources(resources));
        assertThrows(IllegalArgumentException.class, () -> FrontierDomainRelationships.validate(unreserved));
    }

    @Test void independentShipmentPortionCanArriveWhileTheOtherPortionIsCancelledWithoutPayment() {
        FrontierWorldState state = reserved(); SubjectId child = id("claim:shipment-one");
        state = fact(state, SELLER, new GoodsTradeClaimPartitioned(CONTRACT,
                new ResourceClaimPartition(SOURCE_ACCOUNT, CLAIM, child, Map.of(LOT, 20))));
        assertEquals(Map.of(CLAIM, 40, child, 20), state.companies().goodsTrade().contracts().get(CONTRACT).outstandingClaims());
        var resources = state.inventory().fungibleResources().transfer(SOURCE_ACCOUNT, RECEIVER_ACCOUNT, Map.of(LOT, 20), Map.of(child, 20));
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        FrontierDomainRelationships.validate(state);
        assertEquals(Map.of("minecraft:wheat", 40L), ContainerStorageAdmission.inbound(state, RECEIVER, java.util.Optional.empty()));
        var receipt = new GoodsTradeAcceptance(id("receipt:shipment-one"), CONTRACT, 0,
                new ResourceTitleTransfer(RECEIVER_ACCOUNT, child, SELLER, BUYER, Map.of(LOT, 20), Map.of(LOT, id("lot:shipment-bought"))));
        FixedScalar beforeBuyer = state.inventory().economics().require(BUYER).balance();
        state = fact(state, BUYER, new GoodsTradeAccepted(receipt));
        var cancelled = new GoodsTradeCancelled(new GoodsTradeDisposition(id("receipt:cancel-rest"), CONTRACT, 1,
                CLAIM, 40, GoodsTradeDisposition.Reason.CANCELLED_BEFORE_LOADING));
        FrontierWorldState partial = state;
        assertThrows(IllegalArgumentException.class, () -> fact(partial, BUYER, cancelled));
        state = fact(state, SELLER, cancelled);
        assertTrue(state.companies().goodsTrade().contracts().get(CONTRACT).terminal());
        assertFalse(state.companies().goodsTrade().contracts().get(CONTRACT).fulfilled());
        assertEquals(beforeBuyer.minus(FixedScalar.whole(20)), state.inventory().economics().require(BUYER).balance());
        assertFalse(state.inventory().economics().reservations().containsKey(HOLD));
        assertEquals(44, state.inventory().fungibleResources().accounts().get(SOURCE_ACCOUNT).lotQuantities().get(LOT));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        FrontierWorldState closed = state;
        assertThrows(IllegalArgumentException.class, () -> fact(closed, SELLER, cancelled));
        var retired = new GoodsTradeRetired(java.util.Set.of(SELL_ORDER, BUY_ORDER), java.util.Set.of(CONTRACT));
        assertThrows(IllegalArgumentException.class, () -> fact(closed, GoodsTradeMarketIdentity.OWNER, retired));
        assertThrows(IllegalArgumentException.class, () -> fact(partial, GoodsTradeMarketIdentity.OWNER, retired, 10_001));
        FrontierWorldState compacted = fact(closed, GoodsTradeMarketIdentity.OWNER, retired, 10_001);
        assertEquals(GoodsTradeState.empty(), compacted.companies().goodsTrade());
        assertEquals(closed.inventory(), compacted.inventory());
        assertEquals(compacted, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(compacted)));
        FrontierWorldState delivered = arrive(reserved());
        assertThrows(IllegalArgumentException.class, () -> fact(delivered, SELLER, new GoodsTradeCancelled(
                new GoodsTradeDisposition(id("receipt:too-late"), CONTRACT, 0, CLAIM, 60,
                        GoodsTradeDisposition.Reason.CANCELLED_BEFORE_LOADING))));
    }

    @Test void observedPlayerRemovalClosesTheAffectedPromiseWithoutAwardingDeliveryOrMoney() {
        FrontierWorldState state = reserved(); var resources = state.inventory().fungibleResources();
        resources = resources.rebind(SOURCE_ACCOUNT, 7, FungiblePhysicalObservation.bind(resources, SOURCE_ACCOUNT, 7,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(SOURCE, 0)), "minecraft:wheat", 64))));
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        var binding = resources.bindings().values().stream().filter(b -> b.accountId().equals(SOURCE_ACCOUNT)).findFirst().orElseThrow();
        var player = java.util.UUID.fromString("00000000-0000-0000-0000-000000000131");
        var observed = FungiblePhysicalHandoff.departToNew(resources, SOURCE_ACCOUNT, 7, binding, 32,
                id("custody:goods-player"), new ResourceCustody.Player(player), 1,
                new PhysicalStackAddress.PlayerSlot(player, 0)).forfeitAffectedClaims(resources);
        FixedScalar buyerBalance = state.inventory().economics().require(BUYER).balance();
        assertTrue(FungibleClaimForfeitureStateSupport.supports(state, observed));
        FrontierWorldState after = FungibleClaimForfeitureStateSupport.apply(state, observed);
        FrontierReferenceClosure.validateTransition(state, after, List.of());
        var contract = after.companies().goodsTrade().contracts().get(CONTRACT);
        assertTrue(contract.terminal()); assertFalse(contract.fulfilled()); assertTrue(contract.acceptances().isEmpty());
        assertEquals(GoodsTradeDisposition.Reason.OBSERVED_ALLOCATION_CHANGED, contract.dispositions().values().iterator().next().reason());
        assertEquals(buyerBalance, after.inventory().economics().require(BUYER).balance());
        assertFalse(after.inventory().economics().reservations().containsKey(HOLD));
        assertEquals(SELLER, after.inventory().fungibleResources().lots().get(LOT).economicOwnerId());
        assertEquals(32, after.inventory().fungibleResources().accounts().get(id("custody:goods-player")).lotQuantities().get(LOT));
        assertEquals(after, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(after)));
    }

    @Test void manySmallReceiptsHaveBoundedEvidenceWithoutForgettingCancelledQuantityOrAllowingReplay() {
        SubjectId cancelled = id("claim:cancelled-one");
        var contract = new GoodsTradeContract(CONTRACT, SELL_ORDER, BUY_ORDER, SELLER_PARTY, BUYER_PARTY, SOURCE, RECEIVER,
                "minecraft:wheat", 131, PRICE, HOLD, 0, Map.of(CLAIM, 130, cancelled, 1), Map.of());
        var first = new GoodsTradeDisposition(id("receipt:early-cancel"), CONTRACT, 0, cancelled, 1,
                GoodsTradeDisposition.Reason.CANCELLED_BEFORE_LOADING);
        contract = contract.dispose(first);
        for (int i = 1; i <= 130; i++) contract = contract.accept(receipt("receipt:part-" + i, i, 1, "lot:part-" + i));
        assertTrue(contract.terminal()); assertFalse(contract.fulfilled());
        assertEquals(131, contract.revision()); assertEquals(130, contract.acceptedQuantity()); assertEquals(1, contract.disposedQuantity());
        assertEquals(GoodsTradeContract.MAX_RECEIPTS, contract.acceptances().size()); assertTrue(contract.dispositions().isEmpty());
        GoodsTradeContract closed = contract;
        assertThrows(IllegalArgumentException.class, () -> closed.dispose(first));
        assertThrows(IllegalArgumentException.class, () -> closed.accept(receipt("receipt:part-1", 1, 1, "lot:part-1")));
    }

    @Test void titleTransferPreservesHotLayoutEpochAndOtherOwnerRemainderWithoutMovingAStack() {
        SubjectId account = id("custody:hot-title"), lot = id("lot:hot-title"), claim = id("claim:hot-title"), child = id("lot:hot-bought");
        var resources = FungibleResourceLedger.empty().issue(new ResourceLot(lot, SELLER, "minecraft:bread", 60, "fixture:hot-title", List.of()),
                new CustodyAccount(account, new ResourceCustody.Container(RECEIVER), Map.of(lot, 60), Map.of()))
                .reserve(new ClaimAllocation(claim, CONTRACT, SELLER, "minecraft:bread", 40, Map.of(lot, 40), ClaimPurpose.GOODS_TRADE), account);
        var address = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(RECEIVER, 0));
        resources = resources.rebind(account, 7, FungiblePhysicalObservation.bind(resources, account, 7,
                List.of(new FungiblePhysicalObservation.Stack(address, "minecraft:bread", 60))));
        var before = resources.bindings().values().iterator().next();
        var result = resources.transferTitle(new ResourceTitleTransfer(account, claim, SELLER, BUYER, Map.of(lot, 20), Map.of(lot, child)));
        var after = result.bindings().get(before.id());
        assertEquals(address, after.address()); assertEquals(7, after.authorityEpoch()); assertEquals(60, after.quantity());
        assertEquals(20, result.claims().get(claim).quantity()); assertEquals(40, result.lots().get(lot).quantity());
        assertEquals(BUYER, result.lots().get(child).economicOwnerId()); assertEquals(SELLER, result.lots().get(lot).economicOwnerId());
        assertEquals(Map.of(lot, 40, child, 20), result.accounts().get(account).lotQuantities());
        var partitioned = resources.partitionClaim(new ResourceClaimPartition(account, claim, id("claim:hot-child"), Map.of(lot, 20)));
        assertEquals(Map.of(claim, 20, id("claim:hot-child"), 20), partitioned.accounts().get(account).claimQuantities());
        assertEquals(resources.lots(), partitioned.lots());
        assertEquals(7, partitioned.bindings().get(before.id()).authorityEpoch());
        assertEquals(60, partitioned.bindings().get(before.id()).quantity());
        assertEquals(2, partitioned.bindings().get(before.id()).claimQuantities().size());
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:goods-trade-core"), 17));
    }
    private static GoodsTradeOrder sellOrder() {
        return new GoodsTradeOrder(SELL_ORDER, SELLER_PARTY, BUYER_PARTY, GoodsTradeOrder.Side.SELL, SOURCE,
                "minecraft:wheat", 60, 0, PRICE, 10_000);
    }
    private static GoodsTradeOrder buyOrder() {
        return new GoodsTradeOrder(BUY_ORDER, BUYER_PARTY, SELLER_PARTY, GoodsTradeOrder.Side.BUY, RECEIVER,
                "minecraft:wheat", 60, 0, FixedScalar.whole(3), 10_000);
    }
    private static GoodsTradeReserved reservation() {
        var contract = new GoodsTradeContract(CONTRACT, SELL_ORDER, BUY_ORDER, SELLER_PARTY, BUYER_PARTY, SOURCE, RECEIVER,
                "minecraft:wheat", 60, PRICE, HOLD, 0, Map.of(CLAIM, 60), Map.of());
        var claim = new ClaimAllocation(CLAIM, CONTRACT, SELLER, "minecraft:wheat", 60, Map.of(LOT, 60), ClaimPurpose.GOODS_TRADE);
        return new GoodsTradeReserved(contract, List.of(new GoodsTradeStockAllocation(SOURCE_ACCOUNT, claim,
                GoodsTradeStockAllocation.Authority.COLD, 0)));
    }
    private static FrontierWorldState reserved() {
        return fact(fact(fact(initial(), SELLER, new GoodsTradeOrderPlaced(sellOrder())), BUYER,
                new GoodsTradeOrderPlaced(buyOrder())), SELLER, reservation());
    }
    private static FrontierWorldState arrive(FrontierWorldState state) {
        // Shared COLD handoff fixture at declared stations. Body placement is test setup,
        // not navigation, elapsed travel or native unloading acceptance.
        SubjectId actor = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        SubjectId carried = id("custody:goods-courier");
        SurfaceAnchor sourceStation = station(state, SELLER), receiverStation = station(state, BUYER);
        state = atStation(state, actor, sourceStation);
        var taking = new ActorContainerItemOrder(CONTRACT, actor, ActorContainerItemOrder.Direction.TAKE,
                new ActorContainerItemOrder.Portion.Fungible(SOURCE_ACCOUNT, new ResourceCustody.Container(SOURCE),
                        carried, new ResourceCustody.Actor(actor), java.util.Optional.of(CLAIM), "minecraft:wheat", Map.of(LOT, 60)),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(SOURCE), sourceStation,
                ActorContainerItemOrder.Hand.MAIN, 0, 1);
        FrontierWorldState loaded = state.withInventory(ActorItemCustody.transferCold(state, taking));
        FrontierReferenceClosure.validateTransition(state, loaded, List.of());
        loaded = atStation(loaded, actor, receiverStation);
        var placing = new ActorContainerItemOrder(CONTRACT, actor, ActorContainerItemOrder.Direction.PLACE,
                new ActorContainerItemOrder.Portion.Fungible(carried, new ResourceCustody.Actor(actor),
                        RECEIVER_ACCOUNT, new ResourceCustody.Container(RECEIVER), java.util.Optional.of(CLAIM), "minecraft:wheat", Map.of(LOT, 60)),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(RECEIVER), receiverStation,
                ActorContainerItemOrder.Hand.MAIN, 1, 1);
        FrontierWorldState arrived = loaded.withInventory(ActorItemCustody.transferCold(loaded, placing));
        FrontierReferenceClosure.validateTransition(loaded, arrived, List.of()); return arrived;
    }
    private static SurfaceAnchor station(FrontierWorldState state, SubjectId settlementId) {
        var settlement = state.bootstrap().settlements().stream().filter(s -> s.id().equals(settlementId)).findFirst().orElseThrow();
        return SettlementDepotServicePort.forDepot(settlement.structures().stream().filter(s -> s.kind() == StructureKind.DEPOT)
                .findFirst().orElseThrow()).serviceSurface();
    }
    private static FrontierWorldState atStation(FrontierWorldState state, SubjectId actor, SurfaceAnchor station) {
        var actors = new java.util.HashMap<>(state.actorLocations());
        actors.put(actor, actors.get(actor).withBody(BodyPosition.above(station)));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
    }
    private static GoodsTradeAcceptance receipt(String id, long revision, int quantity, String child) {
        return new GoodsTradeAcceptance(id(id), CONTRACT, revision,
                new ResourceTitleTransfer(RECEIVER_ACCOUNT, CLAIM, SELLER, BUYER, Map.of(LOT, quantity), Map.of(LOT, id(child))));
    }
    private static FrontierWorldState fact(FrontierWorldState state, SubjectId actor, FrontierPayload payload) {
        return fact(state, actor, payload, 0);
    }
    private static FrontierWorldState fact(FrontierWorldState state, SubjectId actor, FrontierPayload payload, long atTick) {
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        var emitted = new ProposedEvent(actor, payload);
        FrontierWorldRuntimeDefinition.processRegistry().validateEmissions("goods-trade", List.of(emitted));
        var decoded = codecs.decode(emitted.payload().type(), codecs.encode(emitted.payload()));
        assertEquals(emitted.payload(), decoded);
        var command = new CommandId("command:goods-reduce");
        var event = new FrontierEvent(FrontierEvent.SCHEMA_VERSION, new EventId("event:goods-reduce"),
                new TransactionId("transaction:goods-reduce"), state.bootstrap().worldId(), new Revision(1), new SimInstant(atTick),
                emitted.subject(), CauseChain.root(command), decoded);
        var next = FrontierWorldRuntimeDefinition.reduce(state, event);
        FrontierReferenceClosure.validateTransition(state, next, List.of()); return next;
    }
    private static SubjectId id(String value) { return new SubjectId(value); }
}
