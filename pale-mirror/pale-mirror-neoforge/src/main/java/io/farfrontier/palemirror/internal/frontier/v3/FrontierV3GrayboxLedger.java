package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/** Spatially partitioned durable provenance; it never grants overwrite authority. */
final class FrontierV3GrayboxLedger extends FrontierV3JournaledSavedData {
    /* Format 6 adds explicitly declared worksite cells in the same block-provenance store. */
    private static final int FORMAT = 6;
    private final Map<Long, Claim> claims;
    private java.util.function.Function<BlockPos, Claim> initialClaims = ignored -> null;
    private final Map<Long, FrontierV3WorksiteBlockWitness> worksiteCells;
    /**
     * The only claim family that needs ordinary per-tick retirement.  Keeping its bounded
     * position index beside the durable provenance map prevents a world-wide cell scan just
     * to discover at most 48 temporary work floors.  It is derived from {@link #claims} on
     * hydration, so it grants no independent materialization authority.
     */
    private final NavigableSet<Long> worksiteStagingPositions;
    private final NavigableSet<Long> externalWorksitePositions = new TreeSet<>();
    private final java.util.Set<Long> changedWorksites = new java.util.HashSet<>();

    private FrontierV3GrayboxLedger() { this(new HashMap<>(), new HashMap<>()); }
    private FrontierV3GrayboxLedger(Map<Long, Claim> claims, Map<Long, FrontierV3WorksiteBlockWitness> worksiteCells) {
        super(FrontierV3PhysicalStoreKind.BLOCKS);
        this.claims = pagedTable("claims", claims, Object::toString, Long::parseLong, FrontierV3GrayboxLedger::encodeClaim,
                FrontierV3GrayboxLedger::decodeClaim);
        this.worksiteCells = pagedTable("worksiteCells", worksiteCells, Object::toString, Long::parseLong, (position, witness) -> {
            var encoded = witness.write(); encoded.putLong("pos", position); return encoded;
        }, FrontierV3GrayboxLedger::decodeWorksite);
        this.worksiteStagingPositions = new TreeSet<>();
        claims.forEach((position, claim) -> {
            if (claim.semanticPart().equals(GrayboxSemanticPart.WORKSITE_STAGING.name())) worksiteStagingPositions.add(position);
        });
        worksiteCells.forEach(this::indexExternalWorksite);
    }
    static FrontierV3GrayboxLedger get(ServerLevel level) {
        var ledger = FrontierV3JournaledSavedData.get(level, FrontierV3PhysicalStoreKind.BLOCKS, FrontierV3GrayboxLedger::new, FrontierV3GrayboxLedger::load);
        ledger.initialClaims = position -> FrontierV3GeneratedStructures.claim(level, position);
        return ledger;
    }
    /** Same bounded provenance implementation for the physical-owner composition harness. */
    static FrontierV3GrayboxLedger inMemory() { return new FrontierV3GrayboxLedger(); }
    @Override boolean pagedRecovery() { return true; }
    @Override FrontierV3PhysicalJournal openJournal(java.nio.file.Path path) throws java.io.IOException {
        return openBlocks(path);
    }
    static FrontierV3BlockJournal openBlocks(java.nio.file.Path path) throws java.io.IOException {
        return FrontierV3BlockJournal.open(path, queryIndex());
    }
    static FrontierV3BlockJournal.IndexPolicy queryIndex() {
        return new FrontierV3BlockJournal.IndexPolicy("graybox-reconciliation-v1", row -> {
            // Closed table codec, not generic storage deciding a worksite's business outcome.
            var record = row.getCompound("record");
            return switch (FrontierV3PhysicalStoreKind.BLOCKS.table(row.getInt("table")).wireTag()) {
                case 1 -> decodeClaim(record.getLong("pos"), record).semanticPart().equals(GrayboxSemanticPart.WORKSITE_STAGING.name());
                case 2 -> external(decodeWorksite(record.getLong("pos"), record));
                default -> throw new IllegalStateException("unregistered block query codec");
            };
        });
    }
    @Override void attachedJournal(FrontierV3PhysicalJournal journal) throws java.io.IOException {
        // Initial decoded/unsaved rows may precede attachment in an offline composition.
        // Preserve their indexes; point reads below also see those pending replacements.
        for (String key : journal.indexedKeys()) {
            byte[] bytes = journal.read(key); if (bytes == null) continue; // Conservative stale index after a partial save.
            var row = FrontierV3JournalNbt.decode(bytes); var record = row.getCompound("record");
            long position = Long.parseLong(row.getString("key"));
            switch (FrontierV3PhysicalStoreKind.BLOCKS.table(row.getInt("table")).wireTag()) {
                case 1 -> index(java.util.Objects.requireNonNull(claims.get(position)), position);
                case 2 -> indexExternalWorksite(position, java.util.Objects.requireNonNull(worksiteCells.get(position)));
                default -> throw new java.io.IOException("unregistered block query codec");
            }
        }
    }
    private static boolean external(FrontierV3WorksiteBlockWitness witness) {
        return witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL
                || witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL_PENDING;
    }
    private static Claim decodeClaim(long position, CompoundTag entry) {
        if (!entry.contains("pos", Tag.TAG_LONG) || entry.getLong("pos") != position)
            throw new IllegalStateException("claim address differs from its record");
        return new Claim(requireText(entry.getString("owner"), "owner"), requireTargetTag(entry.getInt("targetTag")),
                requireText(entry.getString("material"), "material"), requireText(entry.getString("part"), "semantic part"),
                entry.getLong("revision"), entry.getBoolean("deferred"));
    }
    private static FrontierV3WorksiteBlockWitness decodeWorksite(long position, CompoundTag entry) {
        var witness = FrontierV3WorksiteBlockWitness.read(entry); var actual = witness.declaration().position();
        if (!entry.contains("pos", Tag.TAG_LONG) || entry.getLong("pos") != position
                || position != new BlockPos(actual.x(), actual.y(), actual.z()).asLong())
            throw new IllegalStateException("worksite address differs from its record");
        return witness;
    }
    Claim claim(BlockPos position) {
        var changed = claims.get(position.asLong());
        return changed == null ? initialClaims.apply(position) : changed;
    }
    /** Initial provenance is read-only; runtime changes are the only journal rows. */
    static FrontierV3GrayboxLedger inMemory(java.util.function.Function<BlockPos, Claim> initialClaims) {
        var ledger = inMemory(); ledger.initialClaims = java.util.Objects.requireNonNull(initialClaims); return ledger;
    }
    FrontierV3WorksiteBlockWitness worksite(BlockPos position) { return worksiteCells.get(position.asLong()); }
    DurabilityFence worksiteFence(List<FrontierV3WorksiteBlockWitness> witnesses) {
        var records = new java.util.LinkedHashMap<String, CompoundTag>();
        for (var witness : witnesses) {
            var point = witness.declaration().position();
            long position = BlockPos.asLong(point.x(), point.y(), point.z());
            if (!witness.equals(worksiteCells.get(position))) throw new IllegalArgumentException("worksite fence is not current");
            var record = witness.write(); record.putLong("pos", position);
            if (records.putIfAbsent(Long.toString(position), record) != null) throw new IllegalArgumentException("duplicate worksite fence");
        }
        return scalarDurabilityFence("worksiteCells", records);
    }
    java.util.Set<Long> takeWorksiteChanges() {
        var changes = java.util.Set.copyOf(changedWorksites); changedWorksites.clear(); return changes;
    }
    List<BlockPos> externalWorksites() { return externalWorksitePositions.stream().map(BlockPos::of).toList(); }
    private void indexExternalWorksite(long address, FrontierV3WorksiteBlockWitness witness) {
        if (external(witness))
            externalWorksitePositions.add(address);
        else externalWorksitePositions.remove(address);
    }
    void worksite(FrontierV3WorksiteBlockWitness witness) {
        var position = witness.declaration().position();
        long address = new BlockPos(position.x(), position.y(), position.z()).asLong();
        var prior = worksiteCells.get(address);
        if (claim(BlockPos.of(address)) != null || prior != null && !prior.declaration().key().equals(witness.declaration().key()))
            throw new IllegalStateException("competing block provenance at worksite cell " + position
                    + "; retained=" + (prior == null ? claims.get(address) : prior.declaration().key())
                    + "; proposed=" + witness.declaration().key());
        if (!witness.equals(prior)) {
            worksiteCells.put(address, witness); indexExternalWorksite(address, witness); changedWorksites.add(address); setDirty();
        }
    }
    /** The registered owner's exact proof changes declaration identity, never the witnessed world block. */
    FrontierV3WorksiteBlockWitness reclassify(FrontierV3WorksiteBlockWitness expected,
            io.farfrontier.palemirror.frontier.v3.model.WorksiteBlockTransition transition) {
        java.util.Objects.requireNonNull(expected); java.util.Objects.requireNonNull(transition);
        var position = expected.declaration().position();
        long address = new BlockPos(position.x(), position.y(), position.z()).asLong();
        if (claim(BlockPos.of(address)) != null || !expected.equals(worksiteCells.get(address))
                || expected.phase() != FrontierV3WorksiteBlockWitness.Phase.SETTLED
                || !transition.predecessor().equals(expected.declaration()))
            throw new IllegalStateException("worksite reclassification lacks its exact settled predecessor at " + position);
        var successor = transition.successor();
        var rebound = new FrontierV3WorksiteBlockWitness(new io.farfrontier.palemirror.frontier.v3.model.WorksiteBlock(
                successor.key(), successor.position(), successor.revision(), expected.declaration().block()),
                expected.before(), FrontierV3WorksiteBlockWitness.Phase.SETTLED);
        worksiteCells.put(address, rebound); indexExternalWorksite(address, rebound); changedWorksites.add(address); setDirty();
        return rebound;
    }
    void ensureCapacityFor(BlockPos position) {
        if (worksiteCells.containsKey(position.asLong())) throw new IllegalStateException("graybox write crosses declared worksite provenance");
    }
    void applied(BlockPos position, String owner, int targetTag, String material, String semanticPart) {
        ensureCapacityFor(position);
        Claim replacement = new Claim(requireText(owner, "owner"), requireTargetTag(targetTag), requireText(material, "material"), requireText(semanticPart, "semantic part"), 0L, false);
        Claim prior = claim(position);
        if (prior != null && !prior.equals(replacement)) {
            throw new IllegalStateException("v3 graybox claim already belongs to another semantic cell");
        }
        if (prior == null) {
            claims.put(position.asLong(), replacement);
            index(replacement, position.asLong());
            setDirty();
        }
    }
    /** Records a foreign obstruction before any v3 block is placed, so a later pass cannot adopt it. */
    void obstructed(BlockPos position, String owner, int targetTag, String material, String semanticPart) {
        ensureCapacityFor(position);
        Claim blocked = new Claim(requireText(owner, "owner"), requireTargetTag(targetTag), requireText(material, "material"), requireText(semanticPart, "semantic part"), 0L, true);
        Claim prior = claim(position);
        if (prior != null && !prior.equals(blocked)) {
            throw new IllegalStateException("v3 graybox obstruction collides with another semantic cell");
        }
        if (prior == null) {
            claims.put(position.asLong(), blocked);
            index(blocked, position.asLong());
            setDirty();
        }
    }
    /** A physical mirror may defer its exact claim, never manufacture a terminal conflict. */
    void defer(BlockPos position) {
        Claim prior = claim(position);
        if (prior == null || prior.deferred()) return;
        claims.put(position.asLong(), new Claim(prior.owner(), prior.targetTag(), prior.material(), prior.semanticPart(), Math.addExact(prior.revision(), 1L), true)); setDirty();
    }
    /**
     * Records a canonical PM-owned loss that predates this chunk's first physical visit.
     *
     * <p>This is a provenance tombstone, not a desired-state write: it never creates or
     * removes a Minecraft block.  It lets a later exact repair prove that the currently empty
     * cell is the retained loss rather than fresh world air.  A nonmatching prior claim remains
     * a fail-closed ownership error.</p>
     */
    void damaged(BlockPos position, String owner, int targetTag, String material, String semanticPart) {
        ensureCapacityFor(position);
        Claim tombstone = new Claim(requireText(owner, "owner"), requireTargetTag(targetTag), requireText(material, "material"), requireText(semanticPart, "semantic part"), 0L, true);
        Claim prior = claim(position);
        if (prior != null && (!prior.owner().equals(tombstone.owner()) || !prior.material().equals(tombstone.material())
                || !prior.semanticPart().equals(tombstone.semanticPart()) || prior.targetTag() != tombstone.targetTag())) {
            throw new IllegalStateException("v3 graybox damage tombstone collides with another semantic cell");
        }
        if (prior == null) {
            claims.put(position.asLong(), tombstone); index(tombstone, position.asLong()); setDirty();
        } else if (!prior.deferred()) {
            claims.put(position.asLong(), new Claim(tombstone.owner(), tombstone.targetTag(), tombstone.material(), tombstone.semanticPart(), Math.addExact(prior.revision(), 1L), true)); setDirty();
        }
    }
    /**
     * Returns a bounded stable snapshot of claims of one semantic kind.  It is deliberately a
     * ledger query, not materialization authority: callers must still require a naturally
     * loaded exact expected block before retiring a temporary cell.
     */
    List<ClaimAt> claimsWithSemanticPart(String semanticPart) {
        if (semanticPart.equals(GrayboxSemanticPart.WORKSITE_STAGING.name())) {
            return worksiteStagingPositions.stream().map(position -> new ClaimAt(BlockPos.of(position), claims.get(position))).toList();
        }
        return claims.entrySet().stream().filter(entry -> entry.getValue().semanticPart().equals(semanticPart))
                .sorted(Map.Entry.comparingByKey()).map(entry -> new ClaimAt(BlockPos.of(entry.getKey()), entry.getValue())).toList();
    }
    /** Removes one exact temporary claim only after its owned world block has been cleared. */
    void retire(BlockPos position, String owner, int targetTag, String material, String semanticPart) {
        Claim claim = claim(position);
        if (initialClaims.apply(position) != null)
            throw new IllegalStateException("initial structure cannot be retired as temporary staging");
        if (claim == null || claim.deferred() || !claim.owner().equals(owner) || claim.targetTag() != requireTargetTag(targetTag) || !claim.material().equals(material)
                || !claim.semanticPart().equals(semanticPart)) {
            throw new IllegalStateException("v3 graybox retirement does not match its exact claim");
        }
        claims.remove(position.asLong());
        worksiteStagingPositions.remove(position.asLong());
        setDirty();
    }
    /** Restores an exact previously claimed semantic cell after its separately durable repair receipt. */
    void repaired(BlockPos position, String owner, int targetTag, String material, String semanticPart) {
        Claim prior = claim(position);
        Claim restored = new Claim(requireText(owner, "owner"), requireTargetTag(targetTag), requireText(material, "material"), requireText(semanticPart, "semantic part"), Math.addExact(prior.revision(), 1L), false);
        if (prior == null || !prior.owner().equals(restored.owner()) || !prior.material().equals(restored.material())
                || !prior.semanticPart().equals(restored.semanticPart()) || prior.targetTag() != restored.targetTag()) {
            throw new IllegalStateException("v3 repair does not match its original graybox claim");
        }
        if (!prior.equals(restored)) { claims.put(position.asLong(), restored); setDirty(); }
    }
    static FrontierV3GrayboxLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        int format = tag.getInt("format"); if (format != FORMAT) throw new IllegalStateException("incompatible v3 graybox ledger; fresh current-schema world required");
        Map<Long, Claim> claims = new HashMap<>(); ListTag entries = tag.getList("claims", Tag.TAG_COMPOUND);
        for (Tag value : entries) { CompoundTag entry = (CompoundTag) value; long position = entry.getLong("pos");
            Claim claim = new Claim(requireText(entry.getString("owner"), "owner"), requireTargetTag(entry.getInt("targetTag")), requireText(entry.getString("material"), "material"),
                    requireText(entry.getString("part"), "semantic part"), entry.getLong("revision"), entry.getBoolean("deferred"));
            if (claims.put(position, claim) != null) throw new IllegalStateException("duplicate v3 graybox claim"); }
        if (!tag.contains("worksiteCells", Tag.TAG_LIST)) throw new IllegalStateException("current block journal lacks its worksite provenance table");
        Map<Long, FrontierV3WorksiteBlockWitness> worksite = new HashMap<>();
        ListTag cells = tag.getList("worksiteCells", Tag.TAG_COMPOUND);
        for (Tag value : cells) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.contains("pos", Tag.TAG_LONG)) throw new IllegalStateException("worksite cell lacks its physical address");
            long address = entry.getLong("pos"); var witness = FrontierV3WorksiteBlockWitness.read(entry);
            var position = witness.declaration().position();
            if (address != new BlockPos(position.x(), position.y(), position.z()).asLong() || claims.containsKey(address)
                    || worksite.put(address, witness) != null) throw new IllegalStateException("invalid/competing worksite block address");
        }
        return new FrontierV3GrayboxLedger(claims, worksite);
    }

    private static CompoundTag encodeClaim(long position, Claim claim) {
        var value = new CompoundTag(); value.putLong("pos", position); value.putString("owner", claim.owner());
        value.putInt("targetTag", claim.targetTag()); value.putString("material", claim.material()); value.putString("part", claim.semanticPart());
        value.putLong("revision", claim.revision()); value.putBoolean("deferred", claim.deferred()); return value;
    }
    private void index(Claim claim, long position) {
        if (claim.semanticPart().equals(GrayboxSemanticPart.WORKSITE_STAGING.name())) worksiteStagingPositions.add(position);
    }
    @Override protected CompoundTag metadata() { var tag = new CompoundTag(); tag.putInt("format", FORMAT); return tag; }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT); ListTag entries = new ListTag();
        claims.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag value = new CompoundTag();
            value.putLong("pos", entry.getKey());
            value.putString("owner", entry.getValue().owner());
            value.putInt("targetTag", entry.getValue().targetTag());
            value.putString("material", entry.getValue().material());
            value.putString("part", entry.getValue().semanticPart());
            value.putLong("revision", entry.getValue().revision());
            value.putBoolean("deferred", entry.getValue().deferred());
            entries.add(value);
        });
        tag.put("claims", entries);
        ListTag cells = new ListTag();
        worksiteCells.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var value = entry.getValue().write(); value.putLong("pos", entry.getKey()); cells.add(value);
        });
        tag.put("worksiteCells", cells); return tag;
    }
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalStateException("v3 graybox claim " + field + " is blank");
        return value;
    }
    private static int requireTargetTag(int value) {
        return io.farfrontier.palemirror.frontier.v3.model.PhysicalDeltaSemanticTargetKind.fromWireTag(value).wireTag();
    }
    record Claim(String owner, int targetTag, String material, String semanticPart, long revision, boolean deferred) {
        Claim {
            owner = requireText(owner, "owner");
            targetTag = requireTargetTag(targetTag);
            material = requireText(material, "material");
            semanticPart = requireText(semanticPart, "semantic part");
            if (revision < 0L) throw new IllegalStateException("v3 graybox claim revision is negative");
        }
        Claim(String owner, int targetTag, String material, String semanticPart, boolean deferred) {
            this(owner, targetTag, material, semanticPart, 0L, deferred);
        }
        /** Compatibility query for non-writable mirror claims; this is not a diagnostic conflict. */
        @Deprecated(forRemoval = true)
        boolean conflicted() { return deferred; }
    }
    record ClaimAt(BlockPos position, Claim claim) { }
}
