package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceResource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Bounded durable physical hand-off for one exact operation or field-post cargo container. */
final class SourceGrayboxCargoLedger {
    static final int MAX_BINDINGS = 2_048;
    private final LinkedHashMap<String, Binding> bindings;

    SourceGrayboxCargoLedger() {
        this(new LinkedHashMap<>());
    }

    private SourceGrayboxCargoLedger(LinkedHashMap<String, Binding> bindings) {
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
        Binding prior = bindings.put(required.id(), required);
        if (prior == null && bindings.size() > MAX_BINDINGS) {
            bindings.remove(required.id());
            throw new IllegalStateException("source graybox cargo binding limit reached");
        }
        return !required.equals(prior);
    }

    boolean replace(Binding binding) {
        if (!bindings.containsKey(binding.id())) throw new IllegalArgumentException("cargo binding is absent: " + binding.id());
        return put(binding);
    }

    boolean remove(String id) {
        return bindings.remove(id) != null;
    }

    ListTag save() {
        ListTag result = new ListTag();
        bindings.values().forEach(binding -> result.add(binding.save()));
        return result;
    }

    static SourceGrayboxCargoLedger load(ListTag encoded) {
        if (encoded.size() > MAX_BINDINGS) throw new IllegalStateException("source graybox cargo binding history exceeds its bound");
        LinkedHashMap<String, Binding> bindings = new LinkedHashMap<>();
        for (Tag item : encoded) {
            Binding binding = Binding.load((CompoundTag) item);
            if (bindings.putIfAbsent(binding.id(), binding) != null) {
                throw new IllegalStateException("duplicate source graybox cargo binding: " + binding.id());
            }
        }
        return new SourceGrayboxCargoLedger(bindings);
    }

    /**
     * {@code RELOCATING} retains the old physical custody point and the new
     * source target independently.  It is the only legal state while an
     * operation has moved in canonical space but the old barrel cannot yet be
     * inspected and retired in a naturally loaded chunk.
     */
    enum State { ACTIVE, RELOCATING, BLOCKED }

    record Binding(String id, String cargoId, String ownerKind, int ownerId, ReferenceResource resource,
                   int x, int y, int z, int targetX, int targetY, int targetZ, int observedItems, State state) {
        Binding {
            required(id, "ID");
            required(cargoId, "cargo ID");
            if (!(ownerKind.equals("operation") || ownerKind.equals("field_post"))) {
                throw new IllegalArgumentException("cargo binding owner kind is invalid");
            }
            if (ownerId < 1) throw new IllegalArgumentException("cargo binding owner ID is invalid");
            resource = Objects.requireNonNull(resource, "resource");
            if (observedItems < 0 || observedItems > SourceGrayboxWarehouseRuntime.BARREL_CAPACITY) {
                throw new IllegalArgumentException("cargo binding observed item count is invalid");
            }
            state = Objects.requireNonNull(state, "state");
            if (state != State.RELOCATING && (x != targetX || y != targetY || z != targetZ)) {
                throw new IllegalArgumentException("settled cargo binding target differs from its physical position");
            }
        }

        /** Compatibility constructor for a non-moving source cargo binding. */
        Binding(String id, String cargoId, String ownerKind, int ownerId, ReferenceResource resource,
                int x, int y, int z, int observedItems, State state) {
            this(id, cargoId, ownerKind, ownerId, resource, x, y, z, x, y, z, observedItems, state);
        }

        Binding withObservedItems(int value) {
            return new Binding(id, cargoId, ownerKind, ownerId, resource, x, y, z, targetX, targetY, targetZ, value, state);
        }

        Binding blocked() {
            // A blocked relocation must retain the old physical point: that
            // is where an uninspected or foreign barrel still exists.  Moving
            // the ledger to the desired target here would forget that world
            // evidence and make a duplicate safe-looking container possible.
            return new Binding(id, cargoId, ownerKind, ownerId, resource, x, y, z,
                    x, y, z, observedItems, State.BLOCKED);
        }

        Binding relocating(int nextX, int nextY, int nextZ) {
            return new Binding(id, cargoId, ownerKind, ownerId, resource, x, y, z, nextX, nextY, nextZ,
                    observedItems, State.RELOCATING);
        }

        Binding retarget(int nextX, int nextY, int nextZ) {
            if (state != State.RELOCATING) throw new IllegalStateException("only moving cargo can change relocation target");
            return relocating(nextX, nextY, nextZ);
        }

        Binding arrived() {
            if (state != State.RELOCATING) throw new IllegalStateException("only moving cargo can arrive");
            return new Binding(id, cargoId, ownerKind, ownerId, resource, targetX, targetY, targetZ,
                    targetX, targetY, targetZ, 0, State.ACTIVE);
        }

        CompoundTag save() {
            CompoundTag result = new CompoundTag();
            result.putString("id", id);
            result.putString("cargo", cargoId);
            result.putString("ownerKind", ownerKind);
            result.putInt("owner", ownerId);
            result.putString("resource", resource.name());
            result.putInt("x", x);
            result.putInt("y", y);
            result.putInt("z", z);
            result.putInt("targetX", targetX);
            result.putInt("targetY", targetY);
            result.putInt("targetZ", targetZ);
            result.putInt("observedItems", observedItems);
            result.putString("state", state.name());
            return result;
        }

        static Binding load(CompoundTag encoded) {
            try {
                if (!encoded.contains("targetX", Tag.TAG_INT) || !encoded.contains("targetY", Tag.TAG_INT)
                        || !encoded.contains("targetZ", Tag.TAG_INT)) {
                    throw new IllegalStateException("source graybox cargo relocation target is absent");
                }
                return new Binding(encoded.getString("id"), encoded.getString("cargo"), encoded.getString("ownerKind"),
                        encoded.getInt("owner"), ReferenceResource.valueOf(encoded.getString("resource")), encoded.getInt("x"),
                        encoded.getInt("y"), encoded.getInt("z"), encoded.getInt("targetX"), encoded.getInt("targetY"),
                        encoded.getInt("targetZ"), encoded.getInt("observedItems"),
                        State.valueOf(encoded.getString("state")));
            } catch (IllegalArgumentException | IllegalStateException invalid) {
                throw new IllegalStateException("source graybox cargo binding is invalid", invalid);
            }
        }

        private static void required(String value, String name) {
            if (value == null || value.isBlank() || value.length() > 160) {
                throw new IllegalArgumentException("cargo binding " + name + " is invalid");
            }
        }
    }
}
