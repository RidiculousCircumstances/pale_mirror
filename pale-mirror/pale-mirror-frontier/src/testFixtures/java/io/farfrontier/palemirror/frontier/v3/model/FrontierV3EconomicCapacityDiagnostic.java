package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** Read-only owner-fact export for network balance analysis. No engine, grants or world writes. */
public final class FrontierV3EconomicCapacityDiagnostic {
    private FrontierV3EconomicCapacityDiagnostic() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: <snapshot-file|seed:number> <horizon-ticks>");
        long horizon = Long.parseLong(args[1]);
        FrontierWorldState state;
        long tick;
        if (args[0].startsWith("seed:")) {
            state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:economic-capacity"),
                    Long.parseLong(args[0].substring(5)), FrontierRulesets.marketQuarryGraybox())); tick = 0;
        } else {
            var checkpoint = FrontierPersistenceCodec.decodeSnapshot(Files.readAllBytes(Path.of(args[0]))).checkpoint();
            state = new FrontierWorldStateCodec().decode(checkpoint.canonicalState()); tick = checkpoint.instant().ticks();
        }
        System.out.println(render(state, tick, horizon));
    }
    public static String render(FrontierWorldState state, long tick, long horizon) {
        var nodes = new ArrayList<String>();
        var participants = state.companies().goodsTrade().participants().participants();
        for (var participant : participants.values().stream().filter(p -> p.policy() == GoodsPolicyKind.PUBLIC_SETTLEMENT)
                .sorted(Comparator.comparing(p -> p.party().id())).toList()) {
            var home = participant.party().id(); var view = GoodsParticipantView.read(state, participant);
            var cohort = state.humanPopulation().residents().values().stream().filter(r -> r.settlementId().equals(home)
                    && state.actorLocations().get(r.id()).condition().status() == ActorLifeStatus.ALIVE).toList();
            var nutrition = cohort.stream().map(r -> state.humanPopulation().nutrition(r.id()).accrueThrough(tick,
                    state.bootstrap().ruleset().residentLife(), r.characteristics().effectiveMetabolismPermille(tick))).toList();
            var basis = SettlementReservePlanning.basis(state, home);
            var stocks = new TreeMap<String, SupplyPlanner.Stock>();
            view.stocks().forEach((item, stock) -> stocks.put(item, new SupplyPlanner.Stock(stock.unclaimedInStorage(),
                    stock.expectedIncoming(), 0, 0, stock.protectedMinimum(), 0, 1,
                    state.bootstrap().ruleset().goodsTrade().supplyPlanning().require(item))));
            var capacities = SupplyCapacityComposition.read(state, home, stocks, new SupplyPlanningWindow(horizon));
            var processes = new ArrayList<String>();
            for (var process : capacities) {
                processes.add("{\"id\":" + string(process.id()) + ",\"product\":" + string(process.product())
                        + ",\"output\":" + process.outputPerBatch() + ",\"inputs\":" + quantities(process.inputs())
                        + ",\"heatPerRecipe\":" + process.heatPerBatch() + ",\"fuels\":" + quantities(process.fuels())
                        + ",\"retainedHeat\":" + process.availableHeat() + ",\"maximumRecipes\":" + process.recipesAvailableBy(horizon)
                        + ",\"recipesPerOperation\":" + process.recipesPerOperation() + ",\"labourPerOperation\":" + process.labourTicksPerOperation()
                        + ",\"workers\":" + quantities(process.labourCapacity()) + ",\"firstOutputTicks\":" + process.firstOutputDelayTicks() + "}");
            }
            var consumption = new TreeMap<String, Long>(); var inventory = new TreeMap<String, Integer>();
            basis.needs().forEach((item, need) -> consumption.put(item, need.consumption().over(horizon)));
            view.stocks().forEach((item, stock) -> inventory.put(item, stock.unclaimedInStorage()));
            var quotes = new ArrayList<String>();
            for (var commodity : state.bootstrap().ruleset().goodsTrade().policies().get(participant.policy()))
                quotes.add("{\"item\":" + string(commodity.itemKind()) + ",\"ask\":" + GoodsParticipantPricing.ask(participant, commodity).raw()
                        + ",\"bid\":" + GoodsParticipantPricing.bid(participant, commodity).raw() + "}");
            var plan = view.supplyPlan().flatMap(SupplyPlanner.Plan::economy).orElseThrow();
            var incoming = new TreeMap<String, Integer>();
            view.stocks().forEach((item, stock) -> incoming.put(item, stock.expectedIncoming()));
            nodes.add("{\"id\":" + string(home.value()) + ",\"cash\":" + view.availableMoney().raw()
                    + ",\"balance\":" + state.inventory().economics().require(home).balance().raw()
                    + ",\"residents\":" + cohort.size()
                    + ",\"hungry\":" + nutrition.stream().filter(n -> n.status() == ResidentNutritionStatus.HUNGRY).count()
                    + ",\"starving\":" + nutrition.stream().filter(n -> n.status() == ResidentNutritionStatus.STARVING).count()
                    + ",\"stocks\":" + quantities(inventory) + ",\"incoming\":" + quantities(incoming)
                    + ",\"consumption\":" + quantities(consumption)
                    + ",\"purchaseBudgets\":" + quantities(plan.purchaseBudgets())
                    + ",\"saleableStock\":" + quantities(plan.saleableStock())
                    + ",\"additionalProduction\":" + quantities(plan.additionalProduction())
                    + ",\"processes\":[" + String.join(",", processes) + "],\"quotes\":[" + String.join(",", quotes)
                    + "],\"known\":[" + participant.known().stream().map(k -> string(k.party().id().value())).sorted().collect(Collectors.joining(",")) + "]}");
        }
        return "{\"schema\":1,\"ruleset\":" + string(state.bootstrap().ruleset().id()) + ",\"tick\":" + tick
                + ",\"horizon\":" + horizon + ",\"capacityScope\":\"current owner-declared executable capabilities; not an upper bound on future development\""
                + ",\"nodes\":[" + String.join(",", nodes) + "]}";
    }
    private static String quantities(Map<String, ? extends Number> values) {
        return "{" + values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> string(e.getKey()) + ":" + e.getValue()).collect(Collectors.joining(",")) + "}";
    }
    private static String string(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
