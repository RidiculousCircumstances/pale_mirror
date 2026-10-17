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

    void add(CommandReceipt receipt) {
        if (receipts.putIfAbsent(receipt.commandId(), receipt) != null)
            throw new IllegalArgumentException("duplicate command receipt");
        byInstant.computeIfAbsent(receipt.submittedAt().ticks(), ignored -> new ArrayList<>()).add(receipt.commandId());
    }

    boolean contains(CommandId id) { return receipts.containsKey(id); }
    int size() { return receipts.size(); }
    List<CommandReceipt> snapshot() { return List.copyOf(receipts.values()); }

    int retainedAt(long oldestInclusive) {
        int expired = 0;
        // Ordinarily no bucket is expired: admission is independent of receipt count.
        // Recovery may supply older receipts in any order; read-only pressure remains exact.
        for (var bucket : byInstant.headMap(oldestInclusive, false).values()) expired += bucket.size();
        return receipts.size() - expired;
    }

    void pruneBefore(long oldestInclusive) {
        while (!byInstant.isEmpty() && byInstant.firstKey() < oldestInclusive) {
            for (var id : byInstant.pollFirstEntry().getValue()) receipts.remove(id);
        }
    }
}
