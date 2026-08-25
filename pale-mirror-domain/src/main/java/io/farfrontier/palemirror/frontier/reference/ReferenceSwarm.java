package io.farfrontier.palemirror.frontier.reference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Mutable source-port of Python's {@code Swarm}; discrete profiles use whole counts. */
public final class ReferenceSwarm {
    private final int id;
    private double x;
    private double y;
    private double power;
    private final int targetId;
    private final double speed;
    private final ReferenceBioformKind kind;
    private final Map<ReferenceBioformKind, Double> composition;
    private ReferenceFormationPhase phase;
    private final double readiness;
    private final Map<ReferenceBioformKind, Double> losses = new LinkedHashMap<>();
    private final Map<ReferenceBioformKind, List<String>> bioformIds = new LinkedHashMap<>();
    private final Integer sourceOrganId;
    private Integer targetX;
    private Integer targetY;
    private double cargo;
    private double geneticCargo;
    private String state = "outbound";
    private Integer forageX;
    private Integer forageY;
    private final boolean feral;

    ReferenceSwarm(int id, double x, double y, double power, int targetId, double speed, ReferenceBioformKind kind,
                   Map<ReferenceBioformKind, Double> composition, ReferenceFormationPhase phase, double readiness,
                   Integer sourceOrganId, Integer targetX, Integer targetY, boolean feral) {
        this.id = id; this.x = x; this.y = y; this.power = power; this.targetId = targetId; this.speed = speed;
        this.kind = kind; this.composition = new LinkedHashMap<>(composition); this.phase = phase; this.readiness = readiness;
        this.sourceOrganId = sourceOrganId; this.targetX = targetX; this.targetY = targetY; this.feral = feral;
        initializeBioformIds();
    }

    public int id() { return id; }
    public double x() { return x; }
    void x(double value) { x = value; }
    public double y() { return y; }
    void y(double value) { y = value; }
    public double power() { return power; }
    void power(double value) { power = value; }
    public int targetId() { return targetId; }
    public double speed() { return speed; }
    public ReferenceBioformKind kind() { return kind; }
    public Map<ReferenceBioformKind, Double> composition() { return Map.copyOf(composition); }
    public ReferenceFormationPhase phase() { return phase; }
    void phase(ReferenceFormationPhase value) { phase = value; }
    public double readiness() { return readiness; }
    public Map<ReferenceBioformKind, Double> losses() { return Map.copyOf(losses); }
    /** Stable one-body IDs for the discrete graybox; source composition remains the public tactical view. */
    public Map<ReferenceBioformKind, List<String>> bioformIds() {
        LinkedHashMap<ReferenceBioformKind, List<String>> copy = new LinkedHashMap<>();
        bioformIds.forEach((kind, ids) -> copy.put(kind, List.copyOf(ids)));
        return Map.copyOf(copy);
    }

    /** Restore the explicit discrete-body ledger after the aggregate swarm fields have been hydrated. */
    void restoreBioformIds(Map<ReferenceBioformKind, List<String>> restored) {
        Objects.requireNonNull(restored, "restored");
        if (!restored.keySet().equals(composition.keySet())) {
            throw new IllegalArgumentException("swarm " + id + " bioform identity kinds do not match its composition");
        }
        LinkedHashMap<ReferenceBioformKind, List<String>> replacement = new LinkedHashMap<>();
        for (ReferenceBioformKind kind : ReferenceBioformKind.values()) {
            List<String> ids = restored.get(kind);
            if (ids == null) continue;
            if (composition.get(kind) != ids.size()) {
                throw new IllegalArgumentException("swarm " + id + " has stale " + kind.id() + " bioform identity count");
            }
            String prefix = "bioform:" + id + ":" + kind.id() + ":";
            if (ids.stream().distinct().count() != ids.size() || ids.stream().anyMatch(value -> !validBioformId(value, prefix))) {
                throw new IllegalArgumentException("swarm " + id + " has malformed " + kind.id() + " bioform identity");
            }
            replacement.put(kind, new java.util.ArrayList<>(ids));
        }
        bioformIds.clear();
        bioformIds.putAll(replacement);
        assertDiscreteBioformInvariants();
    }
    void restoreLosses(Map<ReferenceBioformKind, Double> restored) {
        losses.clear(); losses.putAll(Objects.requireNonNull(restored, "restored"));
    }
    public Integer sourceOrganId() { return sourceOrganId; }
    public Integer targetX() { return targetX; }
    void targetX(Integer value) { targetX = value; }
    public Integer targetY() { return targetY; }
    void targetY(Integer value) { targetY = value; }
    public double cargo() { return cargo; }
    void cargo(double value) { cargo = value; }
    public double geneticCargo() { return geneticCargo; }
    void geneticCargo(double value) { geneticCargo = value; }
    public String state() { return state; }
    void state(String value) { state = value; }
    public Integer forageX() { return forageX; }
    void forageX(Integer value) { forageX = value; }
    public Integer forageY() { return forageY; }
    void forageY(Integer value) { forageY = value; }
    public boolean feral() { return feral; }

    boolean hasExactBioform(String bioformId) {
        return bioformIds.values().stream().anyMatch(ids -> ids.contains(bioformId));
    }

    /**
     * Remove one observed body without sampling another. Aggregate source power
     * is divided equally over the active discrete bodies, because source launch
     * power is not a per-role sum.
     */
    boolean killExactBioform(String bioformId) {
        int before = bioformIds.values().stream().mapToInt(List::size).sum();
        if (before == 0) return false;
        for (ReferenceBioformKind candidate : ReferenceBioformKind.values()) {
            List<String> ids = bioformIds.get(candidate);
            if (ids == null || !ids.remove(bioformId)) continue;
            if (ids.isEmpty()) {
                bioformIds.remove(candidate);
                composition.remove(candidate);
            } else composition.put(candidate, (double) ids.size());
            losses.merge(candidate, 1.0d, Double::sum);
            power = Math.max(0.0d, power * (before - 1) / before);
            return true;
        }
        return false;
    }

    void assertDiscreteBioformInvariants() {
        if (!bioformIds.keySet().equals(composition.keySet())) {
            throw new IllegalStateException("swarm " + id + " bioform identities do not match its composition");
        }
        for (Map.Entry<ReferenceBioformKind, Double> entry : composition.entrySet()) {
            List<String> ids = bioformIds.get(entry.getKey());
            String prefix = "bioform:" + id + ":" + entry.getKey().id() + ":";
            if (ids == null || entry.getValue() != ids.size() || ids.stream().distinct().count() != ids.size()
                    || ids.stream().anyMatch(value -> !validBioformId(value, prefix))) {
                throw new IllegalStateException("swarm " + id + " has malformed " + entry.getKey().id() + " bioform identity");
            }
        }
    }

    private void initializeBioformIds() {
        if (!bioformIds.isEmpty() || composition.values().stream().anyMatch(value -> value <= 0.0d || value != Math.rint(value))) return;
        for (ReferenceBioformKind candidate : ReferenceBioformKind.values()) {
            int count = composition.getOrDefault(candidate, 0.0d).intValue();
            if (count == 0) continue;
            java.util.ArrayList<String> ids = new java.util.ArrayList<>(count);
            for (int ordinal = 1; ordinal <= count; ordinal++) ids.add("bioform:" + id + ":" + candidate.id() + ":" + ordinal);
            bioformIds.put(candidate, ids);
        }
    }

    private static boolean validBioformId(String value, String prefix) {
        if (!value.startsWith(prefix)) return false;
        try { return Integer.parseInt(value.substring(prefix.length())) > 0; }
        catch (NumberFormatException ignored) { return false; }
    }
}
