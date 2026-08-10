package io.farfrontier.palemirror.internal.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
    public Optional<EncounterActorRef> actor(String slotId) {
        return actors.stream().filter(actor -> actor.slotId().equals(slotId)).findFirst();
    }
    public int slotIndex(String slotId) {
        for (int index = 0; index < actors.size(); index++) if (actors.get(index).slotId().equals(slotId)) return index;
        return -1;
    }
    public void activate(String slotId, UUID entityId, String entityTypeId) {
        EncounterActorRef current = actor(slotId).orElse(null);
        int health = current != null && current.status() == EncounterActorRef.Status.ACTIVE
                && entityId.equals(current.entityId()) ? current.combatHitPoints()
                : EncounterActorRef.UNINITIALIZED_COMBAT_HIT_POINTS;
        replace(slotId, entityId, entityTypeId, EncounterActorRef.Status.ACTIVE, 0, 0, health);
        state = EncounterState.ACTIVE;
        diagnostic = "";
    }
    public void activate(String slotId, UUID entityId) {
        EncounterActorRef actor = actor(slotId).orElseThrow();
        activate(slotId, entityId, actor.entityTypeId());
    }
    public void defeated(String slotId, UUID entityId) { replace(slotId, entityId, "", EncounterActorRef.Status.DEFEATED, 0, 0, 0); }
    public void removed(String slotId) { replace(slotId, null, "", EncounterActorRef.Status.REMOVED, 0, 0, 0); }
    public void scheduleRuntime(String slotId, long nextRuntimeTick) {
        EncounterActorRef actor = actor(slotId).orElseThrow();
        replace(slotId, actor.entityId(), actor.entityTypeId(), actor.status(), nextRuntimeTick, actor.actionCounter() + 1,
                actor.combatHitPoints());
    }
    public void initializeCombatHitPoints(String slotId, int hitPoints) {
        if (hitPoints < 1) throw new IllegalArgumentException("hitPoints must be positive");
        EncounterActorRef actor = actor(slotId).orElseThrow();
        if (actor.combatHitPoints() != EncounterActorRef.UNINITIALIZED_COMBAT_HIT_POINTS) return;
        replace(slotId, actor.entityId(), actor.entityTypeId(), actor.status(), actor.nextRuntimeTick(), actor.actionCounter(), hitPoints);
    }
    /** Applies PM combat damage without ever invoking native entity damage hooks. */
    public int consumeCombatHitPoints(String slotId, int damage) {
        if (damage < 1) throw new IllegalArgumentException("damage must be positive");
        EncounterActorRef actor = actor(slotId).orElseThrow();
        if (actor.status() != EncounterActorRef.Status.ACTIVE || actor.combatHitPoints() < 0) return actor.combatHitPoints();
        int remaining = Math.max(0, actor.combatHitPoints() - damage);
        replace(slotId, actor.entityId(), actor.entityTypeId(), actor.status(), actor.nextRuntimeTick(), actor.actionCounter(), remaining);
        return remaining;
    }
    public void degrade(String reason) { state = EncounterState.DEGRADED; diagnostic = Objects.requireNonNull(reason, "reason"); }
    public void clean() { state = EncounterState.CLEANED; diagnostic = ""; }

    private void replace(String slotId, UUID entityId, String entityTypeId, EncounterActorRef.Status status,
                         long nextRuntimeTick, int actionCounter, int combatHitPoints) {
        for (int index = 0; index < actors.size(); index++) {
            EncounterActorRef actor = actors.get(index);
            if (actor.slotId().equals(slotId)) {
                actors.set(index, new EncounterActorRef(slotId, actor.actorProfileId(), entityTypeId, entityId, status,
                        nextRuntimeTick, actionCounter, combatHitPoints));
                return;
            }
        }
    }
}
