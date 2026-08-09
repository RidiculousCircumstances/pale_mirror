package io.farfrontier.palemirror.domain;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Durable PM state for a local siege. Physical objects are held by the NeoForge layer. */
public final class SiegeState {
    public static final Set<String> NODE_SLOTS = Set.of("node_resistance", "node_strength", "node_speed", "node_infested");

    private SiegeStage stage;
    private String definitionId;
    private String definitionVersion;
    private String bossProfileId;
    private final Set<String> destroyedNodes;

    public SiegeState() { this(SiegeStage.INACTIVE, "", "", "", Set.of()); }

    public SiegeState(SiegeStage stage, String definitionId, String definitionVersion, String bossProfileId,
                      Set<String> destroyedNodes) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.definitionId = definitionId == null ? "" : definitionId;
        this.definitionVersion = definitionVersion == null ? "" : definitionVersion;
        this.bossProfileId = bossProfileId == null ? "" : bossProfileId;
        this.destroyedNodes = new LinkedHashSet<>(destroyedNodes == null ? Set.of() : destroyedNodes);
    }

    public SiegeStage stage() { return stage; }
    public String definitionId() { return definitionId; }
    public String definitionVersion() { return definitionVersion; }
    public String bossProfileId() { return bossProfileId; }
    public Set<String> destroyedNodes() { return Set.copyOf(destroyedNodes); }
    public boolean controllerVulnerable() { return !stage.protectsController(); }

    public boolean pending() {
        if (stage != SiegeStage.INACTIVE) return false;
        stage = SiegeStage.PENDING;
        return true;
    }

    public boolean activate(String definitionId, String definitionVersion, String bossProfileId) {
        if (stage != SiegeStage.PENDING || bossProfileId == null || bossProfileId.isBlank()) return false;
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId");
        this.definitionVersion = Objects.requireNonNull(definitionVersion, "definitionVersion");
        this.bossProfileId = bossProfileId;
        destroyedNodes.clear();
        stage = SiegeStage.NODES;
        return true;
    }

    public boolean bypass() {
        if (stage != SiegeStage.PENDING) return false;
        stage = SiegeStage.BYPASSED;
        return true;
    }

    public boolean gateDestroyed(String slotId) {
        Objects.requireNonNull(slotId, "slotId");
        return switch (stage) {
            case NODES -> destroyNode(slotId);
            case BOSS -> advance(slotId, "boss", SiegeStage.BLOODLINK_I);
            case BLOODLINK_I -> advance(slotId, "bloodlink_i", SiegeStage.BLOODLINK_II);
            case BLOODLINK_II -> advance(slotId, "bloodlink_ii", SiegeStage.BLOODLINK_III);
            case BLOODLINK_III -> advance(slotId, "bloodlink_iii", SiegeStage.CONTROLLER_VULNERABLE);
            default -> false;
        };
    }

    public void reset() {
        stage = SiegeStage.INACTIVE;
        definitionId = "";
        definitionVersion = "";
        bossProfileId = "";
        destroyedNodes.clear();
    }

    private boolean destroyNode(String slotId) {
        if (!NODE_SLOTS.contains(slotId) || !destroyedNodes.add(slotId)) return false;
        if (destroyedNodes.containsAll(NODE_SLOTS)) stage = SiegeStage.BOSS;
        return true;
    }

    private boolean advance(String slotId, String expected, SiegeStage next) {
        if (!expected.equals(slotId)) return false;
        stage = next;
        return true;
    }
}
