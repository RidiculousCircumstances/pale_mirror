package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Current physical bindings by their declared account. Reuses the shared
 * immutable inverse view; quantities, epochs and actual addresses remain authoritative. */
public final class FungibleBindingReadIndex {
    private static final ImmutableGroupView<SubjectId, PhysicalStackBinding, SubjectId> ACCOUNTS = new ImmutableGroupView<>();
    private FungibleBindingReadIndex() { }
    public static List<PhysicalStackBinding> forAccount(FungibleResourceLedger ledger, SubjectId account) {
        return forAccount(ledger.bindings(), account);
    }
    static List<PhysicalStackBinding> forAccount(Map<SubjectId, PhysicalStackBinding> bindings, SubjectId account) {
        return ACCOUNTS.rows(FungibleBindingReadIndex.class, bindings, binding -> Set.of(binding.accountId()), Objects.requireNonNull(account));
    }
}
