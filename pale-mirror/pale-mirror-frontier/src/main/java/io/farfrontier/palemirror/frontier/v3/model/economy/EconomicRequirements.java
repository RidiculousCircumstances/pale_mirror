package io.farfrontier.palemirror.frontier.v3.model.economy;

import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner;
import java.util.*;

/** Net material requirements: finished stock substitutes for its inputs, never adds another demand. */
public final class EconomicRequirements {
    public record Projection(Map<String, Long> gross, Map<String, Long> missing) {
        public Projection { gross = Map.copyOf(gross); missing = Map.copyOf(missing); }
    }
    public Projection project(EconomicSnapshot facts, long horizon) {
        return project(facts, horizon, Map.of());
    }
    /** Chosen purchases change the intended input chain, never physical stock or its shortage. */
    public Projection project(EconomicSnapshot facts, long horizon, Map<String, Long> plannedPurchases) {
        if (horizon < 1) throw new IllegalArgumentException("invalid economic horizon");
        var flows = new TreeMap<String, SupplyReservePlanner.Flow>();
        for (var flow : facts.flows()) {
            if (!facts.stocks().containsKey(flow.product()) || !facts.stocks().keySet().containsAll(flow.inputsPerOutput().keySet())
                    || flows.put(flow.product(), flow) != null) throw new IllegalArgumentException("competing or undeclared economic recipe");
        }
        var order = new ArrayList<String>(); var complete = new HashSet<String>();
        for (var item : new TreeSet<>(facts.stocks().keySet())) visit(item, flows, new HashSet<>(), complete, order);
        Collections.reverse(order);
        var gross = new TreeMap<String, Long>(); var missing = new TreeMap<String, Long>();
        facts.needs().forEach((item, need) -> gross.put(item, Math.addExact(need.immediate(), need.consumption().over(horizon))));
        for (var item : order) {
            var stock = facts.stocks().get(item);
            long timedIncoming = stock.arrivals().stream().filter(a -> a.afterTicks() <= horizon).mapToLong(a -> a.quantity()).reduce(0L, Math::addExact);
            // Untimed work promises/quotes cannot erase a dated material requirement.
            long shortage = Math.max(0, gross.get(item) - Math.addExact(stock.available(), timedIncoming));
            missing.put(item, shortage);
            var flow = flows.get(item);
            long ownOutput = Math.max(0, shortage - plannedPurchases.getOrDefault(item, 0L));
            if (flow != null) flow.inputsPerOutput().forEach((input, ratio) -> gross.merge(input, ratio.over(ownOutput), Math::addExact));
        }
        return new Projection(gross, missing);
    }
    private static void visit(String item, Map<String, SupplyReservePlanner.Flow> flows, Set<String> visiting,
                              Set<String> complete, List<String> order) {
        if (complete.contains(item)) return;
        if (!visiting.add(item)) throw new IllegalArgumentException("cyclic economic recipe");
        var flow = flows.get(item);
        if (flow != null) for (var input : new TreeSet<>(flow.inputsPerOutput().keySet())) visit(input, flows, visiting, complete, order);
        visiting.remove(item); complete.add(item); order.add(item);
    }
}
