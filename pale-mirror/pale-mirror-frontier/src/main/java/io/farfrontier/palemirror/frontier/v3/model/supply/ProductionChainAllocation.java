package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;

/** Finite hypothetical local production. A failed branch spends neither stock nor shared labour in the accepted draft. */
public final class ProductionChainAllocation {
    public record Selection(ProductionChainAllocation next, long quantity, long existingStock) { }
    private final List<SupplyPlanner.Process> processes;
    private final Map<String, Long> prices;
    private final Map<String, Long> existing, used, labour, heat, produced;
    private final Map<String, NavigableMap<Long, Long>> buffered;

    public ProductionChainAllocation(List<SupplyPlanner.Process> processes, Map<String, Long> stock,
            Map<String, Long> usedRecipes, Map<String, Long> labour, Map<String, Long> heat, Map<String, Long> prices) {
        this.processes = List.copyOf(processes); this.prices = Map.copyOf(prices);
        this.existing = new TreeMap<>(stock); this.buffered = new TreeMap<>(); this.used = new HashMap<>(usedRecipes);
        this.labour = new TreeMap<>(labour); this.heat = new HashMap<>(heat); this.produced = new TreeMap<>();
        if (processes.size() > 256 || stock.size() > 256 || !stock.keySet().equals(prices.keySet())
                || stock.values().stream().anyMatch(n -> n < 0) || usedRecipes.values().stream().anyMatch(n -> n < 0)
                || labour.values().stream().anyMatch(n -> n < 0) || heat.values().stream().anyMatch(n -> n < 0))
            throw new IllegalArgumentException("invalid local production allocation");
    }
    private ProductionChainAllocation(ProductionChainAllocation source) {
        processes = source.processes; prices = source.prices;
        existing = new TreeMap<>(source.existing); buffered = copyBuffer(source.buffered); used = new HashMap<>(source.used);
        labour = new TreeMap<>(source.labour); heat = new HashMap<>(source.heat); produced = new TreeMap<>(source.produced);
    }
    public Map<String, Long> additionalProduction() { return Map.copyOf(produced); }

    public Selection allocate(String item, long maximum, long deadline, Set<String> allowedFinalProcesses) {
        if (!existing.containsKey(item) || maximum < 0 || deadline < 0) throw new IllegalArgumentException("invalid production request");
        long low = 0, high = maximum; var accepted = this;
        while (low < high) {
            long middle = low + Math.ceilDiv(high - low, 2);
            var draft = new ProductionChainAllocation(this);
            if (draft.supply(item, middle, deadline, Set.of(), Optional.of(allowedFinalProcesses))) { low = middle; accepted = draft; }
            else high = middle - 1;
        }
        return new Selection(accepted, low, existing.get(item) - accepted.existing.get(item));
    }

