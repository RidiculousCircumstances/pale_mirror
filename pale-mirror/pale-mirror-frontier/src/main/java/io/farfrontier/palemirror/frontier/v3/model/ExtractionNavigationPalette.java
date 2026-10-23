package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState;
import java.util.*;

/** Type declarations, not spatial knowledge. A definition permits interpreting a known
 * block; it never proves that block exists at an unobserved position. */
final class ExtractionNavigationPalette {
    private final ImmutableResultView<String, Set<NavigationBlockState>> parts = new ImmutableResultView<>(128);

    Set<NavigationBlockState> read(FrontierWorldState state) {
        return parts.get(List.of(state.bootstrap(), state.extractionSites().deposits(), state.extractionSites().geology()),
                "all", () -> collect(state));
    }

    private Set<NavigationBlockState> collect(FrontierWorldState state) {
        var result = new HashSet<NavigationBlockState>();
        result.addAll(parts.get(List.of(state.bootstrap()), "terrain", () -> terrain(state)));
        result.addAll(parts.get(List.of(state.extractionSites().geology()), "minerals", () -> {
            var blocks = new HashSet<NavigationBlockState>();
            state.extractionSites().geology().deposits().values().forEach(deposit ->
                    deposit.sections().forEach(section -> add(blocks, section.material())));
            return Set.copyOf(blocks);
        }));
        for (var deposit : state.extractionSites().deposits().values()) {
            var layout = deposit.site().layout();
            result.addAll(parts.get(List.of(layout), "layout", () -> {
                var blocks = new HashSet<NavigationBlockState>();
                layout.fixedBlocks().values().forEach(block -> add(blocks, block));
                layout.terrainSupports().values().forEach(block -> add(blocks, block));
                return Set.copyOf(blocks);
            }));
            result.addAll(parts.get(List.of(deposit.cells()), "cells", () -> {
                var blocks = new HashSet<NavigationBlockState>();
                deposit.cells().values().forEach(cell -> add(blocks, cell.knownBlock()));
                return Set.copyOf(blocks);
            }));
            result.addAll(parts.get(List.of(deposit.geometry()), "observations", () -> {
                var blocks = new HashSet<NavigationBlockState>();
                deposit.geometry().values().forEach(observation -> add(blocks, observation.block()));
                return Set.copyOf(blocks);
            }));
        }
        return Set.copyOf(result);
    }

    private static Set<NavigationBlockState> terrain(FrontierWorldState state) {
        var blocks = new HashSet<NavigationBlockState>();
        var rules = state.bootstrap().ruleset().extraction();
        rules.surfaceCut().ifPresent(cut -> {
            add(blocks, cut.cover().before());
            blocks.add(new NavigationBlockState("minecraft:air", Map.of()));
            state.bootstrap().ruleset().fields().agriculture().ifPresent(map ->
                    map.surfaces().values().forEach(palette -> blocks.addAll(palette.blocks())));
        });
        rules.geology().ifPresent(stratum -> blocks.add(new NavigationBlockState(stratum.blockKind(), Map.of())));
        return Set.copyOf(blocks);
    }

    private static void add(Set<NavigationBlockState> target, BlockExtraction.Block block) {
        target.add(new NavigationBlockState(block.kind(), block.properties()));
    }
}
