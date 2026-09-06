package io.farfrontier.palemirror.api;

/** Numeric audit evidence kept independent from prose and log formatting. */
public record FoundryMetric(String id, double value, String unit) {
    public FoundryMetric {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        if (!Double.isFinite(value)) throw new IllegalArgumentException("metric value must be finite");
        unit = unit == null ? "" : unit;
    }
}
