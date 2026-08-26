package io.farfrontier.palemirror.frontier.reference;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonical execution ownership for the physical body of every source-graybox actor.
 *
 * <p>The source world remains responsible for strategic identity, custody and the daily
 * decision transaction. This state owns only the continuous physical hand-off: a body may
 * be COLD (off-screen), prepared, live in a loaded chunk, draining back to the source, or
 * recovering after restart. It deliberately stores positions as sixteenths of a block, not
 * Minecraft doubles, so persistence and a later HOT/COLD reconciliation are deterministic.</p>
 */
public final class ReferenceGrayboxActorExecutionState {
    public static final int POSITION_SCALE = 16;
    public static final int MAX_ACTORS = 4_096;

    private final LinkedHashMap<String, ActorState> actors;

    private ReferenceGrayboxActorExecutionState(Map<String, ActorState> actors) {
        this.actors = new LinkedHashMap<>(actors);
        validate(this.actors.values());
    }

    /** Creates a complete cold ledger from the source snapshot without changing source state. */
    public static ReferenceGrayboxActorExecutionState bootstrap(ReferenceGrayboxSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        LinkedHashMap<String, ActorState> initial = new LinkedHashMap<>();
        for (ActorDescriptor descriptor : descriptors(snapshot)) {
            if (initial.putIfAbsent(descriptor.id(), ActorState.cold(descriptor)) != null) {
                throw new IllegalArgumentException("duplicate source actor: " + descriptor.id());
            }
        }
        return new ReferenceGrayboxActorExecutionState(initial);
    }

    /** Restores a fully validated execution ledger; callers must not manufacture an alternate owner. */
    public static ReferenceGrayboxActorExecutionState restore(Collection<ActorState> restored) {
        Objects.requireNonNull(restored, "restored");
        LinkedHashMap<String, ActorState> values = new LinkedHashMap<>();
        for (ActorState actor : restored) {
            ActorState required = Objects.requireNonNull(actor, "actor");
            if (values.putIfAbsent(required.id(), required) != null) {
                throw new IllegalArgumentException("duplicate restored actor: " + required.id());
            }
        }
        return new ReferenceGrayboxActorExecutionState(values);
    }

    public List<ActorState> actors() {
        return List.copyOf(actors.values());
    }

    public Optional<ActorState> actor(String id) {
        return Optional.ofNullable(actors.get(id));
    }

    /**
     * Reconciles source-owned arrivals, departures and updated source anchors.
     *
     * <p>A COLD actor follows the latest source anchor. A HOT actor keeps its observed physical
     * position until it is drained; source revision and next strategic anchor still advance. If a
     * source actor disappears while it owns a physical executor, it becomes RETIRED and can only
     * be removed by an explicit executor acknowledgement.</p>
     *
     * @return true only when durable execution state changed
     */
    public boolean reconcile(ReferenceGrayboxSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        LinkedHashMap<String, ActorDescriptor> desired = new LinkedHashMap<>();
        for (ActorDescriptor descriptor : descriptors(snapshot)) {
            if (desired.putIfAbsent(descriptor.id(), descriptor) != null) {
                throw new IllegalArgumentException("duplicate source actor: " + descriptor.id());
            }
        }
        boolean changed = false;
        for (ActorDescriptor descriptor : desired.values()) {
            ActorState prior = actors.get(descriptor.id());
            ActorState next = prior == null ? ActorState.cold(descriptor) : prior.withSource(descriptor);
            if (!next.equals(prior)) {
                actors.put(descriptor.id(), next);
                changed = true;
            }
        }
        for (String id : new ArrayList<>(actors.keySet())) {
            if (desired.containsKey(id)) continue;
            ActorState prior = actors.get(id);
            if (prior.mode() == Mode.COLD || prior.mode() == Mode.RETIRED) {
                actors.remove(id);
            } else {
                actors.put(id, prior.retired());
            }
            changed = true;
        }
        validate(actors.values());
        return changed;
    }

    /** Reserves the one deterministic physical execution lease for a cold actor. */
    public boolean prepare(String id, String holder, long gameTick) {
        ActorState prior = require(id);
        if (prior.mode() != Mode.COLD) return false;
        actors.put(id, prior.prepare(holder, gameTick));
        return true;
    }

