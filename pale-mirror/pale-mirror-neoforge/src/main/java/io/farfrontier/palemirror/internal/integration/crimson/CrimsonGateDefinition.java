package io.farfrontier.palemirror.internal.integration.crimson;

import java.util.List;
import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

/** Pinned authored pool for this adapter's PM-owned APEX gate. */
record CrimsonGateDefinition(ResourceLocation id, int version, List<String> bossProfiles) {
    CrimsonGateDefinition {
        Objects.requireNonNull(id, "id");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        bossProfiles = List.copyOf(bossProfiles);
        if (bossProfiles.isEmpty()) throw new IllegalArgumentException("bossProfiles must not be empty");
    }
}
