package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxBioformObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxObservationOutcome;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxResidentObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxStructureObservation;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxWarehouseObservation;
import io.farfrontier.palemirror.internal.effect.EffectLease;
import io.farfrontier.palemirror.internal.effect.EffectLeaseLedger;
import java.util.LinkedHashMap;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Durable canonical owner for one source-parity graybox world. */
final class SourceGrayboxSavedData extends SavedData implements SourceGrayboxCombatOwner {
    static final String DATA_NAME = "pale_mirror_frontier";
    private static final int SCHEMA = 21;
    private static final int MAX_PROCESSED_OBSERVATIONS = 4_096;
    private static final int MAX_EFFECT_LEASES = ReferenceGrayboxActorExecutionState.MAX_ACTORS + 512;
    private final ReferenceGrayboxSimulation simulation;
    private final ReferenceGrayboxActorExecutionState actorExecution;
    private final EffectLeaseLedger effectLeases;
    private final SourceGrayboxWarehouseLedger warehouseLedger;
    private final SourceGrayboxPhysicalScarLedger physicalScars;
    private final LinkedHashSet<String> processedObservationIds;
    private boolean activated;
    private long lastClockGameTime;

    private SourceGrayboxSavedData(ReferenceGrayboxSimulation simulation, ReferenceGrayboxActorExecutionState actorExecution,
                                   EffectLeaseLedger effectLeases,
                                   SourceGrayboxWarehouseLedger warehouseLedger,
                                   SourceGrayboxPhysicalScarLedger physicalScars,
                                   boolean activated, long lastClockGameTime,
                                   LinkedHashSet<String> processedObservationIds) {
        this.simulation = simulation;
        this.actorExecution = actorExecution;
        this.effectLeases = effectLeases;
        this.warehouseLedger = warehouseLedger;
        this.physicalScars = physicalScars;
        this.activated = activated;
        this.lastClockGameTime = lastClockGameTime;
        this.processedObservationIds = processedObservationIds;
    }

