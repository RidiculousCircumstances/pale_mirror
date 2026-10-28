package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Closed immutable collection implementations may describe exact changed keys.
 * The predecessor is weak: no historical map chain is retained. Missing evidence means
 * ordinary complete comparison, never an assumption that the maps are unchanged. */
sealed interface ImmutableMapImage<K, V> extends Map<K, V>
        permits ImmutableLookupMap, FrontierWorldStateSupport.ValidatedImmutableMap {
    Optional<Set<K>> changedKeysFrom(Map<?, ?> predecessor);
    /** Closed immutable storage only, for sharing representation across validation wrappers. */
    Map<K, V> immutableStorage();
    /** Opaque immutable key-order identity; value replacement may preserve it. */
    Object keyOrderIdentity();
}
