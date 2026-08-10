package io.farfrontier.palemirror.internal.effect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * SavedData-owned idempotency and crash-recovery ledger for bounded effects.
 * It has a bounded terminal retention policy so presentation/combat traffic
 * cannot grow canonical world data indefinitely.
 */
public final class EffectLeaseLedger {
    private static final int MAX_TERMINAL_RECORDS = 512;
    private static final long TERMINAL_RETENTION_TICKS = 72_000L;

    private final Map<String, EffectLease> byId;
    private final Map<String, String> idByIdempotencyKey;

    public EffectLeaseLedger() {
        this(Map.of());
    }

    public EffectLeaseLedger(Map<String, EffectLease> leases) {
        this.byId = new LinkedHashMap<>();
        this.idByIdempotencyKey = new LinkedHashMap<>();
        leases.values().stream().sorted(Comparator.comparing(EffectLease::id)).forEach(this::restore);
    }

    public List<EffectLease> leases() { return List.copyOf(byId.values()); }
    public Optional<EffectLease> find(String id) { return Optional.ofNullable(byId.get(id)); }
    public void clear() {
        byId.clear();
        idByIdempotencyKey.clear();
    }

    /** Returns the pre-existing lease for the same idempotency key, if any. */
    public EffectLease plan(EffectLease proposal) {
        Objects.requireNonNull(proposal, "proposal");
        String existingId = idByIdempotencyKey.get(proposal.idempotencyKey());
        if (existingId != null) return byId.get(existingId);
        if (byId.containsKey(proposal.id())) {
            throw new IllegalStateException("Effect lease id collision for " + proposal.id());
        }
        restore(proposal);
        return proposal;
    }

    public boolean begin(String id) {
        EffectLease lease = require(id);
        return lease.begin();
    }

    public void complete(String id, long gameTick) { require(id).complete(gameTick); }
    public void fail(String id, long gameTick, String diagnostic) { require(id).fail(gameTick, diagnostic); }

    /** Running effects have an unknown physical outcome after a restart and must never be replayed implicitly. */
    public boolean recoverAfterRestart(long gameTick) {
        boolean changed = false;
        for (EffectLease lease : byId.values()) {
            if (lease.state() == EffectLeaseState.RUNNING) {
                lease.markUnknownAfterRestart(gameTick);
                changed = true;
            }
        }
        return changed;
    }

    public boolean expireDue(long gameTick) {
        boolean changed = false;
        for (EffectLease lease : byId.values()) {
            if (!lease.state().terminal() && gameTick > lease.expiresAtGameTick()) {
                lease.expire(gameTick);
                changed = true;
            }
        }
        return changed;
    }

    public boolean compact(long gameTick) {
        List<EffectLease> removable = byId.values().stream()
                .filter(lease -> lease.state().terminal())
                .filter(lease -> lease.finishedAtGameTick() > 0 && gameTick - lease.finishedAtGameTick() >= TERMINAL_RETENTION_TICKS)
                .sorted(Comparator.comparingLong(EffectLease::finishedAtGameTick).thenComparing(EffectLease::id))
                .toList();
        removable.forEach(this::remove);
        List<EffectLease> terminal = byId.values().stream().filter(lease -> lease.state().terminal())
                .sorted(Comparator.comparingLong(EffectLease::finishedAtGameTick).thenComparing(EffectLease::id)).toList();
        int overflow = terminal.size() - MAX_TERMINAL_RECORDS;
        for (int index = 0; index < overflow; index++) remove(terminal.get(index));
        return !removable.isEmpty() || overflow > 0;
    }

    private void restore(EffectLease lease) {
        String old = idByIdempotencyKey.putIfAbsent(lease.idempotencyKey(), lease.id());
        if (old != null && !old.equals(lease.id())) throw new IllegalArgumentException("Duplicate effect idempotency key " + lease.idempotencyKey());
        byId.put(lease.id(), lease);
    }

    private EffectLease require(String id) {
        EffectLease lease = byId.get(id);
        if (lease == null) throw new IllegalArgumentException("Unknown effect lease " + id);
        return lease;
    }

    private void remove(EffectLease lease) {
        byId.remove(lease.id());
        idByIdempotencyKey.remove(lease.idempotencyKey(), lease.id());
    }
}
