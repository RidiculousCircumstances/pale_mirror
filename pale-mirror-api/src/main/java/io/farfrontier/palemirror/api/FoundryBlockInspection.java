package io.farfrontier.palemirror.api;

/** Read-only comparison for a single world cell selected by an operator. */
public record FoundryBlockInspection(String regionId, VisualPoint position, boolean loaded,
                                     String ownerId, String expectedState, String actualState,
                                     Integer targetGroundY, String diagnostic) {
    public FoundryBlockInspection {
        if (regionId == null || regionId.isBlank()) throw new IllegalArgumentException("regionId is required");
        if (position == null) throw new IllegalArgumentException("position is required");
        ownerId = ownerId == null || ownerId.isBlank() ? "unowned" : ownerId;
        expectedState = expectedState == null || expectedState.isBlank() ? "unplanned" : expectedState;
        actualState = actualState == null || actualState.isBlank() ? "unavailable" : actualState;
        diagnostic = diagnostic == null ? "" : diagnostic;
    }
}
