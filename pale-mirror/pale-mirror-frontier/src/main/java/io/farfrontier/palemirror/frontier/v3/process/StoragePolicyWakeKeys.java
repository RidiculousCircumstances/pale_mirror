package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ExactInventory;
import java.util.HashSet;
import java.util.Set;

/** Derived dependency invalidation; policies remain owned by inventory. */
final class StoragePolicyWakeKeys {
    private StoragePolicyWakeKeys() { }
    static Set<SubjectId> changed(ExactInventory previous, ExactInventory next) {
        var keys = new HashSet<SubjectId>();
        next.containers().forEach((id, container) -> {
            if (!container.equals(previous.containers().get(id))) {
                keys.add(id);
                keys.add(container.ownerId());
            }
        });
        return Set.copyOf(keys);
    }
}
