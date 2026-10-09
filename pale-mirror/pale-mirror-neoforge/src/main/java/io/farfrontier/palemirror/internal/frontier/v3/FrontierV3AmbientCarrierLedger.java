package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;

import io.farfrontier.palemirror.frontier.v3.model.ActorKind;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded inactive-body evidence; canonical actors and rosters remain domain-owned. */
final class FrontierV3AmbientCarrierLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_ambient_carriers";
    private static final int FORMAT = 11, MAX_CARRIERS = 4_096;
    private final Map<SubjectId, Carrier> carriers;
    private final java.util.Set<SubjectId> dirtyActors = new java.util.TreeSet<>();
    private FrontierV3JournalStore journal;
    private java.nio.file.Path journalPath;
    private void markDirty(SubjectId actor) { dirtyActors.add(actor); setDirty(); }
    private final Map<SubjectId, FrontierV3ActorAdoption> pendingAdoptions = new LinkedHashMap<>();
    /** Separately bounded lifetime markers; successful save never makes an actor fresh again. */
    private final Map<SubjectId, FrontierV3ActorFirstAdmission> firstAdmissions = new LinkedHashMap<>();
    private final Map<SubjectId, FrontierV3SceneDeparture> departures = new LinkedHashMap<>();
    /** Only an exact completed entity-region write plus storage sync may add this marker. */
    private final java.util.Set<SubjectId> savedDepartures = new java.util.HashSet<>();
    /** Only receipts recorded while the pre-load return fence exists qualify for no-load recovery. */
    private final java.util.Set<SubjectId> readFencedDepartures = new java.util.HashSet<>();
    /** A stored body was requested for vanilla loading after this departure. */
    private final java.util.Set<SubjectId> returnReads = new java.util.HashSet<>();
    private final Map<SubjectId, FrontierV3SceneDeparture> departureConflicts = new LinkedHashMap<>();
    private final Map<SubjectId, FrontierV3AmbientDeparture> ambientDepartures = new LinkedHashMap<>();
    private final java.util.Set<SubjectId> savedAmbientDepartures = new java.util.HashSet<>();
    private final Map<SubjectId, FrontierV3AmbientDeparture> ambientDepartureConflicts = new LinkedHashMap<>();
    private final Map<SubjectId, FrontierV3ActorBodyDeparture> bodyDepartures = new LinkedHashMap<>();
    private final Map<SubjectId, FrontierV3ActorBodyDeparture> bodyDepartureConflicts = new LinkedHashMap<>();
    private final Map<SubjectId, Long> savedBodyDepartures = new LinkedHashMap<>();
    /** High-water marks survive receipt withdrawal; loading a body is not a new physical incarnation. */
    private final Map<SubjectId, Long> bodyResidences = new LinkedHashMap<>();
    private FrontierV3AmbientCarrierLedger() { this(new LinkedHashMap<>()); }
    private FrontierV3AmbientCarrierLedger(Map<SubjectId, Carrier> carriers) { this.carriers = carriers; }
    static FrontierV3AmbientCarrierLedger emptyForTest() { return new FrontierV3AmbientCarrierLedger(); }
    long beginBodyResidence(FrontierV3ActorCarrierComposition.Declaration live) {
        var first = firstAdmissions.get(live.actorId());
        if (live.representation() != FrontierV3ActorCarrierComposition.Representation.LIVE_BODY
                || first == null || !first.identity().matches(live) || hasBodyDeparture(live.actorId())
                || hasDepartureConflict(live.actorId())
                || !bodyResidences.containsKey(live.actorId()) && bodyResidences.size() >= MAX_CARRIERS)
            throw new IllegalArgumentException("body residence requires an exact admitted lifetime with no outstanding departure");
        long next = Math.addExact(bodyResidences.getOrDefault(live.actorId(), 0L), 1L);
        bodyResidences.put(live.actorId(), next); markDirty(live.actorId()); return next;
    }
    boolean currentBodyResidence(SubjectId actor, long generation) {
        return generation > 0L && java.util.Objects.equals(bodyResidences.get(actor), generation);
    }
    boolean knownBodyResidence(SubjectId actor, long generation) {
        return generation > 0L && bodyResidences.containsKey(actor) && generation <= bodyResidences.get(actor);
    }
    java.util.Optional<FrontierV3ActorBodyDeparture> bodyDeparture(SubjectId actor) {
        return java.util.Optional.ofNullable(bodyDepartures.get(actor));
    }
    boolean hasBodyDeparture(SubjectId actor) {
        return bodyDepartures.containsKey(actor) || departures.containsKey(actor) || ambientDepartures.containsKey(actor);
    }
    List<FrontierV3ActorBodyDeparture> bodyDepartures() {
        return bodyDepartures.values().stream().sorted(Comparator.comparing(value -> value.identity().actorId())).toList();
    }
    boolean recordBodyDeparture(FrontierV3ActorBodyDeparture receipt) {
        var actor = receipt.identity().actorId();
        if (!currentBodyResidence(actor, receipt.residenceGeneration())) return false;
        var previous = bodyDepartures.get(actor);
        if (previous != null) {
            if (!previous.equals(receipt) && bodyDepartureConflicts.putIfAbsent(actor, receipt) == null) {
                savedBodyDepartures.remove(actor); markDirty(actor);
            }
            // A repeated callback cannot turn a returned/read body into a new departure.
            return previous.equals(receipt) && !hasDepartureConflict(actor);
        }
        if (bodyDepartures.size() >= MAX_CARRIERS || bodyDepartures.values().stream().anyMatch(value ->
                value.identity().entityId().equals(receipt.identity().entityId()))) return false;
        bodyDepartures.put(actor, receipt); markDirty(actor); return true;
    }
    boolean savedBodyDeparture(FrontierV3ActorBodyDeparture receipt) {
        var actor = receipt.identity().actorId();
        return receipt.equals(bodyDepartures.get(actor)) && java.util.Objects.equals(savedBodyDepartures.get(actor), receipt.residenceGeneration())
                && !returnReads.contains(actor) && !hasDepartureConflict(actor);
    }
    boolean confirmSavedBodyDeparture(FrontierV3ActorBodyDeparture receipt) {
        var actor = receipt.identity().actorId();
        if (!receipt.equals(bodyDepartures.get(actor)) || returnReads.contains(actor) || hasDepartureConflict(actor)) return false;
        if (savedBodyDepartures.putIfAbsent(actor, receipt.residenceGeneration()) == null) markDirty(actor);
        return true;
    }

    /** One durable publication for one positively saved batch, never one whole-ledger write per body. */
    void publishSavedDepartures(List<FrontierV3ActorBodyDeparture> bodies,
                               List<FrontierV3SceneDeparture> scenes,
                               List<FrontierV3AmbientDeparture> ambient, Runnable persist) {
        java.util.Objects.requireNonNull(persist, "departure publication");
        if (bodies.size() > MAX_CARRIERS || scenes.size() > MAX_CARRIERS || ambient.size() > MAX_CARRIERS)
            throw new IllegalArgumentException("departure publication exceeds receipt capacity");
        if (bodies.isEmpty() && scenes.isEmpty() && ambient.isEmpty()) return;
        var previousBodies = new java.util.HashMap<>(savedBodyDepartures);
        var previousScenes = new java.util.HashSet<>(savedDepartures);
        var previousAmbient = new java.util.HashSet<>(savedAmbientDepartures);
        try {
            bodies.forEach(this::confirmSavedBodyDeparture);
            scenes.forEach(this::confirmSavedDeparture);
            ambient.forEach(this::confirmSavedAmbientDeparture);
            if (!previousBodies.equals(savedBodyDepartures) || !previousScenes.equals(savedDepartures)
                    || !previousAmbient.equals(savedAmbientDepartures)) persist.run();
        } catch (RuntimeException | Error failedPublication) {
            // No server-thread consumer may see a new saved proof after its publication failed.
            // Disk may already contain the positive batch; withholding the in-memory permission
            // is conservative until a later successful publication or recovery re-proves it.
            savedBodyDepartures.clear(); savedBodyDepartures.putAll(previousBodies);
            savedDepartures.clear(); savedDepartures.addAll(previousScenes);
            savedAmbientDepartures.clear(); savedAmbientDepartures.addAll(previousAmbient);
            bodies.forEach(value -> markDirty(value.identity().actorId()));
            scenes.forEach(value -> markDirty(value.carrier().identity().actorId()));
            ambient.forEach(value -> markDirty(value.carrier().identity().actorId()));
            throw failedPublication;
        }
    }
    boolean resumeBodyDeparture(FrontierV3ActorBodyDeparture receipt) {
        var actor = receipt.identity().actorId();
        if (!receipt.equals(bodyDepartures.get(actor)) || hasDepartureConflict(actor)) return false;
        var fenced = carriers.get(actor);
        if (fenced != null && !fenced.identity().equals(receipt.identity())) return false;
        carriers.remove(actor); forgetDeparture(actor); return true;
    }
    boolean markBodyReturnRead(FrontierV3ActorBodyDeparture receipt) {
        var actor = receipt.identity().actorId();
        if (!receipt.equals(bodyDepartures.get(actor))) return false;
        if (returnReads.add(actor)) { savedBodyDepartures.remove(actor); markDirty(actor); }
        return true;
    }
    java.util.Optional<FrontierV3ActorFirstAdmission> firstAdmission(SubjectId actor) {
        return java.util.Optional.ofNullable(firstAdmissions.get(actor));
    }
    List<FrontierV3ActorFirstAdmission> firstAdmissions() {
        return firstAdmissions.values().stream().sorted(Comparator.comparing(value -> value.identity().actorId())).toList();
    }
    /** Physical history is independent of execution or scene ownership. */
    boolean permitsRecordedOwner(FrontierV3ActorOwnerBinding observed) {
        var adoption = pendingAdoptions.get(observed.declaration().actorId());
        if (adoption != null && !adoption.matches(observed)) return false;
        var first = firstAdmissions.get(observed.declaration().actorId());
        if (first == null) return false;
        if (!first.identity().matches(observed.declaration())
                || first.phase() == FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED
                || first.phase() == FrontierV3ActorFirstAdmission.Phase.PROVEN_ABSENT) return false;
        if (first.phase() == FrontierV3ActorFirstAdmission.Phase.ESTABLISHED) return true;
        return observed.equals(first.attempt().orElseThrow());
    }
    /** Called only by explicit initialization/birth issuance, never an absence-based admission fallback. */
    boolean registerFirstAdmission(FrontierV3ActorFirstAdmission permit) {
        return registerFirstAdmissions(List.of(permit));
    }
    /** Fresh bootstrap and a multi-birth WAL transaction publish no partial permission set. */
    boolean registerFirstAdmissions(List<FrontierV3ActorFirstAdmission> permits) {
        java.util.Objects.requireNonNull(permits, "first-admission permissions");
        if (permits.size() > MAX_CARRIERS) return false;
        var additions = new LinkedHashMap<SubjectId, FrontierV3ActorFirstAdmission>();
        var actors = new java.util.HashSet<SubjectId>();
        var entities = new java.util.HashSet<java.util.UUID>();
        for (FrontierV3ActorFirstAdmission permit : permits) {
            if (permit == null) return false;
            var actor = permit.identity().actorId();
            var id = permit.identity().entityId();
            if (!actors.add(actor) || !entities.add(id)
                    || permit.phase() != FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED || permit.attemptGeneration() != 0L
                    || carriers.containsKey(actor) || pendingAdoptions.containsKey(actor)
                    || departures.containsKey(actor) || ambientDepartures.containsKey(actor) || hasDepartureConflict(actor)) return false;
            var previous = firstAdmissions.get(actor);
            if (previous != null) {
                if (!previous.equals(permit)) return false;
                continue;
            }
            if (firstAdmissions.size() + additions.size() >= MAX_CARRIERS
                    || firstAdmissions.values().stream().anyMatch(value -> value.identity().entityId().equals(id))
                    || carriers.values().stream().anyMatch(value -> value.identity().entityId().equals(id))
                    || pendingAdoptions.values().stream().anyMatch(value -> value.admitted().entityId().equals(id))
                    ) return false;
            additions.put(actor, permit);
        }
        if (!additions.isEmpty()) {
            firstAdmissions.putAll(additions);
            additions.keySet().forEach(this::markDirty);
        }
        return true;
    }
    boolean beginFirstAdmission(FrontierV3ActorOwnerBinding target) {
        var actor = target.declaration().actorId(); var permit = firstAdmissions.get(actor);
        if (permit == null || permit.phase() != FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED
                    && permit.phase() != FrontierV3ActorFirstAdmission.Phase.PROVEN_ABSENT
                || inventorySize() >= MAX_CARRIERS
                || carriers.containsKey(actor) || pendingAdoptions.containsKey(actor)
                || departures.containsKey(actor) || ambientDepartures.containsKey(actor) || hasDepartureConflict(actor)) return false;
        final FrontierV3ActorFirstAdmission pending;
        try { pending = permit.begin(target); } catch (IllegalArgumentException invalid) { return false; }
        firstAdmissions.put(actor, pending); markDirty(actor); return true;
    }
    /** Offline publisher only; absence proof and this transition must share one durable publication. */
    boolean rearmFirstAdmissionAfterAbsence(FrontierV3ActorFirstAdmission expected, String receipt) {
        var actor = expected.identity().actorId();
        if (expected.phase() != FrontierV3ActorFirstAdmission.Phase.PENDING
                || !expected.equals(firstAdmissions.get(actor)) || receipt == null
                || carriers.containsKey(actor) || pendingAdoptions.containsKey(actor)
                || departures.containsKey(actor) || ambientDepartures.containsKey(actor) || hasDepartureConflict(actor)) return false;
        firstAdmissions.put(actor, expected.rearmAfterProvenAbsence(expected.attempt().orElseThrow(), receipt));
        markDirty(actor); return true;
    }
    /** Startup reconciliation only: canonical birth was not published and no creation ever began. */
    boolean retireUnbornPermission(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState recovered,
                                  FrontierV3ActorFirstAdmission expected) {
        return retireUnbornPermissions(recovered, List.of(expected));
    }
    /** Validate every candidate before removing any permission from the shared SavedData image. */
    boolean retireUnbornPermissions(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState recovered,
                                    List<FrontierV3ActorFirstAdmission> expected) {
        java.util.Objects.requireNonNull(recovered, "recovered actor state");
        java.util.Objects.requireNonNull(expected, "unpublished birth permissions");
        var actors = new java.util.HashSet<SubjectId>();
        for (FrontierV3ActorFirstAdmission permission : expected) {
            if (permission == null || !actors.add(permission.identity().actorId())
                    || !canRetireUnbornPermission(recovered, permission)) return false;
        }
        if (actors.isEmpty()) return true;
        actors.forEach(firstAdmissions::remove);
        actors.forEach(this::markDirty);
        return true;
    }
    private boolean canRetireUnbornPermission(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState recovered,
                                              FrontierV3ActorFirstAdmission expected) {
        var actor = expected.identity().actorId();
        if (expected.phase() != FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED
                || !expected.equals(firstAdmissions.get(actor)) || recovered.actorLocations().containsKey(actor)
                || !expected.identity().entityId().equals(io.farfrontier.palemirror.frontier.v3.model.SceneLease
                    .deterministicEntityId(recovered.bootstrap().worldId(), actor))
                || carriers.containsKey(actor) || pendingAdoptions.containsKey(actor)
                || departures.containsKey(actor) || ambientDepartures.containsKey(actor) || hasDepartureConflict(actor)) return false;
        return true;
    }
    boolean rejectUncreatedFirstAdmission(FrontierV3ActorFirstAdmission expected) {
        var actor = expected.identity().actorId();
        if (expected.phase() != FrontierV3ActorFirstAdmission.Phase.PENDING || !expected.equals(firstAdmissions.get(actor))
                || carriers.containsKey(actor) || pendingAdoptions.containsKey(actor)
                || departures.containsKey(actor) || ambientDepartures.containsKey(actor) || hasDepartureConflict(actor)) return false;
        var next = expected.rejectedBeforeCreation(expected.attempt().orElseThrow());
        if (!next.equals(expected)) { firstAdmissions.put(actor, next); markDirty(actor); }
        return true;
    }
    boolean acknowledgeFirstAdmission(FrontierV3ActorFirstAdmission expected, FrontierV3ActorOwnerBinding saved) {
        var actor = expected.identity().actorId();
        if (expected.phase() != FrontierV3ActorFirstAdmission.Phase.PENDING || !expected.equals(firstAdmissions.get(actor))
                || !expected.attempt().orElseThrow().equals(saved)
                || carriers.containsKey(actor) || hasDepartureConflict(actor)) return false;
        firstAdmissions.put(actor, expected.saved(saved)); markDirty(actor); return true;
    }
    /** Mandatory durable boundaries append only exact changed actors, never a whole-world image. */
    @Override public void save(java.io.File file, HolderLookup.Provider registries) {
        try {
            if (journal != null) journal.checkHealthy();
            if (!isDirty()) {
                if (journal != null) journal.append(Map.of()); // A synchronous boundary includes earlier queued writes.
                return;
            }
            var path = file.toPath().toAbsolutePath().normalize();
            if (journal == null) { journal = FrontierV3JournalStore.open(path); journalPath = path; }
            else if (!journalPath.equals(path)) throw new IllegalArgumentException("carrier ledger changed storage target");
            var updates = new java.util.TreeMap<String, byte[]>();
            for (var actor : dirtyActors) {
                var row = actorImage(actor);
                updates.put(actor.value(), row == null ? null : FrontierV3CarrierJournalImage.encode(row));
            }
            journal.append(updates);
            dirtyActors.clear(); setDirty(false);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("unable to persist carrier journal", failure);
        }
    }
    java.util.concurrent.CompletableFuture<Long> persistAsync(ServerLevel level, WorldId worldId) {
        if (get(level, worldId) != this) throw new IllegalArgumentException("foreign actor carrier ledger");
        try {
            var path = storageFile(level, worldId).toAbsolutePath().normalize();
            if (journal == null) { journal = FrontierV3JournalStore.open(path); journalPath = path; }
            else if (!journalPath.equals(path)) throw new IllegalArgumentException("carrier ledger changed storage target");
            var updates = new java.util.TreeMap<String, byte[]>();
            for (var actor : dirtyActors) {
                var row = actorImage(actor);
                updates.put(actor.value(), row == null ? null : FrontierV3CarrierJournalImage.encode(row));
            }
            var receipt = journal.appendAsync(updates);
            dirtyActors.clear(); setDirty(false); // Submitted is not durable: the caller retains the exact receipt.
            return receipt;
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("unable to admit carrier journal write", failure);
        }
    }
    static FrontierV3AmbientCarrierLedger readFile(java.nio.file.Path path, HolderLookup.Provider registries) {
        try {
            var store = FrontierV3JournalStore.open(path);
            var ledger = load(FrontierV3CarrierJournalImage.merge(store.image()), registries);
            ledger.journal = store; ledger.journalPath = path.toAbsolutePath().normalize();
            return ledger;
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("unable to recover carrier journal", failure);
        }
    }
    void finishCheckpoints() {
        if (journal == null) return;
        try { journal.append(Map.of()); journal.awaitCheckpoint(); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException("carrier checkpoint shutdown failed", failure); }
    }
    String journalDiagnostic() {
        if (journal == null) return "{\"attached\":false}";
        var pressure = journal.pressure();
        return "{\"attached\":true,\"durableSequence\":" + pressure.durableSequence()
                + ",\"checkpointSequence\":" + pressure.checkpointSequence()
                + ",\"queuedWrites\":" + pressure.queuedWrites() + ",\"queuedBytes\":" + pressure.queuedBytes()
                + ",\"retainedBytes\":" + pressure.retainedBytes() + ",\"forcedGroups\":" + pressure.forcedGroups()
                + ",\"appendedBytes\":" + pressure.appendedBytes() + "}";
    }
    static FrontierV3AmbientCarrierLedger get(ServerLevel level, WorldId worldId) {
        var path = storageFile(level, worldId);
        var ledger = level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3AmbientCarrierLedger::new,
                (tag, registries) -> readFile(path, registries), DataFixTypes.SAVED_DATA_COMMAND_STORAGE), fileName(worldId));
        try { if (ledger.journal != null) ledger.journal.checkHealthy(); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException("carrier journal writer failed", failure); }
        return ledger;
    }
    private CompoundTag actorImage(SubjectId actor) {
        var row = new CompoundTag(); row.putInt("format", FORMAT); row.putString("actor", actor.value());
        FrontierV3CarrierJournalImage.list(row, "bodyResidences", bodyResidences.containsKey(actor)
                ? FrontierV3CarrierJournalImage.marker(actor, "generation", bodyResidences.get(actor)) : null);
        FrontierV3CarrierJournalImage.list(row, "bodyDepartures", bodyDepartures.containsKey(actor) ? bodyDepartures.get(actor).save() : null);
        FrontierV3CarrierJournalImage.list(row, "bodyDepartureConflicts", bodyDepartureConflicts.containsKey(actor) ? bodyDepartureConflicts.get(actor).save() : null);
        FrontierV3CarrierJournalImage.list(row, "savedBodyDepartures", savedBodyDepartures.containsKey(actor)
                ? FrontierV3CarrierJournalImage.marker(actor, "residenceGeneration", savedBodyDepartures.get(actor)) : null);
        FrontierV3CarrierJournalImage.list(row, "carriers", carriers.containsKey(actor) ? carriers.get(actor).save() : null);
        FrontierV3CarrierJournalImage.list(row, "pendingAdoptions", pendingAdoptions.containsKey(actor) ? pendingAdoptions.get(actor).save() : null);
        FrontierV3CarrierJournalImage.list(row, "firstAdmissions", firstAdmissions.containsKey(actor) ? firstAdmissions.get(actor).save() : null);
        FrontierV3CarrierJournalImage.list(row, "departures", departures.containsKey(actor) ? departures.get(actor).save() : null);
        FrontierV3CarrierJournalImage.list(row, "savedDepartures", savedDepartures.contains(actor) ? FrontierV3CarrierJournalImage.marker(actor) : null);
        FrontierV3CarrierJournalImage.list(row, "readFencedDepartures", readFencedDepartures.contains(actor) ? FrontierV3CarrierJournalImage.marker(actor) : null);
        FrontierV3CarrierJournalImage.list(row, "returnReads", returnReads.contains(actor) ? FrontierV3CarrierJournalImage.marker(actor) : null);
        FrontierV3CarrierJournalImage.list(row, "departureConflicts", departureConflicts.containsKey(actor) ? departureConflicts.get(actor).save() : null);
        FrontierV3CarrierJournalImage.list(row, "ambientDepartures", ambientDepartures.containsKey(actor) ? ambientDepartures.get(actor).save() : null);
        FrontierV3CarrierJournalImage.list(row, "savedAmbientDepartures", savedAmbientDepartures.contains(actor) ? FrontierV3CarrierJournalImage.marker(actor) : null);
        FrontierV3CarrierJournalImage.list(row, "ambientDepartureConflicts", ambientDepartureConflicts.containsKey(actor) ? ambientDepartureConflicts.get(actor).save() : null);
        return FrontierV3CarrierJournalImage.hasRows(row) ? row : null;
    }
    private static String fileName(WorldId worldId) {
        return NAME + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(worldId.value().getBytes(StandardCharsets.UTF_8));
    }
    void persist(ServerLevel level, WorldId worldId) {
        if (get(level, worldId) != this) throw new IllegalArgumentException("foreign actor carrier ledger");
        save(storageFile(level, worldId).toFile(), level.registryAccess());
    }
    static java.nio.file.Path storageFile(ServerLevel level, WorldId worldId) {
        var dimension = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),
                level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
        return dimension.resolve("data").resolve(fileName(worldId) + ".dat");
    }
    Reconciliation reconciliation(FrontierV3ActorCarrierComposition.Declaration live) {
        if (hasDepartureConflict(live.actorId())) return Reconciliation.DEPARTURE_CONFLICT;
        Carrier carrier = carriers.get(live.actorId());
        if (carrier == null) return Reconciliation.NO_FENCED_CARRIER;
        if (!carrier.identity.entityId().equals(live.entityId())) return Reconciliation.UUID_MISMATCH;
        if (carrier.identity.kind() != live.kind()) return Reconciliation.KIND_MISMATCH;
        if (live.representation() != FrontierV3ActorCarrierComposition.Representation.LIVE_BODY) return Reconciliation.REPRESENTATION_MISMATCH;
        return live.epoch() <= carrier.identity().epoch() ? Reconciliation.STALE_REVISION : Reconciliation.READY;
    }
    /** Reject a live exact body plus a retained inactive carrier before either custody changes. */
    Reconciliation reconciliation(FrontierV3ActorCarrierComposition.Declaration live, boolean liveBodyPresent) {
        Reconciliation result = reconciliation(live);
        return liveBodyPresent && result != Reconciliation.NO_FENCED_CARRIER
                ? Reconciliation.CONCURRENT_CUSTODY : result;
    }
    boolean canFence(FrontierV3ActorCarrierComposition.Declaration inactive, long physicalRevision, long ambientRevision) {
        if (inactive.representation() != FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER || physicalRevision < 1L || ambientRevision < 0L
                || hasDepartureConflict(inactive.actorId()) || carriers.containsKey(inactive.actorId())) return false;
        var adoption = pendingAdoptions.get(inactive.actorId());
        var first = firstAdmissions.get(inactive.actorId());
        if (first != null && (!first.identity().matches(inactive)
                || first.phase() == FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED
                || first.phase() == FrontierV3ActorFirstAdmission.Phase.PROVEN_ABSENT
                || first.phase() == FrontierV3ActorFirstAdmission.Phase.PENDING
                    && !first.attempt().orElseThrow().declaration().inactiveCarrier().equals(inactive))) return false;
        // The caller has validated the current canonical owner. A lawful same-body
        // successor can have another owner/revision, but cannot change its physical
        // generation, UUID or kind. This fence supersedes only that exact body.
        if (adoption != null && (!adoption.admitted().entityId().equals(inactive.entityId())
                || adoption.admitted().kind() != inactive.kind()
                || adoption.admitted().epoch() != inactive.epoch())) return false;
        if (adoption == null
                && (first == null || first.phase() != FrontierV3ActorFirstAdmission.Phase.PENDING)
                && inventorySize() >= MAX_CARRIERS) return false;
        return carriers.values().stream().noneMatch(value -> value.identity.entityId().equals(inactive.entityId()))
                && pendingAdoptions.values().stream().noneMatch(value -> !value.admitted().actorId().equals(inactive.actorId())
                    && value.admitted().entityId().equals(inactive.entityId()));
    }
    boolean fence(FrontierV3ActorCarrierComposition.Declaration inactive, long physicalRevision, long ambientRevision) {
        if (hasDepartureConflict(inactive.actorId())) return false;
        Carrier next = new Carrier(inactive, physicalRevision, ambientRevision);
        Carrier previous = carriers.get(inactive.actorId());
        if (previous != null) return previous.equals(next);
        if (!canFence(inactive, physicalRevision, ambientRevision)) return false;
        carriers.put(inactive.actorId(), next);
        var first = firstAdmissions.get(inactive.actorId());
        if (first != null && first.phase() == FrontierV3ActorFirstAdmission.Phase.PENDING)
            firstAdmissions.put(inactive.actorId(), first.fencedAsInactive(inactive));
        pendingAdoptions.remove(inactive.actorId());
        markDirty(inactive.actorId()); return true;
    }
    boolean matchesCarrier(FrontierV3ActorCarrierComposition.Declaration inactive, long physicalRevision, long ambientRevision) {
        return new Carrier(inactive, physicalRevision, ambientRevision).equals(carriers.get(inactive.actorId()));
    }
    boolean hasCarrier(SubjectId actorId) { return carriers.containsKey(actorId); }
    java.util.Optional<Carrier> inactiveCarrier(SubjectId actorId) { return java.util.Optional.ofNullable(carriers.get(actorId)); }
    /** Historical body cleanup uses the fence's own epoch, not a newer ambient lease revision. */
    boolean fencesBody(FrontierV3ActorCarrierComposition.Declaration live) {
        var carrier = carriers.get(live.actorId());
        return live.representation() == FrontierV3ActorCarrierComposition.Representation.LIVE_BODY
                && !hasDepartureConflict(live.actorId()) && carrier != null
                && carrier.identity().equals(live.inactiveCarrier());
    }
    /** Validate the complete survivor set before installing any release fence. */
    boolean canFenceAll(List<Carrier> requested) {
        var actors = new java.util.HashSet<SubjectId>();
        var entities = new java.util.HashSet<java.util.UUID>();
        int projectedSize = inventorySize();
        for (Carrier value : requested) {
            var actor = value.identity().actorId();
            if (!actors.add(actor) || !entities.add(value.identity().entityId()) || hasDepartureConflict(actor)) return false;
            if (matchesCarrier(value.identity(), value.physicalRevision(), value.ambientRevision())) continue;
            if (!canFence(value.identity(), value.physicalRevision(), value.ambientRevision())) return false;
            projectedSize++;
            if (pendingAdoptions.containsKey(actor)) projectedSize--;
            var first = firstAdmissions.get(actor);
            if (first != null && first.phase() == FrontierV3ActorFirstAdmission.Phase.PENDING) projectedSize--;
        }
        return projectedSize <= MAX_CARRIERS;
    }

    boolean fenceAll(List<Carrier> requested) {
        if (!canFenceAll(requested)) return false;
        // Server-thread-owned map: the preflight and mutation cannot interleave.
        // Capacity-releasing transfers go first so a valid complete set cannot
        // transiently exceed the inventory bound because of member order.
        var ordered = requested.stream().sorted(java.util.Comparator.comparingInt(value -> {
            var actor = value.identity().actorId();
            return (pendingAdoptions.containsKey(actor) ? -1 : 0)
                    + (firstAdmissions.containsKey(actor)
                        && firstAdmissions.get(actor).phase() == FrontierV3ActorFirstAdmission.Phase.PENDING ? -1 : 0);
        })).toList();
        for (Carrier value : ordered) {
            if (!fence(value.identity(), value.physicalRevision(), value.ambientRevision()))
                throw new IllegalStateException("validated scene release fence changed during publication");
        }
        return true;
    }
    /** A receipt is evidence only; recording it does not install an inactive carrier. */
    boolean recordDeparture(FrontierV3SceneDeparture departure) {
        SubjectId actor = departure.carrier().identity().actorId();
        if (ambientDepartures.containsKey(actor)) return false;
        var previous = departures.get(actor);
        if (previous != null) {
            if (!previous.equals(departure) && departureConflicts.putIfAbsent(actor, departure) == null) markDirty(actor);
            return previous.equals(departure) && !hasDepartureConflict(actor);
        }
        if (departures.size() >= MAX_CARRIERS) return false;
        if (departures.values().stream().anyMatch(value -> value.carrier().identity().entityId().equals(departure.carrier().identity().entityId()))) return false;
        departures.put(actor, departure);
        readFencedDepartures.add(actor);
        markDirty(actor);
        return true;
    }
    java.util.Optional<FrontierV3SceneDeparture> departure(SubjectId actor) {
        return java.util.Optional.ofNullable(departures.get(actor));
    }
    List<FrontierV3SceneDeparture> departures() {
        return departures.values().stream().sorted(Comparator.comparing(value -> value.carrier().identity().actorId())).toList();
    }
    boolean savedDeparture(FrontierV3SceneDeparture expected) {
        var actor = expected.carrier().identity().actorId();
        var physical = bodyDepartures.get(actor);
        if (physical != null) return physical.matches(expected) && savedBodyDeparture(physical);
        return expected.equals(departures.get(actor)) && savedDepartures.contains(actor)
                && !returnReads.contains(actor) && !hasDepartureConflict(actor);
    }
    boolean noLoadRecoverableDeparture(FrontierV3SceneDeparture expected) {
        return savedDeparture(expected) && (bodyDeparture(expected.carrier().identity().actorId()).filter(value -> value.matches(expected)).isPresent()
                || readFencedDepartures.contains(expected.carrier().identity().actorId()));
    }
    /**
     * A read-fenced unload may enter an independent no-load disk proof even if the ordinary
     * write callback did not publish its saved marker. This is not a release witness: the
     * caller must prove the exact unique serialized body and fresh write/read fences before
     * publishing the marker and releasing any canonical custody.
     */
    boolean noLoadProofCandidate(FrontierV3SceneDeparture expected) {
        var actor = expected.carrier().identity().actorId();
        var physical = bodyDepartures.get(actor);
        if (physical != null) return physical.matches(expected) && !returnReads.contains(actor) && !hasDepartureConflict(actor);
        return expected.equals(departures.get(actor)) && readFencedDepartures.contains(actor)
                && !returnReads.contains(actor) && !hasDepartureConflict(actor);
    }
    boolean confirmSavedDeparture(FrontierV3SceneDeparture expected) {
        var actor = expected.carrier().identity().actorId();
        var physical = bodyDepartures.get(actor);
        if (physical != null) return physical.matches(expected) && confirmSavedBodyDeparture(physical);
        if (!expected.equals(departures.get(actor)) || returnReads.contains(actor) || hasDepartureConflict(actor)) return false;
        if (savedDepartures.add(actor)) markDirty(actor);
        return true;
    }
    boolean markReturnRead(FrontierV3SceneDeparture receipt) {
        var actor = receipt.carrier().identity().actorId();
        if (!receipt.equals(departures.get(actor)) && bodyDeparture(actor).filter(value -> value.matches(receipt)).isEmpty()) return false;
        if (returnReads.add(actor)) {
            savedBodyDepartures.remove(actor); markDirty(actor);
        }
        return true;
    }
    boolean returnRead(SubjectId actor) { return returnReads.contains(actor); }
    boolean recordAmbientDeparture(FrontierV3AmbientDeparture receipt) {
        SubjectId actor = receipt.carrier().identity().actorId();
        var previous = ambientDepartures.get(actor);
        if (previous != null) {
            if (!previous.equals(receipt) && ambientDepartureConflicts.putIfAbsent(actor, receipt) == null) markDirty(actor);
            return previous.equals(receipt) && !hasDepartureConflict(actor);
        }
        if (ambientDepartures.size() >= MAX_CARRIERS || departures.containsKey(actor)
                || ambientDepartures.values().stream().anyMatch(value -> value.carrier().identity().entityId()
                    .equals(receipt.carrier().identity().entityId()))) return false;
        ambientDepartures.put(actor, receipt); markDirty(actor); return true;
    }
    java.util.Optional<FrontierV3AmbientDeparture> ambientDeparture(SubjectId actor) {
        return java.util.Optional.ofNullable(ambientDepartures.get(actor));
    }
    List<FrontierV3AmbientDeparture> ambientDepartures() { return List.copyOf(ambientDepartures.values()); }
    boolean savedAmbientDeparture(FrontierV3AmbientDeparture expected) {
        var actor = expected.carrier().identity().actorId();
        var physical = bodyDepartures.get(actor);
        if (physical != null) return physical.matches(expected) && savedBodyDeparture(physical);
        return expected.equals(ambientDepartures.get(actor)) && savedAmbientDepartures.contains(actor)
                && !returnReads.contains(actor) && !hasDepartureConflict(actor);
    }
    boolean confirmSavedAmbientDeparture(FrontierV3AmbientDeparture expected) {
        var actor = expected.carrier().identity().actorId();
        var physical = bodyDepartures.get(actor);
        if (physical != null) return physical.matches(expected) && confirmSavedBodyDeparture(physical);
        if (!expected.equals(ambientDepartures.get(actor)) || returnReads.contains(actor) || hasDepartureConflict(actor)) return false;
        if (savedAmbientDepartures.add(actor)) markDirty(actor);
        return true;
    }
    boolean resumeAmbientDeparture(FrontierV3AmbientDeparture receipt) {
        SubjectId actor = receipt.carrier().identity().actorId();
        var physical = bodyDepartures.get(actor);
        if (physical != null) return physical.matches(receipt) && resumeBodyDeparture(physical);
        if (hasDepartureConflict(actor) || !receipt.equals(ambientDepartures.get(actor))) return false;
        Carrier fenced = carriers.get(actor);
        if (fenced != null && !fenced.equals(receipt.carrier())) return false;
        carriers.remove(actor); forgetDeparture(actor); return true;
    }
    boolean markAmbientReturnRead(FrontierV3AmbientDeparture receipt) {
        var actor = receipt.carrier().identity().actorId();
        var physical = bodyDepartures.get(actor);
        if (physical != null) return physical.matches(receipt) && markBodyReturnRead(physical);
        if (!receipt.equals(ambientDepartures.get(actor))) return false;
        if (returnReads.add(actor)) { savedAmbientDepartures.remove(actor); markDirty(actor); }
        return true;
    }
    boolean hasDepartureConflict(SubjectId actor) {
        return departureConflicts.containsKey(actor) || ambientDepartureConflicts.containsKey(actor)
                || bodyDepartureConflicts.containsKey(actor);
    }
    boolean retireDeadActor(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState state, SubjectId actor) {
        var location = state.actorLocations().get(actor);
        if (location == null || location.condition().status() != io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus.DEAD
                || state.fencedRecovery().current().containsKey(
                        ActorBodyId.recoveryBindingId(actor))) return false;
        if (carriers.remove(actor) != null) markDirty(actor);
        if (pendingAdoptions.remove(actor) != null) markDirty(actor);
        if (firstAdmissions.remove(actor) != null) markDirty(actor);
        if (bodyResidences.remove(actor) != null) markDirty(actor);
        forgetDeparture(actor);
        return true;
    }
    /** Explicit resolved cleanup; mere reappearance must not discard contradictory evidence. */
    void forgetDeparture(SubjectId actor) {
        boolean changed = departures.remove(actor) != null;
        changed |= bodyDepartures.remove(actor) != null;
        changed |= bodyDepartureConflicts.remove(actor) != null;
        changed |= savedBodyDepartures.remove(actor) != null;
        changed |= savedDepartures.remove(actor);
        changed |= readFencedDepartures.remove(actor);
        changed |= returnReads.remove(actor);
        changed |= departureConflicts.remove(actor) != null;
        changed |= ambientDepartures.remove(actor) != null;
        changed |= savedAmbientDepartures.remove(actor);
        changed |= ambientDepartureConflicts.remove(actor) != null;
        if (changed) markDirty(actor);
    }
    /** Withdraw only this exact provisional fence when the same scene body returns before release. */
    boolean resumeDeparture(FrontierV3SceneDeparture receipt) {
        SubjectId actor = receipt.carrier().identity().actorId();
        var physical = bodyDepartures.get(actor);
        if (physical != null) return physical.matches(receipt) && resumeBodyDeparture(physical);
        if (hasDepartureConflict(actor) || !receipt.equals(departures.get(actor))) return false;
        Carrier fenced = carriers.get(actor);
        if (fenced != null && !fenced.equals(receipt.carrier())) return false;
        carriers.remove(actor);
        forgetDeparture(actor);
        return true;
    }
    long reconstructionEpoch(SubjectId actorId) { Carrier carrier = carriers.get(actorId); if (carrier == null) throw new IllegalStateException("missing v3 ambient carrier"); return Math.addExact(carrier.identity.epoch(), 1L); }
    boolean adopt(FrontierV3ActorOwnerBinding binding) {
        var live = binding.declaration();
        var first = firstAdmissions.get(live.actorId());
        if (first != null && (first.phase() != FrontierV3ActorFirstAdmission.Phase.ESTABLISHED
                || !first.identity().matches(live))) return false;
        if (reconciliation(live) != Reconciliation.READY || pendingAdoptions.containsKey(live.actorId())
               ) return false;
        final FrontierV3ActorAdoption pending;
        try { pending = new FrontierV3ActorAdoption(carriers.get(live.actorId()), binding); }
        catch (IllegalArgumentException invalid) { return false; }
        // Changing representation is not confirmation of an entity-region save.
        // Retain both exact endpoints until the save observer or a superseding
        // physical fence resolves the transfer; this is not inactive custody.
        pendingAdoptions.put(live.actorId(), pending);
        carriers.remove(live.actorId()); forgetDeparture(live.actorId()); markDirty(live.actorId()); return true;
    }
    java.util.Optional<FrontierV3ActorAdoption> pendingAdoption(SubjectId actor) {
        return java.util.Optional.ofNullable(pendingAdoptions.get(actor));
    }
    /** Only a synchronous, explicit no-admission result may call this, never absence after restart. */
    boolean rejectUncreatedAdoption(FrontierV3ActorAdoption expected) {
        var actor = expected.admitted().actorId();
        if (!expected.equals(pendingAdoptions.get(actor)) || carriers.containsKey(actor)
                || hasDepartureConflict(actor) || departures.containsKey(actor) || ambientDepartures.containsKey(actor)) return false;
        carriers.put(actor, expected.predecessor()); pendingAdoptions.remove(actor); markDirty(actor); return true;
    }
    List<FrontierV3ActorAdoption> pendingAdoptions() {
        return pendingAdoptions.values().stream().sorted(Comparator.comparing(value -> value.admitted().actorId())).toList();
    }
    /** Caller must supply current successful entity-write + synchronization evidence. */
    boolean acknowledgeAdoption(FrontierV3ActorAdoption expected,
                                FrontierV3ActorOwnerBinding binding) {
        var saved = binding.declaration();
        if (!expected.matches(binding) || hasDepartureConflict(saved.actorId())
                || !expected.equals(pendingAdoptions.get(saved.actorId()))) return false;
        pendingAdoptions.remove(saved.actorId()); markDirty(saved.actorId()); return true;
    }
    private int inventorySize() {
        return carriers.size() + pendingAdoptions.size()
                + (int) firstAdmissions.values().stream().filter(value -> value.phase() == FrontierV3ActorFirstAdmission.Phase.PENDING).count();
    }
    int inactiveCount() { return carriers.size(); }
    List<FrontierDomainRelationships.CarrierEvidence> relationshipEvidence() {
        return carriers.values().stream().sorted(Comparator.comparing(value -> value.identity.actorId().value())).map(value ->
                new FrontierDomainRelationships.CarrierEvidence(value.identity.actorId(), "carrier:" + value.identity.entityId(), "carrier-revision:" + value.physicalRevision)).toList();
    }
    static FrontierV3AmbientCarrierLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("journalVersion")) throw new IllegalStateException("carrier recovery requires checkpoint AND journal; use readFile");
        int format = tag.getInt("format");
        if (tag.contains("pendingHandoffs") || format != FORMAT) throw new IllegalStateException("incompatible v3 ambient carrier ledger; UAE requires a fresh world");
        ListTag values = inventory(tag, "carriers");
        Map<SubjectId, Carrier> restored = new LinkedHashMap<>();
        for (Tag raw : values) {
            Carrier carrier = Carrier.load((CompoundTag) raw);
            if (restored.putIfAbsent(carrier.identity.actorId(), carrier) != null
                    || restored.values().stream().anyMatch(value -> !value.identity.actorId().equals(carrier.identity.actorId())
                    && value.identity.entityId().equals(carrier.identity.entityId()))) {
                throw new IllegalStateException("duplicate v3 ambient carrier evidence");
            }
        }
        ListTag departures = inventory(tag, "departures");
        var ledger = new FrontierV3AmbientCarrierLedger(restored);
        for (Tag raw : inventory(tag, "bodyResidences")) {
            var row = (CompoundTag) raw;
            if (!row.contains("actor", Tag.TAG_STRING) || !row.contains("generation", Tag.TAG_LONG)
                    || row.getLong("generation") < 1L
                    || ledger.bodyResidences.putIfAbsent(new SubjectId(row.getString("actor")), row.getLong("generation")) != null)
                throw new IllegalStateException("invalid body residence high-water mark");
        }
        for (Tag raw : inventory(tag, "bodyDepartures")) {
            var receipt = FrontierV3ActorBodyDeparture.load((CompoundTag) raw);
            if (ledger.bodyDepartures.containsKey(receipt.identity().actorId()) || !ledger.recordBodyDeparture(receipt))
                throw new IllegalStateException("duplicate actor body departure");
        }
        for (Tag raw : inventory(tag, "bodyDepartureConflicts")) {
            var receipt = FrontierV3ActorBodyDeparture.load((CompoundTag) raw);
            var actor = receipt.identity().actorId();
            if (!ledger.bodyDepartures.containsKey(actor) || ledger.bodyDepartures.get(actor).equals(receipt)
                    || ledger.bodyDepartureConflicts.putIfAbsent(actor, receipt) != null)
                throw new IllegalStateException("invalid actor body departure conflict");
        }
        for (Tag raw : inventory(tag, "savedBodyDepartures")) {
            var row = (CompoundTag) raw;
            if (!row.contains("actor", Tag.TAG_STRING) || !row.contains("residenceGeneration", Tag.TAG_LONG))
                throw new IllegalStateException("incomplete body save marker");
            var actor = new SubjectId(row.getString("actor"));
            if (!ledger.bodyDepartures.containsKey(actor) || ledger.hasDepartureConflict(actor)
                    || ledger.bodyDepartures.get(actor).residenceGeneration() != row.getLong("residenceGeneration")
                    || ledger.savedBodyDepartures.putIfAbsent(actor, row.getLong("residenceGeneration")) != null)
                throw new IllegalStateException("invalid body save marker");
        }
        // Earlier format5 files did not retain these transfers. Missing history
        // stays missing: it is never manufactured from a current body or lease.
        if (tag.contains("pendingAdoptions")) {
            for (Tag raw : inventory(tag, "pendingAdoptions")) {
                var pending = FrontierV3ActorAdoption.load((CompoundTag) raw);
                var identity = pending.admitted();
                if (ledger.carriers.containsKey(identity.actorId())
                        || ledger.carriers.values().stream().anyMatch(value -> value.identity().entityId().equals(identity.entityId()))
                        || ledger.pendingAdoptions.values().stream().anyMatch(value -> value.admitted().entityId().equals(identity.entityId()))
                        || ledger.pendingAdoptions.putIfAbsent(identity.actorId(), pending) != null
                        || ledger.carriers.size() + ledger.pendingAdoptions.size() > MAX_CARRIERS) {
                    throw new IllegalStateException("duplicate or overflowing actor adoption evidence");
                }
            }
        }
        if (tag.contains("firstAdmissions")) {
            for (Tag raw : inventory(tag, "firstAdmissions")) {
                var first = FrontierV3ActorFirstAdmission.load((CompoundTag) raw);
                var actor = first.identity().actorId();
                if (ledger.firstAdmissions.putIfAbsent(actor, first) != null
                        || ledger.inventorySize() > MAX_CARRIERS
                        || ledger.firstAdmissions.values().stream().anyMatch(value -> !value.identity().actorId().equals(actor)
                            && value.identity().entityId().equals(first.identity().entityId()))
                        || ledger.carriers.values().stream().anyMatch(value -> !value.identity().actorId().equals(actor)
                            && value.identity().entityId().equals(first.identity().entityId()))
                        || ledger.pendingAdoptions.values().stream().anyMatch(value -> !value.admitted().actorId().equals(actor)
                            && value.admitted().entityId().equals(first.identity().entityId())))
                    throw new IllegalStateException("duplicate first-admission identity");
                var carrier = ledger.carriers.get(actor); var adoption = ledger.pendingAdoptions.get(actor);
                if (carrier != null && (!first.identity().matches(carrier.identity()) || first.phase() != FrontierV3ActorFirstAdmission.Phase.ESTABLISHED)
                        || adoption != null && (!first.identity().matches(adoption.admitted()) || first.phase() != FrontierV3ActorFirstAdmission.Phase.ESTABLISHED))
                    throw new IllegalStateException("first-admission history contradicts current custody");
            }
        }
        for (Tag raw : departures) {
            var departure = FrontierV3SceneDeparture.load((CompoundTag) raw);
            if (ledger.departures.containsKey(departure.carrier().identity().actorId()) || !ledger.recordDeparture(departure)) {
                throw new IllegalStateException("duplicate scene departure evidence");
            }
        }
        // Loading old data must not manufacture coverage from today's code path.
        ledger.readFencedDepartures.clear();
        for (Tag raw : inventory(tag, "departureConflicts")) {
            var conflict = FrontierV3SceneDeparture.load((CompoundTag) raw);
            var actor = conflict.carrier().identity().actorId();
            var first = ledger.departures.get(actor);
            if (first == null || first.equals(conflict) || ledger.departureConflicts.putIfAbsent(actor, conflict) != null)
                throw new IllegalStateException("invalid scene departure conflict evidence");
        }
        if (format >= 6) {
            for (Tag raw : inventory(tag, "savedDepartures")) {
                var row = (CompoundTag) raw;
                if (!row.contains("actor", Tag.TAG_STRING) || row.size() != 1)
                    throw new IllegalStateException("invalid saved scene-departure marker");
                var actor = new SubjectId(row.getString("actor"));
                if (!ledger.departures.containsKey(actor) || ledger.hasDepartureConflict(actor)
                        || !ledger.savedDepartures.add(actor))
                    throw new IllegalStateException("orphan or duplicate saved scene-departure marker");
            }
        }
        if (format == FORMAT) {
            for (Tag raw : inventory(tag, "readFencedDepartures")) {
                var row = (CompoundTag) raw;
                if (!row.contains("actor", Tag.TAG_STRING) || row.size() != 1)
                    throw new IllegalStateException("invalid scene read-fence coverage");
                var actor = new SubjectId(row.getString("actor"));
                if (!ledger.departures.containsKey(actor) || !ledger.readFencedDepartures.add(actor))
                    throw new IllegalStateException("orphan or duplicate scene read-fence coverage");
            }
            for (Tag raw : inventory(tag, "returnReads")) {
                var row = (CompoundTag) raw;
                if (!row.contains("actor", Tag.TAG_STRING) || row.size() != 1)
                    throw new IllegalStateException("invalid scene return-read marker");
                var actor = new SubjectId(row.getString("actor"));
                if (!ledger.returnReads.add(actor))
                    throw new IllegalStateException("orphan or duplicate scene return-read marker");
            }
        }
        // Format5 worlds predating ambient witnesses have no such observations. Absence is
        // preserved as absence, never upgraded into proof that an actor left safely.
        if (tag.contains("ambientDepartures")) {
            for (Tag raw : inventory(tag, "ambientDepartures")) {
                var receipt = FrontierV3AmbientDeparture.load((CompoundTag) raw);
                if (ledger.ambientDepartures.containsKey(receipt.carrier().identity().actorId())
                        || !ledger.recordAmbientDeparture(receipt)) throw new IllegalStateException("duplicate ambient departure evidence");
            }
        }
        if (tag.contains("ambientDepartureConflicts")) {
            for (Tag raw : inventory(tag, "ambientDepartureConflicts")) {
                var receipt = FrontierV3AmbientDeparture.load((CompoundTag) raw);
                var actor = receipt.carrier().identity().actorId();
                var first = ledger.ambientDepartures.get(actor);
                if (first == null || first.equals(receipt) || ledger.ambientDepartureConflicts.putIfAbsent(actor, receipt) != null)
                    throw new IllegalStateException("invalid ambient departure conflict evidence");
            }
        }
        for (Tag raw : inventory(tag, "savedAmbientDepartures")) {
            var row = (CompoundTag) raw;
            if (!row.contains("actor", Tag.TAG_STRING) || row.size() != 1)
                throw new IllegalStateException("invalid saved ambient-departure marker");
            var actor = new SubjectId(row.getString("actor"));
            if (!ledger.ambientDepartures.containsKey(actor) || ledger.hasDepartureConflict(actor)
                    || !ledger.savedAmbientDepartures.add(actor))
                throw new IllegalStateException("orphan or duplicate saved ambient-departure marker");
        }
        for (var actor : ledger.savedBodyDepartures.keySet()) {
            if (ledger.hasDepartureConflict(actor) || ledger.returnReads.contains(actor))
                throw new IllegalStateException("saved body marker conflicts with retained evidence");
        }
        for (var actor : ledger.returnReads) {
            if (!ledger.hasBodyDeparture(actor)) throw new IllegalStateException("orphan body return-read marker");
        }
        for (var actor : ledger.bodyResidences.keySet()) {
            if (!ledger.firstAdmissions.containsKey(actor)) throw new IllegalStateException("orphan body residence history");
        }
        ledger.dirtyActors.clear(); ledger.setDirty(false);
        return ledger;
    }
    private static ListTag inventory(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag rows) || (!rows.isEmpty() && rows.getElementType() != Tag.TAG_COMPOUND)
                || rows.size() > MAX_CARRIERS) throw new IllegalStateException("invalid carrier inventory: " + key);
        return rows;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT); ListTag values = new ListTag();
        var residences = new ListTag();
        bodyResidences.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var row = new CompoundTag(); row.putString("actor", entry.getKey().value()); row.putLong("generation", entry.getValue());
            residences.add(row);
        });
        tag.put("bodyResidences", residences);
        var physical = new ListTag(); bodyDepartures().forEach(value -> physical.add(value.save()));
        tag.put("bodyDepartures", physical);
        var bodyConflicts = new ListTag();
        bodyDepartureConflicts.values().stream().sorted(Comparator.comparing(value -> value.identity().actorId()))
                .forEach(value -> bodyConflicts.add(value.save()));
        tag.put("bodyDepartureConflicts", bodyConflicts);
        var markers = new ListTag();
        savedBodyDepartures.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var row = new CompoundTag(); row.putString("actor", entry.getKey().value());
            row.putLong("residenceGeneration", entry.getValue()); markers.add(row);
        });
        tag.put("savedBodyDepartures", markers);
        carriers.values().stream().sorted(Comparator.comparing(value -> value.identity.actorId().value())).forEach(value -> values.add(value.save()));
        tag.put("carriers", values);
        var adoptions = new ListTag();
        pendingAdoptions().forEach(value -> adoptions.add(value.save()));
        tag.put("pendingAdoptions", adoptions);
        var first = new ListTag(); firstAdmissions().forEach(value -> first.add(value.save())); tag.put("firstAdmissions", first);
        ListTag departureTags = new ListTag();
        departures.values().stream().sorted(Comparator.comparing(value -> value.carrier().identity().actorId()))
                .forEach(value -> departureTags.add(value.save()));
        tag.put("departures", departureTags);
        ListTag saved = new ListTag();
        savedDepartures.stream().sorted().forEach(actor -> {
            var row = new CompoundTag(); row.putString("actor", actor.value()); saved.add(row);
        });
        tag.put("savedDepartures", saved);
        ListTag guarded = new ListTag();
        readFencedDepartures.stream().sorted().forEach(actor -> {
            var row = new CompoundTag(); row.putString("actor", actor.value()); guarded.add(row);
        });
        tag.put("readFencedDepartures", guarded);
        ListTag returns = new ListTag();
        returnReads.stream().sorted().forEach(actor -> {
            var row = new CompoundTag(); row.putString("actor", actor.value()); returns.add(row);
        });
        tag.put("returnReads", returns);
        ListTag conflicts = new ListTag();
        departureConflicts.values().stream().sorted(Comparator.comparing(value -> value.carrier().identity().actorId()))
                .forEach(value -> conflicts.add(value.save()));
        tag.put("departureConflicts", conflicts);
        var ambient = new ListTag();
        ambientDepartures.values().stream().sorted(Comparator.comparing(value -> value.carrier().identity().actorId()))
                .forEach(value -> ambient.add(value.save()));
        tag.put("ambientDepartures", ambient);
        var savedAmbient = new ListTag();
        savedAmbientDepartures.stream().sorted().forEach(actor -> {
            var row = new CompoundTag(); row.putString("actor", actor.value()); savedAmbient.add(row);
        });
        tag.put("savedAmbientDepartures", savedAmbient);
        var ambientConflicts = new ListTag();
        ambientDepartureConflicts.values().stream().sorted(Comparator.comparing(value -> value.carrier().identity().actorId()))
                .forEach(value -> ambientConflicts.add(value.save()));
        tag.put("ambientDepartureConflicts", ambientConflicts);
        return tag;
    }
    enum Reconciliation {
        NO_FENCED_CARRIER, READY, UUID_MISMATCH, KIND_MISMATCH, REPRESENTATION_MISMATCH,
        STALE_REVISION, CONCURRENT_CUSTODY, DEPARTURE_CONFLICT
    }
    record Carrier(FrontierV3ActorCarrierComposition.Declaration identity, long physicalRevision, long ambientRevision) {
        Carrier { if (identity.representation() != FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER || physicalRevision < 1L || ambientRevision < 0L) throw new IllegalArgumentException("invalid ambient carrier evidence"); }
        CompoundTag save() {
            CompoundTag tag = new CompoundTag(); tag.putString("actor", identity.actorId().value()); tag.putUUID("uuid", identity.entityId());
            tag.putString("kind", identity.kind().name()); tag.putString("owner", identity.owner().name());
            tag.putString("representation", identity.representation().name()); tag.putLong("bodyRevision", identity.authorityRevision());
            tag.putLong("revision", physicalRevision);
            tag.putLong("ambientRevision", ambientRevision); tag.putLong("epoch", identity.epoch()); return tag;
        }
        static Carrier load(CompoundTag tag) {
            if (!tag.contains("actor", Tag.TAG_STRING) || !tag.hasUUID("uuid") || !tag.contains("kind", Tag.TAG_STRING)
                    || !tag.contains("owner", Tag.TAG_STRING) || !tag.contains("representation", Tag.TAG_STRING)
                    || !tag.contains("bodyRevision", Tag.TAG_LONG) || !tag.contains("revision", Tag.TAG_LONG) || !tag.contains("ambientRevision", Tag.TAG_LONG)
                    || !tag.contains("epoch", Tag.TAG_LONG)) throw new IllegalStateException("incomplete v3 ambient carrier evidence");
            try { return new Carrier(new FrontierV3ActorCarrierComposition.Declaration(new SubjectId(tag.getString("actor")),
                    ActorKind.valueOf(tag.getString("kind")), FrontierV3ActorCarrierComposition.Owner.valueOf(tag.getString("owner")),
                    tag.getUUID("uuid"), FrontierV3ActorCarrierComposition.Representation.valueOf(tag.getString("representation")),
                    tag.getLong("bodyRevision"), tag.getLong("epoch")), tag.getLong("revision"), tag.getLong("ambientRevision")); }
            catch (IllegalArgumentException invalid) { throw new IllegalStateException("invalid v3 ambient carrier declaration", invalid); }
        }
    }
}