    private boolean supply(String item, long quantity, long deadline, Set<String> ancestors, Optional<Set<String>> allowed) {
        if (quantity == 0) return true;
        if (deadline < 0 || ancestors.contains(item) || ancestors.size() >= 256) return false;
        long needed = take(item, quantity, deadline);
        if (needed == 0) return true;
        var chain = new HashSet<>(ancestors); chain.add(item);
        for (var process : processes.stream().filter(p -> p.product().equals(item)
                && allowed.map(ids -> ids.contains(p.id())).orElse(true)).sorted(Comparator.comparing(SupplyPlanner.Process::id)).toList()) {
            long already = used.getOrDefault(process.id(), 0L);
            long batches = Math.min(Math.ceilDiv(needed, process.outputPerBatch()), process.recipesAvailableBy(deadline) - already);
            if (batches <= 0) continue;
            var trial = new ProductionChainAllocation(this);
            if (!trial.produce(process, batches, deadline, chain)) continue;
            copy(trial); needed = take(item, needed, deadline);
            if (needed == 0) return true;
        }
        return false;
    }
    private boolean produce(SupplyPlanner.Process process, long batches, long deadline, Set<String> ancestors) {
        long prior = used.getOrDefault(process.id(), 0L), total = Math.addExact(prior, batches);
        if (total > process.recipesAvailableBy(deadline)) return false;
        // Inputs must precede this operation. A future harvest at the horizon cannot feed an earlier bake.
        long ownDuration = Math.addExact(process.firstOutputDelayTicks(),
                Math.multiplyExact(Math.ceilDiv(batches, process.recipesPerOperation()) - 1, process.operationTicks()));
        long inputDeadline = deadline - ownDuration;
        for (var entry : process.inputs().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
            if (!supply(entry.getKey(), Math.multiplyExact(batches, entry.getValue()), inputDeadline, ancestors, Optional.empty())) return false;
        long neededHeat = Math.multiplyExact(batches, process.heatPerBatch()), storedHeat = heat.getOrDefault(process.id(), 0L);
        if (storedHeat < neededHeat) {
            boolean fuelled = false;
            for (var fuel : process.fuels().entrySet().stream().sorted(Comparator
                    .<Map.Entry<String, Long>>comparingLong(e -> Math.ceilDiv(prices.get(e.getKey()), e.getValue()))
                    .thenComparing(Map.Entry::getKey)).toList()) {
                var draft = new ProductionChainAllocation(this);
                long count = Math.ceilDiv(neededHeat - storedHeat, fuel.getValue());
                if (!draft.supply(fuel.getKey(), count, inputDeadline, ancestors, Optional.empty())) continue;
                copy(draft); storedHeat = Math.addExact(storedHeat, Math.multiplyExact(count, fuel.getValue())); fuelled = true; break;
            }
            if (!fuelled) return false;
        }
        long work = Math.subtractExact(process.labourTicks(total), process.labourTicks(prior));
        if (!process.labourCapacity().isEmpty()) {
            long available = process.labourCapacity().keySet().stream().mapToLong(id -> labour.getOrDefault(id, 0L)).sum();
            if (work > available) return false;
            for (var id : new TreeSet<>(process.labourCapacity().keySet())) {
                long take = Math.min(work, labour.get(id)); labour.merge(id, -take, Math::addExact); work -= take;
            }
        }
        heat.put(process.id(), storedHeat - neededHeat); used.put(process.id(), total);
        long output = Math.multiplyExact(batches, process.outputPerBatch());
        // A future surplus is not present stock. Keep its accepted latest release
        // bound so another branch cannot spend it before that production deadline.
        buffered.computeIfAbsent(process.product(), ignored -> new TreeMap<>()).merge(deadline, output, Math::addExact);
        produced.merge(process.product(), output, Math::addExact);
        return true;
    }
    private long take(String item, long quantity, long deadline) {
        long held = Math.min(quantity, existing.get(item)); existing.merge(item, -held, Math::addExact);
        long remaining = quantity - held;
        var releases = buffered.get(item);
        if (releases != null) for (var entry : releases.headMap(deadline, true).entrySet()) {
            long made = Math.min(remaining, entry.getValue());
            entry.setValue(entry.getValue() - made); remaining -= made;
            if (remaining == 0) break;
        }
        return remaining;
    }
    private static Map<String, NavigableMap<Long, Long>> copyBuffer(Map<String, NavigableMap<Long, Long>> source) {
        var copy = new TreeMap<String, NavigableMap<Long, Long>>();
        source.forEach((item, releases) -> copy.put(item, new TreeMap<>(releases))); return copy;
    }
    private void copy(ProductionChainAllocation source) {
        existing.clear(); existing.putAll(source.existing); buffered.clear(); buffered.putAll(copyBuffer(source.buffered));
        used.clear(); used.putAll(source.used); labour.clear(); labour.putAll(source.labour);
        heat.clear(); heat.putAll(source.heat); produced.clear(); produced.putAll(source.produced);
    }
}
