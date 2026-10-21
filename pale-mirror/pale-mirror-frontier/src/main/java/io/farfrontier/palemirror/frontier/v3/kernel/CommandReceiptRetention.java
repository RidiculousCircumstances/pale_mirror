package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandReceipt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Kernel-owned receipts with a derived expiry index; inspection never retires durable facts. */
final class CommandReceiptRetention {
    private final LinkedHashMap<CommandId, CommandReceipt> receipts = new LinkedHashMap<>();
    private final NavigableMap<Long, List<CommandId>> byInstant = new TreeMap<>();
    private final java.util.Set<CommandId> pins = new java.util.HashSet<>();
    private long prunedBefore;

    void add(CommandReceipt receipt) {
        if (receipts.putIfAbsent(receipt.commandId(), receipt) != null)
            throw new IllegalArgumentException("duplicate command receipt");
        byInstant.computeIfAbsent(receipt.submittedAt().ticks(), ignored -> new ArrayList<>()).add(receipt.commandId());
    }

    boolean contains(CommandId id) { return receipts.containsKey(id); }
    int size() { return receipts.size(); }
    List<CommandReceipt> snapshot() { return List.copyOf(receipts.values()); }
    java.util.Optional<CommandReceipt> get(CommandId id) { return java.util.Optional.ofNullable(receipts.get(id)); }
    void pin(CommandId id, int maximum) {
        java.util.Objects.requireNonNull(id);
        if (!pins.contains(id) && pins.size() >= maximum) throw new IllegalStateException("command receipt obligation capacity exhausted");
        pins.add(id);
    }
    void release(CommandId id) {
        pins.remove(id);
        var receipt = receipts.get(id);
        if (receipt != null && receipt.submittedAt().ticks() < prunedBefore) receipts.remove(id);
    }

    int retainedAt(long oldestInclusive) {
        int expired = 0;
        // Ordinarily no bucket is expired: admission is independent of receipt count.
        // Recovery may supply older receipts in any order; read-only pressure remains exact.
        for (var bucket : byInstant.headMap(oldestInclusive, false).values())
            for (var id : bucket) if (!pins.contains(id)) expired++;
        return receipts.size() - expired;
    }

    void pruneBefore(long oldestInclusive) {
        prunedBefore = Math.max(prunedBefore, oldestInclusive);
        while (!byInstant.isEmpty() && byInstant.firstKey() < oldestInclusive) {
            for (var id : byInstant.pollFirstEntry().getValue()) if (!pins.contains(id)) receipts.remove(id);
        }
    }
}
