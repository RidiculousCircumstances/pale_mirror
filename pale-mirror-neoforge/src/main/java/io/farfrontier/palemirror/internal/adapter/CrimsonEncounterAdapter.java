package io.farfrontier.palemirror.internal.adapter;

import java.util.Set;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.IntegrationAdapter;
import io.farfrontier.palemirror.internal.content.EncounterProfile;
import net.neoforged.fml.ModList;

/**
 * Crimson 1.4.3.1 is a public datapack surface, not a local-controller API.
 * It remains an explicitly degraded presentation adapter until an audited actor
 * can be created and observed without global infection writes.
 */
public final class CrimsonEncounterAdapter implements IntegrationAdapter {
    @Override
    public String id() { return "pale_mirror:crimson_encounter"; }

    @Override
    public AdapterHealth health() {
        if (!ModList.get().isLoaded("mr_crimson_curse")) {
            return new AdapterHealth(AdapterHealth.Status.ABSENT, "mr_crimson_curse is not installed", Set.of());
        }
        return new AdapterHealth(AdapterHealth.Status.DEGRADED,
                "Crimson 1.4.3.1 has no public local actor/controller contract; PM anchor remains active without Crimson actors", Set.of());
    }

    /** Returns a visible refusal rather than pretending a vanilla actor is native Crimson content. */
    public String unavailableReason(EncounterProfile.ActorSlot slot) {
        return health().detail() + "; refused requested slot " + slot.id() + " (" + slot.entityType() + ")";
    }
}
