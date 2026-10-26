package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Declared, complete slot partition. No recipe, resource-name or owner selection belongs here. */
public record ContainerCompartmentLayout(int capacity, Map<ContainerSectionId, Set<Integer>> sections) {
    public ContainerCompartmentLayout {
        if (capacity < 1 || capacity > ContainerRecord.MAX_SLOTS || sections == null || sections.isEmpty())
            throw new IllegalArgumentException("invalid container compartment capacity");
        var copied = new TreeMap<ContainerSectionId, Set<Integer>>(); var assigned = new HashSet<Integer>();
        for (var entry : sections.entrySet()) {
            Objects.requireNonNull(entry.getKey()); var slots = Set.copyOf(entry.getValue());
            if (slots.isEmpty()) throw new IllegalArgumentException("empty container section");
            for (int slot : slots) if (slot < 0 || slot >= capacity || !assigned.add(slot))
                throw new IllegalArgumentException("overlapping or out-of-range container sections");
            copied.put(entry.getKey(), slots);
        }
        if (assigned.size() != capacity) throw new IllegalArgumentException("container slot lacks a declared section");
        sections = Collections.unmodifiableMap(copied);
    }
    public Set<Integer> slots(ContainerSectionId section) {
        var slots = sections.get(Objects.requireNonNull(section));
        if (slots == null) throw new IllegalArgumentException("container does not declare section " + section);
        return slots;
    }
    public ContainerSectionId sectionAt(int slot) {
        if (slot < 0 || slot >= capacity) throw new IllegalArgumentException("container slot is outside its layout");
        return sections.entrySet().stream().filter(entry -> entry.getValue().contains(slot))
                .map(Map.Entry::getKey).findFirst().orElseThrow();
    }
    public static ContainerCompartmentLayout stock(int capacity) {
        return new ContainerCompartmentLayout(capacity, Map.of(ContainerSectionId.STOCK,
                java.util.stream.IntStream.range(0, capacity).boxed().collect(java.util.stream.Collectors.toUnmodifiableSet())));
    }
}
