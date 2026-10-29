package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ExactInventory;
import io.farfrontier.palemirror.frontier.v3.model.ImmutableMapChanges;
import java.util.HashSet;
import java.util.Set;

/** Derived dependency invalidation; policies remain owned by inventory. */
final class StoragePolicyWakeKeys {
    private StoragePolicyWakeKeys() { }
    static Set<SubjectId> changed(ExactInventory previous, ExactInventory next) {
        var keys = new HashSet<SubjectId>();
        ImmutableMapChanges.forEach(previous.containers(), next.containers(), change -> {
            keys.add(change.key());
            if (change.before() != null) keys.add(change.before().ownerId());
            if (change.after() != null) keys.add(change.after().ownerId());
        });
        return Set.copyOf(keys);
    }
}
