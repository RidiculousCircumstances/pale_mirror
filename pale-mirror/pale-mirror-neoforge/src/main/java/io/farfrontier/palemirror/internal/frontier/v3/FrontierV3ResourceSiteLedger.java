package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
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

/** Durable ownership boundary for the twelve fixed v3 farm sites, never a block template. */
final class FrontierV3ResourceSiteLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_resource_sites";
    private static final int FORMAT = 6;
    static final int MAX_SITES = 12;
    private final Map<SubjectId, Claim> claims;
    /** First blocked native crop attempt is a diagnostic fact, not part of facility ownership. */
    private final Map<SubjectId, NativeGrowthFence> nativeGrowthFences;
    /**
     * A persisted write-ahead fence for the one physical harvest receipt. It distinguishes a
     * first empty depot slot from output removed before canonical confirmation, which fails
     * closed rather than minting a replacement.
     */
    private final Map<SubjectId, HarvestReceipt> harvestReceipts;

    private FrontierV3ResourceSiteLedger() { this(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>()); }
    private FrontierV3ResourceSiteLedger(Map<SubjectId, Claim> claims, Map<SubjectId, NativeGrowthFence> nativeGrowthFences,
                                         Map<SubjectId, HarvestReceipt> harvestReceipts) {
        this.claims = claims; this.nativeGrowthFences = nativeGrowthFences; this.harvestReceipts = harvestReceipts;
    }

    static FrontierV3ResourceSiteLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3ResourceSiteLedger::new,
                FrontierV3ResourceSiteLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME);
    }

    /** Isolated GameTest fixture state; production always uses the level-owned ledger above. */
    static FrontierV3ResourceSiteLedger fixture() {
        return new FrontierV3ResourceSiteLedger();
    }

    Claim claim(SubjectId siteId) { return claims.get(siteId); }
    NativeGrowthFence nativeGrowthFence(SubjectId siteId) { return nativeGrowthFences.get(siteId); }
    boolean hasHarvestReceipt(SubjectId siteId, ExactItemStack output) {
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
        Claim next = new Claim(intentId, Status.PENDING, 0, 0);
        Claim prior = claims.get(siteId); if (prior != null) {
            if (!prior.equals(next)) throw new IllegalStateException("v3 resource site claim changes intent or lifecycle");
            return;
        }
        if (claims.size() >= MAX_SITES) throw new IllegalStateException("v3 resource site claim limit exceeded");
        claims.put(siteId, next); setDirty();
    }

    /**
     * Re-establishes only the durable terminal predecessor shape of an exact composed
     * successor.  Its following projection retains this stage-seven/64-slot origin, so an
     * interruption cannot reinterpret irrigated farmland and AIR crops as a neutral field.
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
        if (claims.size() >= MAX_SITES) throw new IllegalStateException("v3 resource site claim limit exceeded");
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
        claims.put(siteId, new Claim(prior.intentId(), prior.status(), stage, stage == 7 ? prior.harvestedCropSlots() : 0, prior.projection())); setDirty();
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
        Claim prior = claims.get(siteId);
        if (prior == null || prior.status() == Status.CONFLICT) return;
        claims.put(siteId, new Claim(prior.intentId(), Status.CONFLICT, prior.stage(), prior.harvestedCropSlots(), null)); setDirty();
    }

    static FrontierV3ResourceSiteLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        int format = tag.getInt("format"); if (format != 3 && format != 4 && format != 5 && format != FORMAT) throw new IllegalStateException("incompatible v3 resource site ledger");
        ListTag values = tag.getList("claims", Tag.TAG_COMPOUND);
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
        Map<SubjectId, NativeGrowthFence> nativeGrowthFences = new LinkedHashMap<>();
        if (format >= 5) {
            ListTag fences = tag.getList("nativeGrowthFences", Tag.TAG_COMPOUND);
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
        if (format >= 6) {
            ListTag receipts = tag.getList("harvestReceipts", Tag.TAG_COMPOUND);
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
        return new FrontierV3ResourceSiteLedger(claims, nativeGrowthFences, harvestReceipts);
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
        ListTag fences = new ListTag();
        nativeGrowthFences.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).forEach(entry -> {
            CompoundTag value = entry.getValue().write(); value.putString("site", entry.getKey().value()); fences.add(value);
        });
        tag.put("nativeGrowthFences", fences);
        ListTag receipts = new ListTag();
        harvestReceipts.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).forEach(entry -> {
            CompoundTag value = entry.getValue().write(); value.putString("site", entry.getKey().value()); receipts.add(value);
        });
        tag.put("harvestReceipts", receipts); return tag;
    }

    private void transition(SubjectId siteId, Status expected, Status next) {
        Claim prior = claims.get(siteId);
        if (prior == null || prior.status() != expected) throw new IllegalStateException("v3 resource site claim has unexpected lifecycle");
        claims.put(siteId, new Claim(prior.intentId(), next, prior.stage(), prior.harvestedCropSlots(), prior.projection())); setDirty();
    }
    private Claim required(SubjectId siteId) {
        Claim claim = claims.get(siteId); if (claim == null) throw new IllegalStateException("missing v3 resource site claim"); return claim;
    }

    enum Status { PENDING, ACTIVE, CONFLICT }
    enum ProjectionMode { INITIAL, ADVANCE, SUCCESSOR_RESTORE }
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
