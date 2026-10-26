package io.farfrontier.palemirror.frontier.v3.model;

/** Logical material location within a container, independent of item identity or physical slot order. */
public record ContainerSectionId(String value) implements Comparable<ContainerSectionId> {
    public static final ContainerSectionId STOCK = new ContainerSectionId("stock");
    public static final ContainerSectionId INPUT = new ContainerSectionId("input");
    public static final ContainerSectionId OUTPUT = new ContainerSectionId("output");
    public static final ContainerSectionId FUEL = new ContainerSectionId("fuel");
    public static final ContainerSectionId MAINTENANCE = new ContainerSectionId("maintenance");
    public ContainerSectionId {
        if (value == null || !value.matches("[a-z][a-z0-9_]{0,31}"))
            throw new IllegalArgumentException("invalid declared container section");
    }
    @Override public int compareTo(ContainerSectionId other) { return value.compareTo(other.value); }
}
