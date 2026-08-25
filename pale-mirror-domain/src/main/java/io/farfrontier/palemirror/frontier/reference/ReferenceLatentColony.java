package io.farfrontier.palemirror.frontier.reference;

import java.util.LinkedHashMap;
import java.util.Map;

/** Source-port state of one independently materializable latent colony. */
public final class ReferenceLatentColony {
    private final int x;
    private final int y;
    private final Map<String, Double> memory;
    private final Integer sourceOrganId;
    private double spores;
    private double strength;

    ReferenceLatentColony(int x, int y, double spores, double strength, Map<String, Double> memory, Integer sourceOrganId) {
        this.x = x; this.y = y; this.spores = spores; this.strength = strength;
        this.memory = new LinkedHashMap<>(memory); this.sourceOrganId = sourceOrganId;
    }

    public int x() { return x; }
    public int y() { return y; }
    public double spores() { return spores; }
    void spores(double value) { spores = value; }
    public double strength() { return strength; }
    void strength(double value) { strength = value; }
    public Map<String, Double> memory() { return Map.copyOf(memory); }
    public Integer sourceOrganId() { return sourceOrganId; }
}
