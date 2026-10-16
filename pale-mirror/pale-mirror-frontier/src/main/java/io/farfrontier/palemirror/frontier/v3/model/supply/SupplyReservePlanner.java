package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.math.BigInteger;
import java.util.*;

/** Pure inventory-buffer advice. Providers own consumption, selected recipes and source timing. */
public final class SupplyReservePlanner {
    public record Rate(BigInteger numerator, BigInteger denominator) {
        public static final Rate ZERO = of(0, 1);
        public static Rate of(long units, long ticks) { return new Rate(BigInteger.valueOf(units), BigInteger.valueOf(ticks)); }
        public Rate {
            if (numerator.signum() < 0 || denominator.signum() <= 0) throw new IllegalArgumentException("invalid resource rate");
            var gcd = numerator.gcd(denominator); numerator = numerator.divide(gcd); denominator = denominator.divide(gcd);
        }
        public Rate plus(Rate other) { return new Rate(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)), denominator.multiply(other.denominator)); }
        public Rate times(Rate other) { return new Rate(numerator.multiply(other.numerator), denominator.multiply(other.denominator)); }
        public long over(long ticks) {
            if (ticks < 0) throw new IllegalArgumentException("negative coverage horizon");
            return numerator.multiply(BigInteger.valueOf(ticks)).add(denominator.subtract(BigInteger.ONE)).divide(denominator).longValueExact();
        }
    }
    public record Need(Rate consumption, long immediate) {
        public Need { Objects.requireNonNull(consumption); if (immediate < 0) throw new IllegalArgumentException("negative current need"); }
    }
    /** The owner has already selected one recipe and ingredient/fuel alternatives. */
    public record Flow(String product, Map<String, Rate> inputsPerOutput) {
        public Flow {
            Objects.requireNonNull(product); inputsPerOutput = Map.copyOf(inputsPerOutput);
            if (product.isBlank() || inputsPerOutput.isEmpty() || inputsPerOutput.containsKey(product))
                throw new IllegalArgumentException("invalid reserve flow");
        }
    }
    public enum TimingBasis { LOCAL_ESTIMATE, DELIVERY_ESTIMATE, INITIAL_ESTIMATE }
    public record Timing(long leadTicks, TimingBasis basis) {
        public Timing { Objects.requireNonNull(basis); if (leadTicks < 1) throw new IllegalArgumentException("invalid replenishment estimate"); }
    }
    public record Target(long quantity, long protectedQuantity, long reorderPoint, long horizonTicks,
                         Rate consumption, Timing timing) {
        public Target {
            Objects.requireNonNull(consumption); Objects.requireNonNull(timing);
            if (protectedQuantity < 0 || reorderPoint < protectedQuantity || quantity < reorderPoint || horizonTicks < timing.leadTicks())
                throw new IllegalArgumentException("inconsistent reserve advice");
        }
    }

    public Map<String, Target> plan(Map<String, Need> needs, List<Flow> flows,
                                    Map<String, Timing> timing, ReservePolicy policy) {
        needs = Map.copyOf(needs); timing = Map.copyOf(timing); flows = List.copyOf(flows);
        if (needs.size() > 256 || !needs.keySet().equals(timing.keySet()) || flows.size() > 256)
            throw new IllegalArgumentException("invalid reserve domain");
        var byProduct = new TreeMap<String, Flow>();
        for (var flow : flows) {
            if (!needs.containsKey(flow.product()) || !needs.keySet().containsAll(flow.inputsPerOutput().keySet())
                    || byProduct.put(flow.product(), flow) != null) throw new IllegalArgumentException("undeclared or competing reserve flow");
        }
        var order = new ArrayList<String>(); var complete = new HashSet<String>();
        for (var item : new TreeSet<>(needs.keySet())) visit(item, byProduct, new HashSet<>(), complete, order);
        Collections.reverse(order); // Products first; aggregate shared inputs before expanding them.
        var rates = new HashMap<String, Rate>(); needs.forEach((item, need) -> rates.put(item, need.consumption()));
        for (var item : order) {
            var flow = byProduct.get(item);
            if (flow != null) flow.inputsPerOutput().forEach((input, ratio) -> rates.merge(input, rates.get(item).times(ratio), Rate::plus));
        }
        var result = new TreeMap<String, Target>();
        for (var item : order) {
            var source = timing.get(item); var rate = rates.get(item); long immediate = needs.get(item).immediate();
            long safety = Math.ceilDiv(Math.multiplyExact(source.leadTicks(), policy.safetyPermille()), 1000);
            long horizon = Math.addExact(Math.addExact(source.leadTicks(), safety), policy.reviewTicks());
            long protectedQuantity = Math.addExact(immediate, rate.over(safety));
            long target = Math.addExact(immediate, rate.over(horizon));
            result.put(item, new Target(target, protectedQuantity, Math.addExact(immediate, rate.over(Math.addExact(source.leadTicks(), safety))),
                    horizon, rate, source));
        }
        return Map.copyOf(result);
    }
    private static void visit(String item, Map<String, Flow> flows, Set<String> visiting, Set<String> complete, List<String> order) {
        if (complete.contains(item)) return;
        if (!visiting.add(item)) throw new IllegalArgumentException("cyclic reserve recipe at " + item);
        var flow = flows.get(item);
        if (flow != null) for (var input : new TreeSet<>(flow.inputsPerOutput().keySet())) visit(input, flows, visiting, complete, order);
        visiting.remove(item); complete.add(item); order.add(item);
    }
}
