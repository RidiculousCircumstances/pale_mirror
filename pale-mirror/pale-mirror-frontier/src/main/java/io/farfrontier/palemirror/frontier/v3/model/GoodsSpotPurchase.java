package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Agreed small immediate purchase. Existing ledgers own title and balances; the requesting workflow retains this declaration. */
public record GoodsSpotPurchase(FinancialReservation payment, ResourceTitleTransfer title) {
    public GoodsSpotPurchase {
        Objects.requireNonNull(payment); Objects.requireNonNull(title);
        if (!payment.payeeId().equals(title.sourceOwnerId()) || !payment.payerId().equals(title.destinationOwnerId())
                || payment.budgetId().isEmpty()) throw new IllegalArgumentException("spot purchase must declare its actual parties and finite budget");
    }
}
