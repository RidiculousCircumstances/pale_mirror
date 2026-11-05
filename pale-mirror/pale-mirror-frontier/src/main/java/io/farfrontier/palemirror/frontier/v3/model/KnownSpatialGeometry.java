package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;

import java.util.*;

/**
 * Closed read-only spatial geometry composition. Providers publish immutable lazy regional facts;
 * common movement never discovers a task/owner from the terrain being queried.
 */
final class KnownSpatialGeometry {
    enum Key {
        GRAYBOX_TERRAIN,
        FOREST,
        AUTHORED_BUILDINGS
    }

    interface Provider {
        Key key();

        Snapshot snapshot(FrontierWorldState state);

        Set<NavigationBlockState> palette(FrontierWorldState state);
    }

    interface Snapshot {
        BoundedPedestrianApproach.SurveyedSurface overlay(
                BoundedPedestrianApproach.SurveyedSurface base);

        Set<Region> changesFrom(Snapshot previous);

        /** Dynamic facts only; pristine procedural terrain belongs to the bootstrap epoch. */
        Set<Region> regions();

        Optional<Object> token(Region region);
    }

    private static final List<Provider> PROVIDERS =
            List.of(
                    new GrayboxTerrainKnownGeometry(),
                    new ForestryKnownGeometry(),
                    new AuthoredBuildingKnownGeometry());
    private static final ImmutableResultView<String, Composite> VIEWS =
            new ImmutableResultView<>(4);
    private static final ImmutableResultView<String, Set<NavigationBlockState>> PALETTES =
            new ImmutableResultView<>(4);

    static {
        if (PROVIDERS.size() != Key.values().length
                || !PROVIDERS.stream()
                        .map(Provider::key)
                        .collect(java.util.stream.Collectors.toSet())
                        .equals(EnumSet.allOf(Key.class)))
            throw new IllegalArgumentException("missing or duplicate natural geometry provider");
    }

    record Composite(List<Snapshot> parts) {
        Composite {
            parts = List.copyOf(parts);
        }

        BoundedPedestrianApproach.SurveyedSurface overlay(
                BoundedPedestrianApproach.SurveyedSurface base) {
            for (var part : parts) base = part.overlay(base);
            return base;
        }

        Set<Region> regions() {
            var result = new HashSet<Region>();
            parts.forEach(part -> result.addAll(part.regions()));
            return Set.copyOf(result);
        }

        Optional<Object> token(Region region) {
            var values = parts.stream().map(part -> part.token(region)).toList();
            return values.stream().allMatch(Optional::isEmpty)
                    ? Optional.empty()
                    : Optional.of(values);
        }

        Set<Region> changesFrom(Composite previous) {
            if (this == previous || previous == null) return Set.of();
            var changed = new HashSet<Region>();
            for (int i = 0; i < parts.size(); i++)
                changed.addAll(parts.get(i).changesFrom(previous.parts.get(i)));
            return Set.copyOf(changed);
        }
    }

    static Composite forState(FrontierWorldState state) {
        var parts = PROVIDERS.stream().map(provider -> provider.snapshot(state)).toList();
        var inputs = new ArrayList<Object>();
        inputs.add(state.bootstrap());
        inputs.addAll(parts);
        return VIEWS.get(inputs, "natural-geometry", () -> new Composite(parts));
    }

    static Set<NavigationBlockState> palette(FrontierWorldState state) {
        var parts = PROVIDERS.stream().map(provider -> provider.palette(state)).toList();
        return PALETTES.get(
                new ArrayList<Object>(parts),
                "natural-palette",
                () -> {
                    var result = new HashSet<NavigationBlockState>();
                    parts.forEach(result::addAll);
                    return Set.copyOf(result);
                });
    }

    private KnownSpatialGeometry() {}
}
