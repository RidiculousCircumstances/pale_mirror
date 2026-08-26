package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceResource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Bounded physical hand-off for the real resource containers in the graybox.
 *
 * <p>The domain owns quantity. This ledger owns only which exact Barrel was
 * installed by PM and the last item count acknowledged by the domain, so a
 * later block break can become one precise loss receipt instead of a silent
 * presentation repair.</p>
 */
final class SourceGrayboxWarehouseLedger {
    static final int MAX_BINDINGS = 2_048;
    private final LinkedHashMap<String, Binding> bindings;

    SourceGrayboxWarehouseLedger() {
        this(new LinkedHashMap<>());
    }

    private SourceGrayboxWarehouseLedger(LinkedHashMap<String, Binding> bindings) {
        this.bindings = bindings;
    }

    Binding binding(String id) {
        return bindings.get(id);
    }

    List<Binding> bindings() {
        return List.copyOf(bindings.values());
    }

    boolean put(Binding binding) {
        Binding required = Objects.requireNonNull(binding, "binding");
        Binding previous = bindings.put(required.id(), required);
        if (previous == null && bindings.size() > MAX_BINDINGS) {
            bindings.remove(required.id());
            throw new IllegalStateException("source graybox warehouse binding limit reached");
        }
        return !required.equals(previous);
    }

    boolean replace(Binding binding) {
        if (!bindings.containsKey(binding.id())) throw new IllegalArgumentException("warehouse binding is absent: " + binding.id());
        return put(binding);
    }

    ListTag save() {
        ListTag result = new ListTag();
        bindings.values().forEach(binding -> result.add(binding.save()));
        return result;
    }

    static SourceGrayboxWarehouseLedger load(ListTag encoded) {
        if (encoded.size() > MAX_BINDINGS) throw new IllegalStateException("source graybox warehouse binding history exceeds its bound");
        LinkedHashMap<String, Binding> bindings = new LinkedHashMap<>();
        for (Tag item : encoded) {
            Binding binding = Binding.load((CompoundTag) item);
            if (bindings.putIfAbsent(binding.id(), binding) != null) {
                throw new IllegalStateException("duplicate source graybox warehouse binding: " + binding.id());
            }
        }
        return new SourceGrayboxWarehouseLedger(bindings);
    }

    enum State { ACTIVE, BLOCKED }

    record Binding(String id, int settlementId, ReferenceResource resource, int x, int y, int z, int observedItems, State state) {
        Binding {
            if (id == null || id.isBlank() || id.length() > 128) throw new IllegalArgumentException("warehouse binding ID is invalid");
            if (settlementId < 1) throw new IllegalArgumentException("warehouse binding settlement is invalid");
            resource = Objects.requireNonNull(resource, "resource");
            if (observedItems < 0 || observedItems > 1_728) throw new IllegalArgumentException("warehouse observed item count is invalid");
            state = Objects.requireNonNull(state, "state");
        }

        Binding withObservedItems(int value) {
            return new Binding(id, settlementId, resource, x, y, z, value, state);
        }

        Binding blocked() {
            return new Binding(id, settlementId, resource, x, y, z, observedItems, State.BLOCKED);
        }

        CompoundTag save() {
            CompoundTag result = new CompoundTag();
            result.putString("id", id);
            result.putInt("settlement", settlementId);
            result.putString("resource", resource.name());
            result.putInt("x", x);
            result.putInt("y", y);
            result.putInt("z", z);
            result.putInt("observedItems", observedItems);
            result.putString("state", state.name());
            return result;
        }

        static Binding load(CompoundTag encoded) {
            try {
                return new Binding(encoded.getString("id"), encoded.getInt("settlement"),
                        ReferenceResource.valueOf(encoded.getString("resource")), encoded.getInt("x"), encoded.getInt("y"),
                        encoded.getInt("z"), encoded.getInt("observedItems"), State.valueOf(encoded.getString("state")));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("source graybox warehouse binding is invalid", invalid);
            }
        }
    }
}
