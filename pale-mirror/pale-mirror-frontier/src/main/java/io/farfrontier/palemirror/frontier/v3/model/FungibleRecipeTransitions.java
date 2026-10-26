package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;

/** The existing ledger's atomic transformation across explicitly distinct custody compartments. */
final class FungibleRecipeTransitions {
    static FungibleResourceLedger between(FungibleResourceLedger ledger, SubjectId inputAccount,
            Map<SubjectId, Integer> inputLots, Map<SubjectId, Integer> inputClaims, ResourceLot output,
            SubjectId outputAccount, ResourceCustody outputCustody) {
        if (inputAccount.equals(outputAccount) || ledger.accounts().containsKey(outputAccount) || ledger.lots().containsKey(output.id()))
            throw new IllegalArgumentException("recipe output needs its distinct vacant declared compartment");
        ledger.requireClaimsCoveredByLots(inputLots, inputClaims, "recipe transformation");
        for (var id : inputLots.keySet()) {
            var lot = ledger.lots().get(id);
            if (lot == null || !lot.economicOwnerId().equals(output.economicOwnerId()))
                throw new IllegalArgumentException("recipe transformation has an unknown lot or crosses economic ownership");
        }
        return ledger.destroy(inputAccount, inputLots, inputClaims).issue(output,
                new CustodyAccount(outputAccount, outputCustody, Map.of(output.id(), output.quantity()), Map.of()));
    }
    private FungibleRecipeTransitions() { }
}
