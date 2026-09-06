package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Canonical project escrow and decision; Minecraft owns only its physical presentation work. */
public final class DevelopmentIntent {
    private static final int MAX_CONTRIBUTION_RECEIPTS = 64;
    private final String id;
    private final WorldObjectId communityId;
    private final DevelopmentIntentType type;
    private final WorldObjectId targetSiteId;
    private final ResourceKind requiredResource;
    private final int requiredAmount;
    private int contributedAmount;
    private int autonomousReservedAmount;
    private int qualifyingWaitSteps;
    private final int autonomousWaitRequired;
    private final java.util.Set<String> contributionReceipts;
    private final String policyVersion;
    private DevelopmentIntentState state;
    private String diagnostic;

    public DevelopmentIntent(String id, WorldObjectId communityId, DevelopmentIntentType type,
                             WorldObjectId targetSiteId, ResourceKind requiredResource, int reservedAmount,
                             String policyVersion, DevelopmentIntentState state, String diagnostic) {
        this(id, communityId, type, targetSiteId, requiredResource, reservedAmount, 0, reservedAmount,
                0, 1, java.util.Set.of(), policyVersion, state, diagnostic);
    }

    public DevelopmentIntent(String id, WorldObjectId communityId, DevelopmentIntentType type,
                             WorldObjectId targetSiteId, ResourceKind requiredResource, int requiredAmount,
                             int contributedAmount, int autonomousReservedAmount, int qualifyingWaitSteps,
                             int autonomousWaitRequired, java.util.Set<String> contributionReceipts,
                             String policyVersion, DevelopmentIntentState state, String diagnostic) {
        this.id = requireText(id, "id");
        this.communityId = Objects.requireNonNull(communityId, "communityId");
        this.type = Objects.requireNonNull(type, "type");
        this.targetSiteId = targetSiteId;
        this.requiredResource = requiredResource;
        if (requiredAmount < 0 || contributedAmount < 0 || autonomousReservedAmount < 0
                || contributedAmount + autonomousReservedAmount > requiredAmount
                || (requiredAmount > 0 && requiredResource == null) || qualifyingWaitSteps < 0
                || autonomousWaitRequired < 1 || contributionReceipts.size() > MAX_CONTRIBUTION_RECEIPTS) {
            throw new IllegalArgumentException("Invalid development funding");
        }
        this.requiredAmount = requiredAmount;
        this.contributedAmount = contributedAmount;
        this.autonomousReservedAmount = autonomousReservedAmount;
        this.qualifyingWaitSteps = qualifyingWaitSteps;
        this.autonomousWaitRequired = autonomousWaitRequired;
        this.contributionReceipts = new java.util.LinkedHashSet<>(contributionReceipts);
        this.policyVersion = requireText(policyVersion, "policyVersion");
        this.state = Objects.requireNonNull(state, "state");
        this.diagnostic = diagnostic == null ? "" : diagnostic;
    }
    public String id() { return id; }
    public WorldObjectId communityId() { return communityId; }
    public DevelopmentIntentType type() { return type; }
    public WorldObjectId targetSiteId() { return targetSiteId; }
    public ResourceKind requiredResource() { return requiredResource; }
    /** Backward-compatible name for the part reserved from canonical settlement stock. */
    public int reservedAmount() { return autonomousReservedAmount; }
    public int requiredAmount() { return requiredAmount; }
    public int contributedAmount() { return contributedAmount; }
    public int remainingAmount() { return requiredAmount - contributedAmount - autonomousReservedAmount; }
    public int qualifyingWaitSteps() { return qualifyingWaitSteps; }
    public int autonomousWaitRequired() { return autonomousWaitRequired; }
    public java.util.Set<String> contributionReceipts() { return java.util.Set.copyOf(contributionReceipts); }
    public boolean funded() { return remainingAmount() == 0; }
    public String policyVersion() { return policyVersion; }
    public DevelopmentIntentState state() { return state; }
    public String diagnostic() { return diagnostic; }
    public boolean start() { if (state != DevelopmentIntentState.PLANNED || !funded()) return false; state = DevelopmentIntentState.MATERIALIZING; return true; }
    public boolean complete() { if (state != DevelopmentIntentState.MATERIALIZING && state != DevelopmentIntentState.PLANNED) return false; state = DevelopmentIntentState.ACTIVE; diagnostic = ""; return true; }
    public boolean block(String reason) { if (state == DevelopmentIntentState.ACTIVE || state == DevelopmentIntentState.CANCELLED) return false; state = DevelopmentIntentState.BLOCKED; diagnostic = requireText(reason, "reason"); return true; }
    public boolean cancel(String reason) { if (state == DevelopmentIntentState.ACTIVE || state == DevelopmentIntentState.CANCELLED) return false; state = DevelopmentIntentState.CANCELLED; diagnostic = requireText(reason, "reason"); return true; }
    public boolean contribute(int amount, String receiptId) {
        if (state != DevelopmentIntentState.PLANNED || amount <= 0 || receiptId == null || receiptId.isBlank()
                || contributionReceipts.contains(receiptId)) return false;
        if (contributionReceipts.size() >= MAX_CONTRIBUTION_RECEIPTS || amount > remainingAmount()) {
            throw new IllegalStateException("Development contribution exceeds its pinned project escrow");
        }
        contributionReceipts.add(receiptId);
        contributedAmount += amount;
        return true;
    }
    public void waitQualifiedStep() { if (state == DevelopmentIntentState.PLANNED && !funded()) qualifyingWaitSteps++; }
    public boolean autonomousFundingDue() { return qualifyingWaitSteps >= autonomousWaitRequired && !funded(); }
    public void reserveAutonomously(int amount) {
        if (state != DevelopmentIntentState.PLANNED || amount <= 0 || amount > remainingAmount()) {
            throw new IllegalStateException("Invalid autonomous development reservation");
        }
        autonomousReservedAmount += amount;
    }
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
