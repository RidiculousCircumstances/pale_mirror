package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EconomicAccountReferencesTest {
    @Test void warmedAccountsRejectRemovedOwnersAndNewForgedAccountsWithoutPoisoningValidBranches() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:account-reference-delta"), 71));
        var person = state.humanPopulation().residents().values().iterator().next();
        var account = new EconomicAccount(person.id(), EconomicOwnerKind.RESIDENT,
                EconomicAccountStatus.ACTIVE, FixedScalar.ZERO, FixedScalar.ZERO);
        assertEquals(EconomicOwnerKind.RESIDENT, account.ownerKind());
        var accounts = ImmutableLookupMap.copyOf(Map.of(account.ownerId(), account));
        var residents = ImmutableLookupMap.copyOf(Map.of(person.id(), person));
        var companies = Map.<SubjectId, Company>of();
        EconomicAccountReferences.validate(state.bootstrap(), accounts, companies, residents);
        var removed = ImmutableLookupMap.withoutEntry(residents, person.id());
        assertThrows(IllegalArgumentException.class, () -> EconomicAccountReferences.validate(state.bootstrap(), accounts, companies, removed));
        assertDoesNotThrow(() -> EconomicAccountReferences.validate(state.bootstrap(), accounts, companies, residents));
        var forged = new EconomicAccount(new SubjectId("resident:unknown-title"), EconomicOwnerKind.RESIDENT,
                EconomicAccountStatus.ACTIVE, FixedScalar.ZERO, FixedScalar.ZERO);
        var inserted = ImmutableLookupMap.withEntry(accounts, forged.ownerId(), forged);
        assertThrows(IllegalArgumentException.class, () -> EconomicAccountReferences.validate(state.bootstrap(), inserted, companies, residents));
        assertDoesNotThrow(() -> ImmutableInvariantCheck.full(() -> {
            EconomicAccountReferences.validate(state.bootstrap(), accounts, companies, residents); return null;
        }));
    }
}
