package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Reuses seller policy and ordinary resource/money settlement. No hunger, route or mission stage logic. */
public final class GoodsSpotPurchaseAuthority {
    private GoodsSpotPurchaseAuthority() { }
    /** Seller consent and actual stock, before any money is reserved. */
    public record Quote(int quantity, io.farfrontier.palemirror.frontier.v3.api.FixedScalar unitPrice) {
        public Quote {
            Objects.requireNonNull(unitPrice);
            if (quantity < 1 || unitPrice.raw() <= 0) throw new IllegalArgumentException("empty or unpriced spot quote");
        }
    }
    public static Optional<Quote> quote(FrontierWorldState state, SubjectId container, SubjectId sellerId,
                                        SubjectId buyerId, String kind, long atTick) {
        var seller = state.companies().goodsTrade().participants().participants().get(sellerId);
        var buyer = state.companies().goodsTrade().participants().participants().get(buyerId);
        if (seller == null || buyer == null || sellerId.equals(buyerId)
                || !GoodsParticipantStorage.eligible(state, seller, container, kind)) return Optional.empty();
        var rules = state.bootstrap().ruleset().goodsTrade();
        var sale = GoodsParticipantPolicies.require(seller).decide(GoodsParticipantAssessment.view(state, seller, atTick), rules).stream()
                .filter(i -> i.side() == GoodsTradeOrder.Side.SELL && i.itemKind().equals(kind)).findFirst().orElse(null);
        if (sale == null || rules.policies().get(buyer.policy()).stream().noneMatch(c -> c.itemKind().equals(kind)
                && GoodsParticipantPricing.bid(buyer, c).compareTo(sale.limit()) >= 0)) return Optional.empty();
        int quantity = (int) Math.min(sale.quantity(), GoodsParticipantStorage.unclaimed(state, seller, container, kind));
        return quantity > 0 ? Optional.of(new Quote(quantity, sale.limit())) : Optional.empty();
    }
    public static int availableQuantity(FrontierWorldState state, SubjectId container, SubjectId sellerId,
                                       SubjectId buyerId, SubjectId budgetId, String kind, long atTick) {
        var budget = state.inventory().economics().budgets().get(budgetId);
        if (budget == null || !budget.payerId().equals(buyerId)) return 0;
        return quote(state, container, sellerId, buyerId, kind, atTick)
                .map(q -> (int) Math.min(q.quantity(), budget.remaining().raw() / q.unitPrice().raw())).orElse(0);
    }
    public static Optional<GoodsSpotPurchase> offer(FrontierWorldState state, UnitResourceTransfer transfer,
                                                   SubjectId buyerId, SubjectId budgetId, SubjectId reasonId, long atTick) {
        return offer(state, transfer, buyerId, state.inventory().economics().budgets().get(budgetId), reasonId, atTick);
    }
    /** The owner publishes this budget and its exact purchase atomically, not as speculative cash. */
    public static Optional<GoodsSpotPurchase> offer(FrontierWorldState state, UnitResourceTransfer transfer,
                                                   SubjectId buyerId, FinancialBudget budget, SubjectId reasonId, long atTick) {
        var seller = state.companies().goodsTrade().participants().participants().get(transfer.sourceEconomicOwnerId());
        if (seller == null || !GoodsParticipantStorage.eligible(state, seller, transfer.containerId(), transfer.itemKind()) || seller.party().id().equals(buyerId)
                || budget == null || !budget.payerId().equals(buyerId)) return Optional.empty();
        var sale = quote(state, transfer.containerId(), transfer.sourceEconomicOwnerId(), buyerId, transfer.itemKind(), atTick)
                .filter(q -> q.quantity() >= transfer.quantity());
        if (sale.isEmpty()) return Optional.empty();
        var amount = sale.orElseThrow().unitPrice().multiply(transfer.quantity());
        if (budget.remaining().compareTo(amount) < 0) return Optional.empty();
        var splits = new LinkedHashMap<SubjectId, SubjectId>();
        for (var entry : new TreeMap<>(transfer.lots()).entrySet()) {
            var lot = state.inventory().fungibleResources().lots().get(entry.getKey());
            if (lot == null || !lot.economicOwnerId().equals(seller.party().id()) || !lot.itemKind().equals(transfer.itemKind()))
                throw new IllegalArgumentException("purchase offer names another seller's resource");
            if (entry.getValue() < lot.quantity()) splits.put(lot.id(), new SubjectId("lot:spot-purchase/" + identity(transfer.claimId().value() + "\n" + lot.id().value())));
        }
        var payment = new FinancialReservation(new SubjectId("reservation:spot-purchase/" + identity(transfer.claimId().value())),
                buyerId, seller.party().id(), reasonId, amount, Optional.of(budget.id()));
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
    public static GoodsTradeState acceptedHistory(GoodsTradeState trade, UnitResourceTransfer transfer, GoodsSpotPurchase purchase, long now) {
        if (purchase.payment().amount().raw() % transfer.quantity() != 0)
            throw new IllegalArgumentException("spot payment does not retain an exact unit quote");
        return trade.recordSale(transfer.itemKind(), new io.farfrontier.palemirror.frontier.v3.model.market.MarketPriceHistory.Sale(
                purchase.payment().id(), purchase.payment().payeeId(), purchase.payment().payerId(), now, transfer.quantity(),
                new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(purchase.payment().amount().raw() / transfer.quantity())));
    }
    public static ExactInventory cancel(ExactInventory inventory, GoodsSpotPurchase purchase) {
        if (!purchase.payment().equals(inventory.economics().reservations().get(purchase.payment().id())))
            throw new IllegalArgumentException("purchase cancellation lost exact held payment");
        return inventory.withEconomics(inventory.economics().release(purchase.payment().id()));
    }
    private static UUID identity(String value) { return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
}
