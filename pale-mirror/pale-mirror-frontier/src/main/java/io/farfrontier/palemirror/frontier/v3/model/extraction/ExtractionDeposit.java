package io.farfrontier.palemirror.frontier.v3.model.extraction;

import java.util.*;

/** Sole depletion history. Removing a source externally is not a production receipt. */
public record ExtractionDeposit(ExtractionSite site, Map<Long, CellState> cells, Map<io.farfrontier.palemirror.frontier.v3.model.BlockPosition, GeometryState> geometry) {
    public record GeometryState(long revision, BlockExtraction.Block block) {
        public GeometryState { Objects.requireNonNull(block); if (revision < 2) throw new IllegalArgumentException("external geometry needs a successor generation"); }
    }
    public ExtractionDeposit(ExtractionSite site, Map<Long, CellState> cells) { this(site, cells, Map.of()); }
    public enum Disposition {
        PRESENT(1), EXTRACTED(2), EXTERNALLY_CHANGED(3);
        private final int tag;
        Disposition(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Disposition fromWireTag(int tag) {
            return switch (tag) { case 1 -> PRESENT; case 2 -> EXTRACTED; case 3 -> EXTERNALLY_CHANGED;
                default -> throw new IllegalArgumentException("unknown extraction disposition tag"); };
        }
    }
    public record CellState(long revision, Disposition disposition, BlockExtraction.Block knownBlock, Optional<String> extractionOperation) {
        public CellState(long revision, Disposition disposition, BlockExtraction.Block knownBlock) {
            this(revision, disposition, knownBlock, Optional.empty());
        }
        public CellState {
            Objects.requireNonNull(disposition); Objects.requireNonNull(knownBlock); Objects.requireNonNull(extractionOperation);
            if (revision < 1) throw new IllegalArgumentException("extraction cell needs a positive version");
            if (disposition == Disposition.EXTRACTED && extractionOperation.isEmpty()
                    || disposition == Disposition.PRESENT && extractionOperation.isPresent()
                    || extractionOperation.filter(value -> value.isBlank() || value.length() > 160).isPresent())
                throw new IllegalArgumentException("depletion requires its exact production operation");
        }
    }
    public ExtractionDeposit {
        Objects.requireNonNull(site); cells = Map.copyOf(cells); geometry = Map.copyOf(geometry);
        if (!site.layout().fixedBlocks().keySet().containsAll(geometry.keySet()))
            throw new IllegalArgumentException("external geometry must name an authored worksite cell");
        if (!cells.keySet().equals(site.layout().cells().stream().map(ExtractionLayout.Cell::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet())))
            throw new IllegalArgumentException("depletion history must cover the exact declared deposit");
        for (var cell : site.layout().cells()) {
            CellState state = cells.get(cell.id());
            if (state.disposition() == Disposition.PRESENT && !state.knownBlock().equals(cell.definition().before())
                    || state.disposition() == Disposition.EXTRACTED && !state.knownBlock().equals(cell.definition().after()))
                throw new IllegalArgumentException("depletion disposition contradicts its declared block");
        }
    }
    public static ExtractionDeposit initial(ExtractionSite site) {
        return new ExtractionDeposit(site, site.layout().cells().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                ExtractionLayout.Cell::id, cell -> new CellState(1, Disposition.PRESENT, cell.definition().before()))));
    }
    public List<ExtractionLayout.Cell> available(Set<Long> reserved) {
        return site.layout().cells().stream().filter(cell -> cells.get(cell.id()).disposition() == Disposition.PRESENT
                && !reserved.contains(cell.id()) && cell.prerequisites().stream().allMatch(id ->
                    cells.get(id).knownBlock().equals(site.layout().require(id).definition().after()))).toList();
    }
    public ExtractionDeposit observe(long cellId, long expectedRevision, BlockExtraction.Block actual) {
        return transition(cellId, expectedRevision, Disposition.EXTERNALLY_CHANGED, actual, cells.get(cellId).extractionOperation());
    }
    /** Only the extraction process may call this after its exact retained effect has settled. */
    public ExtractionDeposit extracted(long cellId, long expectedRevision, String operation) {
        CellState current = cells.get(cellId);
        if (current == null || current.disposition() != Disposition.PRESENT)
            throw new IllegalArgumentException("cannot produce again from a depleted or foreign source");
        return transition(cellId, expectedRevision, Disposition.EXTRACTED, site.layout().require(cellId).definition().after(), Optional.of(operation));
    }
    private ExtractionDeposit transition(long cellId, long expectedRevision, Disposition disposition, BlockExtraction.Block block, Optional<String> operation) {
        var current = Objects.requireNonNull(cells.get(cellId), "declared extraction cell");
        if (current.revision() != expectedRevision) throw new IllegalArgumentException("stale extraction cell observation");
        if (current.knownBlock().equals(block) && current.disposition() == disposition) return this;
        var next = new LinkedHashMap<>(cells);
        next.put(cellId, new CellState(Math.addExact(expectedRevision, 1), disposition, block, operation));
        return new ExtractionDeposit(site, next, geometry);
    }
    public ExtractionDeposit observeGeometry(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position,
            long expectedRevision, BlockExtraction.Block actual) {
        if (!site.layout().fixedBlocks().containsKey(position)) throw new IllegalArgumentException("unknown extraction geometry cell");
        var prior = geometry.get(position);
        if ((prior == null ? 1 : prior.revision()) != expectedRevision) throw new IllegalArgumentException("stale worksite geometry observation");
        var next = new LinkedHashMap<>(geometry); next.put(position, new GeometryState(Math.addExact(expectedRevision, 1), actual));
        return new ExtractionDeposit(site, cells, next);
    }
}