    /** Promotes a prepared actor only when the same executor still owns its lease. */
    public boolean activate(String id, String leaseId, String holder, long gameTick) {
        ActorState prior = require(id);
        if (prior.mode() != Mode.PREPARING || !prior.leaseId().equals(leaseId) || !prior.holder().equals(holder)) return false;
        actors.put(id, prior.transition(Mode.HOT, gameTick));
        return true;
    }

    /** Releases an unmaterialized preparation lease after its bounded preparation window expires. */
    public boolean cancelPreparation(String id, String leaseId, String holder, long gameTick) {
        ActorState prior = require(id);
        if (prior.mode() != Mode.PREPARING || !prior.leaseId().equals(leaseId) || !prior.holder().equals(holder)) return false;
        actors.put(id, prior.cold(gameTick));
        return true;
    }

    /** Begins a hand-off; source code must capture the actual position before settling COLD. */
    public boolean beginDrain(String id, String leaseId, String holder, long gameTick) {
        ActorState prior = require(id);
        if (prior.mode() != Mode.HOT || !prior.leaseId().equals(leaseId) || !prior.holder().equals(holder)) return false;
        actors.put(id, prior.transition(Mode.DRAINING, gameTick));
        return true;
    }

    /** Captures a real Minecraft position after movement/combat without exposing binary64 to the domain. */
    public boolean capture(String id, String leaseId, String holder, int xSixteenths, int zSixteenths, long gameTick) {
        ActorState prior = require(id);
        if (!(prior.mode() == Mode.HOT || prior.mode() == Mode.DRAINING)
                || !prior.leaseId().equals(leaseId) || !prior.holder().equals(holder)) return false;
        actors.put(id, prior.capture(xSixteenths, zSixteenths, gameTick));
        return true;
    }

    /** Records that a player still needs this prepared/live body, providing bounded drain hysteresis. */
    public boolean touchDemand(String id, String leaseId, String holder, long gameTick) {
        ActorState prior = require(id);
        if (!(prior.mode() == Mode.PREPARING || prior.mode() == Mode.HOT)
                || !prior.leaseId().equals(leaseId) || !prior.holder().equals(holder)) return false;
        actors.put(id, prior.touchDemand(gameTick));
        return true;
    }

    /**
     * Reserves one local physical action before Minecraft may apply it. The
     * durable epoch is never reused and the cooldown belongs to the exact
     * source actor instead of an ephemeral native mob goal.
     */
    public Optional<CombatAction> reserveCombatAction(String id, String leaseId, String holder, long gameTick, long cooldownTicks) {
        if (cooldownTicks < 1L) throw new IllegalArgumentException("combat cooldown must be positive");
        ActorState prior = require(id);
        if (prior.mode() != Mode.HOT || !prior.leaseId().equals(leaseId) || !prior.holder().equals(holder)
                || gameTick < prior.nextCombatAtGameTick()) return Optional.empty();
        ActorState next = prior.reserveCombatAction(gameTick, cooldownTicks);
        actors.put(id, next);
        return Optional.of(new CombatAction(combatActionId(next), next.id(), next.leaseId(), next.combatActionEpoch(), gameTick));
    }

    /** Releases a draining executor only after its physical state was captured. */
    public boolean settleCold(String id, String leaseId, String holder, long gameTick) {
        ActorState prior = require(id);
        if (prior.mode() != Mode.DRAINING || !prior.leaseId().equals(leaseId) || !prior.holder().equals(holder)) return false;
        actors.put(id, prior.cold(gameTick));
        return true;
    }

    /** Marks unfinished executors as unknown at restart. They must be inspected, never replayed blindly. */
    public boolean enterRecovery(long gameTick) {
        boolean changed = false;
        for (Map.Entry<String, ActorState> entry : actors.entrySet()) {
            ActorState prior = entry.getValue();
            if (prior.mode() == Mode.COLD || prior.mode() == Mode.RETIRED || prior.mode() == Mode.RECOVERING) continue;
            entry.setValue(prior.transition(Mode.RECOVERING, gameTick));
            changed = true;
        }
        return changed;
    }

