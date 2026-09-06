package io.farfrontier.palemirror.internal.materialization;

import io.farfrontier.palemirror.api.SemanticSlotKey;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SemanticSlotLedger {
    private final Map<String, SemanticSlotRecord> slots;
    public SemanticSlotLedger() { this(new LinkedHashMap<>()); }
    public SemanticSlotLedger(Map<String, SemanticSlotRecord> slots) { this.slots = new LinkedHashMap<>(slots); }
    public Collection<SemanticSlotRecord> slots() { return java.util.List.copyOf(slots.values()); }
    public Optional<SemanticSlotRecord> find(SemanticSlotKey key) { return Optional.ofNullable(slots.get(key.value())); }
    public void register(SemanticSlotRecord slot) {
        if (slots.putIfAbsent(slot.key().value(), slot) != null) throw new IllegalStateException("Duplicate semantic slot " + slot.key().value());
    }
    public void clear() { slots.clear(); }
}
