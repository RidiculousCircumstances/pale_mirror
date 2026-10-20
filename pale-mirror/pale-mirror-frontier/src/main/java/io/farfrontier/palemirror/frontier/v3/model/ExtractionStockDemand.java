package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import java.util.*;

/** Desired products select source cells and their resource-bearing prerequisites, not a second mining engine. */
public final class ExtractionStockDemand {
    private static final ImmutableInputView<Map<SubjectId, Set<String>>> UNDEVELOPED = new ImmutableInputView<>();
    public static Set<Long> requiredCells(FrontierWorldState state, ExtractionDeposit deposit, Optional<SubjectId> completing, long atTick) {
        var demanded = demandedProducts(state, deposit, completing, atTick);
        var result = new HashSet<Long>(); var pending = new ArrayDeque<Long>();
        for (var cell : deposit.site().layout().cells())
            if (deposit.cells().get(cell.id()).disposition() == ExtractionDeposit.Disposition.PRESENT
                    && cell.definition().coldOutput().stream().anyMatch(output -> demanded.contains(output.itemKind()))) pending.add(cell.id());
        while (!pending.isEmpty()) {
            long id = pending.removeFirst();
            if (result.add(id)) pending.addAll(deposit.site().layout().require(id).prerequisites());
        }
        return Set.copyOf(result);
    }
    public static boolean requested(FrontierWorldState state, ExtractionDeposit deposit, Optional<SubjectId> completing, long atTick) {
        // A boolean work-admission query needs no prerequisite closure or allocated cell set.
        // The primary material also admits future extensions beyond this finite declaration.
        if (state.bootstrap().ruleset().extraction().source().coldOutput().stream().anyMatch(output ->
                ProductionStockDemand.read(state, deposit.site().settlementId(), output.itemKind(), completing, atTick).requested())) return true;
        var demanded = demandedProducts(state, deposit, completing, atTick);
        return !demanded.isEmpty() && (deposit.site().layout().cells().stream().anyMatch(cell ->
                deposit.cells().get(cell.id()).disposition() == ExtractionDeposit.Disposition.PRESENT
                        && cell.definition().coldOutput().stream().anyMatch(output -> demanded.contains(output.itemKind())))
                || undevelopedProducts(state).get(deposit.site().id()).stream().anyMatch(demanded::contains));
    }
    static Set<String> demandedProducts(FrontierWorldState state, ExtractionDeposit deposit, Optional<SubjectId> completing, long atTick) {
        var products = new HashSet<>(deposit.site().layout().outputKinds());
        products.addAll(undevelopedProducts(state).get(deposit.site().id()));
        return state.bootstrap().ruleset().goodsTrade().policies().get(GoodsPolicyKind.PUBLIC_SETTLEMENT).stream()
                .map(GoodsTradeRules.Commodity::itemKind).filter(products::contains)
                .filter(kind -> ProductionStockDemand.read(state, deposit.site().settlementId(), kind, completing, atTick).requested())
                .collect(java.util.stream.Collectors.toSet());
    }
    /** Declared but not yet admitted natural sources keep demand alive after
     * the initial face is depleted. This projection never grants reach or work. */
    private static Map<SubjectId, Set<String>> undevelopedProducts(FrontierWorldState state) {
        return UNDEVELOPED.get(List.of(state.bootstrap(), state.extractionSites().deposits(),
                state.extractionSites().geologicalExclusions(), state.extractionSites().geology(), state.extractionSites().knowledge()), () -> {
            var result = new LinkedHashMap<SubjectId, Set<String>>();
            for (var deposit : state.extractionSites().deposits().values()) {
                var sources = deposit.site().layout().cells().stream().map(ExtractionLayout.Cell::source)
                        .collect(java.util.stream.Collectors.toSet());
                var products = new HashSet<String>();
                var geometry = new ExtractionWorkingSurfaces(state, deposit);
                for (var position : deposit.site().layout().terrainSupports().keySet()) {
                    if (sources.contains(position)) continue;
                    geometry.at(position).flatMap(state.bootstrap().ruleset().extraction()::definitionFor)
                            .ifPresent(material -> material.coldOutput().forEach(output -> products.add(output.itemKind())));
                }
                result.put(deposit.site().id(), Set.copyOf(products));
            }
            return Map.copyOf(result);
        });
    }
    public static boolean requested(FrontierWorldState state, SubjectId home, long atTick) {
        return state.bootstrap().ruleset().extraction().enabled() && state.extractionSites().deposits().values().stream()
                .filter(deposit -> deposit.site().settlementId().equals(home)).anyMatch(deposit -> requested(state, deposit, Optional.empty(), atTick));
    }
    private ExtractionStockDemand() { }
}
