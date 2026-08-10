package io.farfrontier.palemirror.internal.combat;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Bounded SavedData ledger for PM-controlled optional threat combat.  It is
 * deliberately separate from domain state: encounters represent a facility
 * but never decide its infection, scenario or recovery outcome.
 */
public final class ThreatCombatLedger {
    private static final int MAX_TERMINAL_PROJECTILES = 512;
    private static final long TERMINAL_RETENTION_TICKS = 72_000L;
    private final Map<String, ThreatActorControlState> actors;
    private final Map<String, PmProjectileRef> projectiles;

    public ThreatCombatLedger() { this(Map.of(), Map.of()); }

    public ThreatCombatLedger(Map<String, ThreatActorControlState> actors, Map<String, PmProjectileRef> projectiles) {
        this.actors = new LinkedHashMap<>();
        this.projectiles = new LinkedHashMap<>();
        actors.values().stream().sorted(Comparator.comparing(ThreatActorControlState::key)).forEach(state -> this.actors.put(state.key(), state));
        projectiles.values().stream().sorted(Comparator.comparing(PmProjectileRef::id)).forEach(ref -> this.projectiles.put(ref.id(), ref));
    }

    public static String actorKey(String sourceId, String facilityId, String role, String slotId) {
        return sourceId + ":" + facilityId + ":" + role + ":" + slotId;
    }

    public List<ThreatActorControlState> actors() { return List.copyOf(actors.values()); }
    public List<PmProjectileRef> projectiles() { return List.copyOf(projectiles.values()); }
    public Optional<ThreatActorControlState> actor(String key) { return Optional.ofNullable(actors.get(key)); }
    public Optional<PmProjectileRef> projectile(String id) { return Optional.ofNullable(projectiles.get(id)); }

    public ThreatActorControlState attachActor(String sourceId, String facilityId, String role, String slotId,
                                                String profileId, UUID entityId, int hitPoints) {
        String key = actorKey(sourceId, facilityId, role, slotId);
        ThreatActorControlState current = actors.get(key);
        if (current == null) {
            current = new ThreatActorControlState(key, sourceId, facilityId, role, slotId, profileId, entityId,
                    hitPoints, 0L, 0L, 0, ThreatActorControlState.Status.ACTIVE);
            actors.put(key, current);
            return current;
        }
        if (!Objects.equals(current.entityId(), entityId) || current.status() != ThreatActorControlState.Status.ACTIVE
                || !current.profileId().equals(profileId)) current.attach(profileId, entityId, hitPoints);
        return current;
    }

    public void retireActor(String sourceId, String facilityId, String role, String slotId) {
        actor(actorKey(sourceId, facilityId, role, slotId)).ifPresent(ThreatActorControlState::retire);
    }

    public int applyActorDamage(String key, int amount) { return requireActor(key).applyDamage(amount); }
    public void scheduleActorAction(String key, long gameTick) { requireActor(key).scheduleAction(gameTick); }
    public void scheduleActorMovement(String key, long gameTick, int cursor) { requireActor(key).scheduleMovement(gameTick, cursor); }

    public PmProjectileRef planProjectile(PmProjectileRef proposal) {
        Objects.requireNonNull(proposal, "proposal");
        PmProjectileRef existing = projectiles.get(proposal.id());
        if (existing != null) return existing;
        projectiles.put(proposal.id(), proposal);
        return proposal;
    }

    public boolean activateProjectile(String id, UUID entityId) {
        PmProjectileRef ref = requireProjectile(id);
        PmProjectileRef.State before = ref.state();
        ref.activate(entityId);
        return before != ref.state();
    }

    public boolean impactProjectile(String id, String impactLeaseId, long gameTick) {
        return requireProjectile(id).impact(impactLeaseId, gameTick);
    }

    public void discardProjectile(String id, long gameTick, String reason) { requireProjectile(id).discard(gameTick, reason); }

    public boolean recoverAfterRestart(long gameTick) {
        boolean changed = false;
        for (PmProjectileRef ref : projectiles.values()) {
            PmProjectileRef.State before = ref.state();
            ref.recoverAfterRestart(gameTick);
            changed |= before != ref.state();
        }
        return changed;
    }

    public boolean expireAndCompact(long gameTick) {
        boolean changed = false;
        for (PmProjectileRef ref : projectiles.values()) {
            if (!ref.terminal() && gameTick > ref.expiresAtGameTick()) {
                ref.expire(gameTick);
                changed = true;
            }
        }
        List<PmProjectileRef> terminal = projectiles.values().stream().filter(PmProjectileRef::terminal)
                .sorted(Comparator.comparingLong(PmProjectileRef::finishedAtGameTick).thenComparing(PmProjectileRef::id)).toList();
        for (PmProjectileRef ref : terminal) {
            if (ref.finishedAtGameTick() > 0 && gameTick - ref.finishedAtGameTick() >= TERMINAL_RETENTION_TICKS) {
                projectiles.remove(ref.id());
                changed = true;
            }
        }
        terminal = projectiles.values().stream().filter(PmProjectileRef::terminal)
                .sorted(Comparator.comparingLong(PmProjectileRef::finishedAtGameTick).thenComparing(PmProjectileRef::id)).toList();
        for (int index = 0; index < terminal.size() - MAX_TERMINAL_PROJECTILES; index++) {
            projectiles.remove(terminal.get(index).id());
            changed = true;
        }
        return changed;
    }

    public void clear() {
        actors.clear();
        projectiles.clear();
    }

    private ThreatActorControlState requireActor(String key) {
        ThreatActorControlState state = actors.get(key);
        if (state == null) throw new IllegalArgumentException("Unknown PM combat actor " + key);
        return state;
    }

    private PmProjectileRef requireProjectile(String id) {
        PmProjectileRef ref = projectiles.get(id);
        if (ref == null) throw new IllegalArgumentException("Unknown PM projectile " + id);
        return ref;
    }
}
