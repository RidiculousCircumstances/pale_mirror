package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Exact immutable dependency tuple: identity-fenced scopes plus complete local fact tokens.
 * This is disposable calculation evidence, never a world revision or mutation authority. */
public final class RegionalReadVersion {
    private final List<Object> scopes;
    private final Map<Region, List<Object>> facts;
    private final int hash;
    public RegionalReadVersion(List<Object> scopes, Map<Region, List<Object>> facts) {
        this.scopes = List.copyOf(scopes);
        var copy = new HashMap<Region, List<Object>>(); facts.forEach((region, values) -> copy.put(region, List.copyOf(values)));
        this.facts = Map.copyOf(copy);
        int result = this.facts.hashCode();
        for (var scope : this.scopes) result = 31 * result + System.identityHashCode(scope);
        hash = result;
    }
    @Override public int hashCode() { return hash; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RegionalReadVersion value) || hash != value.hash || scopes.size() != value.scopes.size()) return false;
        for (int i = 0; i < scopes.size(); i++) if (scopes.get(i) != value.scopes.get(i)) return false;
        return facts.equals(value.facts);
    }
}
