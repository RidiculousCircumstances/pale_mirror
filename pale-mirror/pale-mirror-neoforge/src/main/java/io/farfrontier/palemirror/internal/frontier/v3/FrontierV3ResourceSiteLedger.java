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

/** One current cell-owned field journal; no stage/prefix compatibility authority. */
final class FrontierV3ResourceSiteLedger extends FrontierV3JournaledSavedData {
    private static final int FORMAT = 16;
    static final int MAX_SITES = 12;
    private final Map<SubjectId, FieldClaim> fieldClaims;
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
            new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>()); }
    private FrontierV3ResourceSiteLedger(Map<SubjectId, FieldClaim> fieldClaims,
            Map<SubjectId, FrontierV3ResourceSiteDeliveryWitness> deliveries,
            Map<SubjectId, FrontierV3ResourceSiteHandProjectionWitness> hands,
            Map<SubjectId, FrontierV3ResourceFieldPlayerBreakWitness> breaks,
            Map<SubjectId, FrontierV3ResourceFieldWorldChangeWitness> world,
            Map<SubjectId, FrontierV3ResourceFieldForeignChangeWitness> foreign) {
        super(FrontierV3PhysicalStoreKind.FIELDS);
        if (fieldClaims.size() > MAX_SITES || deliveries.size() > MAX_SITES || hands.size() > MAX_SITES
                || breaks.size() > MAX_SITES || world.size() > MAX_SITES || foreign.size() > MAX_SITES
                || hands.keySet().stream().anyMatch(deliveries::containsKey)
                || breaks.keySet().stream().anyMatch(site -> !fieldClaims.containsKey(site))
                || world.keySet().stream().anyMatch(site -> !fieldClaims.containsKey(site))
                || foreign.keySet().stream().anyMatch(site -> !fieldClaims.containsKey(site)
                    || world.containsKey(site) || breaks.containsKey(site)))
            throw new IllegalArgumentException("invalid cell-owned field evidence");
        this.fieldClaims = fragmentedTable("fieldClaims", fieldClaims, SubjectId::value, new FrontierV3FieldJournalCodec());
        fieldDeliveries = table("fieldDeliveries", deliveries, SubjectId::value, (id, value) -> value.write());
        fieldHandProjections = table("fieldHandProjections", hands, SubjectId::value, (id, value) -> value.write());
        fieldPlayerBreaks = table("fieldPlayerBreaks", breaks, SubjectId::value, (id, value) -> value.write());
        fieldWorldChanges = table("fieldWorldChanges", world, SubjectId::value, (id, value) -> value.write());
        fieldForeignChanges = table("fieldForeignChanges", foreign, SubjectId::value, (id, value) -> value.write());
    }
    static FrontierV3ResourceSiteLedger get(ServerLevel level) {
        return FrontierV3JournaledSavedData.get(level, FrontierV3PhysicalStoreKind.FIELDS,
                FrontierV3ResourceSiteLedger::new, FrontierV3ResourceSiteLedger::load);
    }
    static java.nio.file.Path storageFile(ServerLevel level) {
        return FrontierV3JournaledSavedData.storageFile(level, FrontierV3PhysicalStoreKind.FIELDS);
    }
    @Override protected CompoundTag metadata() { var tag = new CompoundTag(); tag.putInt("format", FORMAT); return tag; }
    static CompoundTag encodeField(FieldClaim claim) {
        var value = new CompoundTag(); value.putString("site", claim.siteId().value());
        value.putString("intent", claim.intentId().value()); value.putString("status", claim.status().name());
        if (claim instanceof FieldInitialization initial) { value.putString("kind", "INITIAL"); value.put("initial", initial.cursor().write()); }
        else if (claim instanceof FieldOwnership owner) { value.putString("kind", "OWNED"); value.put("witness", owner.witness().write()); }
        else throw new IllegalStateException("unknown field claim");
        return value;
    }

    /** Isolated GameTest fixture state; production always uses the level-owned ledger above. */
    static FrontierV3ResourceSiteLedger fixture() {
        return new FrontierV3ResourceSiteLedger();
    }

    SiteClaim siteClaim(SubjectId siteId) {
        FieldClaim cells = fieldClaims.get(siteId); return cells == null ? null : new CellSiteClaim(cells);
    }
    FieldClaim fieldClaim(SubjectId siteId) { return fieldClaims.get(siteId); }
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
                || !owner.witness().admitsWorldChange(change)
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
        // A COLD-accounted lot is owned by its actor account, not by the field's
        // independently materialized block claim. The depot-side worker can become
        // HOT before first-field projection finishes (or while that chunk is absent).
        if (fieldDeliveries.containsKey(witness.siteId()))
            throw new IllegalStateException("field hand projection has a competing physical owner");
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
        reserveFieldInitialization(site, intentId, ResourceFieldCycle.seeded(site.id(), site.layout(), 1));
    }
    void reserveFieldInitialization(ResourceSite site, PhysicalIntentId intentId, ResourceFieldCycle target) {
        FieldClaim claim = new FieldInitialization(site.id(), intentId, Status.PENDING, InitialCursor.atStart(site, target));
        SubjectId siteId = claim.siteId();
        FieldClaim prior = fieldClaims.putIfAbsent(siteId, claim);
        if (prior != null) {
            if (!prior.equals(claim)) throw new IllegalStateException("field site already has another cell claim");
            return;
        }
        if (fieldClaims.size() > MAX_SITES) {
            fieldClaims.remove(siteId); throw new IllegalStateException("v3 field claim limit exceeded");
        }
        setDirty();
    }
    void advanceFieldInitialization(ResourceSite site, FrontierV3ResourceFieldInitialPlan.Review review) {
        FieldInitialization prior = fieldClaim(site.id()) instanceof FieldInitialization value ? value : null;
        if (prior == null || prior.status() != Status.PENDING || !prior.cursor().matches(site)
                || prior.cursor().complete() || !prior.cursor().prepared()
                || !review.writtenThisCall() && !review.reconciledProjection()
                || !review.matches(site, prior.cursor(), FrontierV3ResourceFieldInitialPlan.Disposition.APPLIED))
            throw new IllegalStateException("initial field write lacks its exact observed next block");
        fieldClaims.put(prior.siteId(), new FieldInitialization(prior.siteId(), prior.intentId(), Status.PENDING,
                prior.cursor().advanced())); setDirty();
    }
    void prepareFieldInitialization(ResourceSite site, FrontierV3ResourceFieldInitialPlan.Review review) {
        FieldInitialization prior = fieldClaim(site.id()) instanceof FieldInitialization value ? value : null;
        if (prior == null || prior.status() != Status.PENDING || !prior.cursor().matches(site)
                || prior.cursor().complete() || prior.cursor().prepared()
                || !review.matches(site, prior.cursor(), FrontierV3ResourceFieldInitialPlan.Disposition.BEFORE))
            throw new IllegalStateException("initial field write lacks its exact observed neutral predecessor");
        fieldClaims.put(prior.siteId(), new FieldInitialization(prior.siteId(), prior.intentId(), Status.PENDING,
                prior.cursor().preparedStep(review.observed().orElseThrow()))); setDirty();
    }
    void prepareFieldInitializationBatch(ResourceSite site,
            java.util.List<FrontierV3ResourceFieldInitialPlan.Review> reviews) {
        FieldInitialization prior = fieldClaim(site.id()) instanceof FieldInitialization value ? value : null;
        if (prior == null || prior.status() != Status.PENDING || !prior.cursor().matches(site)
                || prior.cursor().prepared() || reviews.isEmpty()
                || reviews.size() > FrontierV3ResourceSiteExecutor.projectionWriteBudget()
                || prior.cursor().nextWrite() + reviews.size() > prior.cursor().writeCount())
            throw new IllegalStateException("initial field batch lacks its exact unprepared owner");
        for (int offset = 0; offset < reviews.size(); offset++) {
            var review = reviews.get(offset);
            var step = FrontierV3ResourceFieldInitialPlan.stepAt(site, prior.cursor().nextWrite() + offset, prior.cursor().target());
            boolean emptyTarget = (step.target().isAir() || step.target().is(net.minecraft.world.level.block.Blocks.DIRT))
                    && review.disposition() == FrontierV3ResourceFieldInitialPlan.Disposition.APPLIED;
            if (!review.step().equals(step) || review.observed().isEmpty()
                    || !emptyTarget && review.disposition() != FrontierV3ResourceFieldInitialPlan.Disposition.BEFORE)
                throw new IllegalStateException("initial field batch has an unloaded, foreign or unclaimed predecessor");
        }
        fieldClaims.put(site.id(), new FieldInitialization(site.id(), prior.intentId(), Status.PENDING,
                prior.cursor().preparedBatch(reviews.stream().map(review -> review.observed().orElseThrow()).toList())));
        setDirty();
    }
    void activateField(ServerLevel level, ResourceSite site, ResourceFieldCycle target, FrontierV3ResourceFieldWitness witness) {
        FieldInitialization prior = fieldClaim(site.id()) instanceof FieldInitialization value ? value : null;
        if (prior == null || prior.status() != Status.PENDING || !prior.cursor().matches(site)
                || !prior.cursor().complete() || !witness.matchesCycle(target)
                || !prior.cursor().target().equals(witness))
            throw new IllegalStateException("initial field cannot activate without its complete exact retained target");
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
    void conflict(SubjectId siteId) {
        FieldClaim field = fieldClaims.get(siteId);
        if (field != null) {
            if (field.status() == Status.CONFLICT) return;
            fieldClaims.put(siteId, field instanceof FieldInitialization initial
                    ? initial.conflicted() : ((FieldOwnership) field).conflicted());
            setDirty();
            return;
        }
    }

    static FrontierV3ResourceSiteLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!java.util.Set.of("format", "fieldClaims", "fieldDeliveries", "fieldHandProjections", "fieldPlayerBreaks", "fieldWorldChanges", "fieldForeignChanges").containsAll(tag.getAllKeys()))
            throw new IllegalStateException("obsolete or undeclared field ledger section");
        int format = tag.getInt("format");
        if (format != FORMAT) throw new IllegalStateException("incompatible v3 resource site ledger: format " + format + "; fresh current-schema world required");
        Map<SubjectId, FieldClaim> fieldClaims = new LinkedHashMap<>();
        ListTag fieldValues = rows(tag, "fieldClaims");
        if (fieldValues.size() > MAX_SITES)
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
            if (fieldClaims.put(site, claim) != null)
                throw new IllegalStateException("duplicate or competing v3 field claim owner");
        }
        Map<SubjectId, FrontierV3ResourceSiteDeliveryWitness> fieldDeliveries = new LinkedHashMap<>();
        ListTag deliveryRows = rows(tag, "fieldDeliveries");
        if (deliveryRows.size() > MAX_SITES) throw new IllegalStateException("field delivery witness limit exceeded");
        for (Tag value : deliveryRows) {
            FrontierV3ResourceSiteDeliveryWitness witness = FrontierV3ResourceSiteDeliveryWitness.read((CompoundTag) value);
            if (fieldDeliveries.put(witness.siteId(), witness) != null)
                throw new IllegalStateException("field delivery witness has no unique COLD crop owner");
        }
        Map<SubjectId, FrontierV3ResourceSiteHandProjectionWitness> fieldHandProjections = new LinkedHashMap<>();
        ListTag handRows = rows(tag, "fieldHandProjections");
        if (handRows.size() > MAX_SITES) throw new IllegalStateException("field hand witness limit exceeded");
        for (Tag value : handRows) {
            var witness = FrontierV3ResourceSiteHandProjectionWitness.read((CompoundTag) value);
            if (fieldHandProjections.put(witness.siteId(), witness) != null)
                throw new IllegalStateException("field hand witness has no unique COLD crop owner");
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
                    || !owner.witness().retainsWorldChange(change)
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
        return new FrontierV3ResourceSiteLedger(fieldClaims,
                fieldDeliveries, fieldHandProjections, fieldPlayerBreaks, fieldWorldChanges, fieldForeignChanges);
    }

    private static ListTag rows(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag values)
                || !values.isEmpty() && values.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalStateException("v3 resource site ledger has a missing or mistyped " + key + " section");
        return values;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT);
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

    enum Status { PENDING, ACTIVE, CONFLICT }
    sealed interface SiteClaim permits CellSiteClaim {
        SubjectId siteId();
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
    record InitialBatch(int start, java.util.List<net.minecraft.world.level.block.state.BlockState> predecessors) {
        InitialBatch {
            predecessors = java.util.List.copyOf(predecessors);
            if (start < 0 || predecessors.isEmpty() || predecessors.size() > FrontierV3ResourceSiteExecutor.projectionWriteBudget())
                throw new IllegalArgumentException("initial projection batch is outside its bounded window");
        }
        int end() { return start + predecessors.size(); }
        CompoundTag write() {
            var tag = new CompoundTag(); tag.putInt("start", start);
            var states = new ListTag();
            predecessors.forEach(state -> states.add(net.minecraft.nbt.NbtUtils.writeBlockState(state)));
            tag.put("predecessors", states); return tag;
        }
        static InitialBatch read(CompoundTag tag) {
            if (!tag.contains("start", Tag.TAG_INT) || !tag.contains("predecessors", Tag.TAG_LIST))
                throw new IllegalStateException("incomplete initial projection batch");
            var values = tag.getList("predecessors", Tag.TAG_COMPOUND);
            if (values.isEmpty() || values.size() > FrontierV3ResourceSiteExecutor.projectionWriteBudget())
                throw new IllegalStateException("initial projection predecessors exceed the bounded window");
            var states = new java.util.ArrayList<net.minecraft.world.level.block.state.BlockState>();
            for (var value : values) {
                var stateTag = (CompoundTag) value;
                var state = net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), stateTag);
                if (!net.minecraft.nbt.NbtUtils.writeBlockState(state).equals(stateTag))
                    throw new IllegalStateException("unknown or malformed initial projection predecessor");
                states.add(state);
            }
            return new InitialBatch(tag.getInt("start"), states);
        }
    }
    record InitialCursor(long epoch, long layoutRevision, String layoutFingerprint,
                         int nextWrite, int writeCount, java.util.Optional<InitialBatch> batch,
                         FrontierV3ResourceFieldWitness target) {
        InitialCursor {
            java.util.Objects.requireNonNull(target, "retained initial field target");
            java.util.Objects.requireNonNull(batch, "initial projection batch");
            if (!target.projectionImageOnly() || epoch < 1 || epoch != target.epoch() || layoutRevision != target.layoutRevision()
                    || !java.util.Objects.equals(layoutFingerprint, target.layoutFingerprint())
                    || layoutRevision < 1 || layoutFingerprint == null
                    || !layoutFingerprint.matches("[0-9a-f]{64}") || nextWrite < 0
                    || writeCount < 0 || writeCount > ResourceFieldLayout.MAX_CELLS * 3
                    || nextWrite > writeCount || batch.filter(value -> value.start() > nextWrite
                        || value.end() <= nextWrite || value.end() > writeCount).isPresent())
                throw new IllegalArgumentException("initial field cursor is invalid");
        }
        static InitialCursor atStart(ResourceSite site, ResourceFieldCycle cycle) {
            if (!cycle.siteId().equals(site.id()) || !cycle.layout().equals(site.layout())
                    || !cycle.pendingPlayerBreaks().isEmpty())
                throw new IllegalArgumentException("initial field has a foreign or unsettled canonical target");
            var target = FrontierV3ResourceFieldWitness.claimed(site.id(), cycle.epoch(),
                    ResourceFieldPhysicalSurface.fromCycle(cycle));
            return new InitialCursor(cycle.epoch(), site.layout().revision(), site.layout().fingerprint(), 0,
                    FrontierV3ResourceFieldInitialPlan.writeCount(site), java.util.Optional.empty(), target);
        }
        boolean prepared() { return batch.isPresent(); }
        net.minecraft.world.level.block.state.BlockState predecessor() {
            var prepared = batch.orElseThrow(); return prepared.predecessors().get(nextWrite - prepared.start());
        }
        boolean complete() { return nextWrite == writeCount; }
        boolean matches(ResourceSite site) {
            return site.layout().revision() == layoutRevision
                    && site.layout().fingerprint().equals(layoutFingerprint)
                    && FrontierV3ResourceFieldInitialPlan.writeCount(site) == writeCount
                    && target.matchesLayout(site.id(), site.layout());
        }
        InitialCursor preparedStep(net.minecraft.world.level.block.state.BlockState before) {
            return preparedBatch(java.util.List.of(before));
        }
        InitialCursor preparedBatch(java.util.List<net.minecraft.world.level.block.state.BlockState> before) {
            if (prepared() || complete()) throw new IllegalStateException("initial field already has a prepared batch");
            return new InitialCursor(epoch, layoutRevision, layoutFingerprint, nextWrite, writeCount,
                    java.util.Optional.of(new InitialBatch(nextWrite, before)), target);
        }
        InitialCursor advanced() {
            if (!prepared() || complete()) throw new IllegalStateException("initial field cursor cannot advance an unprepared step");
            var retained = nextWrite + 1 == batch.orElseThrow().end() ? java.util.Optional.<InitialBatch>empty() : batch;
            return new InitialCursor(epoch, layoutRevision, layoutFingerprint, nextWrite + 1, writeCount, retained, target);
        }
        CompoundTag writeHeader() {
            CompoundTag tag = new CompoundTag(); tag.putLong("epoch", epoch); tag.putLong("revision", layoutRevision);
            tag.putString("fingerprint", layoutFingerprint); tag.putInt("next", nextWrite); tag.putInt("count", writeCount);
            batch.ifPresent(value -> tag.put("batch", value.write()));
            tag.put("target", target.writeHeader());
            return tag;
        }
        CompoundTag write() { var tag = writeHeader(); tag.put("target", target.write()); return tag; }
        static InitialCursor read(CompoundTag tag) {
            if (!tag.contains("epoch", Tag.TAG_LONG) || !tag.contains("revision", Tag.TAG_LONG)
                    || !tag.contains("fingerprint", Tag.TAG_STRING) || !tag.contains("next", Tag.TAG_INT)
                    || !tag.contains("count", Tag.TAG_INT)
                    || !tag.contains("target", Tag.TAG_COMPOUND)
                    || tag.contains("batch") && !tag.contains("batch", Tag.TAG_COMPOUND)
                    || tag.contains("prepared"))
                throw new IllegalStateException("incomplete initial field cursor");
            return new InitialCursor(tag.getLong("epoch"), tag.getLong("revision"), tag.getString("fingerprint"),
                    tag.getInt("next"), tag.getInt("count"), tag.contains("batch", Tag.TAG_COMPOUND)
                        ? java.util.Optional.of(InitialBatch.read(tag.getCompound("batch"))) : java.util.Optional.empty(),
                    FrontierV3ResourceFieldWitness.read(tag.getCompound("target")));
        }
    }
    record FieldInitialization(SubjectId siteId, PhysicalIntentId intentId, Status status,
                               InitialCursor cursor) implements FieldClaim {
        FieldInitialization {
            java.util.Objects.requireNonNull(siteId, "initial field site");
            java.util.Objects.requireNonNull(intentId, "initial field intent");
            java.util.Objects.requireNonNull(cursor, "initial field cursor");
            if (!siteId.equals(cursor.target().siteId())
                    || !siteId.value().startsWith("site:") || status != Status.PENDING && status != Status.CONFLICT)
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
}
