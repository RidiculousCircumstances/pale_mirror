package io.farfrontier.palemirror.frontier.reference;

import java.util.List;

/** Exact named outcome of one Python {@code apply_exposed_casualties} event. */
public record ReferenceCasualtyResult(List<String> killedIds, List<String> woundedIds) {
    public ReferenceCasualtyResult {
        killedIds = List.copyOf(killedIds);
        woundedIds = List.copyOf(woundedIds);
    }
}
