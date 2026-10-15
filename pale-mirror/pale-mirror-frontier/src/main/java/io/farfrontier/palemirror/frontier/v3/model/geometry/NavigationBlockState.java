package io.farfrontier.palemirror.frontier.v3.model.geometry;

import java.util.*;

/** Nominal observed state used to reject an obsolete shape after a canonical source transition. */
public record NavigationBlockState(String kind, Map<String, String> properties) {
    public NavigationBlockState {
        Objects.requireNonNull(kind); properties = Map.copyOf(properties);
        if (kind.isBlank() || kind.length() > 256 || properties.size() > 32
                || properties.entrySet().stream().anyMatch(entry -> entry.getKey().isBlank()
                    || entry.getKey().length() > 128 || entry.getValue().isBlank() || entry.getValue().length() > 128))
            throw new IllegalArgumentException("unbounded navigation block declaration");
    }
}
