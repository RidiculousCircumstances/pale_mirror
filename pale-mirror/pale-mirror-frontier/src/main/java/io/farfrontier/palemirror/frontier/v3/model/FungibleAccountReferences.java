package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Read-only inverse of declared resource/account references. No balance, eligibility
 * or successful-validation result is retained. Only the current computing-thread image lives. */
final class FungibleAccountReferences {
    private sealed interface Membership permits Lot, Claim, Actor { }
    private record Lot(SubjectId id) implements Membership { }
    private record Claim(SubjectId id) implements Membership { }
    private record Actor(SubjectId id) implements Membership { }
    private static final ImmutableGroupView<SubjectId, CustodyAccount, Membership> ACCOUNTS = new ImmutableGroupView<>();
    private FungibleAccountReferences() { }

    static List<CustodyAccount> lotHolders(Map<SubjectId, CustodyAccount> accounts, SubjectId lot) {
        return rows(accounts, new Lot(Objects.requireNonNull(lot)));
    }
    static List<CustodyAccount> claimHolders(Map<SubjectId, CustodyAccount> accounts, SubjectId claim) {
        return rows(accounts, new Claim(Objects.requireNonNull(claim)));
    }
    static List<CustodyAccount> actorAccounts(Map<SubjectId, CustodyAccount> accounts, SubjectId actor) {
        return rows(accounts, new Actor(Objects.requireNonNull(actor)));
    }
    private static List<CustodyAccount> rows(Map<SubjectId, CustodyAccount> accounts, Membership group) {
        return ACCOUNTS.unorderedRows(FungibleAccountReferences.class, accounts,
                FungibleAccountReferences::memberships, group);
    }
    private static Set<Membership> memberships(CustodyAccount account) {
        var groups = new HashSet<Membership>();
        account.lotQuantities().keySet().forEach(id -> groups.add(new Lot(id)));
        account.claimQuantities().keySet().forEach(id -> groups.add(new Claim(id)));
        if (account.custody() instanceof ResourceCustody.Actor actor) groups.add(new Actor(actor.actorId()));
        return groups;
    }
}
