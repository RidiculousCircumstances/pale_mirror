package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Immutable declaration directory. Each site's block-sized parts are shared across
 * world drafts; changing one worksite never copies every other mine's blocks. */
public final class ExtractionGeometryIndex {
    private static final ImmutableInputView<ExtractionGeometryIndex> CURRENT = new ImmutableInputView<>();
    private static final ImmutableResultView<SubjectId, Part> PARTS = new ImmutableResultView<>(SimulationCapacity.MAX_EXTRACTION_SITES * 2);
    public record Chunk(int x, int z) { }
    private final Map<SubjectId, ExtractionSite> declarations;
    private final List<ExtractionRegion> regions;
    private final Map<ExtractionRegion, List<ExtractionLayout.Cell>> cells;
    private final Map<Chunk, List<ExtractionRegion>> chunks;
    private final Map<Chunk, List<Part>> owned;
    private final Map<Chunk, List<SubjectId>> worksiteOwners;

    private record Part(Map<Chunk, Set<BlockPosition>> owned, Map<Chunk, Set<BlockPosition>> sources,
                        Map<Chunk, List<ExtractionLayout.Cell>> cells, Set<Chunk> worksites) {
        static Part of(ExtractionLayout layout) {
            var owned = new HashMap<Chunk, Set<BlockPosition>>();
            var sources = new HashMap<Chunk, Set<BlockPosition>>();
            var cells = new HashMap<Chunk, List<ExtractionLayout.Cell>>();
            layout.fixedBlocks().keySet().forEach(position -> add(owned, position));
            for (var cell : layout.cells()) {
                if (!add(sources, cell.source())) throw new IllegalArgumentException("competing extraction source ownership");
                add(owned, cell.source());
                cells.computeIfAbsent(chunk(cell.source()), ignored -> new ArrayList<>()).add(cell);
            }
            owned.replaceAll((chunk, values) -> Collections.unmodifiableSet(values));
            sources.replaceAll((chunk, values) -> Collections.unmodifiableSet(values));
            cells.replaceAll((chunk, values) -> List.copyOf(values));
            var worksites = new HashSet<Chunk>();
            layout.worksitePositions().keySet().forEach(region -> worksites.add(new Chunk(region.x(), region.z())));
            return new Part(Map.copyOf(owned), Map.copyOf(sources), Map.copyOf(cells), Set.copyOf(worksites));
        }
    }
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
        var worksiteCandidates = new HashMap<Chunk, List<SubjectId>>();
        var positions = new HashMap<Chunk, List<Part>>();
        var grouped = new HashMap<ExtractionRegion, List<ExtractionLayout.Cell>>();
        this.declarations.forEach((id, site) -> {
            if (!id.equals(site.id())) throw new IllegalArgumentException("geometry has a foreign declaration key");
            var part = PARTS.get(List.of(site.layout()), id, () -> Part.of(site.layout()));
            part.worksites().forEach(chunk -> worksiteCandidates.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(id));
            part.owned().keySet().forEach(chunk -> positions.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(part));
            part.cells().forEach((chunk, values) -> grouped.put(new ExtractionRegion(id, chunk.x(), chunk.z()), values));
        });
        // Only co-located parts can compete. Each part already proves its own
        // uniqueness; distant sources need neither a new global set nor a scan.
        positions.forEach((chunk, parts) -> {
            for (int a = 0; a < parts.size(); a++) for (int b = a + 1; b < parts.size(); b++)
                if (!Collections.disjoint(parts.get(a).sources().getOrDefault(chunk, Set.of()),
                        parts.get(b).sources().getOrDefault(chunk, Set.of())))
                    throw new IllegalArgumentException("competing extraction source ownership");
        });
        worksiteCandidates.replaceAll((chunk, owners) -> owners.stream().sorted().toList());
        worksiteOwners = Map.copyOf(worksiteCandidates);
        regions = grouped.keySet().stream().sorted(Comparator.comparing(ExtractionRegion::siteId)
                .thenComparingInt(ExtractionRegion::chunkX).thenComparingInt(ExtractionRegion::chunkZ)).toList();
        cells = Map.copyOf(grouped);
        var byChunk = new LinkedHashMap<Chunk, List<ExtractionRegion>>();
        for (var region : regions) byChunk.computeIfAbsent(new Chunk(region.chunkX(), region.chunkZ()), ignored -> new ArrayList<>()).add(region);
        byChunk.replaceAll((chunk, values) -> List.copyOf(values)); chunks = Collections.unmodifiableMap(byChunk);
        positions.replaceAll((chunk, parts) -> List.copyOf(parts)); owned = Map.copyOf(positions);
    }
    private static Chunk chunk(BlockPosition position) { return new Chunk(position.x() >> 4, position.z() >> 4); }
    private static boolean add(Map<Chunk, Set<BlockPosition>> positions, BlockPosition position) {
        return positions.computeIfAbsent(chunk(position), ignored -> new HashSet<>()).add(position);
    }
    /** Candidates include natural terrain and infrastructure, not only resource custody cells. */
    public List<SubjectId> worksiteOwners(Chunk chunk) { return worksiteOwners.getOrDefault(chunk, List.of()); }
    public boolean owns(BlockPosition position) {
        var chunk = chunk(position);
        for (var part : owned.getOrDefault(chunk, List.of())) if (part.owned().get(chunk).contains(position)) return true;
        return false;
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
