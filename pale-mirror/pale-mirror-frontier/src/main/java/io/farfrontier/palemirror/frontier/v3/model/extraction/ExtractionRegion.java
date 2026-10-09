package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Producer-owned source region: one exact naturally loaded chunk, never the entire settlement. */
public record ExtractionRegion(SubjectId siteId, int chunkX, int chunkZ) {
    public ExtractionRegion { Objects.requireNonNull(siteId); }
    public List<ExtractionLayout.Cell> cells(ExtractionSiteState state) {
        var deposit = Objects.requireNonNull(state.deposits().get(siteId), "declared extraction region site");
        var cells = deposit.site().layout().cells().stream().filter(cell -> contains(cell.source())).toList();
        if (cells.isEmpty()) throw new IllegalArgumentException("extraction source region has no declared sources");
        return cells;
    }
    public boolean contains(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) {
        return Math.floorDiv(position.x(), 16) == chunkX && Math.floorDiv(position.z(), 16) == chunkZ;
    }
    public SubjectId objectId() { return new SubjectId("extraction-region:" + siteId.value().replace(':', '-') + "/x" + chunkX + "/z" + chunkZ); }
    public SubjectId scopeId() { return new SubjectId("custody:" + objectId().value().replace(':', '-')); }
    public String provenance() { return "pale-mirror:" + objectId().value(); }
    public static List<ExtractionRegion> all(ExtractionSiteState state) {
        return state.deposits().values().stream().flatMap(deposit -> deposit.site().layout().cells().stream()
                .map(cell -> new ExtractionRegion(deposit.site().id(), Math.floorDiv(cell.source().x(), 16), Math.floorDiv(cell.source().z(), 16))))
                .distinct().sorted(Comparator.comparing(ExtractionRegion::siteId).thenComparingInt(ExtractionRegion::chunkX)
                        .thenComparingInt(ExtractionRegion::chunkZ)).toList();
    }
}
