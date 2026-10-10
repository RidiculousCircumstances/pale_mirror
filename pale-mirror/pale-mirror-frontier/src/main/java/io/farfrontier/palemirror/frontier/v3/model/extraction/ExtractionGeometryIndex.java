package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Immutable declaration index. Depletion, worker progress and custody never change this view. */
public final class ExtractionGeometryIndex {
    public record Chunk(int x, int z) { }
    private final Map<SubjectId, ExtractionSite> declarations;
    private final List<ExtractionRegion> regions;
    private final Map<ExtractionRegion, List<ExtractionLayout.Cell>> cells;
    private final Map<Chunk, List<ExtractionRegion>> chunks;

    public ExtractionGeometryIndex(Map<SubjectId, ExtractionSite> declarations) {
        this.declarations = Map.copyOf(declarations);
        Map<ExtractionRegion, List<ExtractionLayout.Cell>> grouped = new HashMap<>();
        this.declarations.forEach((id, site) -> {
            if (!id.equals(site.id())) throw new IllegalArgumentException("geometry has a foreign declaration key");
            for (var cell : site.layout().cells()) {
                var region = new ExtractionRegion(id, Math.floorDiv(cell.source().x(), 16), Math.floorDiv(cell.source().z(), 16));
                grouped.computeIfAbsent(region, ignored -> new ArrayList<>()).add(cell);
            }
        });
        regions = grouped.keySet().stream().sorted(Comparator.comparing(ExtractionRegion::siteId)
                .thenComparingInt(ExtractionRegion::chunkX).thenComparingInt(ExtractionRegion::chunkZ)).toList();
        Map<ExtractionRegion, List<ExtractionLayout.Cell>> immutableCells = new HashMap<>();
        grouped.forEach((region, values) -> immutableCells.put(region, List.copyOf(values)));
        cells = Map.copyOf(immutableCells);
        Map<Chunk, List<ExtractionRegion>> byChunk = new LinkedHashMap<>();
        for (var region : regions) byChunk.computeIfAbsent(new Chunk(region.chunkX(), region.chunkZ()), ignored -> new ArrayList<>()).add(region);
        Map<Chunk, List<ExtractionRegion>> immutableChunks = new LinkedHashMap<>();
        byChunk.forEach((chunk, values) -> immutableChunks.put(chunk, List.copyOf(values)));
        chunks = Collections.unmodifiableMap(immutableChunks);
    }
    public static Map<SubjectId, ExtractionSite> declarations(ExtractionSiteState state) {
        Map<SubjectId, ExtractionSite> sites = new HashMap<>();
        state.deposits().forEach((id, deposit) -> sites.put(id, deposit.site()));
        return Map.copyOf(sites);
    }
    public boolean matches(Map<SubjectId, ExtractionSite> sites) { return declarations.equals(sites); }
    public List<ExtractionRegion> regions() { return regions; }
    public Set<Chunk> chunks() { return chunks.keySet(); }
    public List<ExtractionRegion> regions(Chunk chunk) { return chunks.getOrDefault(chunk, List.of()); }
    public List<ExtractionLayout.Cell> cells(ExtractionRegion region) {
        var result = cells.get(region);
        if (result == null) throw new IllegalArgumentException("undeclared extraction region");
        return result;
    }
}
