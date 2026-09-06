package io.farfrontier.palemirror.internal.economy;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Bounded physical transaction log. Canonical resource amounts remain in the domain economy. */
public final class ResourceTransferLedger {
    public static final int MAX_RECORDS = 2048;
    public static final long MIN_TERMINAL_RETENTION_STEPS = 256;
    private final Map<String, ResourceTransfer> transfers;

    public ResourceTransferLedger() { this(new LinkedHashMap<>()); }
    public ResourceTransferLedger(Map<String, ResourceTransfer> transfers) {
        this.transfers = new LinkedHashMap<>(transfers);
        if (this.transfers.size() > MAX_RECORDS) throw new IllegalStateException("Resource transfer ledger exceeds its bound");
    }

    public Collection<ResourceTransfer> transfers() { return java.util.List.copyOf(transfers.values()); }
    public Optional<ResourceTransfer> find(String id) { return Optional.ofNullable(transfers.get(id)); }
    public boolean hasActiveFor(UUID playerId) {
        return transfers.values().stream().anyMatch(value -> value.playerId().equals(playerId) && !value.state().terminal());
    }
    public void add(ResourceTransfer transfer) {
        if (transfers.size() >= MAX_RECORDS) throw new IllegalStateException("Resource transfer ledger is full");
        if (transfers.putIfAbsent(transfer.id(), transfer) != null) throw new IllegalStateException("Duplicate resource transfer " + transfer.id());
    }
    public boolean compact(long currentStep) {
        int before = transfers.size();
        transfers.values().stream().filter(value -> value.state().terminal()
                        && currentStep - value.createdStep() >= MIN_TERMINAL_RETENTION_STEPS)
                .sorted(Comparator.comparingLong(ResourceTransfer::createdStep).thenComparing(ResourceTransfer::id))
                .map(ResourceTransfer::id).toList().forEach(transfers::remove);
        return transfers.size() != before;
    }
    public void clear() { transfers.clear(); }
}
