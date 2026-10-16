package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;

/** Ephemeral forecast allocation, not inventory custody. Unknown arrivals cannot fund timed output. */
final class SupplyResources {
    private final Map<String, NavigableMap<Long, Long>> available = new HashMap<>();
    private final Map<String, String> fuels = new HashMap<>();
    private final Map<String, Long> processReady = new HashMap<>();
    private final Map<String, Long> inputRequirements = new HashMap<>();
    private final Map<String, Long> labour = new TreeMap<>();

    long requireInput(String item, long quantity) {
        return inputRequirements.merge(item, quantity, Math::addExact);
    }

    SupplyResources(Map<String, SupplyPlanner.Stock> stocks, List<SupplyPlanner.Process> processes) {
        for (var process : processes) process.labourCapacity().forEach((worker, ticks) -> {
            var previous = labour.putIfAbsent(worker, ticks);
            if (previous != null && !previous.equals(ticks)) throw new IllegalArgumentException("inconsistent shared labour horizon");
        });
        stocks.forEach((item, stock) -> {
            var quantities = new TreeMap<Long, Long>(); quantities.put(0L, stock.available());
            stock.arrivals().forEach(arrival -> quantities.merge(arrival.afterTicks(), arrival.quantity(), Math::addExact));
            available.put(item, quantities);
        });
    }

    long labourBatches(SupplyPlanner.Process process) {
        if (process.labourCapacity().isEmpty()) return Long.MAX_VALUE;
        return process.labourCapacity().keySet().stream().mapToLong(labour::get).reduce(0L, Math::addExact) / process.labourTicksPerBatch();
    }
    void allocateLabour(SupplyPlanner.Process process, long batches) {
        if (process.labourCapacity().isEmpty()) return;
        long needed = Math.multiplyExact(batches, process.labourTicksPerBatch());
        for (var worker : process.labourCapacity().keySet().stream().sorted().toList()) {
            long used = Math.min(needed, labour.get(worker));
            labour.put(worker, labour.get(worker) - used); needed -= used;
        }
        if (needed != 0) throw new IllegalArgumentException("production overcommits shared labour");
    }

    Set<String> retain(List<SupplyPlanner.Dependency> commitments) {
        var unsecured = new HashSet<String>();
        for (var dependency : commitments.stream().sorted(Comparator.comparing(SupplyPlanner.Dependency::product)
                .thenComparing(SupplyPlanner.Dependency::resource)).toList()) {
            long held = available.get(dependency.resource()).getOrDefault(0L, 0L);
            if (held < dependency.quantity()) unsecured.add(dependency.product());
            // Existing jobs own these inputs first, even if their remaining processing
            // deadline is unknown. Do not use the same arriving cargo for another job.
            consume(dependency.resource(), dependency.quantity());
        }
        return Set.copyOf(unsecured);
    }

    Optional<FuelRequirement> fuel(SupplyPlanner.Process process, long batches, long allocated) {
        long heat = Math.max(0, Math.multiplyExact(batches, process.heatPerBatch())
                - Math.max(0, process.availableHeat() - Math.multiplyExact(allocated, process.heatPerBatch())));
        if (heat == 0) return Optional.empty();
        // Keep the chosen alternative stable during one process allocation, including binary search.
        String item = fuels.computeIfAbsent(process.id(), ignored -> process.fuels().keySet().stream()
                .sorted(Comparator.<String>comparingLong(key -> Math.multiplyExact(available.get(key).values().stream()
                                .reduce(0L, Math::addExact), process.fuels().get(key)))
                        .reversed().thenComparing(java.util.function.Function.identity())).findFirst().orElseThrow());
        return Optional.of(new FuelRequirement(item, Math.ceilDiv(heat, process.fuels().get(item))));
    }

    private Map<String, Long> required(SupplyPlanner.Process process, long batches, long allocated) {
        var result = new HashMap<String, Long>();
        process.inputs().forEach((item, quantity) -> result.put(item, Math.multiplyExact(batches, quantity)));
        fuel(process, batches, allocated).ifPresent(value -> result.merge(value.item(), value.quantity(), Math::addExact));
        return result;
    }

    OptionalLong readyAt(SupplyPlanner.Process process, long batches, long allocated) {
        long ready = processReady.getOrDefault(process.id(), 0L);
        for (var entry : required(process, batches, allocated).entrySet()) {
            long missing = entry.getValue();
            for (var tranche : available.get(entry.getKey()).entrySet()) {
                if (missing <= 0) break;
                missing -= tranche.getValue(); ready = Math.max(ready, tranche.getKey());
            }
            if (missing > 0) return OptionalLong.empty();
        }
        return OptionalLong.of(ready);
    }

    void allocate(SupplyPlanner.Process process, long batches, long allocated) {
        if (batches == 0) return;
        long ready = readyAt(process, batches, allocated).orElseThrow(() -> new IllegalArgumentException("unbacked production coverage"));
        processReady.put(process.id(), ready);
        required(process, batches, allocated).forEach(this::consume);
    }
    private void consume(String item, long quantity) {
        for (var entry : available.get(item).entrySet()) {
            long used = Math.min(quantity, entry.getValue());
            entry.setValue(entry.getValue() - used); quantity -= used;
            if (quantity == 0) break;
        }
    }
}
