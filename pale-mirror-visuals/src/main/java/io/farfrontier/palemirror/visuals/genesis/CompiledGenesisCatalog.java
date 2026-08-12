package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import java.util.List;
import java.util.Map;

/** Atomically published, read-only address space for PM worldgen. */
public record CompiledGenesisCatalog(int version, String hash, List<AuthoredRegionSeed> manifests,
                                     Map<Long, CompiledChunkSlice> chunks) {
    public CompiledGenesisCatalog {
        manifests = List.copyOf(manifests);
        chunks = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(chunks));
    }

    public CompiledChunkSlice chunk(long key) { return chunks.get(key); }
}
