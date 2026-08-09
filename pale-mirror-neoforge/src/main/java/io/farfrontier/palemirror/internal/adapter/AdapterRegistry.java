package io.farfrontier.palemirror.internal.adapter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.api.IntegrationAdapter;
import io.farfrontier.palemirror.internal.integration.crimson.CrimsonSandboxAdapter;

public final class AdapterRegistry {
    private static final VanillaAnchorAdapter VANILLA_ANCHOR = new VanillaAnchorAdapter();
    private static final CrimsonSandboxAdapter CRIMSON = new CrimsonSandboxAdapter();
    private static final Map<String, IntegrationAdapter> ADAPTERS = Map.of(
            VANILLA_ANCHOR.id(), VANILLA_ANCHOR,
            CRIMSON.id(), CRIMSON);

    private AdapterRegistry() { }
    public static List<IntegrationAdapter> all() { return ADAPTERS.values().stream().sorted(java.util.Comparator.comparing(IntegrationAdapter::id)).toList(); }
    public static VanillaAnchorAdapter vanillaAnchor() { return VANILLA_ANCHOR; }
    public static CrimsonSandboxAdapter crimson() { return CRIMSON; }
    public static boolean supports(Set<Capability> capabilities) {
        Set<Capability> available = ADAPTERS.values().stream()
                .filter(adapter -> adapter.health().status() == io.farfrontier.palemirror.api.AdapterHealth.Status.AVAILABLE)
                .flatMap(adapter -> adapter.health().capabilities().stream())
                .collect(Collectors.toUnmodifiableSet());
        return available.containsAll(capabilities);
    }
}
