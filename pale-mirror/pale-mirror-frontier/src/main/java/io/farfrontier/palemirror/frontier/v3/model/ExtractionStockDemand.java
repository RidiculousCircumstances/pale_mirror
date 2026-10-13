package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import java.util.*;

/** Desired products select source cells and their resource-bearing prerequisites, not a second mining engine. */
public final class ExtractionStockDemand {
    public static Set<Long> requiredCells(FrontierWorldState state, ExtractionDeposit deposit, Optional<SubjectId> completing) {
        var demanded = demandedProducts(state, deposit, completing);
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
    public static boolean requested(FrontierWorldState state, ExtractionDeposit deposit, Optional<SubjectId> completing) {
        // A boolean work-admission query needs no prerequisite closure or allocated cell set.
        // The primary material also admits future extensions beyond this finite declaration.
        if (state.bootstrap().ruleset().extraction().source().coldOutput().stream().anyMatch(output ->
                ProductionStockDemand.read(state, deposit.site().settlementId(), output.itemKind(), completing).requested())) return true;
        var demanded = demandedProducts(state, deposit, completing);
        return !demanded.isEmpty() && deposit.site().layout().cells().stream().anyMatch(cell ->
                deposit.cells().get(cell.id()).disposition() == ExtractionDeposit.Disposition.PRESENT
                        && cell.definition().coldOutput().stream().anyMatch(output -> demanded.contains(output.itemKind())));
    }
    private static Set<String> demandedProducts(FrontierWorldState state, ExtractionDeposit deposit, Optional<SubjectId> completing) {
        var products = deposit.site().layout().outputKinds();
        return state.bootstrap().ruleset().goodsTrade().policies().get(GoodsPolicyKind.PUBLIC_SETTLEMENT).stream()
                .map(GoodsTradeRules.Commodity::itemKind).filter(products::contains)
                .filter(kind -> ProductionStockDemand.read(state, deposit.site().settlementId(), kind, completing).requested())
                .collect(java.util.stream.Collectors.toSet());
    }
    public static boolean requested(FrontierWorldState state, SubjectId home) {
        return state.bootstrap().ruleset().extraction().enabled() && state.extractionSites().deposits().values().stream()
                .filter(deposit -> deposit.site().settlementId().equals(home)).anyMatch(deposit -> requested(state, deposit, Optional.empty()));
    }
    private ExtractionStockDemand() { }
}
