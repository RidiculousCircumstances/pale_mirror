package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;

/** Read-only site geometry capabilities. Navigation consumes facts, never a site's jobs or commodity. */
final class KnownSiteGeometry {
    enum Owner { EXTRACTION }
    interface Provider {
        Owner owner();
        View project(FrontierWorldState state);
        Set<NavigationBlockState> palette(FrontierWorldState state);
    }
    record View(Map<TerrainColumn, List<SurfaceAnchor>> supports, Set<BlockPosition> obstacles,
                Set<BlockPosition> withdrawnSupports, Set<BlockPosition> unknown,
                Map<BlockPosition, NavigationBlockState> retainedBlocks) {
        View(Map<TerrainColumn, List<SurfaceAnchor>> supports, Set<BlockPosition> obstacles, Set<BlockPosition> withdrawnSupports, Set<BlockPosition> unknown) {
            this(supports, obstacles, withdrawnSupports, unknown, Map.of());
        }
        View(Map<TerrainColumn, List<SurfaceAnchor>> supports, Set<BlockPosition> obstacles, Set<BlockPosition> withdrawnSupports) {
            this(supports, obstacles, withdrawnSupports, Set.of());
        }
        View {
            supports = RegionalSurfaceMap.copyOf(supports); obstacles = RegionalBlockSet.copyOf(obstacles);
            withdrawnSupports = RegionalBlockSet.copyOf(withdrawnSupports);
            unknown = RegionalBlockSet.copyOf(unknown);
            retainedBlocks = RegionalBlockMap.copyOf(retainedBlocks);
        }
    }
    private static final List<Provider> PROVIDERS = List.of(new ExtractionKnownGeometry());
    static {
        if (!PROVIDERS.stream().map(Provider::owner).collect(java.util.stream.Collectors.toSet())
                .equals(EnumSet.allOf(Owner.class)) || PROVIDERS.size() != Owner.values().length)
            throw new IllegalArgumentException("missing or duplicate known-site geometry provider");
    }
    private static final ImmutableResultView<String, View> COMPLETED = new ImmutableResultView<>(4);
    private static volatile Projection preparedSeed;
    private static final ThreadLocal<Projection> WORKING = ThreadLocal.withInitial(() -> {
        var seed = preparedSeed;
        return seed == null ? new Projection() : new Projection(seed);
    });
    private static final ImmutableResultView<String, Set<NavigationBlockState>> PALETTES = new ImmutableResultView<>(2);
    private KnownSiteGeometry() { }
    /** Block type discovery must not construct spatial contacts or acquire the projection lock. */
    static Set<NavigationBlockState> palette(FrontierWorldState state) {
        var parts = new ArrayList<>(PROVIDERS.stream().map(provider -> provider.palette(state)).toList());
        parts.add(KnownNaturalGeometry.palette(state));
        return PALETTES.get(new ArrayList<Object>(parts), "types", () -> {
            var blocks = new HashSet<NavigationBlockState>();
            parts.forEach(blocks::addAll);
            return Set.copyOf(blocks);
        });
    }
    static View forState(FrontierWorldState state) {
        var parts = PROVIDERS.stream().map(provider -> provider.project(state)).toList();
        var inputs = new ArrayList<Object>(); inputs.add(state.bootstrap()); inputs.addAll(parts);
        return COMPLETED.get(inputs, "geometry", () -> {
            var builder = WORKING.get();
            var result = builder.compose(state.bootstrap(), parts);
            var seed = preparedSeed;
            if (seed == null || seed.cachedView != result) preparedSeed = new Projection(builder);
            return result;
        });
    }
    private static final class Projection {
        private FrontierBootstrap cachedBootstrap;
        private View cachedView;
        private RegionalBlockMapContributions<Owner, NavigationBlockState> blockParts = new RegionalBlockMapContributions<>();
        private RegionalSurfaceContributions<Owner> surfaceParts = new RegionalSurfaceContributions<>(true);
        private RegionalBlockContributions<Owner> obstacleParts = new RegionalBlockContributions<>();
        private RegionalBlockContributions<Owner> withdrawnParts = new RegionalBlockContributions<>();
        private RegionalBlockContributions<Owner> unknownParts = new RegionalBlockContributions<>();
        private List<View> previous = List.of();
        Projection() { }
        /** Published only as a quiescent seed; each consumer owns its mutable copy. */
        Projection(Projection source) {
            cachedBootstrap = source.cachedBootstrap; cachedView = source.cachedView; previous = source.previous;
            blockParts = source.blockParts.fork(); surfaceParts = source.surfaceParts.fork();
            obstacleParts = source.obstacleParts.fork(); withdrawnParts = source.withdrawnParts.fork();
            unknownParts = source.unknownParts.fork();
        }
        View compose(FrontierBootstrap bootstrap, List<View> parts) {
            if (cachedBootstrap != bootstrap) {
                surfaceParts = new RegionalSurfaceContributions<>(true); obstacleParts = new RegionalBlockContributions<>();
                withdrawnParts = new RegionalBlockContributions<>(); unknownParts = new RegionalBlockContributions<>();
                blockParts = new RegionalBlockMapContributions<>(); previous = List.of(); cachedView = null;
            }
            for (int i = 0; i < PROVIDERS.size(); i++) {
                var provider = PROVIDERS.get(i); var contribution = parts.get(i);
                if (i < previous.size() && previous.get(i) == contribution) continue;
                surfaceParts.replace(provider.owner(), RegionalSurfaceMap.copyOf(contribution.supports()));
                obstacleParts.replace(provider.owner(), RegionalBlockSet.copyOf(contribution.obstacles()));
                withdrawnParts.replace(provider.owner(), RegionalBlockSet.copyOf(contribution.withdrawnSupports()));
                unknownParts.replace(provider.owner(), RegionalBlockSet.copyOf(contribution.unknown()));
                blockParts.replace(provider.owner(), RegionalBlockMap.copyOf(contribution.retainedBlocks()));
            }
            var blocks = blockParts.snapshot();
            if (cachedView == null || cachedView.retainedBlocks() != blocks || cachedView.supports() != surfaceParts.snapshot()
                    || cachedView.obstacles() != obstacleParts.snapshot() || cachedView.withdrawnSupports() != withdrawnParts.snapshot()
                    || cachedView.unknown() != unknownParts.snapshot())
                cachedView = new View(surfaceParts.snapshot(), obstacleParts.snapshot(), withdrawnParts.snapshot(), unknownParts.snapshot(), blocks);
            cachedBootstrap = bootstrap; previous = parts;
            return cachedView;
        }
    }
}
