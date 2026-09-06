package io.farfrontier.palemirror.frontier.reference;

import java.util.List;
import java.util.Objects;

/** Mutable physical trade route ported from Python {@code Route}. */
public final class ReferenceRoute {
    private static final double QUARANTINE_CAPACITY_MULTIPLIER = 0.18d;
    private static final double QUARANTINE_INFECTION_MULTIPLIER = 0.30d;

    private final int a;
    private final int b;
    private final double distance;
    private double capacity;
    private double risk;
    private double quality = 1.0d;
    private double infection;
    private int quarantineUntil = -1;
    private int disruptionUntil = -1;
    private double disruptionMultiplier = 1.0d;
    private double checkpointCapacityMultiplier = 1.0d;
    private List<String> sectorKeys = List.of();
    private double sectorInfection;

    public ReferenceRoute(int a, int b, double distance, double capacity) {
        this.a = a;
        this.b = b;
        this.distance = distance;
        this.capacity = capacity;
    }

    public int a() { return a; }
    public int b() { return b; }
    public double distance() { return distance; }
    public double capacity() { return capacity; }
    public void capacity(double value) { capacity = value; }
    public double risk() { return risk; }
    public void risk(double value) { risk = value; }
    public double quality() { return quality; }
    public void quality(double value) { quality = value; }
    public double infection() { return infection; }
    public void infection(double value) { infection = value; }
    public int quarantineUntil() { return quarantineUntil; }
    public void quarantineUntil(int value) { quarantineUntil = value; }
    public int disruptionUntil() { return disruptionUntil; }
    public void disruptionUntil(int value) { disruptionUntil = value; }
    public double disruptionMultiplier() { return disruptionMultiplier; }
    public void disruptionMultiplier(double value) { disruptionMultiplier = value; }
    public double checkpointCapacityMultiplier() { return checkpointCapacityMultiplier; }
    public void checkpointCapacityMultiplier(double value) { checkpointCapacityMultiplier = value; }
    public List<String> sectorKeys() { return sectorKeys; }
    public void sectorKeys(List<String> value) { sectorKeys = List.copyOf(Objects.requireNonNull(value, "sectorKeys")); }
    public double sectorInfection() { return sectorInfection; }
    public void sectorInfection(double value) { sectorInfection = value; }

    public ReferenceRouteKey key() {
        return ReferenceRouteKey.between(a, b);
    }

    public double capacityOn(Integer day) {
        double result = capacity;
        if (day != null && day <= quarantineUntil) result *= QUARANTINE_CAPACITY_MULTIPLIER;
        if (day != null && day <= disruptionUntil) result *= disruptionMultiplier;
        return result * checkpointCapacityMultiplier;
    }

    public double infectionOn(Integer day) {
        return day != null && day <= quarantineUntil ? infection * QUARANTINE_INFECTION_MULTIPLIER : infection;
    }
}
