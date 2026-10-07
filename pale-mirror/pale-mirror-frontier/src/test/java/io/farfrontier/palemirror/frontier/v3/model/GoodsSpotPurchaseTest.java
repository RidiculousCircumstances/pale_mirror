package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Commercial transaction checks, not native receipt or full-mission acceptance. */
class GoodsSpotPurchaseTest {
    private record Fixture(FrontierWorldState state, UnitResourceTransfer transfer, SubjectId buyer, SubjectId budget) { }
    private static Fixture fixture(FixedScalar funds) {
        var state = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:paid-supplies"), 41,
                FrontierRulesets.installed("frontier-v3-expedition-candidate-r1")).initialState();
        var seller = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:1"));
        var buyer = new SubjectId("settlement:2"); var owner = new SubjectId("mission:paid-fixture");
        var budget = new SubjectId("budget:paid-fixture");
        state = state.withInventory(state.inventory().withEconomics(state.inventory().economics().reserveBudget(
                new FinancialBudget(budget, buyer, FinancialBudget.OwnerKind.TRANSPORT_MISSION, owner, funds))));
        var actor = state.humanPopulation().residents().values().stream().filter(r -> r.settlementId().equals(buyer)).findFirst().orElseThrow().id();
        var resources = state.inventory().fungibleResources();
        var account = FungibleResourceCustodySupport.accountAtContainer(state, seller.endpoint().containerId()).orElseThrow();
        var selected = FungibleResourceCustodySupport.selectAtAccount(resources, account.id(), seller.party().id(), FoodCatalog.BREAD, 2).orElseThrow();
        var transfer = new UnitResourceTransfer(new SubjectId("claim:paid-fixture"), actor, seller.endpoint().containerId(), account.id(),
                new SubjectId("custody:paid-fixture"), seller.party().id(), FoodCatalog.BREAD, selected.lotQuantities(),
                new ActorItemSlot.Pocket(0), seller.endpoint().station(), state.actorExecutions().next(actor, ActorActivityKind.GROUP_MEMBER, owner), Optional.empty());
        return new Fixture(state, transfer, buyer, budget);
    }
    @Test void foreignFoodRequiresConsentAndFundsAndSettlesOnlyAfterActualCustodyReceipt() {
        var f = fixture(FixedScalar.whole(20)); var before = f.state().inventory(); var t = f.transfer();
        var purchase = GoodsSpotPurchaseAuthority.offer(f.state(), t, f.buyer(), f.budget(), t.execution().activityOwnerId()).orElseThrow();
        var held = GoodsSpotPurchaseAuthority.reserve(before, purchase);
        assertEquals(before.economics().accounts(), held.economics().accounts(), "reservation cannot pay the supplier");
        assertThrows(IllegalArgumentException.class, () -> GoodsSpotPurchaseAuthority.receive(held, t, purchase));
        var resources = held.fungibleResources().reserve(new ClaimAllocation(t.claimId(), t.execution().activityOwnerId(),
                t.sourceEconomicOwnerId(), t.itemKind(), t.quantity(), t.lots(), ClaimPurpose.EXPEDITION_SUPPLY), t.sourceAccountId());
        resources = resources.transferActorOrderCold(t.order(t.execution().activityOwnerId(), 1));
        var received = GoodsSpotPurchaseAuthority.receive(held.withFungibleResources(resources), t, purchase);
        assertEquals(before.fungibleResources().lots().values().stream().mapToInt(ResourceLot::quantity).sum(),
                received.fungibleResources().lots().values().stream().mapToInt(ResourceLot::quantity).sum());
        assertEquals(t.quantity(), UnitInventory.available(received.fungibleResources(), t.actorId(), f.buyer(), t.itemKind()));
        assertEquals(before.economics().require(f.buyer()).balance().minus(purchase.payment().amount()), received.economics().require(f.buyer()).balance());
        assertEquals(before.economics().require(t.sourceEconomicOwnerId()).balance().plus(purchase.payment().amount()),
                received.economics().require(t.sourceEconomicOwnerId()).balance());
        assertFalse(received.fungibleResources().claims().containsKey(t.claimId()));
        assertFalse(received.economics().reservations().containsKey(purchase.payment().id()));
        assertThrows(IllegalArgumentException.class, () -> GoodsSpotPurchaseAuthority.receive(received, t, purchase), "receipt cannot pay twice");
    }
    @Test void cancellationRestoresFiniteBudgetAndDoesNotTransferFoodOrTitle() {
        var f = fixture(FixedScalar.whole(20)); var t = f.transfer();
        var purchase = GoodsSpotPurchaseAuthority.offer(f.state(), t, f.buyer(), f.budget(), t.execution().activityOwnerId()).orElseThrow();
        var restored = GoodsSpotPurchaseAuthority.cancel(GoodsSpotPurchaseAuthority.reserve(f.state().inventory(), purchase), purchase);
        assertEquals(f.state().inventory(), restored);
        var poor = fixture(FixedScalar.ONE);
        assertTrue(GoodsSpotPurchaseAuthority.offer(poor.state(), poor.transfer(), poor.buyer(), poor.budget(), t.execution().activityOwnerId()).isEmpty());
        assertEquals(0, GoodsSpotPurchaseAuthority.availableQuantity(poor.state(), t.containerId(), t.sourceEconomicOwnerId(), poor.buyer(), poor.budget(), FoodCatalog.BREAD));
    }
}
