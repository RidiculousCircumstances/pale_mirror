package io.farfrontier.palemirror.internal.adapter;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.api.IntegrationAdapter;

public final class AdapterRegistry {
    private static final List<IntegrationAdapter> ADAPTERS = List.of(new TestThreatAdapter(), new CrimsonAdapter());

    private AdapterRegistry() { }
    public static List<IntegrationAdapter> all() { return ADAPTERS; }
    public static TestThreatAdapter testThreat() { return (TestThreatAdapter) ADAPTERS.getFirst(); }
    public static boolean supports(Set<Capability> capabilities) {
        Set<Capability> available = ADAPTERS.stream()
                .filter(adapter -> adapter.health().status() == io.farfrontier.palemirror.api.AdapterHealth.Status.AVAILABLE)
                .flatMap(adapter -> adapter.health().capabilities().stream())
                .collect(Collectors.toUnmodifiableSet());
        return available.containsAll(capabilities);
    }
}
