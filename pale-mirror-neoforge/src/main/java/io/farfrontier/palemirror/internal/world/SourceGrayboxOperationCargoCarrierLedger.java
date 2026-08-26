package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceResource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Bounded HOT/COLD hand-off for one physical operation cargo carrier.
 *
 * <p>The source owns the operation and its quantity. This ledger owns only
 * the exact Minecraft hand-off: stable carrier identity, observed ordinary
 * items and whether a chest minecart currently has a live physical body.</p>
 */
final class SourceGrayboxOperationCargoCarrierLedger {
    static final int MAX_BINDINGS = 2_048;
    static final int POSITION_SCALE = 16;
    private final LinkedHashMap<String, Binding> bindings;

    SourceGrayboxOperationCargoCarrierLedger() { this(new LinkedHashMap<>()); }
    private SourceGrayboxOperationCargoCarrierLedger(LinkedHashMap<String, Binding> bindings) { this.bindings = bindings; }

    Binding binding(String id) { return bindings.get(id); }
    List<Binding> bindings() { return List.copyOf(bindings.values()); }

    boolean put(Binding binding) {
        Binding required = Objects.requireNonNull(binding, "binding");
        Binding prior = bindings.put(required.id(), required);
        if (prior == null && bindings.size() > MAX_BINDINGS) {
            bindings.remove(required.id());
            throw new IllegalStateException("source graybox operation carrier binding limit reached");
        }
        return !required.equals(prior);
    }

    boolean replace(Binding binding) {
        if (!bindings.containsKey(binding.id())) throw new IllegalArgumentException("operation cargo carrier binding is absent: " + binding.id());
        return put(binding);
    }

    boolean remove(String id) { return bindings.remove(id) != null; }

    ListTag save() {
        ListTag result = new ListTag();
        bindings.values().forEach(binding -> result.add(binding.save()));
        return result;
    }

    static SourceGrayboxOperationCargoCarrierLedger load(ListTag encoded) {
        if (encoded.size() > MAX_BINDINGS) throw new IllegalStateException("source graybox operation carrier history exceeds its bound");
        LinkedHashMap<String, Binding> bindings = new LinkedHashMap<>();
        for (Tag item : encoded) {
            Binding binding = Binding.load((CompoundTag) item);
            if (bindings.putIfAbsent(binding.id(), binding) != null) {
                throw new IllegalStateException("duplicate source graybox operation carrier binding: " + binding.id());
            }
        }
        return new SourceGrayboxOperationCargoCarrierLedger(bindings);
    }

    enum Mode { COLD, HOT, BLOCKED }

    record Binding(String id, String cargoId, int operationId, ReferenceResource resource, int observedItems,
                   int actualXSixteenths, int actualZSixteenths, Mode mode) {
        Binding {
            required(id, "ID");
            required(cargoId, "cargo ID");
            if (!cargoId.startsWith("operation:")) throw new IllegalArgumentException("operation carrier cargo ID is invalid");
            if (operationId < 1) throw new IllegalArgumentException("operation carrier operation ID is invalid");
            resource = Objects.requireNonNull(resource, "resource");
            if (observedItems < 0 || observedItems > SourceGrayboxWarehouseRuntime.BARREL_CAPACITY) {
                throw new IllegalArgumentException("operation carrier observed item count is invalid");
            }
            mode = Objects.requireNonNull(mode, "mode");
        }

        Binding withObservedItems(int value) {
            return new Binding(id, cargoId, operationId, resource, value, actualXSixteenths, actualZSixteenths, mode);
        }

        Binding at(int xSixteenths, int zSixteenths) {
            return new Binding(id, cargoId, operationId, resource, observedItems, xSixteenths, zSixteenths, mode);
        }

        Binding withMode(Mode value) {
            return new Binding(id, cargoId, operationId, resource, observedItems, actualXSixteenths, actualZSixteenths, value);
        }

        Binding blocked() { return withMode(Mode.BLOCKED); }

        CompoundTag save() {
            CompoundTag result = new CompoundTag();
            result.putString("id", id);
            result.putString("cargo", cargoId);
            result.putInt("operation", operationId);
            result.putString("resource", resource.name());
            result.putInt("observedItems", observedItems);
            result.putInt("actualX16", actualXSixteenths);
            result.putInt("actualZ16", actualZSixteenths);
            result.putString("mode", mode.name());
            return result;
        }

        static Binding load(CompoundTag encoded) {
            try {
                if (!encoded.contains("actualX16", Tag.TAG_INT) || !encoded.contains("actualZ16", Tag.TAG_INT)) {
                    throw new IllegalStateException("source graybox operation carrier position is absent");
                }
                return new Binding(encoded.getString("id"), encoded.getString("cargo"), encoded.getInt("operation"),
                        ReferenceResource.valueOf(encoded.getString("resource")), encoded.getInt("observedItems"),
                        encoded.getInt("actualX16"), encoded.getInt("actualZ16"), Mode.valueOf(encoded.getString("mode")));
            } catch (IllegalArgumentException | IllegalStateException invalid) {
                throw new IllegalStateException("source graybox operation carrier binding is invalid", invalid);
            }
        }

        private static void required(String value, String name) {
            if (value == null || value.isBlank() || value.length() > 160) {
                throw new IllegalArgumentException("operation carrier " + name + " is invalid");
            }
        }
    }
}