    /** Re-adopts an inspected physical actor only through its pre-restart lease. */
    public boolean recoverHot(String id, String leaseId, String holder, long gameTick) {
        ActorState prior = require(id);
        if (prior.mode() != Mode.RECOVERING || !prior.leaseId().equals(leaseId) || !prior.holder().equals(holder)) return false;
        actors.put(id, prior.transition(Mode.HOT, gameTick));
        return true;
    }

    /** Resolves a missing/rejected post-restart actor as cold without manufacturing a replacement. */
    public boolean recoverCold(String id, String leaseId, String holder, long gameTick) {
        ActorState prior = require(id);
        if (prior.mode() != Mode.RECOVERING || !prior.leaseId().equals(leaseId) || !prior.holder().equals(holder)) return false;
        actors.put(id, prior.cold(gameTick));
        return true;
    }

    /** Executor acknowledgement for a source actor that disappeared while physical work was still pending. */
    public boolean acknowledgeRetired(String id, String leaseId, String holder, long gameTick) {
        ActorState prior = require(id);
        if (prior.mode() != Mode.RETIRED || !prior.leaseId().equals(leaseId) || !prior.holder().equals(holder)) return false;
        if (gameTick < prior.changedAtGameTick()) throw new IllegalArgumentException("execution clock moved backwards");
        actors.remove(id);
        return true;
    }

    /** Exact, source-owned actor descriptors used by the physical execution layer. */
    public static List<ActorDescriptor> descriptors(ReferenceGrayboxSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<ActorDescriptor> result = new ArrayList<>(snapshot.residents().size() + snapshot.bioforms().size());
        for (ReferenceGrayboxSnapshot.Resident resident : snapshot.residents()) {
            result.add(new ActorDescriptor(resident.id(), ActorKind.RESIDENT, residentRevision(snapshot.profileId(), resident),
                    center(resident.position().x()), center(resident.position().z())));
        }
        for (ReferenceGrayboxSnapshot.Bioform bioform : snapshot.bioforms()) {
            result.add(new ActorDescriptor(bioform.id(), ActorKind.BIOFORM, bioformRevision(snapshot.profileId(), bioform),
                    center(bioform.position().x()), center(bioform.position().z())));
        }
        result.sort(Comparator.comparing(ActorDescriptor::id));
        return List.copyOf(result);
    }

    /**
     * A physical lease belongs to one body, not to an incidental complete-frame digest.
     *
     * <p>The projection's {@link ReferenceGrayboxSnapshot#stateRevision()} changes when a
     * different settlement, route, label, or presentation-only field changes. Binding that
     * global revision to every actor made a valid saved lease look stale after hydration. This
     * stable digest instead covers the complete source-visible semantics of this one resident
     * that the physical executor may observe or act upon.</p>
     */
    private static String residentRevision(String profileId, ReferenceGrayboxSnapshot.Resident resident) {
        return semanticRevision("resident", profileId, resident.id(), Integer.toString(resident.homeSettlementId()),
                resident.occupation(), resident.economicClass(), resident.location(),
                resident.locationId() == null ? "" : resident.locationId().toString(), resident.condition(),
                resident.deploymentRole() == null ? "" : resident.deploymentRole(),
                Integer.toString(resident.position().x()), Integer.toString(resident.position().z()), resident.colour());
    }

    /** Stable source-visible semantics for one exact materialized bioform. */
    private static String bioformRevision(String profileId, ReferenceGrayboxSnapshot.Bioform bioform) {
        return semanticRevision("bioform", profileId, bioform.id(), Integer.toString(bioform.swarmId()), bioform.kind(),
                bioform.phase(), Boolean.toString(bioform.feral()), Integer.toString(bioform.position().x()),
                Integer.toString(bioform.position().z()), bioform.colour());
    }

