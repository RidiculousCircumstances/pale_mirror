package io.farfrontier.palemirror.api;

import java.util.List;

/** Immutable report shared by compile gates, live inspection and artifact exporters. */
public record FoundryAuditReport(int formatVersion, String regionId, String catalogHash,
                                 FoundryAuditPhase phase, List<FoundryFinding> findings,
                                 List<FoundryMetric> metrics, List<FoundryMapSample> mapSamples) {
    public FoundryAuditReport {
        if (formatVersion < 1) throw new IllegalArgumentException("formatVersion must be positive");
        if (regionId == null || regionId.isBlank()) throw new IllegalArgumentException("regionId is required");
        if (catalogHash == null || catalogHash.isBlank()) throw new IllegalArgumentException("catalogHash is required");
        if (phase == null) throw new IllegalArgumentException("phase is required");
        findings = List.copyOf(findings);
        metrics = List.copyOf(metrics);
        mapSamples = List.copyOf(mapSamples);
    }

    public long count(FoundrySeverity severity) {
        return findings.stream().filter(value -> value.severity() == severity).count();
    }

    public boolean passed() { return findings.stream().noneMatch(value -> value.severity().failsGate()); }

    public String summary() {
        return "Foundry " + phase + " " + regionId + ": " + (passed() ? "PASS" : "FAIL")
                + " blockers=" + count(FoundrySeverity.BLOCKER) + " errors=" + count(FoundrySeverity.ERROR)
                + " warnings=" + count(FoundrySeverity.WARNING);
    }
}
