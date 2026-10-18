package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockGeometry;
import java.util.Map;
import java.util.Optional;

/** Exact content-declared terrain facts shared by planning and invalidation; never inferred from a worksite. */
public final class ExtractionTerrainFacts {
    private static final BlockExtraction.Block AIR = new BlockExtraction.Block("minecraft:air", Map.of());
    // One derived proof per execution thread, keyed by the exact immutable declarations.
    // Weak references neither retain retired worlds nor serialize/cache physical facts.
    private static final ThreadLocal<CoverageProof> COVERAGE = new ThreadLocal<>();
    private static final ThreadLocal<TerrainView> TERRAIN = new ThreadLocal<>();
    private record TerrainView(java.lang.ref.WeakReference<FrontierBootstrap> bootstrap,
            Optional<KnownBlockGeometry.Sample<BlockExtraction.Block>> air,
            Optional<KnownBlockGeometry.Sample<BlockExtraction.Block>> cover,
            Optional<KnownBlockGeometry.Sample<BlockExtraction.Block>> rock,
            Map<io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState,
                KnownBlockGeometry.Sample<BlockExtraction.Block>> surfaces) { }
    private record CoverageProof(java.lang.ref.WeakReference<FrontierBootstrap> bootstrap,
            java.lang.ref.WeakReference<io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockExclusions> exclusions) { }
    private ExtractionTerrainFacts() { }

    /** Coverage alone must not construct a nominal block/sample for every invalidated voxel. */
    public static boolean contains(FrontierBootstrap bootstrap, BlockPosition position) {
        if (!bootstrap.bounds().contains(position)) return false;
        var rules = bootstrap.ruleset().extraction();
        if (rules.surfaceCut().isPresent() && position.y() >= rules.surfaceCut().orElseThrow().topY()) return true;
        return rules.geology().filter(stratum -> position.y() >= stratum.minY() && position.y() <= stratum.maxY()).isPresent();
    }

    public static void validateExclusions(FrontierBootstrap bootstrap,
            io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockExclusions exclusions) {
        java.util.Objects.requireNonNull(bootstrap); java.util.Objects.requireNonNull(exclusions);
        var proof = COVERAGE.get();
        if (proof != null && proof.bootstrap().get() == bootstrap && proof.exclusions().get() == exclusions) return;
        var previous = proof != null && proof.bootstrap().get() == bootstrap ? proof.exclusions().get() : null;
        var unchecked = previous == null ? exclusions.positions()
                : exclusions.additionsSince(previous).orElse(exclusions.positions());
        for (var position : unchecked)
            if (!contains(bootstrap, position)) throw new IllegalArgumentException("geological exclusion has no declared baseline");
        COVERAGE.set(new CoverageProof(new java.lang.ref.WeakReference<>(bootstrap), new java.lang.ref.WeakReference<>(exclusions)));
    }
    public static Optional<KnownBlockGeometry.Sample<BlockExtraction.Block>> at(FrontierBootstrap bootstrap, BlockPosition position) {
        if (!bootstrap.bounds().contains(position)) return Optional.empty();
        var rules = bootstrap.ruleset().extraction();
        var view = TERRAIN.get();
        if (view == null || view.bootstrap().get() != bootstrap) {
            var air = rules.surfaceCut().map(cut -> new KnownBlockGeometry.Sample<>(AIR, cut.revision(), KnownBlockGeometry.Sample.Permission.PUBLIC));
            var cover = rules.surfaceCut().map(cut -> new KnownBlockGeometry.Sample<>(cut.cover().before(), cut.revision(), KnownBlockGeometry.Sample.Permission.PUBLIC));
            var rock = rules.geology().map(stratum -> new KnownBlockGeometry.Sample<>(new BlockExtraction.Block(stratum.blockKind(), Map.of()),
                    stratum.revision(), KnownBlockGeometry.Sample.Permission.PUBLIC));
            var surfaces = new java.util.HashMap<io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState,
                    KnownBlockGeometry.Sample<BlockExtraction.Block>>();
            if (rules.surfaceCut().isPresent()) bootstrap.ruleset().fields().agriculture().ifPresent(map ->
                    map.surfaces().values().forEach(palette -> palette.blocks().forEach(block -> surfaces.computeIfAbsent(block,
                        value -> new KnownBlockGeometry.Sample<>(new BlockExtraction.Block(value.kind(), value.properties()),
                            rules.surfaceCut().orElseThrow().revision(), KnownBlockGeometry.Sample.Permission.PUBLIC)))));
            view = new TerrainView(new java.lang.ref.WeakReference<>(bootstrap), air, cover, rock, Map.copyOf(surfaces));
            TERRAIN.set(view);
        }
        if (rules.surfaceCut().isPresent() && position.y() > rules.surfaceCut().orElseThrow().topY())
            return view.air();
        if (rules.surfaceCut().isPresent() && position.y() == rules.surfaceCut().orElseThrow().topY()) {
            var surface = bootstrap.ruleset().fields().agriculture().flatMap(map -> map.surfaceAt(position.x(), position.z()));
            return surface.isPresent() ? Optional.of(java.util.Objects.requireNonNull(view.surfaces().get(surface.orElseThrow()))) : view.cover();
        }
        return rules.geology().filter(stratum -> position.y() >= stratum.minY() && position.y() <= stratum.maxY()).isPresent()
                ? view.rock() : Optional.empty();
    }
}
