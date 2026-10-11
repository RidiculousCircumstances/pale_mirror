package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockGeometry;
import java.util.Map;
import java.util.Optional;

/** Exact content-declared terrain facts shared by planning and invalidation; never inferred from a worksite. */
public final class ExtractionTerrainFacts {
    private ExtractionTerrainFacts() { }
    public static Optional<KnownBlockGeometry.Sample<BlockExtraction.Block>> at(FrontierBootstrap bootstrap, BlockPosition position) {
        if (!bootstrap.bounds().contains(position)) return Optional.empty();
        var rules = bootstrap.ruleset().extraction();
        var cover = rules.surfaceCut().filter(value -> value.topY() == position.y());
        if (cover.isPresent()) return Optional.of(new KnownBlockGeometry.Sample<>(cover.orElseThrow().cover().before(),
                cover.orElseThrow().revision(), KnownBlockGeometry.Sample.Permission.PUBLIC));
        return rules.geology().flatMap(stratum -> stratum.at(bootstrap.bounds(), position).map(kind ->
                new KnownBlockGeometry.Sample<>(new BlockExtraction.Block(kind, Map.of()), stratum.revision(),
                        KnownBlockGeometry.Sample.Permission.PUBLIC)));
    }
}
