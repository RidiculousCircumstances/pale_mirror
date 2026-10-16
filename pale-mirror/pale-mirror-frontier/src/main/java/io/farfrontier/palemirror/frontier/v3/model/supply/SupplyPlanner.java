package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;

/** Pure supply advice. No jobs, physical endpoints, reservations or inventory authority. */
public final class SupplyPlanner {
    public enum Reason { STOCK_REPLENISHMENT, CONSUMPTION_DEADLINE, PRODUCTION_INPUT, PRODUCTION_FUEL, CRITICAL_PRODUCTION_DEADLINE, UNSECURED_PRODUCTION }
    /** Quantity already included in Stock.incoming, with an owner-supplied relative planning ETA. */
    public record Arrival(long quantity, long afterTicks) {
        public Arrival {
            if (quantity < 1 || afterTicks < 0) throw new IllegalArgumentException("invalid incoming delivery timing");
        }
    }
    public record Stock(long available, long incoming, long promisedProduction, long target,
                        long protectedMinimum, long consumptionUnits, long consumptionPeriodTicks,
                        SupplyPolicy policy, List<Arrival> arrivals) {
        public Stock(long available, long incoming, long promisedProduction, long target,
                     long protectedMinimum, long consumptionUnits, long consumptionPeriodTicks, SupplyPolicy policy) {
            this(available, incoming, promisedProduction, target, protectedMinimum, consumptionUnits, consumptionPeriodTicks, policy, List.of());
        }
        public Stock {
            Objects.requireNonNull(policy);
            arrivals = List.copyOf(arrivals);
            if (available < 0 || incoming < 0 || promisedProduction < 0 || target < 0
                    || protectedMinimum < 0 || consumptionUnits < 0 || consumptionPeriodTicks < 1
                    || arrivals.size() > 1024 || arrivals.stream().mapToLong(Arrival::quantity).reduce(0, Math::addExact) > incoming)
                throw new IllegalArgumentException("invalid supply stock facts");
        }
        public long covered() { return Math.addExact(available, Math.addExact(incoming, promisedProduction)); }
        public long deadline() {
            return consumptionUnits == 0 ? Long.MAX_VALUE
                    : Math.multiplyExact(available, consumptionPeriodTicks) / consumptionUnits;
        }
        public long reorderPoint() {
            long travelUse = Math.ceilDiv(Math.multiplyExact(consumptionUnits, policy.leadTicks()), consumptionPeriodTicks);
            return Math.max(protectedMinimum, Math.max(travelUse,
                    Math.multiplyExact(target, policy.reorderPermille()) / 1000));
        }
    }
    /** Capacity owner supplies feasible recipes and conservative timing, not an executor. */
    public record Process(String id, String product, int outputPerBatch, Map<String, Integer> inputs,
                          long heatPerBatch, Map<String, Long> fuels, long availableHeat,
                          long firstOutputDelayTicks, long batchTicks, int maximumBatches) {
        public Process {
            Objects.requireNonNull(id); Objects.requireNonNull(product);
            inputs = Map.copyOf(inputs); fuels = Map.copyOf(fuels);
            if (id.isBlank() || product.isBlank() || outputPerBatch < 1 || inputs.isEmpty()
                    || inputs.entrySet().stream().anyMatch(e -> e.getKey().isBlank() || e.getValue() < 1)
                    || heatPerBatch < 0 || availableHeat < 0 || firstOutputDelayTicks < 0
                    || batchTicks < 1 || maximumBatches < 1
                    || fuels.entrySet().stream().anyMatch(e -> e.getKey().isBlank() || e.getValue() < 1)
                    || heatPerBatch > 0 && fuels.isEmpty())
                throw new IllegalArgumentException("invalid supplied production capacity");
        }
    }
    public record Demand(long purchase, long production, long recoveryProduction, long protectedInput, long deadlineTicks,
                         Set<Reason> reasons, Optional<String> process) {
        public Demand(long purchase, long production, long protectedInput, long deadlineTicks,
                      Set<Reason> reasons, Optional<String> process) {
            this(purchase, production, 0, protectedInput, deadlineTicks, reasons, process);
        }
        public Demand {
            reasons = Set.copyOf(reasons); process = Objects.requireNonNull(process);
            if (purchase < 0 || production < 0 || recoveryProduction < 0 || protectedInput < 0 || deadlineTicks < 0)
                throw new IllegalArgumentException("invalid supply demand");
        }
        public boolean urgent(long leadTicks) {
            return (reasons.contains(Reason.CONSUMPTION_DEADLINE) || reasons.contains(Reason.CRITICAL_PRODUCTION_DEADLINE))
                    && deadlineTicks <= leadTicks;
        }
    }
    public record Plan(Map<String, Demand> demands) {
        public Plan { demands = Map.copyOf(demands); }
        public Demand require(String item) { return Objects.requireNonNull(demands.get(item), "undeclared demand " + item); }
    }
    /** Remaining unsecured dependency of already committed production. Not a second output promise. */
    public record Dependency(String product, String resource, long quantity, Reason reason) {
        public Dependency {
            Objects.requireNonNull(product); Objects.requireNonNull(resource); Objects.requireNonNull(reason);
            if (product.isBlank() || resource.isBlank() || product.equals(resource) || quantity < 1
                    || reason != Reason.PRODUCTION_INPUT && reason != Reason.PRODUCTION_FUEL)
                throw new IllegalArgumentException("invalid committed production dependency");
        }
    }
    public Plan plan(Map<String, Stock> facts, List<Process> capacities) {
        return plan(facts, capacities, List.of());
    }
    public Plan plan(Map<String, Stock> facts, List<Process> capacities, List<Dependency> commitments) {
        var observed = Map.copyOf(facts); var processes = List.copyOf(capacities);
        commitments = List.copyOf(commitments);
        if (observed.isEmpty() || observed.size() > 256 || processes.size() > 256
                || commitments.size() > 4096
                || processes.stream().map(Process::id).distinct().count() != processes.size())
            throw new IllegalArgumentException("unbounded or duplicate supply inputs");
        for (var process : processes) {
            requireStock(observed, process.product());
            process.inputs().keySet().forEach(item -> requireStock(observed, item));
            process.fuels().keySet().forEach(item -> requireStock(observed, item));
        }
        validateDependencies(observed, processes, commitments);
        var resources = new SupplyResources(observed);
        var unsecured = resources.retain(commitments);
        var pendingUnsecured = new HashMap<String, Long>();
        var backed = new HashMap<>(observed);
        for (var product : unsecured) {
            var value = observed.get(product);
            pendingUnsecured.put(product, value.promisedProduction());
            backed.put(product, new Stock(value.available(), value.incoming(), 0, value.target(),
                    value.protectedMinimum(), value.consumptionUnits(), value.consumptionPeriodTicks(), value.policy(), value.arrivals()));
        }
        var stocks = Map.copyOf(backed);
        var demands = new TreeMap<String, Demand>();
        var allocatedBatches = new HashMap<String, Long>();
        stocks.keySet().forEach(item -> demands.put(item,
                new Demand(0, 0, 0, Long.MAX_VALUE, Set.of(), Optional.empty())));
        for (var committed : commitments) {
            var product = requireStock(stocks, committed.product());
            long deadline = product.policy().essential() ? product.deadline() : Long.MAX_VALUE;
            dependency(stocks, processes, demands, allocatedBatches, resources, committed.resource(), committed.quantity(),
                    deadline, Set.of(), committed.reason(), new HashSet<>(Set.of(committed.product())));
        }
        for (var entry : stocks.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var stock = entry.getValue();
            // An admitted replenishment finishes toward the upper target; its first
            // partial promise must not switch the same purchase off at the lower mark.
            if (stock.covered() > stock.reorderPoint() && stock.incoming() == 0) continue;
            long missing = Math.max(0, Math.max(stock.target(), stock.protectedMinimum()) - stock.covered());
            var retained = demands.get(entry.getKey());
            missing = Math.max(0, missing - retained.purchase() - retained.production());
            if (missing == 0) continue;
            var reasons = EnumSet.of(Reason.STOCK_REPLENISHMENT);
            long deadline = stock.policy().essential() ? stock.deadline() : Long.MAX_VALUE;
            if (deadline <= stock.policy().leadTicks()) reasons.add(Reason.CONSUMPTION_DEADLINE);
            // An existing unfunded job keeps its identity and dependency demand. Do not
            // invent another job, but do not let its output suppress the purchase fallback.
            long pending = Math.min(missing, pendingUnsecured.getOrDefault(entry.getKey(), 0L));
            if (pending > 0) {
                reasons.add(Reason.UNSECURED_PRODUCTION);
                var combined = EnumSet.copyOf(reasons); combined.addAll(retained.reasons());
                demands.put(entry.getKey(), new Demand(Math.addExact(retained.purchase(), pending), retained.production(),
                        retained.recoveryProduction(), retained.protectedInput(), Math.min(deadline, retained.deadlineTicks()),
                        combined, retained.process()));
            }
            if (missing > pending) expand(stocks, processes, demands, allocatedBatches, resources,
                    entry.getKey(), missing - pending, deadline, reasons, new HashSet<>());
        }
        return new Plan(demands);
    }
    private void expand(Map<String, Stock> stocks, List<Process> capacities, Map<String, Demand> demands, Map<String, Long> allocatedBatches, SupplyResources resources,
                        String item, long amount, long deadline, Set<Reason> reasons, Set<String> ancestors) {
        if (!ancestors.add(item)) throw new IllegalArgumentException("cyclic production dependency at " + item);
        var stock = requireStock(stocks, item);
        var selected = capacities.stream().filter(p -> p.product().equals(item)
                        && feasibleBatches(p, resources, allocatedBatches.getOrDefault(p.id(), 0L), amount, deadline) > 0)
                .sorted(Comparator.comparingLong((Process p) -> firstOutput(p, stocks)).thenComparing(Process::id)).findFirst();
        // Missing a deadline must not delete the path to recovery. Keep emergency
        // purchasing separate: late production is not timely supply coverage.
        if (selected.isEmpty()) selected = capacities.stream().filter(p -> p.product().equals(item)
                        && potentialBatches(p, allocatedBatches.getOrDefault(p.id(), 0L), amount) > 0)
                .sorted(Comparator.comparingLong((Process p) -> firstOutput(p, stocks)).thenComparing(Process::id)).findFirst();
        long produced = 0, recovery = 0;
        if (selected.isPresent()) {
            var process = selected.orElseThrow();
            long allocated = allocatedBatches.getOrDefault(process.id(), 0L);
            long batches = potentialBatches(process, allocated, amount);
            long secured = feasibleBatches(process, resources, allocated, amount, deadline);
            // Coverage and intent are separate even for a partially funded batch plan.
            var fuel = resources.fuel(process, batches, allocated);
            resources.allocate(process, secured, allocated);
            allocatedBatches.put(process.id(), Math.addExact(allocated, batches));
            produced = Math.multiplyExact(secured, process.outputPerBatch());
            recovery = Math.multiplyExact(batches - secured, process.outputPerBatch());
            if (batches > 0) {
                long inputDeadline = deadline == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0,
                        deadline - Math.addExact(process.firstOutputDelayTicks(),
                                Math.multiplyExact(allocated + batches - 1, process.batchTicks())));
                for (var input : process.inputs().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
                    dependency(stocks, capacities, demands, allocatedBatches, resources, input.getKey(), Math.multiplyExact(batches, input.getValue()),
                            inputDeadline, reasons, Reason.PRODUCTION_INPUT, ancestors);
                if (fuel.isPresent()) {
                    dependency(stocks, capacities, demands, allocatedBatches, resources, fuel.orElseThrow().item(), fuel.orElseThrow().quantity(),
                            inputDeadline, reasons, Reason.PRODUCTION_FUEL, ancestors);
                }
            }
        }
        var previous = demands.get(item); var combined = EnumSet.noneOf(Reason.class);
        combined.addAll(previous.reasons()); combined.addAll(reasons);
        if (recovery > 0) combined.add(Reason.UNSECURED_PRODUCTION);
        demands.put(item, new Demand(Math.addExact(previous.purchase(), amount - produced),
                Math.addExact(previous.production(), produced),
                Math.addExact(previous.recoveryProduction(), recovery), previous.protectedInput(),
                Math.min(previous.deadlineTicks(), deadline), combined,
                produced + recovery > 0 ? Optional.of(selected.orElseThrow().id()) : previous.process()));
        ancestors.remove(item);
    }
    private void dependency(Map<String, Stock> stocks, List<Process> capacities, Map<String, Demand> demands, Map<String, Long> allocatedBatches, SupplyResources resources,
                            String item, long required, long deadline, Set<Reason> reasons,
                            Reason dependency, Set<String> ancestors) {
        var stock = requireStock(stocks, item); var previous = demands.get(item);
        long total = Math.addExact(previous.protectedInput(), required);
        var combined = EnumSet.noneOf(Reason.class); combined.addAll(previous.reasons()); combined.add(dependency);
        long missing = Math.max(0, total - stock.covered());
        if (missing > 0 && deadline <= stock.policy().leadTicks()) combined.add(Reason.CRITICAL_PRODUCTION_DEADLINE);
        demands.put(item, new Demand(previous.purchase(), previous.production(), previous.recoveryProduction(), total,
                Math.min(previous.deadlineTicks(), deadline), combined, previous.process()));
        long additional = Math.max(0, missing - previous.purchase() - previous.production());
        if (additional > 0) {
            combined.addAll(reasons); combined.add(dependency);
            if (deadline <= stock.policy().leadTicks()) combined.add(Reason.CRITICAL_PRODUCTION_DEADLINE);
            expand(stocks, capacities, demands, allocatedBatches, resources, item, additional, deadline, combined, ancestors);
        }
    }
    private static long potentialBatches(Process process, long allocated, long amount) {
        return Math.min(process.maximumBatches() - allocated, amount / process.outputPerBatch());
    }
    /** Only logistics-timed inputs may fund forecast output; normative lead time ranks intent only. */
    private static long feasibleBatches(Process process, SupplyResources resources, long allocated, long amount, long deadline) {
        long upper = potentialBatches(process, allocated, amount);
        long lower = 0;
        while (lower < upper) {
            long candidate = lower + (upper - lower + 1) / 2;
            long total = Math.addExact(allocated, candidate);
            long completion = Math.addExact(process.firstOutputDelayTicks(),
                    Math.multiplyExact(total - 1, process.batchTicks()));
            var ready = resources.readyAt(process, candidate, allocated);
            if (ready.isPresent() && ready.getAsLong() <= deadline && completion <= deadline - ready.getAsLong()) lower = candidate;
            else upper = candidate - 1;
        }
        return lower;
    }
    private static long firstOutput(Process process, Map<String, Stock> stocks) {
        return firstOutput(process, stocks, 1);
    }
    private static long firstOutput(Process process, Map<String, Stock> stocks, long batches) {
        long inputDelay = process.inputs().entrySet().stream()
                .filter(e -> stocks.get(e.getKey()).available() < Math.multiplyExact(batches, e.getValue()))
                .mapToLong(e -> stocks.get(e.getKey()).policy().leadTicks()).max().orElse(0);
        long requiredHeat = Math.multiplyExact(batches, process.heatPerBatch());
        if (requiredHeat > process.availableHeat()) {
            long heat = requiredHeat - process.availableHeat();
            long fuelDelay = process.fuels().entrySet().stream().mapToLong(e ->
                    stocks.get(e.getKey()).available() >= Math.ceilDiv(heat, e.getValue()) ? 0 : stocks.get(e.getKey()).policy().leadTicks())
                    .min().orElseThrow();
            inputDelay = Math.max(inputDelay, fuelDelay);
        }
        return Math.addExact(process.firstOutputDelayTicks(), inputDelay);
    }
    private static void validateDependencies(Map<String, Stock> stocks, List<Process> processes, List<Dependency> commitments) {
        var graph = new TreeMap<String, Set<String>>();
        stocks.keySet().forEach(item -> graph.put(item, new TreeSet<>()));
        for (var process : processes) {
            graph.get(process.product()).addAll(process.inputs().keySet());
            if (process.heatPerBatch() > 0) graph.get(process.product()).addAll(process.fuels().keySet());
        }
        for (var dependency : commitments) {
            requireStock(stocks, dependency.product()); requireStock(stocks, dependency.resource());
            graph.get(dependency.product()).add(dependency.resource());
        }
        var complete = new HashSet<String>();
        for (var item : graph.keySet()) validateAcyclic(graph, item, new HashSet<>(), complete);
    }
    private static void validateAcyclic(Map<String, Set<String>> graph, String item, Set<String> path, Set<String> complete) {
        if (complete.contains(item)) return;
        if (!path.add(item)) throw new IllegalArgumentException("cyclic production catalog at " + item);
        for (var dependency : graph.get(item)) validateAcyclic(graph, dependency, path, complete);
        path.remove(item); complete.add(item);
    }
    private static Stock requireStock(Map<String, Stock> stocks, String item) {
        return Objects.requireNonNull(stocks.get(item), "production references undeclared resource " + item);
    }
}
