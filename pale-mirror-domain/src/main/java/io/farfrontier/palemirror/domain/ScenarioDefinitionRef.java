package io.farfrontier.palemirror.domain;

import java.util.List;
import java.util.Objects;

/** Immutable authoring snapshot pinned into a scenario at offer time. */
public record ScenarioDefinitionRef(String id, String version, List<String> stages,
                                    List<String> requiredCapabilities, long cooldownSteps) {
    public ScenarioDefinitionRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        stages = List.copyOf(stages);
        requiredCapabilities = List.copyOf(requiredCapabilities);
        if (!stages.containsAll(List.of("OFFERED", "INVESTIGATE", "RECOVER", "RESOLVED"))) {
            throw new IllegalArgumentException("Scenario definition is missing core stages");
        }
        if (cooldownSteps < 0) throw new IllegalArgumentException("cooldownSteps must not be negative");
    }
}
