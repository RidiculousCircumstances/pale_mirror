package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Reuses seller policy and ordinary resource/money settlement. No hunger, route or mission stage logic. */
public final class GoodsSpotPurchaseAuthority {
    private GoodsSpotPurchaseAuthority() { }
    public static int availableQuantity(FrontierWorldState state, SubjectId container, SubjectId sellerId,
                                       SubjectId buyerId, SubjectId budgetId, String kind) {
        var seller = state.companies().goodsTrade().participants().participants().get(sellerId);
        var buyer = state.companies().goodsTrade().participants().participants().get(buyerId);
        var budget = state.inventory().economics().budgets().get(budgetId);
        if (seller == null || buyer == null || !seller.endpoint().containerId().equals(container) || sellerId.equals(buyerId)
                || budget == null || !budget.payerId().equals(buyerId)) return 0;
        var rules = state.bootstrap().ruleset().goodsTrade();
        var sale = GoodsParticipantPolicies.require(seller).decide(GoodsParticipantView.read(state, seller), rules).stream()
                .filter(i -> i.side() == GoodsTradeOrder.Side.SELL && i.itemKind().equals(kind)).findFirst().orElse(null);
        if (sale == null || rules.policies().get(buyer.policy()).stream().noneMatch(c -> c.itemKind().equals(kind)
                && c.maximumBuyPrice().compareTo(sale.limit()) >= 0)) return 0;
        return (int) Math.min(sale.quantity(), budget.remaining().raw() / sale.limit().raw());
    }
    public static Optional<GoodsSpotPurchase> offer(FrontierWorldState state, UnitResourceTransfer transfer,
                                                   SubjectId buyerId, SubjectId budgetId, SubjectId reasonId) {
        var seller = state.companies().goodsTrade().participants().participants().get(transfer.sourceEconomicOwnerId());
        var budget = state.inventory().economics().budgets().get(budgetId);
        if (seller == null || !seller.endpoint().containerId().equals(transfer.containerId()) || seller.party().id().equals(buyerId)
                || budget == null || !budget.payerId().equals(buyerId)) return Optional.empty();
        var rules = state.bootstrap().ruleset().goodsTrade();
        var sale = GoodsParticipantPolicies.require(seller).decide(GoodsParticipantView.read(state, seller), rules).stream()
                .filter(i -> i.side() == GoodsTradeOrder.Side.SELL && i.itemKind().equals(transfer.itemKind()) && i.quantity() >= transfer.quantity()).findFirst();
        if (sale.isEmpty()) return Optional.empty();
        var amount = sale.orElseThrow().limit().multiply(transfer.quantity());
        var buyer = state.companies().goodsTrade().participants().participants().get(buyerId);
        if (buyer == null || rules.policies().get(buyer.policy()).stream().noneMatch(c -> c.itemKind().equals(transfer.itemKind())
                && c.maximumBuyPrice().compareTo(sale.orElseThrow().limit()) >= 0) || budget.remaining().compareTo(amount) < 0) return Optional.empty();
        var splits = new LinkedHashMap<SubjectId, SubjectId>();
        for (var entry : new TreeMap<>(transfer.lots()).entrySet()) {
            var lot = state.inventory().fungibleResources().lots().get(entry.getKey());
            if (lot == null || !lot.economicOwnerId().equals(seller.party().id()) || !lot.itemKind().equals(transfer.itemKind()))
                throw new IllegalArgumentException("purchase offer names another seller's resource");
            if (entry.getValue() < lot.quantity()) splits.put(lot.id(), new SubjectId("lot:spot-purchase/" + identity(transfer.claimId().value() + "\n" + lot.id().value())));
        }
        var payment = new FinancialReservation(new SubjectId("reservation:spot-purchase/" + identity(transfer.claimId().value())),
                buyerId, seller.party().id(), reasonId, amount, Optional.of(budgetId));
        return Optional.of(new GoodsSpotPurchase(payment, new ResourceTitleTransfer(transfer.destinationAccountId(), transfer.claimId(),
                seller.party().id(), buyerId, transfer.lots(), splits)));
    }
    public static ExactInventory reserve(ExactInventory inventory, GoodsSpotPurchase purchase) {
        return inventory.withEconomics(inventory.economics().reserveFromBudget(purchase.payment().budgetId().orElseThrow(), purchase.payment()));
    }
    public static ExactInventory receive(ExactInventory inventory, UnitResourceTransfer transfer, GoodsSpotPurchase purchase) {
        var account = inventory.fungibleResources().accounts().get(transfer.destinationAccountId());
        if (account == null || !account.custody().equals(new ResourceCustody.Actor(transfer.actorId()))
                || !purchase.title().accountId().equals(account.id()) || !purchase.title().claimId().equals(transfer.claimId())
                || !purchase.title().portions().equals(transfer.lots()) || !purchase.payment().equals(inventory.economics().reservations().get(purchase.payment().id())))
            throw new IllegalArgumentException("spot purchase lacks the actual complete goods receipt and retained payment");
        return inventory.withFungibleResources(inventory.fungibleResources().transferTitle(purchase.title()))
                .withEconomics(inventory.economics().settle(purchase.payment().id()));
    }
    public static ExactInventory cancel(ExactInventory inventory, GoodsSpotPurchase purchase) {
        if (!purchase.payment().equals(inventory.economics().reservations().get(purchase.payment().id())))
            throw new IllegalArgumentException("purchase cancellation lost exact held payment");
        return inventory.withEconomics(inventory.economics().release(purchase.payment().id()));
    }
    private static UUID identity(String value) { return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
}
