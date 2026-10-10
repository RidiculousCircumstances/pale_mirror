package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import java.util.*;

/** Exact retained geometric proposal; admission is neither mining nor a resource/custody receipt. */
public record ExtractionAreaExtended(SubjectId siteId, long expectedRevision,
        List<AdjacentExcavationPlanner.Column<BlockExtraction.Block>> columns) implements FrontierPayload {
    public ExtractionAreaExtended {
        Objects.requireNonNull(siteId); columns = List.copyOf(columns);
        if (expectedRevision < 1 || columns.isEmpty() || columns.size() > AdjacentExcavationPlanner.MAX_COLUMNS
                || columns.stream().map(column -> new TerrainColumn(column.floor().x(), column.floor().z())).distinct().count() != columns.size())
            throw new IllegalArgumentException("invalid bounded excavation admission");
        for (var column : columns) {
            if (column.station().y() - column.floor().y() < 0 || column.station().y() - column.floor().y() > 1
                    || Math.abs(column.station().x() - column.floor().x()) + Math.abs(column.station().z() - column.floor().z()) != 1
                    || column.support().permission() != KnownBlockGeometry.Sample.Permission.PUBLIC
                    || column.upper().permission() != KnownBlockGeometry.Sample.Permission.PUBLIC
                    || column.lower().permission() != KnownBlockGeometry.Sample.Permission.PUBLIC)
                throw new IllegalArgumentException("excavation requires an adjacent known public front");
        }
    }
    @Override public String type() { return "frontier.extraction_area_extended"; }
}
