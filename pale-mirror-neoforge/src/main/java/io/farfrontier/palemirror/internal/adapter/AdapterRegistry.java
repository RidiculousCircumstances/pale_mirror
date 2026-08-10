package io.farfrontier.palemirror.internal.adapter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.api.IntegrationAdapter;
import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.domain.InfectionSourceId;
import io.farfrontier.palemirror.internal.content.EncounterProfile;
import io.farfrontier.palemirror.internal.integration.crimson.CrimsonSandboxAdapter;
import io.farfrontier.palemirror.internal.integration.spore.SporeSandboxAdapter;
import io.farfrontier.palemirror.internal.integration.ActorOperationResult;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class AdapterRegistry {
    private static final VanillaAnchorAdapter VANILLA_ANCHOR = new VanillaAnchorAdapter();
    private static final CrimsonSandboxAdapter CRIMSON = new CrimsonSandboxAdapter();
    private static final SporeSandboxAdapter SPORE = new SporeSandboxAdapter();
    private static final Map<String, IntegrationAdapter> ADAPTERS = Map.of(
            VANILLA_ANCHOR.id(), VANILLA_ANCHOR,
            CRIMSON.id(), CRIMSON,
            SPORE.id(), SPORE);

    private AdapterRegistry() { }
    public static List<IntegrationAdapter> all() { return ADAPTERS.values().stream().sorted(java.util.Comparator.comparing(IntegrationAdapter::id)).toList(); }
    public static VanillaAnchorAdapter vanillaAnchor() { return VANILLA_ANCHOR; }
    public static CrimsonSandboxAdapter crimson() { return CRIMSON; }
    public static SporeSandboxAdapter spore() { return SPORE; }
    public static List<ThreatActorAdapter> threatActors() { return List.of(CRIMSON, SPORE); }
    public static ThreatActorAdapter sourceActor(InfectionSourceId source) {
        return threatActors().stream().filter(adapter -> adapter.source().equals(source)).findFirst()
                .orElseGet(() -> new UnavailableThreatActorAdapter(source));
    }
    public static boolean supports(Set<Capability> capabilities) {
        Set<Capability> available = ADAPTERS.values().stream()
                .filter(adapter -> adapter.health().status() == io.farfrontier.palemirror.api.AdapterHealth.Status.AVAILABLE)
                .flatMap(adapter -> adapter.health().capabilities().stream())
                .collect(Collectors.toUnmodifiableSet());
        return available.containsAll(capabilities);
    }

    /** Unknown source identities must visibly degrade optional presentation rather than impersonating another source. */
    private record UnavailableThreatActorAdapter(InfectionSourceId source) implements ThreatActorAdapter {
        @Override public String id() { return "pale_mirror:unavailable_source_actor"; }
        @Override public AdapterHealth health() { return new AdapterHealth(AdapterHealth.Status.BLOCKED,
                "No installed PM actor adapter for source " + source.value(), Set.of()); }
        @Override public ActorOperationResult ensureActor(ServerLevel level, TestMineRecord site, String jobId,
                                                           EncounterProfile.ActorSlot slot) {
            return ActorOperationResult.unavailable(health().detail());
        }
        @Override public ActorOperationResult removeActor(ServerLevel level, TestMineRecord site, String slotId) {
            return ActorOperationResult.unavailable(health().detail());
        }
        @Override public boolean matchesActor(Entity entity) { return false; }
        @Override public boolean matchesOwnedActor(Entity entity, TestMineRecord site, String slotId) { return false; }
    }
}
