package io.farfrontier.palemirror.internal.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** SavedData-owned native presentation record. A missing encounter never blocks the PM anchor. */
public final class EncounterRecord {
    private final String profileId;
    private final String profileVersion;
    private final String jobId;
    private final long desiredRevision;
    private final List<EncounterActorRef> actors;
    private EncounterState state;
    private String diagnostic;

    public EncounterRecord(String profileId, String profileVersion, String jobId, long desiredRevision,
                           List<EncounterActorRef> actors, EncounterState state, String diagnostic) {
        this.profileId = profileId == null ? "" : profileId;
        this.profileVersion = profileVersion == null ? "" : profileVersion;
        this.jobId = jobId == null ? "" : jobId;
        this.desiredRevision = desiredRevision;
        this.actors = new ArrayList<>(actors == null ? List.of() : actors);
        this.state = Objects.requireNonNull(state, "state");
        this.diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public static EncounterRecord none() { return new EncounterRecord("", "", "", 0, List.of(), EncounterState.NONE, ""); }
    public String profileId() { return profileId; }
    public String profileVersion() { return profileVersion; }
    public String jobId() { return jobId; }
    public long desiredRevision() { return desiredRevision; }
    public List<EncounterActorRef> actors() { return List.copyOf(actors); }
    public EncounterState state() { return state; }
    public String diagnostic() { return diagnostic; }
    public void activate() { state = EncounterState.ACTIVE; diagnostic = ""; }
    public void degrade(String reason) { state = EncounterState.DEGRADED; diagnostic = Objects.requireNonNull(reason, "reason"); }
    public void clean() { state = EncounterState.CLEANED; diagnostic = ""; }
}
