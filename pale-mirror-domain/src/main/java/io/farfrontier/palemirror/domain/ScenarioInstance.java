package io.farfrontier.palemirror.domain;

import java.util.Objects;
import java.util.List;

public final class ScenarioInstance {
    private final String id;
    private final String sourceEventId;
    private final WorldObjectId target;
    private final StoryAudienceId audience;
    private final String definitionId;
    private final String definitionVersion;
    private final List<String> pinnedStages;
    private final List<String> requiredCapabilities;
    private final String encounterProfileId;
    private final String encounterProfileVersion;
    private final ScenarioArchetype archetype;
    private ScenarioStatus status;
    private ScenarioStatus resumeStatus;
    private String blockedReason;
    private String resolutionOutcome;

    public ScenarioInstance(String id, String sourceEventId, WorldObjectId target, StoryAudienceId audience,
                            String definitionId, String definitionVersion, ScenarioStatus status) {
        this(id, sourceEventId, target, audience, definitionId, definitionVersion,
                List.of("OFFERED", "INVESTIGATE", "RECOVER", "RESOLVED"), List.of(), status, null, "");
    }

    public ScenarioInstance(String id, String sourceEventId, WorldObjectId target, StoryAudienceId audience,
                            String definitionId, String definitionVersion, List<String> pinnedStages,
                            List<String> requiredCapabilities, ScenarioStatus status, ScenarioStatus resumeStatus,
                            String blockedReason) {
        this(id, sourceEventId, target, audience, definitionId, definitionVersion, pinnedStages, requiredCapabilities,
                "", "", ScenarioArchetype.INVESTIGATION_RECOVERY, status, resumeStatus, blockedReason);
    }

    public ScenarioInstance(String id, String sourceEventId, WorldObjectId target, StoryAudienceId audience,
                            String definitionId, String definitionVersion, List<String> pinnedStages,
                            List<String> requiredCapabilities, String encounterProfileId, String encounterProfileVersion,
                            ScenarioArchetype archetype, ScenarioStatus status, ScenarioStatus resumeStatus, String blockedReason) {
        this(id, sourceEventId, target, audience, definitionId, definitionVersion, pinnedStages, requiredCapabilities,
                encounterProfileId, encounterProfileVersion, archetype, status, resumeStatus, blockedReason, "");
    }

    public ScenarioInstance(String id, String sourceEventId, WorldObjectId target, StoryAudienceId audience,
                            String definitionId, String definitionVersion, List<String> pinnedStages,
                            List<String> requiredCapabilities, String encounterProfileId, String encounterProfileVersion,
                            ScenarioArchetype archetype, ScenarioStatus status, ScenarioStatus resumeStatus,
                            String blockedReason, String resolutionOutcome) {
        this.id = Objects.requireNonNull(id, "id");
        this.sourceEventId = Objects.requireNonNull(sourceEventId, "sourceEventId");
        this.target = Objects.requireNonNull(target, "target");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId");
        this.definitionVersion = Objects.requireNonNull(definitionVersion, "definitionVersion");
        this.pinnedStages = List.copyOf(pinnedStages);
        this.requiredCapabilities = List.copyOf(requiredCapabilities);
        this.encounterProfileId = encounterProfileId == null ? "" : encounterProfileId;
        this.encounterProfileVersion = encounterProfileVersion == null ? "" : encounterProfileVersion;
        this.archetype = archetype == null ? ScenarioArchetype.INVESTIGATION_RECOVERY : archetype;
        this.status = Objects.requireNonNull(status, "status");
        this.resumeStatus = resumeStatus;
        this.blockedReason = blockedReason == null ? "" : blockedReason;
        this.resolutionOutcome = resolutionOutcome == null ? "" : resolutionOutcome;
    }

    public String id() { return id; }
    public String sourceEventId() { return sourceEventId; }
    public WorldObjectId target() { return target; }
    public StoryAudienceId audience() { return audience; }
    public String definitionId() { return definitionId; }
    public String definitionVersion() { return definitionVersion; }
    public ScenarioStatus status() { return status; }
    public List<String> pinnedStages() { return pinnedStages; }
    public List<String> requiredCapabilities() { return requiredCapabilities; }
    public String encounterProfileId() { return encounterProfileId; }
    public String encounterProfileVersion() { return encounterProfileVersion; }
    public ScenarioArchetype archetype() { return archetype; }
    public ScenarioStatus resumeStatus() { return resumeStatus; }
    public String blockedReason() { return blockedReason; }
    public String resolutionOutcome() { return resolutionOutcome; }
    public void setStatus(ScenarioStatus status) { this.status = Objects.requireNonNull(status, "status"); }
    public boolean resolve(String outcome) {
        if (status.isTerminal()) return false;
        status = ScenarioStatus.RESOLVED;
        resolutionOutcome = Objects.requireNonNull(outcome, "outcome");
        return true;
    }
    public boolean block(String reason) {
        if (status == ScenarioStatus.BLOCKED || status.isTerminal()) return false;
        resumeStatus = status;
        status = ScenarioStatus.BLOCKED;
        blockedReason = Objects.requireNonNull(reason, "reason");
        return true;
    }
    public boolean resume() {
        if (status != ScenarioStatus.BLOCKED || resumeStatus == null) return false;
        status = resumeStatus;
        resumeStatus = null;
        blockedReason = "";
        return true;
    }
}
