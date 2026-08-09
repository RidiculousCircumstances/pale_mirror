package io.farfrontier.palemirror.internal.adapter;

import java.util.List;

import io.farfrontier.palemirror.api.IntegrationAdapter;

public final class AdapterRegistry {
    private static final List<IntegrationAdapter> ADAPTERS = List.of(new TestThreatAdapter(), new CrimsonAdapter());

    private AdapterRegistry() { }
    public static List<IntegrationAdapter> all() { return ADAPTERS; }
    public static TestThreatAdapter testThreat() { return (TestThreatAdapter) ADAPTERS.getFirst(); }
}
