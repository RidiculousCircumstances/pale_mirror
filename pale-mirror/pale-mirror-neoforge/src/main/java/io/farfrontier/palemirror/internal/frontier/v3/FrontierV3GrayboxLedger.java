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

/** Durable, bounded provenance for v3 graybox cells; it never grants overwrite authority. */
final class FrontierV3GrayboxLedger extends FrontierV3JournaledSavedData {
    /* Format 6 adds explicitly declared worksite cells in the same block-provenance store. */
    private static final int FORMAT = 6;
    private static final int MAX_CELLS = 65_536;
    private final Map<Long, Claim> claims;
    private final Map<Long, FrontierV3WorksiteBlockWitness> worksiteCells;
    /**
     * The only claim family that needs ordinary per-tick retirement.  Keeping its bounded
     * position index beside the durable provenance map prevents a global 65,536-cell scan just
     * to discover at most 48 temporary work floors.  It is derived from {@link #claims} on
     * hydration, so it grants no independent materialization authority.
     */
    private final NavigableSet<Long> worksiteStagingPositions;
    private final NavigableSet<Long> externalWorksitePositions = new TreeSet<>();

    private FrontierV3GrayboxLedger() { this(new HashMap<>(), new HashMap<>()); }
    private FrontierV3GrayboxLedger(Map<Long, Claim> claims, Map<Long, FrontierV3WorksiteBlockWitness> worksiteCells) {
        super(FrontierV3PhysicalStoreKind.BLOCKS);
        this.claims = table("claims", claims, Object::toString, (position, claim) -> encodeClaim(position, claim));
        this.worksiteCells = table("worksiteCells", worksiteCells, Object::toString, (position, witness) -> {
            var encoded = witness.write(); encoded.putLong("pos", position); return encoded;
        });
        this.worksiteStagingPositions = new TreeSet<>();
        claims.forEach((position, claim) -> {
            if (claim.semanticPart().equals(GrayboxSemanticPart.WORKSITE_STAGING.name())) worksiteStagingPositions.add(position);
        });
        worksiteCells.forEach(this::indexExternalWorksite);
    }
    static FrontierV3GrayboxLedger get(ServerLevel level) {
        return FrontierV3JournaledSavedData.get(level, FrontierV3PhysicalStoreKind.BLOCKS, FrontierV3GrayboxLedger::new, FrontierV3GrayboxLedger::load);
    }
    /** Same bounded provenance implementation for the physical-owner composition harness. */
    static FrontierV3GrayboxLedger inMemory() { return new FrontierV3GrayboxLedger(); }
    Claim claim(BlockPos position) { return claims.get(position.asLong()); }
    FrontierV3WorksiteBlockWitness worksite(BlockPos position) { return worksiteCells.get(position.asLong()); }
    List<BlockPos> externalWorksites() { return externalWorksitePositions.stream().map(BlockPos::of).toList(); }
    private void indexExternalWorksite(long address, FrontierV3WorksiteBlockWitness witness) {
        if (witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL
                || witness.phase() == FrontierV3WorksiteBlockWitness.Phase.EXTERNAL_PENDING)
            externalWorksitePositions.add(address);
        else externalWorksitePositions.remove(address);
    }
    void worksite(FrontierV3WorksiteBlockWitness witness) {
        var position = witness.declaration().position();
        long address = new BlockPos(position.x(), position.y(), position.z()).asLong();
        var prior = worksiteCells.get(address);
        if (claims.containsKey(address) || prior != null && !prior.declaration().key().equals(witness.declaration().key()))
            throw new IllegalStateException("competing block provenance at worksite cell");
        if (prior == null && claims.size() + worksiteCells.size() >= MAX_CELLS)
            throw new IllegalStateException("bounded physical block provenance is full");
        if (!witness.equals(prior)) {
            worksiteCells.put(address, witness); indexExternalWorksite(address, witness); setDirty();
        }
    }
    void ensureCapacityFor(BlockPos position) {
        if (worksiteCells.containsKey(position.asLong())) throw new IllegalStateException("graybox write crosses declared worksite provenance");
        if (!claims.containsKey(position.asLong()) && claims.size() + worksiteCells.size() >= MAX_CELLS) {
            throw new IllegalStateException("v3 graybox claim limit exceeded");
        }
    }
    void applied(BlockPos position, String owner, int targetTag, String material, String semanticPart) {
        ensureCapacityFor(position);
        Claim replacement = new Claim(requireText(owner, "owner"), requireTargetTag(targetTag), requireText(material, "material"), requireText(semanticPart, "semantic part"), 0L, false);
        Claim prior = claims.putIfAbsent(position.asLong(), replacement);
        if (prior != null && !prior.equals(replacement)) {
            throw new IllegalStateException("v3 graybox claim already belongs to another semantic cell");
        }
        if (prior == null) {
            index(replacement, position.asLong());
            setDirty();
        }
    }
    /** Records a foreign obstruction before any v3 block is placed, so a later pass cannot adopt it. */
    void obstructed(BlockPos position, String owner, int targetTag, String material, String semanticPart) {
        ensureCapacityFor(position);
        Claim blocked = new Claim(requireText(owner, "owner"), requireTargetTag(targetTag), requireText(material, "material"), requireText(semanticPart, "semantic part"), 0L, true);
        Claim prior = claims.putIfAbsent(position.asLong(), blocked);
        if (prior != null && !prior.equals(blocked)) {
            throw new IllegalStateException("v3 graybox obstruction collides with another semantic cell");
        }
        if (prior == null) {
            index(blocked, position.asLong());
            setDirty();
        }
    }
    /** A physical mirror may defer its exact claim, never manufacture a terminal conflict. */
    void defer(BlockPos position) {
        Claim prior = claims.get(position.asLong());
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
        Claim prior = claims.putIfAbsent(position.asLong(), tombstone);
        if (prior != null && (!prior.owner().equals(tombstone.owner()) || !prior.material().equals(tombstone.material())
                || !prior.semanticPart().equals(tombstone.semanticPart()) || prior.targetTag() != tombstone.targetTag())) {
            throw new IllegalStateException("v3 graybox damage tombstone collides with another semantic cell");
        }
        if (prior == null) {
            index(tombstone, position.asLong()); setDirty();
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
        Claim claim = claims.get(position.asLong());
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
        Claim prior = claims.get(position.asLong());
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
        if (entries.size() > MAX_CELLS) throw new IllegalStateException("v3 graybox claim limit exceeded");
        for (Tag value : entries) { CompoundTag entry = (CompoundTag) value; long position = entry.getLong("pos");
            Claim claim = new Claim(requireText(entry.getString("owner"), "owner"), requireTargetTag(entry.getInt("targetTag")), requireText(entry.getString("material"), "material"),
                    requireText(entry.getString("part"), "semantic part"), entry.getLong("revision"), entry.getBoolean("deferred"));
            if (claims.put(position, claim) != null) throw new IllegalStateException("duplicate v3 graybox claim"); }
        if (!tag.contains("worksiteCells", Tag.TAG_LIST)) throw new IllegalStateException("current block journal lacks its worksite provenance table");
        Map<Long, FrontierV3WorksiteBlockWitness> worksite = new HashMap<>();
        ListTag cells = tag.getList("worksiteCells", Tag.TAG_COMPOUND);
        if (entries.size() + cells.size() > MAX_CELLS) throw new IllegalStateException("bounded physical block provenance is full");
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
