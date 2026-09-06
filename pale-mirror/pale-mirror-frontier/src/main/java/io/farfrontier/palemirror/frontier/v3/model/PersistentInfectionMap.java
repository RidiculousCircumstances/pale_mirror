package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable sparse infection field with bounded copy-on-write depth.
 *
 * <p>One infection pulse must not clone every already infected cell. The most recent bounded
 * delta window is copied independently from the large base snapshot, so both {@link #get(Object)}
 * and {@link #containsKey(Object)} remain constant-time even while the next full snapshot is
 * deferred. Ordinary enumeration walks the base and its bounded delta window without allocating a
 * duplicate map; a materialized immutable snapshot is created only when compaction reaches the
 * delta bound. This is an internal canonical representation, never a persistence format.</p>
 */
final class PersistentInfectionMap extends AbstractMap<InfectionCell, FixedRatio> {
    private static final int MAX_DELTA_DEPTH = 32;

    private final Map<InfectionCell, FixedRatio> base;
    private final PersistentInfectionMap parent;
    private final InfectionCell changedCell;
    private final FixedRatio changedValue;
    private final boolean removed;
    /** Last-write-wins values since {@link #base}; at most {@value #MAX_DELTA_DEPTH} entries. */
    private final Map<InfectionCell, Delta> deltas;
    private final int size;
    private final int depth;
    private volatile Map<InfectionCell, FixedRatio> snapshot;

    private PersistentInfectionMap(Map<InfectionCell, FixedRatio> base) {
        this.base = Map.copyOf(base); this.parent = null; this.changedCell = null; this.changedValue = null;
        this.removed = false; this.deltas = Map.of(); this.size = base.size(); this.depth = 0; this.snapshot = this.base;
    }

    private PersistentInfectionMap(PersistentInfectionMap parent, InfectionCell changedCell, FixedRatio changedValue, boolean removed) {
        this.base = parent.base; this.parent = parent; this.changedCell = changedCell; this.changedValue = changedValue; this.removed = removed;
        boolean existed = parent.containsKey(changedCell);
        LinkedHashMap<InfectionCell, Delta> next = new LinkedHashMap<>(parent.deltas);
        next.put(changedCell, new Delta(changedValue, removed));
        this.deltas = Map.copyOf(next);
        this.size = removed ? (existed ? parent.size - 1 : parent.size) : (existed ? parent.size : parent.size + 1);
        this.depth = parent.depth + 1;
    }

    static PersistentInfectionMap from(Map<InfectionCell, FixedRatio> values) {
        Objects.requireNonNull(values, "infection values");
        return values instanceof PersistentInfectionMap persistent ? persistent : new PersistentInfectionMap(values);
    }

    PersistentInfectionMap changed(InfectionCell cell, FixedRatio value) {
        Objects.requireNonNull(cell, "infection cell");
        FixedRatio prior = get(cell);
        if (Objects.equals(prior, value)) return this;
        if (depth >= MAX_DELTA_DEPTH) return new PersistentInfectionMap(snapshot()).changed(cell, value);
        return new PersistentInfectionMap(this, cell, value, value == null);
    }

    boolean directlyFollows(PersistentInfectionMap previous, InfectionCell cell, long priorRaw, long nextRaw) {
        return parent == previous && changedCell.equals(cell)
                && raw(previous.get(cell)) == priorRaw && raw(get(cell)) == nextRaw;
    }

    @Override public FixedRatio get(Object key) {
        Delta delta = deltas.get(key);
        return delta == null ? base.get(key) : delta.removed ? null : delta.value;
    }

    @Override public boolean containsKey(Object key) {
        Delta delta = deltas.get(key);
        return delta == null ? base.containsKey(key) : !delta.removed;
    }

    @Override public int size() { return size; }

    /**
     * A complete immutable snapshot would cost one full sparse-field copy for every strict audit.
     * The canonical field is already immutable; this view composes its base and at most 32
     * last-write-wins deltas exactly, while retaining snapshot construction for bounded compaction.
     */
    @Override public Set<Entry<InfectionCell, FixedRatio>> entrySet() {
        return new AbstractSet<>() {
            @Override public Iterator<Entry<InfectionCell, FixedRatio>> iterator() {
                Iterator<Entry<InfectionCell, FixedRatio>> baseEntries = base.entrySet().iterator();
                Iterator<Entry<InfectionCell, Delta>> deltaEntries = deltas.entrySet().iterator();
                return new Iterator<>() {
                    private Entry<InfectionCell, FixedRatio> next;

                    @Override public boolean hasNext() {
                        if (next == null) next = advance();
                        return next != null;
                    }

                    @Override public Entry<InfectionCell, FixedRatio> next() {
                        if (!hasNext()) throw new NoSuchElementException();
                        Entry<InfectionCell, FixedRatio> current = next; next = null;
                        return current;
                    }

                    private Entry<InfectionCell, FixedRatio> advance() {
                        while (baseEntries.hasNext()) {
                            Entry<InfectionCell, FixedRatio> entry = baseEntries.next();
                            Delta delta = deltas.get(entry.getKey());
                            if (delta == null) return entry;
                            if (!delta.removed) return Map.entry(entry.getKey(), delta.value);
                        }
                        while (deltaEntries.hasNext()) {
                            Entry<InfectionCell, Delta> entry = deltaEntries.next();
                            if (!base.containsKey(entry.getKey()) && !entry.getValue().removed) {
                                return Map.entry(entry.getKey(), entry.getValue().value);
                            }
                        }
                        return null;
                    }
                };
            }

            @Override public int size() { return size; }
        };
    }

    private Map<InfectionCell, FixedRatio> snapshot() {
        Map<InfectionCell, FixedRatio> retained = snapshot;
        if (retained != null) return retained;
        LinkedHashMap<InfectionCell, FixedRatio> next = new LinkedHashMap<>(base);
        deltas.forEach((cell, delta) -> {
            if (delta.removed) next.remove(cell); else next.put(cell, delta.value);
        });
        retained = Map.copyOf(next);
        snapshot = retained;
        return retained;
    }

    private static long raw(FixedRatio ratio) { return ratio == null ? 0L : ratio.value().raw(); }

    private record Delta(FixedRatio value, boolean removed) {
        private Delta {
            if (!removed) Objects.requireNonNull(value, "infection delta value");
        }
    }
}