    static SourceGrayboxSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(
                () -> fresh(overworld.getSeed()),
                SourceGrayboxSavedData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), DATA_NAME);
    }

    static SourceGrayboxSavedData fresh(long seed) {
        ReferenceGrayboxSimulation simulation = ReferenceGrayboxSimulation.create(seed);
        return new SourceGrayboxSavedData(simulation, ReferenceGrayboxActorExecutionState.bootstrap(simulation.snapshot()),
                new EffectLeaseLedger(), new SourceGrayboxWarehouseLedger(), new SourceGrayboxPhysicalScarLedger(), false, 0L, new LinkedHashSet<>());
    }

    /**
     * DimensionDataStorage turns a loader failure into a fresh record.  The
     * source graybox must instead stop before storage sees an unhydratable
     * canonical document, otherwise the old clock could silently disappear.
     */
    static void assertCompatibleData(Path worldRoot) {
        PaleMirrorSavedDataCompatibility.assertHydratable(worldRoot, DATA_NAME,
                SourceGrayboxSavedData::assertHydratable, "source graybox canonical state");
    }

    /** Package-visible only so the Minecraft GameTest boundary can prove the preflight rejection. */
    static void assertHydratable(CompoundTag tag) {
        load(tag, null);
    }

    @Override public ReferenceGrayboxSnapshot snapshot() { return simulation.snapshot(); }
    @Override public ReferenceGrayboxActorExecutionState actorExecution() { return actorExecution; }
    @Override public EffectLeaseLedger effectLeases() { return effectLeases; }
    SourceGrayboxWarehouseLedger warehouseLedger() { return warehouseLedger; }
    SourceGrayboxPhysicalScarLedger physicalScars() { return physicalScars; }
    void markPhysicalScarsDirty() { setDirty(); }
    void markWarehouseLedgerDirty() { setDirty(); }
    @Override public void markEffectLeaseDirty() { setDirty(); }
    boolean activated() { return activated; }

    /** Game time may be moved backwards by an operator; execution leases keep a monotonic local ordering. */
    @Override public long actorExecutionGameTime(long observedGameTime) {
        long result = Math.max(0L, observedGameTime);
        for (ReferenceGrayboxActorExecutionState.ActorState actor : actorExecution.actors()) {
            result = Math.max(result, Math.max(actor.changedAtGameTick(), actor.demandedAtGameTick()));
        }
        return result;
    }

    boolean prepareActor(String id, String holder, long gameTick) {
        boolean changed = actorExecution.prepare(id, holder, gameTick);
        if (changed) setDirty();
        return changed;
    }

    boolean activateActor(String id, String leaseId, String holder, long gameTick) {
        boolean changed = actorExecution.activate(id, leaseId, holder, gameTick);
        if (changed) setDirty();
        return changed;
    }

    boolean cancelActorPreparation(String id, String leaseId, String holder, long gameTick) {
        boolean changed = actorExecution.cancelPreparation(id, leaseId, holder, gameTick);
        if (changed) setDirty();
        return changed;
    }

    boolean captureActor(String id, String leaseId, String holder, int xSixteenths, int zSixteenths, long gameTick) {
        boolean changed = actorExecution.capture(id, leaseId, holder, xSixteenths, zSixteenths, gameTick);
        if (changed) setDirty();
        return changed;
    }

    boolean touchActorDemand(String id, String leaseId, String holder, long gameTick) {
        boolean changed = actorExecution.touchDemand(id, leaseId, holder, gameTick);
        if (changed) setDirty();
        return changed;
    }

    boolean beginActorDrain(String id, String leaseId, String holder, long gameTick) {
        boolean changed = actorExecution.beginDrain(id, leaseId, holder, gameTick);
        if (changed) setDirty();
        return changed;
    }

    boolean settleActorCold(String id, String leaseId, String holder, long gameTick) {
        boolean changed = actorExecution.settleCold(id, leaseId, holder, gameTick);
        if (changed) setDirty();
        return changed;
    }

    boolean enterActorRecovery(long gameTick) {
        boolean changed = actorExecution.enterRecovery(gameTick);
        if (changed) setDirty();
        return changed;
    }

    boolean recoverActorHot(String id, String leaseId, String holder, long gameTick) {
        boolean changed = actorExecution.recoverHot(id, leaseId, holder, gameTick);
        if (changed) setDirty();
        return changed;
    }

    boolean recoverActorCold(String id, String leaseId, String holder, long gameTick) {
        boolean changed = actorExecution.recoverCold(id, leaseId, holder, gameTick);
        if (changed) setDirty();
        return changed;
    }

    boolean acknowledgeRetiredActor(String id, String leaseId, String holder, long gameTick) {
        boolean changed = actorExecution.acknowledgeRetired(id, leaseId, holder, gameTick);
        if (changed) setDirty();
        return changed;
    }

    @Override public java.util.Optional<ReferenceGrayboxActorExecutionState.CombatAction> reserveActorCombat(String id, String leaseId,
                                                                                                               String holder, long gameTick,
                                                                                                               long cooldownTicks) {
        java.util.Optional<ReferenceGrayboxActorExecutionState.CombatAction> action = actorExecution.reserveCombatAction(
                id, leaseId, holder, gameTick, cooldownTicks);
        action.ifPresent(ignored -> setDirty());
        return action;
    }

    boolean recoverEffectLeases(long gameTick) {
        boolean changed = effectLeases.recoverAfterRestart(gameTick);
        if (changed) setDirty();
        return changed;
    }

    boolean maintainEffectLeases(long gameTick) {
        boolean changed = effectLeases.expireDue(gameTick);
        // Local combat may finish up to the bounded per-interval action budget
        // at once. Compact on that same cadence, rather than allowing a source
        // day of terminal receipts to accumulate in memory.
        changed |= effectLeases.compact(gameTick);
        if (changed) setDirty();
        return changed;
    }

    void activate(long gameTime) {
        if (activated) return;
        synchronizeActorExecution();
        activated = true;
        lastClockGameTime = gameTime;
        setDirty();
    }

    int advanceDueDays(long gameTime, long intervalTicks, int maximumDays) {
        return advanceDueDaySnapshots(gameTime, intervalTicks, maximumDays).size();
    }

    /**
     * Retains the exact immutable result of every due source-day boundary so
     * the physical boundary can settle missed days cold instead of silently
     * forgetting their already-committed effects or replaying them late.
     */
    List<ReferenceGrayboxSnapshot> advanceDueDaySnapshots(long gameTime, long intervalTicks, int maximumDays) {
        if (!activated || intervalTicks < 1 || maximumDays < 1) return List.of();
        if (gameTime < lastClockGameTime) {
            lastClockGameTime = gameTime;
            setDirty();
            return List.of();
        }
        long due = Math.min(maximumDays, (gameTime - lastClockGameTime) / intervalTicks);
        List<ReferenceGrayboxSnapshot> boundaries = new java.util.ArrayList<>();
        for (long index = 0; index < due; index++) {
            simulation.tick();
            boundaries.add(simulation.snapshot());
        }
        if (due > 0) {
            synchronizeActorExecution();
            lastClockGameTime += due * intervalTicks;
            setDirty();
        }
        return List.copyOf(boundaries);
    }

    void advance(int days) {
        if (!activated) throw new IllegalStateException("source graybox is not activated");
        if (days < 1 || days > 365) throw new IllegalArgumentException("source graybox advance must be between one and 365 days");
        for (int index = 0; index < days; index++) simulation.tick();
        synchronizeActorExecution();
        setDirty();
    }

    ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxResidentObservation observation) {
        return observe(observation.eventId(), () -> simulation.observe(observation));
    }

    ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxBioformObservation observation) {
        return observe(observation.eventId(), () -> simulation.observe(observation));
    }

    ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxStructureObservation observation) {
        return observe(observation.eventId(), () -> simulation.observe(observation));
    }

    ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxWarehouseObservation observation) {
        return observe(observation.eventId(), () -> simulation.observe(observation));
    }

    private ReferenceGrayboxObservationOutcome observe(String eventId, java.util.function.Supplier<ReferenceGrayboxObservationOutcome> apply) {
        if (processedObservationIds.contains(eventId)) {
            return new ReferenceGrayboxObservationOutcome(eventId, ReferenceGrayboxObservationOutcome.Status.REJECTED_CONFLICT,
                    "observation event was already processed", simulation.snapshot().stateRevision());
        }
        ReferenceGrayboxObservationOutcome outcome = apply.get();
        if (outcome.applied()) {
            synchronizeActorExecution();
            processedObservationIds.add(eventId);
            while (processedObservationIds.size() > MAX_PROCESSED_OBSERVATIONS) processedObservationIds.removeFirst();
            setDirty();
        }
        return outcome;
    }

    static SourceGrayboxSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("schemaVersion") != SCHEMA || !tag.contains("sourceState", Tag.TAG_COMPOUND)) {
            throw new IllegalStateException("incompatible Frontier SavedData; reset the disposable graybox world");
        }
        LinkedHashSet<String> processed = readProcessed(tag.getList("processedObservationIds", Tag.TAG_STRING));
        ReferenceGrayboxSimulation simulation = SourceGrayboxStateNbt.read(tag.getCompound("sourceState"));
        if (!tag.contains("actorExecution", Tag.TAG_COMPOUND)) {
            throw new IllegalStateException("incompatible Frontier SavedData; reset the disposable graybox world");
        }
        ReferenceGrayboxActorExecutionState actorExecution = SourceGrayboxActorExecutionNbt.read(tag.getCompound("actorExecution"));
        if (!tag.contains("effectLeases", Tag.TAG_LIST)) {
            throw new IllegalStateException("incompatible Frontier SavedData; reset the disposable graybox world");
        }
        ListTag encodedLeases = tag.getList("effectLeases", Tag.TAG_COMPOUND);
        if (encodedLeases.size() > MAX_EFFECT_LEASES) {
            throw new IllegalStateException("source graybox effect history exceeds its bound");
        }
        java.util.Map<String, EffectLease> leases = new LinkedHashMap<>();
        EffectLeaseLedger effectLeases;
        try {
            for (Tag element : encodedLeases) {
                EffectLease lease = PaleMirrorAuxiliaryPresentationCodec.readEffectLease((CompoundTag) element);
                if (leases.putIfAbsent(lease.id(), lease) != null) throw new IllegalStateException("duplicate source graybox effect lease");
            }
            effectLeases = new EffectLeaseLedger(leases);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("source graybox effect lease is invalid", invalid);
        }
        if (!tag.contains("warehouseLedger", Tag.TAG_LIST)) {
            throw new IllegalStateException("incompatible Frontier SavedData; reset the disposable graybox world");
        }
        SourceGrayboxWarehouseLedger warehouseLedger = SourceGrayboxWarehouseLedger.load(tag.getList("warehouseLedger", Tag.TAG_COMPOUND));
        if (!tag.contains("physicalScars", Tag.TAG_LIST)) {
            throw new IllegalStateException("incompatible Frontier SavedData; reset the disposable graybox world");
        }
        SourceGrayboxPhysicalScarLedger physicalScars = SourceGrayboxPhysicalScarLedger.load(tag.getList("physicalScars", Tag.TAG_COMPOUND));
        assertActorExecutionMatchesSource(actorExecution, simulation.snapshot());
        return new SourceGrayboxSavedData(simulation, actorExecution, effectLeases, warehouseLedger, physicalScars,
                tag.getBoolean("activated"), tag.getLong("lastClockGameTime"), processed);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("schemaVersion", SCHEMA);
        tag.putBoolean("activated", activated);
        tag.putLong("lastClockGameTime", lastClockGameTime);
        tag.put("sourceState", SourceGrayboxStateNbt.write(simulation));
        tag.put("actorExecution", SourceGrayboxActorExecutionNbt.write(actorExecution));
        ListTag leases = new ListTag();
        effectLeases.leases().forEach(lease -> leases.add(PaleMirrorAuxiliaryPresentationCodec.writeEffectLease(lease)));
        tag.put("effectLeases", leases);
        tag.put("warehouseLedger", warehouseLedger.save());
        tag.put("physicalScars", physicalScars.save());
        ListTag processed = new ListTag();
        processedObservationIds.forEach(id -> processed.add(StringTag.valueOf(id)));
        tag.put("processedObservationIds", processed);
        return tag;
    }

    private static LinkedHashSet<String> readProcessed(ListTag encoded) {
        if (encoded.size() > MAX_PROCESSED_OBSERVATIONS) throw new IllegalStateException("source graybox observation history exceeds its bound");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Tag item : encoded) {
            String id = item.getAsString();
            if (id.isBlank() || id.length() > 128 || !result.add(id)) throw new IllegalStateException("source graybox observation history is invalid");
        }
        return result;
    }

    private void synchronizeActorExecution() {
        if (actorExecution.reconcile(simulation.snapshot())) setDirty();
    }

    /** A bad execution ledger is not allowed to fabricate/forget a source body during SavedData load. */
    private static void assertActorExecutionMatchesSource(ReferenceGrayboxActorExecutionState execution,
                                                          ReferenceGrayboxSnapshot snapshot) {
        java.util.Map<String, ReferenceGrayboxActorExecutionState.ActorDescriptor> expected = new java.util.LinkedHashMap<>();
        for (ReferenceGrayboxActorExecutionState.ActorDescriptor descriptor : ReferenceGrayboxActorExecutionState.descriptors(snapshot)) {
            expected.put(descriptor.id(), descriptor);
        }
        for (ReferenceGrayboxActorExecutionState.ActorState actor : execution.actors()) {
            ReferenceGrayboxActorExecutionState.ActorDescriptor descriptor = expected.remove(actor.id());
            if (descriptor == null) {
                if (actor.mode() != ReferenceGrayboxActorExecutionState.Mode.RETIRED) {
                    throw new IllegalStateException("source graybox execution has a non-retired actor absent from source: " + actor.id());
                }
                continue;
            }
            if (actor.mode() == ReferenceGrayboxActorExecutionState.Mode.RETIRED || actor.kind() != descriptor.kind()
                    || !actor.sourceRevision().equals(descriptor.sourceRevision())) {
                throw new IllegalStateException("source graybox actor execution disagrees with source: " + actor.id());
            }
        }
        if (!expected.isEmpty()) {
            throw new IllegalStateException("source graybox actor execution is missing source actors: " + expected.keySet().iterator().next());
        }
    }
}
