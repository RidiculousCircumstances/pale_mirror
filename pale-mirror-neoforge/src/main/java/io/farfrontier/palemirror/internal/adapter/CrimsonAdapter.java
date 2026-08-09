package io.farfrontier.palemirror.internal.adapter;

import java.util.Set;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.IntegrationAdapter;
import net.neoforged.fml.ModList;

/** Deliberately non-operational until the pinned Crimson L1 audit is automated. */
public final class CrimsonAdapter implements IntegrationAdapter {
    @Override
    public String id() { return "pale_mirror:crimson"; }

    @Override
    public AdapterHealth health() {
        if (!ModList.get().isLoaded("mr_crimson_curse")) {
            return new AdapterHealth(AdapterHealth.Status.ABSENT, "mr_crimson_curse is not installed", Set.of());
        }
        return new AdapterHealth(AdapterHealth.Status.BLOCKED,
                "Crimson 1.4.3.1 exposes only global infection controls; no local controller identity/observation contract", Set.of());
    }
}
