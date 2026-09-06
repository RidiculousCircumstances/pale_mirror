package io.farfrontier.palemirror.api;

/** Stable ownership boundary; permissions never apply beyond this exact key. */
public record SemanticSlotKey(String objectId, String moduleId, String slotId) {
    public SemanticSlotKey {
        require(objectId, "objectId"); require(moduleId, "moduleId"); require(slotId, "slotId");
    }
    public String value() { return objectId + "|" + moduleId + "|" + slotId; }
    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
