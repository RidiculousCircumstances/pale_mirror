package io.farfrontier.palemirror.domain;

import java.util.List;
import java.util.Objects;

/** Immutable authoring snapshot pinned into a scenario at offer time. */
public record ScenarioDefinitionRef(String id, String version, List<String> stages,
                                    List<String> requiredCapabilities, long cooldownSteps,
                                    String encounterProfileId, String encounterProfileVersion,
                                    ScenarioArchetype archetype) {
    public ScenarioDefinitionRef(String id, String version, List<String> stages,
                                 List<String> requiredCapabilities, long cooldownSteps) {
        this(id, version, stages, requiredCapabilities, cooldownSteps, "", "", ScenarioArchetype.INVESTIGATION_RECOVERY);
    }

    public ScenarioDefinitionRef(String id, String version, List<String> stages,
                                 List<String> requiredCapabilities, long cooldownSteps,
                                 String encounterProfileId, String encounterProfileVersion) {
        this(id, version, stages, requiredCapabilities, cooldownSteps, encounterProfileId, encounterProfileVersion,
                ScenarioArchetype.INVESTIGATION_RECOVERY);
    }

    public ScenarioDefinitionRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        encounterProfileId = encounterProfileId == null ? "" : encounterProfileId;
        encounterProfileVersion = encounterProfileVersion == null ? "" : encounterProfileVersion;
        archetype = archetype == null ? ScenarioArchetype.INVESTIGATION_RECOVERY : archetype;
        stages = List.copyOf(stages);
        requiredCapabilities = List.copyOf(requiredCapabilities);
        List<String> requiredStages = archetype == ScenarioArchetype.INVESTIGATION_RECOVERY
                ? List.of("OFFERED", "INVESTIGATE", "RECOVER", "RESOLVED")
                : List.of("OFFERED", "ASSESS", "RESPOND", "RESOLVED");
        if (!stages.containsAll(requiredStages)) {
            throw new IllegalArgumentException("Scenario definition is missing stages for " + archetype);
        }
        if (cooldownSteps < 0) throw new IllegalArgumentException("cooldownSteps must not be negative");
    }
}
