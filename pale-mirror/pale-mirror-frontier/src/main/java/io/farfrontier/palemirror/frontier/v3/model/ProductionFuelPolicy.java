package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Production declares its protected fuel buffer; commerce merely obeys this current minimum. */
public final class ProductionFuelPolicy {
    public static int reserveRequirement(FrontierWorldState state, SubjectId home, String itemKind) {
        var catalog = state.bootstrap().ruleset().production();
        var fuel = catalog.fuels().stream().filter(value -> value.itemKind().equals(itemKind)).findFirst();
        if (fuel.isEmpty()) return 0;
        long heat = state.inventory().containers().values().stream().filter(record -> record.ownerId().equals(home)
                && state.inventory().surfaces().get(record.id()).status() != ContainerSurfaceStatus.RETIRED
                && state.inventory().surfaces().get(record.id()).status() != ContainerSurfaceStatus.CONFLICT)
                .flatMap(record -> record.productionStation().stream()).mapToLong(station -> catalog.recipes().stream()
                        .filter(recipe -> recipe.capability() == station.capability())
                        .mapToLong(recipe -> Math.multiplyExact(recipe.heatPerBatch(), 2L * (64 / recipe.ingredient().quantity())))
                        .max().orElse(0)).sum();
        return Math.toIntExact(Math.ceilDiv(heat, fuel.orElseThrow().heatUnits()));
    }
    private ProductionFuelPolicy() { }
}
