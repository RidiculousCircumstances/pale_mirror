package io.farfrontier.palemirror.frontier.reference;

import java.util.LinkedHashMap;
import java.util.Map;

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
}
