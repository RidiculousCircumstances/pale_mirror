package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** One SavedData owner for twelve sites; legacy and cell claims are disjoint during cutover. */
final class FrontierV3ResourceSiteLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_resource_sites";
    private static final int FORMAT = 14;
    static final int MAX_SITES = 12;
    private final Map<SubjectId, Claim> claims;
    /** Mutually exclusive replacement for a site's legacy stage/prefix claim. */
    private final Map<SubjectId, FieldClaim> fieldClaims;
    /** First blocked native crop attempt is a diagnostic fact, not part of facility ownership. */
    private final Map<SubjectId, NativeGrowthFence> nativeGrowthFences;
    /**
     * A persisted write-ahead fence for the one physical harvest receipt. It distinguishes a
     * first empty depot slot from output removed before canonical confirmation, which fails
     * closed rather than minting a replacement.
     */
    private final Map<SubjectId, HarvestReceipt> harvestReceipts;
    /** A positive cell-owned delivery is fenced before either Vanilla inventory is edited. */
    private final Map<SubjectId, FrontierV3ResourceSiteDeliveryWitness> fieldDeliveries;
    /** A COLD actor part cannot first appear in a HOT hand without this before-effect owner. */
    private final Map<SubjectId, FrontierV3ResourceSiteHandProjectionWitness> fieldHandProjections;
    /** One explicit player-action fence per site while Vanilla may change its crop cell. */
    private final Map<SubjectId, FrontierV3ResourceFieldPlayerBreakWitness> fieldPlayerBreaks;
    /** One observed world postcondition per site until its canonical receipt and cell claim agree. */
    private final Map<SubjectId, FrontierV3ResourceFieldWorldChangeWitness> fieldWorldChanges;
    /** A foreign-cell edit owns one site's physical cause until its local WAL/claim agree. */
    private final Map<SubjectId, FrontierV3ResourceFieldForeignChangeWitness> fieldForeignChanges;

    private FrontierV3ResourceSiteLedger() { this(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
            new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
            new LinkedHashMap<>()); }
    private FrontierV3ResourceSiteLedger(Map<SubjectId, Claim> claims, Map<SubjectId, FieldClaim> fieldClaims,
                                         Map<SubjectId, NativeGrowthFence> nativeGrowthFences,
                                         Map<SubjectId, HarvestReceipt> harvestReceipts,
                                         Map<SubjectId, FrontierV3ResourceSiteDeliveryWitness> fieldDeliveries,
                                         Map<SubjectId, FrontierV3ResourceSiteHandProjectionWitness> fieldHandProjections,
                                         Map<SubjectId, FrontierV3ResourceFieldPlayerBreakWitness> fieldPlayerBreaks,
                                         Map<SubjectId, FrontierV3ResourceFieldWorldChangeWitness> fieldWorldChanges,
                                         Map<SubjectId, FrontierV3ResourceFieldForeignChangeWitness> fieldForeignChanges) {
        if (claims.size() + fieldClaims.size() > MAX_SITES || claims.keySet().stream().anyMatch(fieldClaims::containsKey))
            throw new IllegalArgumentException("v3 field has competing or unbounded physical claim owners");
        if (fieldDeliveries.size() > MAX_SITES || fieldDeliveries.keySet().stream().anyMatch(site -> !fieldClaims.containsKey(site)))
            throw new IllegalArgumentException("field delivery lacks its one cell-owned site");
        if (fieldHandProjections.size() > MAX_SITES || fieldHandProjections.keySet().stream().anyMatch(site -> !fieldClaims.containsKey(site))
                || fieldHandProjections.keySet().stream().anyMatch(fieldDeliveries::containsKey))
            throw new IllegalArgumentException("field hand projection lacks its one cell-owned site or overlaps delivery");
        if (fieldPlayerBreaks.size() > MAX_SITES || fieldPlayerBreaks.keySet().stream().anyMatch(site -> !fieldClaims.containsKey(site)))
            throw new IllegalArgumentException("player field break lacks its one cell-owned site");
        if (fieldWorldChanges.size() > MAX_SITES || fieldWorldChanges.keySet().stream().anyMatch(site -> !fieldClaims.containsKey(site)))
            throw new IllegalArgumentException("world field change lacks its one cell-owned site");
        if (fieldForeignChanges.size() > MAX_SITES || fieldForeignChanges.keySet().stream()
                .anyMatch(site -> !fieldClaims.containsKey(site) || fieldWorldChanges.containsKey(site)
                        || fieldPlayerBreaks.containsKey(site)))
            throw new IllegalArgumentException("foreign field change lacks a unique cell-owned site");
        this.claims = claims; this.fieldClaims = fieldClaims;
        this.nativeGrowthFences = nativeGrowthFences; this.harvestReceipts = harvestReceipts;
        this.fieldDeliveries = fieldDeliveries; this.fieldHandProjections = fieldHandProjections;
        this.fieldPlayerBreaks = fieldPlayerBreaks;
        this.fieldWorldChanges = fieldWorldChanges;
        this.fieldForeignChanges = fieldForeignChanges;
    }

    static FrontierV3ResourceSiteLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3ResourceSiteLedger::new,
                FrontierV3ResourceSiteLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME);
    }

    /** Persist the level-owned claim before its next physical block effect is permitted. */
    void persist(ServerLevel level) {
        if (get(level) != this) throw new IllegalArgumentException("foreign resource-site ledger");
        save(storageFile(level).toFile(), level.registryAccess());
    }

    static java.nio.file.Path storageFile(ServerLevel level) {
        var dimension = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),
                level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
        return dimension.resolve("data").resolve(NAME + ".dat");
    }

    /** Atomic single-file SavedData publication; not atomic with the chunk region or canonical WAL. */
    @Override public void save(java.io.File file, HolderLookup.Provider registries) {
        if (!isDirty()) return;
        java.nio.file.Path target = file.toPath();
        java.nio.file.Path staged = null;
        try {
            var root = new CompoundTag();
            root.put("data", save(new CompoundTag(), registries));
            net.minecraft.nbt.NbtUtils.addCurrentDataVersion(root);
            java.nio.file.Files.createDirectories(target.getParent());
            staged = java.nio.file.Files.createTempFile(target.getParent(), ".resource-sites-", ".tmp");
            net.minecraft.nbt.NbtIo.writeCompressed(root, staged);
            try (var channel = java.nio.channels.FileChannel.open(staged, java.nio.file.StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            java.nio.file.Files.move(staged, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            staged = null;
            try (var directory = java.nio.channels.FileChannel.open(target.getParent(), java.nio.file.StandardOpenOption.READ)) {
                directory.force(true);
            }
            setDirty(false);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("unable to atomically persist resource-site ledger", failure);
        } finally {
            if (staged != null) {
                try { java.nio.file.Files.deleteIfExists(staged); }
                catch (java.io.IOException cleanupFailure) { /* original failure remains authoritative */ }
            }
        }
    }

    /** Isolated GameTest fixture state; production always uses the level-owned ledger above. */
    static FrontierV3ResourceSiteLedger fixture() {
        return new FrontierV3ResourceSiteLedger();
    }

    /** Read the explicit versioned claim variant without asking either incompatible API to infer it. */
    SiteClaim siteClaim(SubjectId siteId) {
        Claim legacy = claims.get(siteId);
        FieldClaim cells = fieldClaims.get(siteId);
        if (legacy != null && cells != null) throw new IllegalStateException("field has competing physical owners");
        if (legacy != null) return new LegacySiteClaim(siteId, legacy);
        return cells == null ? null : new CellSiteClaim(cells);
    }
    Claim claim(SubjectId siteId) {
        if (fieldClaims.containsKey(siteId)) throw new IllegalStateException("cell-owned field cannot use the legacy stage/prefix claim");
        return claims.get(siteId);
    }
    FieldClaim fieldClaim(SubjectId siteId) {
        if (claims.containsKey(siteId)) throw new IllegalStateException("legacy field cannot use the replacement cell claim");
        return fieldClaims.get(siteId);
    }
    FrontierV3ResourceSiteDeliveryWitness fieldDelivery(SubjectId siteId) { return fieldDeliveries.get(siteId); }
    FrontierV3ResourceSiteHandProjectionWitness fieldHandProjection(SubjectId siteId) { return fieldHandProjections.get(siteId); }
    FrontierV3ResourceFieldPlayerBreakWitness fieldPlayerBreak(SubjectId siteId) { return fieldPlayerBreaks.get(siteId); }
    FrontierV3ResourceFieldWorldChangeWitness fieldWorldChange(SubjectId siteId) { return fieldWorldChanges.get(siteId); }
    FrontierV3ResourceFieldForeignChangeWitness fieldForeignChange(SubjectId siteId) { return fieldForeignChanges.get(siteId); }
    java.util.List<FrontierV3ResourceFieldForeignChangeWitness> pendingFieldForeignChanges() {
        return fieldForeignChanges.values().stream()
                .sorted(java.util.Comparator.comparing(FrontierV3ResourceFieldForeignChangeWitness::siteId)).toList();
    }
    void beginFieldForeignChange(FrontierV3ResourceFieldForeignChangeWitness change) {
        if (!(fieldClaim(change.siteId()) instanceof FieldOwnership owner) || owner.status() != Status.ACTIVE
                || owner.witness().cell(change.cellId()).pending().isPresent()
                || fieldWorldChanges.containsKey(change.siteId()) || fieldPlayerBreaks.containsKey(change.siteId()))
            throw new IllegalStateException("foreign field change lacks an unblocked physical site");
        var before = change.hold().before();
        var cell = owner.witness().cell(change.cellId());
        if (cell.foreign().isPresent() != (before.soil() == ResourceFieldCycle.Soil.OBSTRUCTED
                || before.crop() == ResourceFieldCycle.Crop.OBSTRUCTED)
                || cell.foreign().isEmpty()
                && !cell.committed().equals(ResourceFieldPhysicalSurface.Condition.of(before)))
            throw new IllegalStateException("foreign field change has a different physical predecessor");
        var prior = fieldForeignChanges.putIfAbsent(change.siteId(), change);
        if (prior != null && !prior.equals(change)) throw new IllegalStateException("another foreign cause owns this field");
        if (prior == null) setDirty();
    }
    void observeFieldForeignChange(FrontierV3ResourceFieldForeignChangeWitness before,
                                   FrontierV3ResourceFieldForeignChangeWitness observed) {
        if (!before.hold().equals(observed.hold()) || before.observed().isPresent() || observed.observed().isEmpty()
                || !fieldForeignChanges.replace(before.siteId(), before, observed))
            throw new IllegalStateException("foreign field postcondition lacks its exact retained cause");
        setDirty();
    }
    void retireFieldForeignChange(FrontierV3ResourceFieldForeignChangeWitness change) {
        if (!fieldForeignChanges.remove(change.siteId(), change))
            throw new IllegalStateException("foreign field change has no exact retained witness");
        setDirty();
    }
    java.util.List<FrontierV3ResourceFieldWorldChangeWitness> pendingFieldWorldChanges() {
        return fieldWorldChanges.values().stream().sorted(java.util.Comparator.comparing(FrontierV3ResourceFieldWorldChangeWitness::siteId)).toList();
    }
    void beginFieldWorldChange(FrontierV3ResourceFieldWorldChangeWitness change) {
        if (!(fieldClaim(change.siteId()) instanceof FieldOwnership owner) || owner.status() != Status.ACTIVE
                || !owner.witness().cell(change.cellId()).committed().equals(change.before())
                || owner.witness().cell(change.cellId()).pending().isPresent()
                || owner.witness().cell(change.cellId()).foreign().isPresent()
                || fieldPlayerBreaks.containsKey(change.siteId())
                || fieldForeignChanges.containsKey(change.siteId()))
            throw new IllegalStateException("world field change lacks an unblocked exact physical predecessor");
        var prior = fieldWorldChanges.putIfAbsent(change.siteId(), change);
        if (prior != null && !prior.equals(change)) throw new IllegalStateException("another world change owns this field");
        if (prior == null) setDirty();
    }
    void retireFieldWorldChange(FrontierV3ResourceFieldWorldChangeWitness change) {
        if (!fieldWorldChanges.remove(change.siteId(), change))
            throw new IllegalStateException("world field change has no exact retained witness");
        setDirty();
    }
    java.util.List<FrontierV3ResourceFieldPlayerBreakWitness> pendingFieldPlayerBreaks() {
        return fieldPlayerBreaks.values().stream().sorted(java.util.Comparator.comparing(FrontierV3ResourceFieldPlayerBreakWitness::siteId)).toList();
    }
    void beginFieldPlayerBreak(FrontierV3ResourceFieldPlayerBreakWitness witness) {
        if (!(fieldClaim(witness.siteId()) instanceof FieldOwnership owner)
                || owner.status() != Status.ACTIVE
                || owner.witness().cell(witness.cellId()).pending().isPresent()
                || owner.witness().cell(witness.cellId()).foreign().isPresent()
                || !owner.witness().cell(witness.cellId()).committed().equals(witness.before())
                || fieldWorldChanges.containsKey(witness.siteId())
                || fieldForeignChanges.containsKey(witness.siteId()))
            throw new IllegalStateException("player break lacks an active exact field predecessor");
        var prior = fieldPlayerBreaks.putIfAbsent(witness.siteId(), witness);
        if (prior != null && !prior.equals(witness)) throw new IllegalStateException("another player break already owns this field");
        if (prior == null) setDirty();
    }
    void observeFieldPlayerBreak(FrontierV3ResourceFieldPlayerBreakWitness before,
                                 FrontierV3ResourceFieldPlayerBreakWitness observed) {
        if (before.observedChange().isPresent() || observed.observedChange().isEmpty()
                || !before.siteId().equals(observed.siteId()) || !before.cellId().equals(observed.cellId())
                || !before.actionId().equals(observed.actionId())
                || !fieldPlayerBreaks.replace(before.siteId(), before, observed))
            throw new IllegalStateException("player break observation lacks its exact before-effect witness");
        setDirty();
    }
    void retireFieldPlayerBreak(FrontierV3ResourceFieldPlayerBreakWitness witness) {
        if (witness.observedChange().isEmpty() || !fieldPlayerBreaks.remove(witness.siteId(), witness))
            throw new IllegalStateException("player break retirement lacks its accepted observed witness");
        setDirty();
    }
    void beginFieldHandProjection(FrontierV3ResourceSiteHandProjectionWitness witness) {
        if (!(fieldClaim(witness.siteId()) instanceof FieldOwnership owner) || owner.status() != Status.ACTIVE
                || fieldDeliveries.containsKey(witness.siteId()))
            throw new IllegalStateException("field hand projection has no exclusive active cell owner");
        var prior = fieldHandProjections.putIfAbsent(witness.siteId(), witness);
        if (prior != null && !prior.equals(witness)) throw new IllegalStateException("field hand projection changes its durable predecessor");
        if (prior == null) setDirty();
    }
    void retireFieldHandProjection(FrontierV3ResourceSiteHandProjectionWitness witness) {
        if (!witness.equals(fieldHandProjections.get(witness.siteId())))
            throw new IllegalStateException("field hand projection retirement lacks its exact witness");
        fieldHandProjections.remove(witness.siteId()); setDirty();
    }
    boolean hasPendingFieldDelivery(SubjectId containerId) {
        return fieldDeliveries.values().stream().anyMatch(witness -> witness.containerId().equals(containerId));
    }
    java.util.List<FrontierV3ResourceSiteDeliveryWitness> pendingFieldDeliveries() {
        return fieldDeliveries.values().stream().sorted(java.util.Comparator.comparing(FrontierV3ResourceSiteDeliveryWitness::siteId)).toList();
    }
    void beginFieldDelivery(FrontierV3ResourceSiteDeliveryWitness witness) {
        if (!(fieldClaim(witness.siteId()) instanceof FieldOwnership owner) || owner.status() != Status.ACTIVE)
            throw new IllegalStateException("field delivery has no active cell owner");
        if (fieldHandProjections.containsKey(witness.siteId()))
            throw new IllegalStateException("field delivery cannot overtake an unretired actor-hand projection");
        FrontierV3ResourceSiteDeliveryWitness prior = fieldDeliveries.putIfAbsent(witness.siteId(), witness);
        if (prior != null && !prior.equals(witness)) throw new IllegalStateException("field delivery changes its durable predecessor");
        if (prior == null) setDirty();
    }
    void retireFieldDelivery(FrontierV3ResourceSiteDeliveryWitness witness) {
        if (!witness.equals(fieldDeliveries.get(witness.siteId()))) throw new IllegalStateException("field delivery retirement lacks its exact witness");
        fieldDeliveries.remove(witness.siteId()); setDirty();
    }
    /** Reserve the one cell owner before any first-field block write. */
    void reserveFieldInitialization(ResourceSite site, PhysicalIntentId intentId) {
        FieldClaim claim = new FieldInitialization(site.id(), intentId, Status.PENDING, InitialCursor.atStart(site));
        SubjectId siteId = claim.siteId();
        if (claims.containsKey(siteId)) throw new IllegalStateException("field site still has a legacy stage/prefix owner");
        FieldClaim prior = fieldClaims.putIfAbsent(siteId, claim);
        if (prior != null) {
            if (!prior.equals(claim)) throw new IllegalStateException("field site already has another cell claim");
            return;
        }
        if (fieldClaims.size() + claims.size() > MAX_SITES) {
            fieldClaims.remove(siteId); throw new IllegalStateException("v3 field claim limit exceeded");
        }
        setDirty();
    }
    void advanceFieldInitialization(ResourceSite site, FrontierV3ResourceFieldInitialPlan.Review review) {
        FieldInitialization prior = fieldClaim(site.id()) instanceof FieldInitialization value ? value : null;
        if (prior == null || prior.status() != Status.PENDING || !prior.cursor().matches(site)
                || prior.cursor().complete() || !prior.cursor().prepared()
                || !review.writtenThisCall()
                || !review.matches(site, prior.cursor().nextWrite(), FrontierV3ResourceFieldInitialPlan.Disposition.APPLIED))
            throw new IllegalStateException("initial field write lacks its exact observed next block");
        fieldClaims.put(prior.siteId(), new FieldInitialization(prior.siteId(), prior.intentId(), Status.PENDING,
                prior.cursor().advanced())); setDirty();
    }
    void prepareFieldInitialization(ResourceSite site, FrontierV3ResourceFieldInitialPlan.Review review) {
        FieldInitialization prior = fieldClaim(site.id()) instanceof FieldInitialization value ? value : null;
        if (prior == null || prior.status() != Status.PENDING || !prior.cursor().matches(site)
                || prior.cursor().complete() || prior.cursor().prepared()
                || !review.matches(site, prior.cursor().nextWrite(), FrontierV3ResourceFieldInitialPlan.Disposition.BEFORE))
            throw new IllegalStateException("initial field write lacks its exact observed neutral predecessor");
        fieldClaims.put(prior.siteId(), new FieldInitialization(prior.siteId(), prior.intentId(), Status.PENDING,
                prior.cursor().preparedStep())); setDirty();
    }
    void activateField(ServerLevel level, ResourceSite site, ResourceFieldCycle target, FrontierV3ResourceFieldWitness witness) {
        FieldInitialization prior = fieldClaim(site.id()) instanceof FieldInitialization value ? value : null;
        if (prior == null || prior.status() != Status.PENDING || !prior.cursor().matches(site)
                || !prior.cursor().complete() || !witness.matchesCycle(target)
                || !ResourceFieldCycle.seeded(site.id(), site.layout(), 1).equals(target))
            throw new IllegalStateException("initial field cannot activate without its complete exact seeded cycle");
        for (ResourceFieldLayout.Cell cell : site.layout().cells()) {
            FrontierV3ResourceFieldWitness.Cell physical = witness.cell(cell.id());
            if (!physical.committed().equals(ResourceFieldPhysicalSurface.Condition.of(target.cell(cell.id())))
                    || physical.pending().isPresent() || physical.foreign().isPresent())
                throw new IllegalStateException("initial field has an incomplete or foreign cell claim");
        }
        FrontierV3ResourceFieldInitialPlan.requireCompletePhysicalField(level, site, target, witness);
        fieldClaims.put(prior.siteId(), new FieldOwnership(site.id(), prior.intentId(), Status.ACTIVE, witness)); setDirty();
    }
    /** Active witness replacement cannot forge or bypass an initial physical-write cursor. */
    void replaceFieldClaim(FieldClaim prior, FieldClaim next) {
        if (!(prior instanceof FieldOwnership) || !(next instanceof FieldOwnership)
                || !prior.siteId().equals(next.siteId()) || !prior.intentId().equals(next.intentId())
                || prior.status() == Status.CONFLICT
                || fieldClaims.get(prior.siteId()) != prior)
            throw new IllegalStateException("field cell claim replacement has a foreign or stale owner");
        fieldClaims.put(prior.siteId(), next); setDirty();
    }
    NativeGrowthFence nativeGrowthFence(SubjectId siteId) {
        if (fieldClaims.containsKey(siteId)) throw new IllegalStateException("cell-owned field cannot use a legacy native-growth fence");
        return nativeGrowthFences.get(siteId);
    }
    boolean hasHarvestReceipt(SubjectId siteId, ExactItemStack output) {
        if (fieldClaims.containsKey(siteId)) throw new IllegalStateException("cell-owned field cannot use a legacy exact-stack receipt");
        HarvestReceipt receipt = harvestReceipts.get(siteId);
        return receipt != null && receipt.matches(output);
    }

    /** Writes the durable receipt fence before the matching chest slot is materialized. */
    boolean recordHarvestReceipt(SubjectId siteId, ExactItemStack output) {
        Claim claim = required(siteId);
        if (claim.status() != Status.ACTIVE || claim.stage() != 7 || claim.harvestedCropSlots() != 64) {
            throw new IllegalStateException("v3 resource site receipt has incomplete harvest cursor");
        }
        HarvestReceipt receipt = HarvestReceipt.from(output);
        HarvestReceipt prior = harvestReceipts.putIfAbsent(siteId, receipt);
        if (prior == null) { setDirty(); return true; }
        if (!prior.equals(receipt)) throw new IllegalStateException("v3 resource site receipt changes output identity");
        return false;
    }

    /** Retain only the first fence edge, so later equivalent random ticks cannot rewrite its source. */
    void recordNativeGrowthFence(SubjectId siteId, NativeGrowthFence fence) {
        if (!claims.containsKey(siteId)) throw new IllegalStateException("v3 native crop fence has no site claim");
        if (nativeGrowthFences.putIfAbsent(siteId, fence) == null) setDirty();
    }

    void reserve(SubjectId siteId, PhysicalIntentId intentId) {
        if (fieldClaims.containsKey(siteId)) throw new IllegalStateException("cell-owned field cannot reserve a legacy claim");
        Claim next = new Claim(intentId, Status.PENDING, 0, 0);
        Claim prior = claims.get(siteId); if (prior != null) {
            if (!prior.equals(next)) throw new IllegalStateException("v3 resource site claim changes intent or lifecycle");
            return;
        }
        if (claims.size() + fieldClaims.size() >= MAX_SITES) throw new IllegalStateException("v3 resource site claim limit exceeded");
        claims.put(siteId, next); setDirty();
    }

    /**
     * Re-establishes only the durable terminal predecessor shape of an exact composed
     * successor.  Its following projection retains this stage-seven/64-slot origin, so an
     * interruption cannot reinterpret irrigated farmland and replanted crops as a neutral field.
     */
    void reserveComposedTerminalSuccessor(SubjectId siteId, PhysicalIntentId intentId) {
        reserveComposedSuccessor(siteId, intentId, 7, 64);
    }

    /**
     * Re-establishes a bounded, exact physical predecessor of a composed successor.  The
     * caller supplies only a complete stage surface (or the stage-seven/64 terminal receipt),
     * never a mixed or inferred cursor.
     */
    void reserveComposedSuccessor(SubjectId siteId, PhysicalIntentId intentId, int predecessorStage,
                                  int predecessorHarvestedCropSlots) {
        if (fieldClaims.containsKey(siteId)) throw new IllegalStateException("cell-owned field cannot reserve a legacy successor");
        if (predecessorStage < 0 || predecessorStage > 7
                || (predecessorStage == 7
                ? predecessorHarvestedCropSlots != 0 && predecessorHarvestedCropSlots != 64
                : predecessorHarvestedCropSlots != 0)) {
            throw new IllegalArgumentException("v3 composed successor predecessor is invalid");
        }
        Claim next = new Claim(intentId, Status.PENDING, predecessorStage, predecessorHarvestedCropSlots);
        Claim prior = claims.get(siteId); if (prior != null) {
            if (!prior.equals(next)) throw new IllegalStateException("v3 resource site claim changes composed successor lifecycle");
            return;
        }
        if (claims.size() + fieldClaims.size() >= MAX_SITES) throw new IllegalStateException("v3 resource site claim limit exceeded");
        claims.put(siteId, next);
        // A composed successor has already retained the output in canonical custody; this
        // physical cursor may not leave a retired predecessor receipt fence behind.
        harvestReceipts.remove(siteId); setDirty();
    }

    void activate(SubjectId siteId) { transition(siteId, Status.PENDING, Status.ACTIVE); }
    /**
     * Persists the exact bounded physical transition before its first world write.  A claim is
     * the stable facility owner; this separate fence is deliberately the only mutable
     * lifecycle/projection cursor so restart recovery never infers ownership from a mixed crop
     * surface.
     */
    void beginProjection(SubjectId siteId, ProjectionTransition projection) {
        Claim prior = required(siteId);
        if (prior.projection() != null && !prior.projection().equals(projection)) {
            throw new IllegalStateException("v3 resource site projection replaces an active cursor");
        }
        if (prior.projection() == null) {
            claims.put(siteId, prior.withProjection(projection)); setDirty();
        }
    }
    void advanceProjection(SubjectId siteId, int nextWrite) {
        Claim prior = required(siteId); ProjectionTransition projection = prior.projection();
        if (projection == null || nextWrite != projection.nextWrite() + 1 || nextWrite > projection.writeCount()) {
            throw new IllegalStateException("v3 resource site projection cursor is invalid");
        }
        claims.put(siteId, prior.withProjection(projection.advance())); setDirty();
    }
    void completeProjection(SubjectId siteId) {
        Claim prior = required(siteId); ProjectionTransition projection = prior.projection();
        if (projection == null || projection.nextWrite() != projection.writeCount()) {
            throw new IllegalStateException("v3 resource site projection is incomplete");
        }
        claims.put(siteId, prior.withProjection(null)); setDirty();
    }
    void updateStage(SubjectId siteId, int stage) {
        Claim prior = required(siteId);
        if (prior.status() != Status.ACTIVE) throw new IllegalStateException("v3 resource site stage is not active");
        if (prior.stage() == stage) return;
        Claim next = new Claim(prior.intentId(), prior.status(), stage, stage == 7 ? prior.harvestedCropSlots() : 0, prior.projection());
        // The validated projector has completed regrowth into a new nonmature
        // surface. Its predecessor's receipt must not fence the next harvest.
        // Mature-to-mature restoration retires it separately in restoreOne().
        if (prior.stage() == 7 && stage < 7) harvestReceipts.remove(siteId);
        claims.put(siteId, next); setDirty();
    }
    void harvestOne(SubjectId siteId, int completedCropSlots) {
        Claim prior = required(siteId);
        if (prior.status() != Status.ACTIVE || prior.stage() != 7 || completedCropSlots != prior.harvestedCropSlots() + 1) {
            throw new IllegalStateException("v3 resource site harvest cursor is invalid");
        }
        claims.put(siteId, new Claim(prior.intentId(), prior.status(), prior.stage(), completedCropSlots, prior.projection())); setDirty();
    }
    /**
     * Records one bounded reverse transition from the fully harvested predecessor to its exact
     * next growth-epoch field.  This is deliberately the inverse of {@link #harvestOne}: the
     * same immutable prefix encoding remains the durable restart witness while the successor
     * field is restored one naturally observed slot at a time.
     */
    void restoreOne(SubjectId siteId, int remainingHarvestedCropSlots) {
        Claim prior = required(siteId);
        if (prior.status() != Status.ACTIVE || prior.stage() != 7
                || remainingHarvestedCropSlots != prior.harvestedCropSlots() - 1) {
            throw new IllegalStateException("v3 resource site successor restore cursor is invalid");
        }
        // A successor restore is admitted only after canonical confirmation. Once its first
        // physical crop is restored, the prior receipt no longer fences a future harvest epoch.
        if (remainingHarvestedCropSlots == 63) harvestReceipts.remove(siteId);
        claims.put(siteId, new Claim(prior.intentId(), prior.status(), prior.stage(), remainingHarvestedCropSlots, prior.projection())); setDirty();
    }
    void conflict(SubjectId siteId) {
        FieldClaim field = fieldClaims.get(siteId);
        if (field != null) {
            if (field.status() == Status.CONFLICT) return;
            fieldClaims.put(siteId, field instanceof FieldInitialization initial
                    ? initial.conflicted() : ((FieldOwnership) field).conflicted());
            setDirty();
            return;
        }
        Claim prior = claims.get(siteId);
        if (prior == null || prior.status() == Status.CONFLICT) return;
        claims.put(siteId, new Claim(prior.intentId(), Status.CONFLICT, prior.stage(), prior.harvestedCropSlots(), null)); setDirty();
    }

    static FrontierV3ResourceSiteLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        int format = tag.getInt("format");
        if (format != FORMAT) throw new IllegalStateException("incompatible v3 resource site ledger: format " + format + "; fresh current-schema world required");
        ListTag values = rows(tag, "claims");
        if (values.size() > MAX_SITES) throw new IllegalStateException("v3 resource site claim limit exceeded");
        Map<SubjectId, Claim> claims = new LinkedHashMap<>();
        for (Tag value : values) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.contains("site", Tag.TAG_STRING) || !entry.contains("intent", Tag.TAG_STRING) || !entry.contains("status", Tag.TAG_STRING)
                    || !entry.contains("stage", Tag.TAG_INT) || !entry.contains("harvested", Tag.TAG_INT)) {
                throw new IllegalStateException("incomplete v3 resource site claim");
            }
            SubjectId site = new SubjectId(entry.getString("site"));
            if (!site.value().startsWith("site:")) throw new IllegalStateException("invalid v3 resource site claim identity");
            Status status;
            try { status = Status.valueOf(entry.getString("status")); }
            catch (IllegalArgumentException invalid) { throw new IllegalStateException("invalid v3 resource site claim status", invalid); }
            int stage = entry.getInt("stage"), harvested = entry.getInt("harvested");
            ProjectionTransition projection = format >= 4 && entry.contains("projection", Tag.TAG_COMPOUND)
                    ? ProjectionTransition.read(entry.getCompound("projection")) : null;
            if (claims.put(site, new Claim(new PhysicalIntentId(entry.getString("intent")), status, stage, harvested, projection)) != null) {
                throw new IllegalStateException("duplicate v3 resource site claim");
            }
        }
        Map<SubjectId, FieldClaim> fieldClaims = new LinkedHashMap<>();
        ListTag fieldValues = rows(tag, "fieldClaims");
        if (fieldValues.size() + claims.size() > MAX_SITES)
            throw new IllegalStateException("v3 cell field claim limit exceeded");
        for (Tag value : fieldValues) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.contains("site", Tag.TAG_STRING) || !entry.contains("intent", Tag.TAG_STRING)
                    || !entry.contains("status", Tag.TAG_STRING) || !entry.contains("kind", Tag.TAG_STRING))
                throw new IllegalStateException("incomplete v3 cell field claim");
            SubjectId site = new SubjectId(entry.getString("site"));
            Status status;
            try { status = Status.valueOf(entry.getString("status")); }
            catch (IllegalArgumentException invalid) { throw new IllegalStateException("invalid v3 cell field claim status", invalid); }
            PhysicalIntentId intent = new PhysicalIntentId(entry.getString("intent"));
            FieldClaim claim = switch (entry.getString("kind")) {
                case "INITIAL" -> {
                    if (!entry.contains("initial", Tag.TAG_COMPOUND) || entry.contains("witness"))
                        throw new IllegalStateException("initial field has missing or competing physical evidence");
                    yield new FieldInitialization(site, intent, status, InitialCursor.read(entry.getCompound("initial")));
                }
                case "OWNED" -> {
                    if (!entry.contains("witness", Tag.TAG_COMPOUND) || entry.contains("initial"))
                        throw new IllegalStateException("owned field has missing or competing physical evidence");
                    yield new FieldOwnership(site, intent, status,
                            FrontierV3ResourceFieldWitness.read(entry.getCompound("witness")));
                }
                default -> throw new IllegalStateException("unknown v3 cell field claim kind");
            };
            if (claims.containsKey(site) || fieldClaims.put(site, claim) != null)
                throw new IllegalStateException("duplicate or competing v3 field claim owner");
        }
        Map<SubjectId, NativeGrowthFence> nativeGrowthFences = new LinkedHashMap<>();
        {
            ListTag fences = rows(tag, "nativeGrowthFences");
            if (fences.size() > MAX_SITES) throw new IllegalStateException("v3 resource site native fence limit exceeded");
            for (Tag value : fences) {
                CompoundTag entry = (CompoundTag) value;
                if (!entry.contains("site", Tag.TAG_STRING)) throw new IllegalStateException("incomplete v3 native crop fence");
                SubjectId site = new SubjectId(entry.getString("site"));
                if (!claims.containsKey(site) || nativeGrowthFences.put(site, NativeGrowthFence.read(entry)) != null) {
                    throw new IllegalStateException("invalid v3 native crop fence site");
                }
            }
        }
        Map<SubjectId, HarvestReceipt> harvestReceipts = new LinkedHashMap<>();
        {
            ListTag receipts = rows(tag, "harvestReceipts");
            if (receipts.size() > MAX_SITES) throw new IllegalStateException("v3 resource site receipt limit exceeded");
            for (Tag value : receipts) {
                CompoundTag entry = (CompoundTag) value;
                if (!entry.contains("site", Tag.TAG_STRING)) throw new IllegalStateException("incomplete v3 resource site receipt");
                SubjectId site = new SubjectId(entry.getString("site")); Claim claim = claims.get(site);
                if (claim == null || claim.stage() != 7 || claim.harvestedCropSlots() != 64
                        || harvestReceipts.put(site, HarvestReceipt.read(entry)) != null) {
                    throw new IllegalStateException("invalid v3 resource site receipt site");
                }
            }
        }
        Map<SubjectId, FrontierV3ResourceSiteDeliveryWitness> fieldDeliveries = new LinkedHashMap<>();
        ListTag deliveryRows = rows(tag, "fieldDeliveries");
        if (deliveryRows.size() > MAX_SITES) throw new IllegalStateException("field delivery witness limit exceeded");
        for (Tag value : deliveryRows) {
            FrontierV3ResourceSiteDeliveryWitness witness = FrontierV3ResourceSiteDeliveryWitness.read((CompoundTag) value);
            if (!(fieldClaims.get(witness.siteId()) instanceof FieldOwnership)
                    || fieldDeliveries.put(witness.siteId(), witness) != null)
                throw new IllegalStateException("field delivery witness has no unique cell-owned site");
        }
        Map<SubjectId, FrontierV3ResourceSiteHandProjectionWitness> fieldHandProjections = new LinkedHashMap<>();
        ListTag handRows = rows(tag, "fieldHandProjections");
        if (handRows.size() > MAX_SITES) throw new IllegalStateException("field hand witness limit exceeded");
        for (Tag value : handRows) {
            var witness = FrontierV3ResourceSiteHandProjectionWitness.read((CompoundTag) value);
            if (!(fieldClaims.get(witness.siteId()) instanceof FieldOwnership)
                    || fieldHandProjections.put(witness.siteId(), witness) != null)
                throw new IllegalStateException("field hand witness has no unique cell-owned site");
        }
        Map<SubjectId, FrontierV3ResourceFieldPlayerBreakWitness> fieldPlayerBreaks = new LinkedHashMap<>();
        ListTag playerRows = rows(tag, "fieldPlayerBreaks");
        if (playerRows.size() > MAX_SITES) throw new IllegalStateException("field player-break witness limit exceeded");
        for (Tag value : playerRows) {
            var witness = FrontierV3ResourceFieldPlayerBreakWitness.read((CompoundTag) value);
            if (!(fieldClaims.get(witness.siteId()) instanceof FieldOwnership owner)
                    || !owner.witness().cell(witness.cellId()).committed().equals(witness.before())
                    && witness.observedChange().isEmpty()
                    || fieldPlayerBreaks.put(witness.siteId(), witness) != null)
                throw new IllegalStateException("field player-break witness has no unique exact cell owner");
        }
        Map<SubjectId, FrontierV3ResourceFieldWorldChangeWitness> fieldWorldChanges = new LinkedHashMap<>();
        // Format 14 retains the explicit owned-world and foreign-world cause registers.
        ListTag worldRows = rows(tag, "fieldWorldChanges");
        if (worldRows.size() > MAX_SITES) throw new IllegalStateException("field world-change witness limit exceeded");
        for (Tag value : worldRows) {
            var change = FrontierV3ResourceFieldWorldChangeWitness.read((CompoundTag) value);
            if (!(fieldClaims.get(change.siteId()) instanceof FieldOwnership owner)
                    || !owner.witness().cell(change.cellId()).committed().equals(change.before())
                    && !owner.witness().cell(change.cellId()).committed().equals(change.after())
                    || fieldWorldChanges.put(change.siteId(), change) != null)
                throw new IllegalStateException("field world-change witness has no unique exact cell owner");
        }
        Map<SubjectId, FrontierV3ResourceFieldForeignChangeWitness> fieldForeignChanges = new LinkedHashMap<>();
        ListTag foreignRows = rows(tag, "fieldForeignChanges");
        if (foreignRows.size() > MAX_SITES) throw new IllegalStateException("field foreign-change witness limit exceeded");
        for (Tag value : foreignRows) {
            var change = FrontierV3ResourceFieldForeignChangeWitness.read((CompoundTag) value);
            if (!(fieldClaims.get(change.siteId()) instanceof FieldOwnership)
                    || fieldWorldChanges.containsKey(change.siteId()) || fieldPlayerBreaks.containsKey(change.siteId())
                    || fieldForeignChanges.put(change.siteId(), change) != null)
                throw new IllegalStateException("field foreign-change witness has no unique exact cell owner");
        }
        return new FrontierV3ResourceSiteLedger(claims, fieldClaims, nativeGrowthFences, harvestReceipts,
                fieldDeliveries, fieldHandProjections, fieldPlayerBreaks, fieldWorldChanges, fieldForeignChanges);
    }

    private static ListTag rows(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag values)
                || !values.isEmpty() && values.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalStateException("v3 resource site ledger has a missing or mistyped " + key + " section");
        return values;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT); ListTag values = new ListTag();
        claims.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).forEach(entry -> {
            CompoundTag value = new CompoundTag(); value.putString("site", entry.getKey().value());
            value.putString("intent", entry.getValue().intentId().value()); value.putString("status", entry.getValue().status().name()); value.putInt("stage", entry.getValue().stage());
            value.putInt("harvested", entry.getValue().harvestedCropSlots());
            if (entry.getValue().projection() != null) value.put("projection", entry.getValue().projection().write());
            values.add(value);
        });
        tag.put("claims", values);
        ListTag fieldValues = new ListTag();
        fieldClaims.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).forEach(entry -> {
            FieldClaim claim = entry.getValue();
            CompoundTag value = new CompoundTag(); value.putString("site", entry.getKey().value());
            value.putString("intent", claim.intentId().value()); value.putString("status", claim.status().name());
            if (claim instanceof FieldInitialization initialization) {
                value.putString("kind", "INITIAL"); value.put("initial", initialization.cursor().write());
            } else if (claim instanceof FieldOwnership owned) {
                value.putString("kind", "OWNED"); value.put("witness", owned.witness().write());
            } else throw new IllegalStateException("unknown cell field claim variant");
            fieldValues.add(value);
        });
        tag.put("fieldClaims", fieldValues);
        ListTag fences = new ListTag();
        nativeGrowthFences.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).forEach(entry -> {
            CompoundTag value = entry.getValue().write(); value.putString("site", entry.getKey().value()); fences.add(value);
        });
        tag.put("nativeGrowthFences", fences);
        ListTag receipts = new ListTag();
        harvestReceipts.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).forEach(entry -> {
            CompoundTag value = entry.getValue().write(); value.putString("site", entry.getKey().value()); receipts.add(value);
        });
        tag.put("harvestReceipts", receipts);
        ListTag deliveryRows = new ListTag();
        fieldDeliveries.values().stream().sorted(java.util.Comparator.comparing(FrontierV3ResourceSiteDeliveryWitness::siteId))
                .forEach(witness -> deliveryRows.add(witness.write()));
        tag.put("fieldDeliveries", deliveryRows);
        ListTag handRows = new ListTag();
        fieldHandProjections.values().stream().sorted(java.util.Comparator.comparing(FrontierV3ResourceSiteHandProjectionWitness::siteId))
                .forEach(witness -> handRows.add(witness.write()));
        tag.put("fieldHandProjections", handRows);
        ListTag playerRows = new ListTag();
        fieldPlayerBreaks.values().stream().sorted(java.util.Comparator.comparing(FrontierV3ResourceFieldPlayerBreakWitness::siteId))
                .forEach(witness -> playerRows.add(witness.write()));
        tag.put("fieldPlayerBreaks", playerRows);
        ListTag worldRows = new ListTag();
        fieldWorldChanges.values().stream().sorted(java.util.Comparator.comparing(FrontierV3ResourceFieldWorldChangeWitness::siteId))
                .forEach(change -> worldRows.add(change.write()));
        tag.put("fieldWorldChanges", worldRows);
        ListTag foreignRows = new ListTag();
        fieldForeignChanges.values().stream()
                .sorted(java.util.Comparator.comparing(FrontierV3ResourceFieldForeignChangeWitness::siteId))
                .forEach(change -> foreignRows.add(change.write()));
        tag.put("fieldForeignChanges", foreignRows);
        return tag;
    }

    private void transition(SubjectId siteId, Status expected, Status next) {
        if (fieldClaims.containsKey(siteId)) throw new IllegalStateException("cell-owned field cannot use a legacy lifecycle transition");
        Claim prior = claims.get(siteId);
        if (prior == null || prior.status() != expected) throw new IllegalStateException("v3 resource site claim has unexpected lifecycle");
        claims.put(siteId, new Claim(prior.intentId(), next, prior.stage(), prior.harvestedCropSlots(), prior.projection())); setDirty();
    }
    private Claim required(SubjectId siteId) {
        if (fieldClaims.containsKey(siteId)) throw new IllegalStateException("cell-owned field cannot use a legacy receipt or cursor");
        Claim claim = claims.get(siteId); if (claim == null) throw new IllegalStateException("missing v3 resource site claim"); return claim;
    }

    enum Status { PENDING, ACTIVE, CONFLICT }
    sealed interface SiteClaim permits LegacySiteClaim, CellSiteClaim {
        SubjectId siteId();
    }
    record LegacySiteClaim(SubjectId siteId, Claim claim) implements SiteClaim {
        LegacySiteClaim {
            java.util.Objects.requireNonNull(siteId, "legacy field site");
            java.util.Objects.requireNonNull(claim, "legacy field claim");
        }
    }
    record CellSiteClaim(FieldClaim claim) implements SiteClaim {
        CellSiteClaim { java.util.Objects.requireNonNull(claim, "cell field claim"); }
        public SubjectId siteId() { return claim.siteId(); }
    }
    sealed interface FieldClaim permits FieldInitialization, FieldOwnership {
        SubjectId siteId();
        PhysicalIntentId intentId();
        Status status();
    }
    record InitialCursor(long epoch, long layoutRevision, String layoutFingerprint,
                         int nextWrite, int writeCount, boolean prepared) {
        InitialCursor {
            if (epoch != 1 || layoutRevision < 1 || layoutFingerprint == null
                    || !layoutFingerprint.matches("[0-9a-f]{64}") || nextWrite < 0
                    || writeCount < 0 || writeCount > ResourceFieldLayout.MAX_CELLS * 3
                    || nextWrite > writeCount || prepared && nextWrite == writeCount)
                throw new IllegalArgumentException("initial field cursor is invalid");
        }
        static InitialCursor atStart(ResourceSite site) {
            return new InitialCursor(1, site.layout().revision(), site.layout().fingerprint(), 0,
                    FrontierV3ResourceFieldInitialPlan.writeCount(site), false);
        }
        boolean complete() { return nextWrite == writeCount; }
        boolean matches(ResourceSite site) {
            return site.layout().revision() == layoutRevision
                    && site.layout().fingerprint().equals(layoutFingerprint)
                    && FrontierV3ResourceFieldInitialPlan.writeCount(site) == writeCount;
        }
        InitialCursor preparedStep() { return new InitialCursor(epoch, layoutRevision, layoutFingerprint, nextWrite, writeCount, true); }
        InitialCursor advanced() {
            if (!prepared || complete()) throw new IllegalStateException("initial field cursor cannot advance an unprepared step");
            return new InitialCursor(epoch, layoutRevision, layoutFingerprint, nextWrite + 1, writeCount, false);
        }
        CompoundTag write() {
            CompoundTag tag = new CompoundTag(); tag.putLong("epoch", epoch); tag.putLong("revision", layoutRevision);
            tag.putString("fingerprint", layoutFingerprint); tag.putInt("next", nextWrite); tag.putInt("count", writeCount);
            tag.putBoolean("prepared", prepared);
            return tag;
        }
        static InitialCursor read(CompoundTag tag) {
            if (!tag.contains("epoch", Tag.TAG_LONG) || !tag.contains("revision", Tag.TAG_LONG)
                    || !tag.contains("fingerprint", Tag.TAG_STRING) || !tag.contains("next", Tag.TAG_INT)
                    || !tag.contains("count", Tag.TAG_INT) || !tag.contains("prepared", Tag.TAG_BYTE))
                throw new IllegalStateException("incomplete initial field cursor");
            byte prepared = tag.getByte("prepared");
            if (prepared != 0 && prepared != 1) throw new IllegalStateException("invalid initial field write-ahead flag");
            return new InitialCursor(tag.getLong("epoch"), tag.getLong("revision"), tag.getString("fingerprint"),
                    tag.getInt("next"), tag.getInt("count"), prepared == 1);
        }
    }
    record FieldInitialization(SubjectId siteId, PhysicalIntentId intentId, Status status,
                               InitialCursor cursor) implements FieldClaim {
        FieldInitialization {
            java.util.Objects.requireNonNull(siteId, "initial field site");
            java.util.Objects.requireNonNull(intentId, "initial field intent");
            java.util.Objects.requireNonNull(cursor, "initial field cursor");
            if (!siteId.value().startsWith("site:") || status != Status.PENDING && status != Status.CONFLICT)
                throw new IllegalArgumentException("initial field has invalid owner or status");
        }
        FieldInitialization conflicted() { return new FieldInitialization(siteId, intentId, Status.CONFLICT, cursor); }
    }
    record FieldOwnership(SubjectId siteId, PhysicalIntentId intentId, Status status,
                          FrontierV3ResourceFieldWitness witness) implements FieldClaim {
        FieldOwnership {
            java.util.Objects.requireNonNull(siteId, "cell field site");
            java.util.Objects.requireNonNull(intentId, "cell field intent");
            java.util.Objects.requireNonNull(status, "cell field status");
            java.util.Objects.requireNonNull(witness, "cell field physical witness");
            if (!siteId.value().startsWith("site:") || status == Status.PENDING
                    || !siteId.equals(witness.siteId()))
                throw new IllegalArgumentException("cell field claim lacks an active exact site owner");
        }
        FieldOwnership withWitness(FrontierV3ResourceFieldWitness next) {
            return new FieldOwnership(siteId, intentId, status, next);
        }
        FieldOwnership conflicted() { return new FieldOwnership(siteId, intentId, Status.CONFLICT, witness); }
    }
    // Serialized names are stable tags. INITIAL retains its original all-AIR meaning.
    enum ProjectionMode { INITIAL, INITIAL_SOIL, ADVANCE, SUCCESSOR_RESTORE }
    record HarvestReceipt(SubjectId outputId, String itemKind, int count, SubjectId containerId, int slot) {
        HarvestReceipt {
            if (outputId == null || itemKind == null || itemKind.isBlank() || count < 1 || containerId == null || slot < 0) {
                throw new IllegalArgumentException("v3 resource site receipt is invalid");
            }
        }
        static HarvestReceipt from(ExactItemStack output) {
            if (!(output.custody() instanceof InventoryCustody.ContainerSlot slot)) {
                throw new IllegalArgumentException("v3 resource site receipt lacks a container slot");
            }
            return new HarvestReceipt(output.id(), output.itemKind(), output.count(), slot.containerId(), slot.slot());
        }
        boolean matches(ExactItemStack output) { return equals(from(output)); }
        CompoundTag write() {
            CompoundTag tag = new CompoundTag(); tag.putString("output", outputId.value()); tag.putString("kind", itemKind);
            tag.putInt("count", count); tag.putString("container", containerId.value()); tag.putInt("slot", slot); return tag;
        }
        static HarvestReceipt read(CompoundTag tag) {
            if (!tag.contains("output", Tag.TAG_STRING) || !tag.contains("kind", Tag.TAG_STRING) || !tag.contains("count", Tag.TAG_INT)
                    || !tag.contains("container", Tag.TAG_STRING) || !tag.contains("slot", Tag.TAG_INT)) {
                throw new IllegalStateException("incomplete v3 resource site receipt");
            }
            return new HarvestReceipt(new SubjectId(tag.getString("output")), tag.getString("kind"), tag.getInt("count"),
                    new SubjectId(tag.getString("container")), tag.getInt("slot"));
        }
    }
    /** Immutable first event retained independently from the stable facility claim. */
    record NativeGrowthFence(String source, BlockPosition position, int observedAge, int claimStage) {
        NativeGrowthFence {
            if (source == null || source.isBlank() || position == null || observedAge < 0 || observedAge > 7 || claimStage < 0 || claimStage > 7) {
                throw new IllegalArgumentException("v3 native crop fence observation is invalid");
            }
        }
        CompoundTag write() {
            CompoundTag tag = new CompoundTag(); tag.putString("source", source); tag.putInt("x", position.x()); tag.putInt("y", position.y());
            tag.putInt("z", position.z()); tag.putInt("age", observedAge); tag.putInt("claimStage", claimStage); return tag;
        }
        static NativeGrowthFence read(CompoundTag tag) {
            if (!tag.contains("source", Tag.TAG_STRING) || !tag.contains("x", Tag.TAG_INT) || !tag.contains("y", Tag.TAG_INT)
                    || !tag.contains("z", Tag.TAG_INT) || !tag.contains("age", Tag.TAG_INT) || !tag.contains("claimStage", Tag.TAG_INT)) {
                throw new IllegalStateException("incomplete v3 native crop fence observation");
            }
            return new NativeGrowthFence(tag.getString("source"), new BlockPosition(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                    tag.getInt("age"), tag.getInt("claimStage"));
        }
    }
    record ProjectionTransition(String source, int fromStage, int fromHarvestedCropSlots, int targetStage,
                                int targetHarvestedCropSlots, int nextWrite, int writeCount, ProjectionMode mode) {
        ProjectionTransition {
            if (source == null || source.isBlank() || fromStage < 0 || fromStage > 7 || fromHarvestedCropSlots < 0 || fromHarvestedCropSlots > 64
                    || fromStage != 7 && fromHarvestedCropSlots != 0 || targetStage < 0 || targetStage > 7
                    || targetHarvestedCropSlots < 0 || targetHarvestedCropSlots > 64
                    || targetStage != 7 && targetHarvestedCropSlots != 0
                    || nextWrite < 0 || writeCount < 1 || nextWrite > writeCount || mode == null) {
                throw new IllegalArgumentException("v3 resource site projection transition is invalid");
            }
        }
        ProjectionTransition advance() { return new ProjectionTransition(source, fromStage, fromHarvestedCropSlots, targetStage, targetHarvestedCropSlots, nextWrite + 1, writeCount, mode); }
        CompoundTag write() {
            CompoundTag tag = new CompoundTag(); tag.putString("source", source); tag.putInt("fromStage", fromStage); tag.putInt("fromHarvested", fromHarvestedCropSlots); tag.putInt("stage", targetStage);
            tag.putInt("harvested", targetHarvestedCropSlots); tag.putInt("next", nextWrite); tag.putInt("count", writeCount); tag.putString("mode", mode.name());
            return tag;
        }
        static ProjectionTransition read(CompoundTag tag) {
            if (!tag.contains("source", Tag.TAG_STRING) || !tag.contains("fromStage", Tag.TAG_INT) || !tag.contains("fromHarvested", Tag.TAG_INT)
                    || !tag.contains("stage", Tag.TAG_INT) || !tag.contains("harvested", Tag.TAG_INT)
                    || !tag.contains("next", Tag.TAG_INT) || !tag.contains("count", Tag.TAG_INT) || !tag.contains("mode", Tag.TAG_STRING)) {
                throw new IllegalStateException("incomplete v3 resource site projection transition");
            }
            try { return new ProjectionTransition(tag.getString("source"), tag.getInt("fromStage"), tag.getInt("fromHarvested"),
                    tag.getInt("stage"), tag.getInt("harvested"), tag.getInt("next"), tag.getInt("count"), ProjectionMode.valueOf(tag.getString("mode"))); }
            catch (IllegalArgumentException invalid) { throw new IllegalStateException("invalid v3 resource site projection transition", invalid); }
        }
    }
    record Claim(PhysicalIntentId intentId, Status status, int stage, int harvestedCropSlots, ProjectionTransition projection) {
        Claim(PhysicalIntentId intentId, Status status, int stage, int harvestedCropSlots) { this(intentId, status, stage, harvestedCropSlots, null); }
        Claim {
            if (stage < 0 || stage > 7 || harvestedCropSlots < 0 || harvestedCropSlots > 64
                    || stage != 7 && harvestedCropSlots != 0) throw new IllegalArgumentException("v3 resource site claim stage is invalid");
        }
        Claim withProjection(ProjectionTransition next) { return new Claim(intentId, status, stage, harvestedCropSlots, next); }
    }
}
