package io.farfrontier.palemirror.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, version-pinned logical gate plan selected by one source adapter. */
public record GatePlanRef(String id, String version, List<GatePhaseRef> phases, Map<String, String> opaqueParameters) {
    public GatePlanRef(String id, String version, List<GatePhaseRef> phases) {
        this(id, version, phases, Map.of());
    }

    public GatePlanRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        phases = List.copyOf(Objects.requireNonNull(phases, "phases"));
        opaqueParameters = Map.copyOf(Objects.requireNonNull(opaqueParameters, "opaqueParameters"));
        if (id.isBlank() || version.isBlank() || phases.isEmpty()) {
            throw new IllegalArgumentException("Gate plan requires id, version, and at least one phase");
        }
        HashSet<String> phaseIds = new HashSet<>();
        HashSet<String> partIds = new HashSet<>();
        for (GatePhaseRef phase : phases) {
            if (!phaseIds.add(phase.id())) throw new IllegalArgumentException("Gate phase ids must be unique");
            for (String partId : phase.requiredPartIds()) {
                if (!partIds.add(partId)) throw new IllegalArgumentException("Gate part ids must be globally unique");
            }
        }
        if (opaqueParameters.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getKey().isBlank()
                || entry.getValue() == null || entry.getValue().isBlank())) {
            throw new IllegalArgumentException("Gate plan opaque parameters must be non-blank");
        }
    }
}
