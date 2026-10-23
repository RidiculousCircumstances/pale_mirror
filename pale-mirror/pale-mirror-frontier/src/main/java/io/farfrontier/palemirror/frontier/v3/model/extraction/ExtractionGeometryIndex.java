package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ImmutableInputView;
import java.util.*;

/** Immutable declaration index. Depletion, worker progress and custody never change this view. */
public final class ExtractionGeometryIndex {
    private static final ImmutableInputView<ExtractionGeometryIndex> CURRENT = new ImmutableInputView<>();
    public record Chunk(int x, int z) { }
    private final Map<SubjectId, ExtractionSite> declarations;
    private final List<ExtractionRegion> regions;
    private final Map<ExtractionRegion, List<ExtractionLayout.Cell>> cells;
    private final Map<Chunk, List<ExtractionRegion>> chunks;
    private final Map<Chunk, Set<BlockPosition>> owned;
    private final Map<Chunk, List<SubjectId>> worksiteOwners;

    /** Keys only immutable site declarations, not depletion, work or geological exclusions. */
    public static ExtractionGeometryIndex forDeposits(Map<SubjectId, ExtractionDeposit> deposits) {
        var sites = new TreeMap<SubjectId, ExtractionSite>();
        deposits.forEach((id, deposit) -> {
            if (!id.equals(deposit.site().id())) throw new IllegalArgumentException("geometry has a foreign declaration key");
            sites.put(id, deposit.site());
        });
        return CURRENT.get(new ArrayList<Object>(sites.values()), () -> new ExtractionGeometryIndex(sites));
    }

    public ExtractionGeometryIndex(Map<SubjectId, ExtractionSite> declarations) {
        this.declarations = Map.copyOf(declarations);
        var sources = new HashSet<BlockPosition>();
        var worksiteCandidates = new HashMap<Chunk, List<SubjectId>>();
        var positions = new HashMap<Chunk, Set<BlockPosition>>();
        Map<ExtractionRegion, List<ExtractionLayout.Cell>> grouped = new HashMap<>();
        this.declarations.forEach((id, site) -> {
            if (!id.equals(site.id())) throw new IllegalArgumentException("geometry has a foreign declaration key");
            site.layout().worksitePositions().keySet().forEach(region ->
                    worksiteCandidates.computeIfAbsent(new Chunk(region.x(), region.z()), ignored -> new ArrayList<>()).add(id));
            site.layout().fixedBlocks().keySet().forEach(position -> add(positions, position));
            for (var cell : site.layout().cells()) {
                if (!sources.add(cell.source())) throw new IllegalArgumentException("competing extraction source ownership");
                add(positions, cell.source());
                var region = new ExtractionRegion(id, Math.floorDiv(cell.source().x(), 16), Math.floorDiv(cell.source().z(), 16));
                grouped.computeIfAbsent(region, ignored -> new ArrayList<>()).add(cell);
            }
        });
        worksiteCandidates.replaceAll((chunk, owners) -> owners.stream().sorted().toList());
        worksiteOwners = Map.copyOf(worksiteCandidates);
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
        var frozen = new HashMap<Chunk, Set<BlockPosition>>();
        positions.forEach((chunk, values) -> frozen.put(chunk, Collections.unmodifiableSet(values)));
        owned = Map.copyOf(frozen);
    }
    private static void add(Map<Chunk, Set<BlockPosition>> positions, BlockPosition position) {
        positions.computeIfAbsent(new Chunk(position.x() >> 4, position.z() >> 4), ignored -> new HashSet<>()).add(position);
    }
    /** Candidates include natural terrain and infrastructure, not only resource custody cells.
     * The producer still resolves the exact position and rejects overlapping declarations. */
    public List<SubjectId> worksiteOwners(Chunk chunk) { return worksiteOwners.getOrDefault(chunk, List.of()); }
    public boolean owns(BlockPosition position) {
        return owned.getOrDefault(new Chunk(position.x() >> 4, position.z() >> 4), Set.of()).contains(position);
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