    private static String semanticRevision(String kind, String... fields) {
        StringBuilder canonical = new StringBuilder("frontier-graybox-actor-revision-v1");
        appendSemanticField(canonical, kind);
        for (String field : fields) appendSemanticField(canonical, field);
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required for source actor revisions", unavailable);
        }
    }

    private static void appendSemanticField(StringBuilder target, String field) {
        String value = Objects.requireNonNull(field, "semantic actor field");
        target.append(value.length()).append(':').append(value);
    }

    private static int center(int block) {
        return Math.multiplyExact(block, POSITION_SCALE) + POSITION_SCALE / 2;
    }

    private ActorState require(String id) {
        ActorState state = actors.get(Objects.requireNonNull(id, "id"));
        if (state == null) throw new IllegalArgumentException("unknown source actor: " + id);
        return state;
    }

    private static void validate(Collection<ActorState> values) {
        if (values.size() > MAX_ACTORS) throw new IllegalArgumentException("source graybox actor execution exceeds its bound");
        for (ActorState actor : values) Objects.requireNonNull(actor, "actor");
    }

    public enum ActorKind { RESIDENT, BIOFORM }

    public enum Mode { COLD, PREPARING, HOT, DRAINING, RECOVERING, RETIRED }

    /** Durable identity of one non-replayable physical combat action. */
    public record CombatAction(String id, String actorId, String leaseId, long actionEpoch, long scheduledAtGameTick) {
        public CombatAction {
            if (id == null || id.isBlank() || actorId == null || actorId.isBlank() || leaseId == null || leaseId.isBlank()
                    || actionEpoch < 1L || scheduledAtGameTick < 0L) {
                throw new IllegalArgumentException("combat action identity is invalid");
            }
        }
    }

    public record ActorDescriptor(String id, ActorKind kind, String sourceRevision, int anchorXSixteenths, int anchorZSixteenths) {
        public ActorDescriptor {
            requireId(id, kind);
            kind = Objects.requireNonNull(kind, "kind");
            requireRevision(sourceRevision);
        }
    }

    /** Durable, exact state for one source actor and its one possible physical executor. */
    public record ActorState(String id, ActorKind kind, Mode mode, String sourceRevision,
                             int anchorXSixteenths, int anchorZSixteenths,
                             int actualXSixteenths, int actualZSixteenths,
                             long leaseEpoch, String leaseId, String holder, long changedAtGameTick, long demandedAtGameTick,
                             long combatActionEpoch, long nextCombatAtGameTick) {
        public ActorState {
            requireId(id, kind);
            kind = Objects.requireNonNull(kind, "kind");
            mode = Objects.requireNonNull(mode, "mode");
            requireRevision(sourceRevision);
            if (leaseEpoch < 0L || changedAtGameTick < 0L || demandedAtGameTick < 0L
                    || combatActionEpoch < 0L || nextCombatAtGameTick < 0L) {
                throw new IllegalArgumentException("execution epoch or game tick is invalid");
            }
            leaseId = leaseId == null ? "" : leaseId;
            holder = holder == null ? "" : holder;
            boolean leased = mode != Mode.COLD;
            if (leased && !(leaseEpoch > 0L && leaseId.equals(lease(id, kind, leaseEpoch)) && validHolder(holder))) {
                throw new IllegalArgumentException("actor execution lease is invalid");
            }
            if (!leased && (!leaseId.isEmpty() || !holder.isEmpty())) {
                throw new IllegalArgumentException("cold actor must not retain an execution lease");
            }
        }

        static ActorState cold(ActorDescriptor descriptor) {
            return new ActorState(descriptor.id(), descriptor.kind(), Mode.COLD, descriptor.sourceRevision(),
                    descriptor.anchorXSixteenths(), descriptor.anchorZSixteenths(), descriptor.anchorXSixteenths(),
                    descriptor.anchorZSixteenths(), 0L, "", "", 0L, 0L, 0L, 0L);
        }

        ActorState withSource(ActorDescriptor descriptor) {
            if (!id.equals(descriptor.id()) || kind != descriptor.kind()) throw new IllegalArgumentException("actor source identity changed");
            boolean cold = mode == Mode.COLD;
            return new ActorState(id, kind, mode, descriptor.sourceRevision(), descriptor.anchorXSixteenths(), descriptor.anchorZSixteenths(),
                    cold ? descriptor.anchorXSixteenths() : actualXSixteenths, cold ? descriptor.anchorZSixteenths() : actualZSixteenths,
                    leaseEpoch, leaseId, holder, changedAtGameTick, demandedAtGameTick, combatActionEpoch, nextCombatAtGameTick);
        }

        ActorState prepare(String nextHolder, long gameTick) {
            return new ActorState(id, kind, Mode.PREPARING, sourceRevision, anchorXSixteenths, anchorZSixteenths,
                    actualXSixteenths, actualZSixteenths, Math.addExact(leaseEpoch, 1L), lease(id, kind, leaseEpoch + 1L),
                    requireHolder(nextHolder), requireForward(gameTick), gameTick, combatActionEpoch, nextCombatAtGameTick);
        }

        ActorState transition(Mode next, long gameTick) {
            return new ActorState(id, kind, next, sourceRevision, anchorXSixteenths, anchorZSixteenths,
                    actualXSixteenths, actualZSixteenths, leaseEpoch, leaseId, holder, requireForward(gameTick), demandedAtGameTick,
                    combatActionEpoch, nextCombatAtGameTick);
        }

        ActorState capture(int x, int z, long gameTick) {
            return new ActorState(id, kind, mode, sourceRevision, anchorXSixteenths, anchorZSixteenths, x, z,
                    leaseEpoch, leaseId, holder, requireForward(gameTick), demandedAtGameTick, combatActionEpoch, nextCombatAtGameTick);
        }

        ActorState cold(long gameTick) {
            return new ActorState(id, kind, Mode.COLD, sourceRevision, anchorXSixteenths, anchorZSixteenths,
                    actualXSixteenths, actualZSixteenths, leaseEpoch, "", "", requireForward(gameTick), demandedAtGameTick,
                    combatActionEpoch, nextCombatAtGameTick);
        }

        ActorState retired() {
            if (mode == Mode.COLD) throw new IllegalStateException("cold actor must be removed directly");
            return new ActorState(id, kind, Mode.RETIRED, sourceRevision, anchorXSixteenths, anchorZSixteenths,
                    actualXSixteenths, actualZSixteenths, leaseEpoch, leaseId, holder, changedAtGameTick, demandedAtGameTick,
                    combatActionEpoch, nextCombatAtGameTick);
        }

        ActorState touchDemand(long gameTick) {
            long tick = requireForward(gameTick);
            return new ActorState(id, kind, mode, sourceRevision, anchorXSixteenths, anchorZSixteenths,
                    actualXSixteenths, actualZSixteenths, leaseEpoch, leaseId, holder, changedAtGameTick,
                    Math.max(demandedAtGameTick, tick), combatActionEpoch, nextCombatAtGameTick);
        }

        ActorState reserveCombatAction(long gameTick, long cooldownTicks) {
            long tick = requireForward(gameTick);
            long epoch = Math.addExact(combatActionEpoch, 1L);
            return new ActorState(id, kind, mode, sourceRevision, anchorXSixteenths, anchorZSixteenths,
                    actualXSixteenths, actualZSixteenths, leaseEpoch, leaseId, holder, tick, demandedAtGameTick,
                    epoch, Math.addExact(tick, cooldownTicks));
        }

        private long requireForward(long gameTick) {
            if (gameTick < changedAtGameTick) throw new IllegalArgumentException("execution clock moved backwards");
            return gameTick;
        }
    }

    private static String lease(String id, ActorKind kind, long epoch) {
        return "graybox-actor:" + kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + id + ":" + epoch;
    }

    private static String combatActionId(ActorState actor) {
        return "graybox-combat:" + actor.kind().name().toLowerCase(java.util.Locale.ROOT) + ":" + actor.id()
                + ":lease:" + actor.leaseEpoch() + ":action:" + actor.combatActionEpoch();
    }

    private static void requireId(String id, ActorKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (id == null || id.isBlank() || id.length() > 192
                || (kind == ActorKind.RESIDENT && !id.startsWith("resident:"))
                || (kind == ActorKind.BIOFORM && !id.startsWith("bioform:"))) {
            throw new IllegalArgumentException("source actor identity is invalid");
        }
    }

    private static void requireRevision(String revision) {
        if (revision == null || !revision.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("source actor revision is invalid");
        }
    }

    private static String requireHolder(String value) {
        if (!validHolder(value)) throw new IllegalArgumentException("execution holder is invalid");
        return value;
    }

    private static boolean validHolder(String value) {
        return value != null && !value.isBlank() && value.length() <= 128 && value.matches("[a-z0-9:_./-]+");
    }
}
