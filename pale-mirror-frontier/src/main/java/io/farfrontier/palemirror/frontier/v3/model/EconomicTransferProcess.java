package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;

/** One ordered mutation path for account balances; no item claim or custody changes here. */
final class EconomicTransferProcess {
    private EconomicTransferProcess() { }

    static List<ProposedEvent> plan(FrontierWorldState state, EconomicTransfer transfer) {
        state.inventory().economics().transfer(transfer.payerId(), transfer.payeeId(), transfer.amount());
        return List.of(new ProposedEvent(transfer.payerId(), transfer));
    }

    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, EconomicTransfer transfer) {
        if (!subject.equals(transfer.payerId())) throw new IllegalArgumentException("economic transfer subject must be its payer");
        EconomicLedger next = state.inventory().economics().transfer(transfer.payerId(), transfer.payeeId(), transfer.amount());
        return state.withInventory(state.inventory().withEconomics(next));
    }
}
