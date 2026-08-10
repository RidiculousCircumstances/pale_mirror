package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Canonical decision and reservation; Minecraft owns only its physical presentation work. */
public final class DevelopmentIntent {
    private final String id;
    private final WorldObjectId communityId;
    private final DevelopmentIntentType type;
    private final WorldObjectId targetSiteId;
    private final ResourceKind requiredResource;
    private final int reservedAmount;
    private final String policyVersion;
    private DevelopmentIntentState state;
    private String diagnostic;

    public DevelopmentIntent(String id, WorldObjectId communityId, DevelopmentIntentType type,
                             WorldObjectId targetSiteId, ResourceKind requiredResource, int reservedAmount,
                             String policyVersion, DevelopmentIntentState state, String diagnostic) {
        this.id = requireText(id, "id");
        this.communityId = Objects.requireNonNull(communityId, "communityId");
        this.type = Objects.requireNonNull(type, "type");
        this.targetSiteId = targetSiteId;
        this.requiredResource = requiredResource;
        if (reservedAmount < 0 || (reservedAmount > 0 && requiredResource == null)) throw new IllegalArgumentException("Invalid development reservation");
        this.reservedAmount = reservedAmount;
        this.policyVersion = requireText(policyVersion, "policyVersion");
        this.state = Objects.requireNonNull(state, "state");
        this.diagnostic = diagnostic == null ? "" : diagnostic;
    }
    public String id() { return id; }
    public WorldObjectId communityId() { return communityId; }
    public DevelopmentIntentType type() { return type; }
    public WorldObjectId targetSiteId() { return targetSiteId; }
    public ResourceKind requiredResource() { return requiredResource; }
    public int reservedAmount() { return reservedAmount; }
    public String policyVersion() { return policyVersion; }
    public DevelopmentIntentState state() { return state; }
    public String diagnostic() { return diagnostic; }
    public boolean start() { if (state != DevelopmentIntentState.PLANNED) return false; state = DevelopmentIntentState.MATERIALIZING; return true; }
    public boolean complete() { if (state != DevelopmentIntentState.MATERIALIZING && state != DevelopmentIntentState.PLANNED) return false; state = DevelopmentIntentState.ACTIVE; diagnostic = ""; return true; }
    public boolean block(String reason) { if (state == DevelopmentIntentState.ACTIVE || state == DevelopmentIntentState.CANCELLED) return false; state = DevelopmentIntentState.BLOCKED; diagnostic = requireText(reason, "reason"); return true; }
    public boolean cancel(String reason) { if (state == DevelopmentIntentState.ACTIVE || state == DevelopmentIntentState.CANCELLED) return false; state = DevelopmentIntentState.CANCELLED; diagnostic = requireText(reason, "reason"); return true; }
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
