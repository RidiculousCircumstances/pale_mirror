package io.farfrontier.palemirror.domain;

import java.util.Objects;

public final class ScenarioInstance {
    private final String id;
    private final String sourceEventId;
    private final WorldObjectId target;
    private final StoryAudienceId audience;
    private final String definitionId;
    private final String definitionVersion;
    private ScenarioStatus status;

    public ScenarioInstance(String id, String sourceEventId, WorldObjectId target, StoryAudienceId audience,
                            String definitionId, String definitionVersion, ScenarioStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.sourceEventId = Objects.requireNonNull(sourceEventId, "sourceEventId");
        this.target = Objects.requireNonNull(target, "target");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId");
        this.definitionVersion = Objects.requireNonNull(definitionVersion, "definitionVersion");
        this.status = Objects.requireNonNull(status, "status");
    }

    public String id() { return id; }
    public String sourceEventId() { return sourceEventId; }
    public WorldObjectId target() { return target; }
    public StoryAudienceId audience() { return audience; }
    public String definitionId() { return definitionId; }
    public String definitionVersion() { return definitionVersion; }
    public ScenarioStatus status() { return status; }
    public void setStatus(ScenarioStatus status) { this.status = Objects.requireNonNull(status, "status"); }
}
