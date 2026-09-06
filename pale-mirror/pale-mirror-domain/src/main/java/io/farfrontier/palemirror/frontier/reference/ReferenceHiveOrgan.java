package io.farfrontier.palemirror.frontier.reference;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Mutable source-port of Python's public-compatible {@code Nest} organ. */
public final class ReferenceHiveOrgan {
    private final int id;
    private final int x;
    private final int y;
    private final Map<ReferenceMutation, Integer> mutations = new LinkedHashMap<>();
    private double biomass;
    private double samples;
    private int lastProjectDay = -10_000;
    private Integer colonyId;
    private Integer parentNestId;
    private String role;
    private ReferenceOrganKind kind;
    private double vitality = 100.0d;
    private boolean feral;

    public ReferenceHiveOrgan(int id, int x, int y, double biomass, double samples, Integer parentNestId, ReferenceOrganKind kind) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.biomass = biomass;
        this.samples = samples;
        this.parentNestId = parentNestId;
        this.kind = Objects.requireNonNull(kind, "kind");
        role = kind.name().toLowerCase();
    }

    public int id() { return id; }
    public int x() { return x; }
    public int y() { return y; }
    public double biomass() { return biomass; }
    public void biomass(double value) { biomass = value; }
    public double samples() { return samples; }
    public void samples(double value) { samples = value; }
    public Map<ReferenceMutation, Integer> mutations() { return Map.copyOf(mutations); }
    public void mutation(ReferenceMutation value, int level) { mutations.put(Objects.requireNonNull(value, "mutation"), level); }
    public int mutationLevel(ReferenceMutation value) { return mutations.getOrDefault(Objects.requireNonNull(value, "mutation"), 0); }
    public int lastProjectDay() { return lastProjectDay; }
    public void lastProjectDay(int value) { lastProjectDay = value; }
    public Integer colonyId() { return colonyId; }
    public void colonyId(Integer value) { colonyId = value; }
    public Integer parentNestId() { return parentNestId; }
    public void parentNestId(Integer value) { parentNestId = value; }
    public String role() { return role; }
    public void role(String value) { role = Objects.requireNonNull(value, "role"); }
    public ReferenceOrganKind kind() { return kind; }
    public void kind(ReferenceOrganKind value) { kind = Objects.requireNonNull(value, "kind"); }
    public double vitality() { return vitality; }
    public void vitality(double value) { vitality = value; }
    public boolean feral() { return feral; }
    public void feral(boolean value) { feral = value; }
}
