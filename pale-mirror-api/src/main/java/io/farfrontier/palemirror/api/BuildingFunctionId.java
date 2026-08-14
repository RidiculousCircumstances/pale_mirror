package io.farfrontier.palemirror.api;

/** Namespaced reusable building capability identity, independent of any one settlement archetype. */
public record BuildingFunctionId(String value) {
    public BuildingFunctionId {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("building function must be a namespaced id: " + value);
        }
    }
}
