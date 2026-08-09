package io.farfrontier.palemirror.internal.content;

import java.util.List;
import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

/** Pinned, authored pool for a PM-owned APEX siege. */
public record CrimsonSiegeDefinition(ResourceLocation id, int version, List<String> bossProfiles) {
    public CrimsonSiegeDefinition {
        Objects.requireNonNull(id, "id");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        bossProfiles = List.copyOf(bossProfiles);
        if (bossProfiles.isEmpty()) throw new IllegalArgumentException("bossProfiles must not be empty");
    }
}
