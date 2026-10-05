package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Participant declaration, not an owner-kind inference from an ID or container. */
public record GoodsTradeParty(SubjectId id, EconomicOwnerKind kind) {
    public GoodsTradeParty {
        Objects.requireNonNull(id); Objects.requireNonNull(kind);
        if (kind != EconomicOwnerKind.COMPANY && kind != EconomicOwnerKind.SETTLEMENT_TREASURY) {
            throw new IllegalArgumentException("goods trade requires a declared settlement or company");
        }
    }
    public void validate(EconomicLedger ledger) {
        EconomicAccount account = ledger.require(id);
        if (account.ownerKind() != kind || account.status() != EconomicAccountStatus.ACTIVE) {
            throw new IllegalArgumentException("trade party differs from its active nominal economic account");
        }
    }
}
