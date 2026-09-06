package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Durable pre-impact evidence for field cells hit by a real explosion.
 *
 * <p>The canonical field plan and active resource-site claim already provide the exact baseline,
 * so this ledger retains one bounded site-level witness instead of duplicating 128 block states.
 * It is an observation queue, never permission to replay a blast or rebuild a field.</p>
 */
final class FrontierV3ResourceSiteExplosionLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_resource_site_explosions";
    private static final int FORMAT = 1;
    private static final int MAX_EFFECTS = 64;
    private static final int MAX_SITES_PER_EFFECT = FrontierV3ResourceSiteLedger.MAX_SITES;
    private final LinkedHashMap<String, Pending> pending;
    private long nextExternalSequence;

    private FrontierV3ResourceSiteExplosionLedger() { this(new LinkedHashMap<>(), 0L); }
    private FrontierV3ResourceSiteExplosionLedger(LinkedHashMap<String, Pending> pending, long nextExternalSequence) {
        this.pending = pending; this.nextExternalSequence = nextExternalSequence;
    }

    static FrontierV3ResourceSiteExplosionLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3ResourceSiteExplosionLedger::new,
                FrontierV3ResourceSiteExplosionLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME);
    }

    boolean captureExternal(ServerLevel level, long gameTime, List<BlockPos> affected, List<ResourceSite> sites,
                            FrontierV3ResourceSiteLedger claims) {
        String id = "effect:external-resource-site-explosion:" + Long.toUnsignedString(nextExternalSequence++);
        return capture(level, gameTime, id, affected, sites, claims);
    }

    boolean captureManaged(ServerLevel level, long gameTime, PhysicalIntentId intentId, List<BlockPos> affected,
                           List<ResourceSite> sites, FrontierV3ResourceSiteLedger claims) {
        Objects.requireNonNull(intentId, "intent id");
        return capture(level, gameTime, "effect:managed-resource-site-explosion:" + intentId.value(), affected, sites, claims);
    }

    private boolean capture(ServerLevel level, long gameTime, String id, List<BlockPos> affected, List<ResourceSite> sites,
                            FrontierV3ResourceSiteLedger claims) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(affected, "affected blocks");
        Objects.requireNonNull(sites, "resource sites"); Objects.requireNonNull(claims, "resource site claims");
        if (pending.containsKey(id)) return false;
        List<Candidate> candidates = sites.stream().sorted(Comparator.comparing(ResourceSite::id)).map(site -> candidate(level, affected, site, claims))
                .flatMap(Optional::stream).toList();
        if (candidates.isEmpty()) return false;
        if (pending.size() >= MAX_EFFECTS) throw new IllegalStateException("v3 resource-site explosion retention exceeded");
        pending.put(id, new Pending(id, gameTime, candidates)); setDirty(); return true;
    }

    Optional<Ready> nextReady(long gameTime) {
        for (Pending effect : pending.values()) {
            if (effect.capturedAtGameTime() < gameTime && !effect.candidates().isEmpty()) {
                return Optional.of(new Ready(effect.id(), effect.candidates().getFirst()));
            }
        }
        return Optional.empty();
    }

    void resolve(Ready ready) {
        Pending effect = pending.get(ready.effectId());
        if (effect == null || effect.candidates().isEmpty() || !effect.candidates().getFirst().equals(ready.candidate())) {
            throw new IllegalStateException("v3 resource-site explosion resolution is not the retained queue head");
        }
        List<Candidate> remaining = effect.candidates().subList(1, effect.candidates().size());
        if (remaining.isEmpty()) pending.remove(effect.id());
        else pending.put(effect.id(), new Pending(effect.id(), effect.capturedAtGameTime(), remaining));
        setDirty();
    }

    private static Optional<Candidate> candidate(ServerLevel level, List<BlockPos> affected, ResourceSite site,
                                                  FrontierV3ResourceSiteLedger claims) {
        FrontierV3ResourceSiteLedger.Claim claim = claims.claim(site.id());
        if (claim == null || claim.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE || !FrontierV3ResourceSiteExecutor.loaded(level, site)
                || !FrontierV3ResourceSiteExecutor.matches(level, site, claim.stage())) return Optional.empty();
        return affected.stream().distinct().sorted(Comparator.comparingLong(BlockPos::asLong)).filter(position -> contains(site, position)).findFirst()
                .map(position -> new Candidate(site.id(), claim.stage(), position.asLong()));
    }

    private static boolean contains(ResourceSite site, BlockPos position) {
        return site.managedSlots().stream().map(slot -> new BlockPos(slot.x(), slot.y(), slot.z())).anyMatch(position::equals);
    }

    static FrontierV3ResourceSiteExplosionLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != FORMAT) throw new IllegalStateException("incompatible v3 resource-site explosion ledger");
        long next = tag.getLong("nextExternalSequence"); if (next < 0L) throw new IllegalStateException("invalid v3 resource-site explosion sequence");
        ListTag values = tag.getList("pending", Tag.TAG_COMPOUND);
        if (values.size() > MAX_EFFECTS) throw new IllegalStateException("v3 resource-site explosion retention exceeded");
        LinkedHashMap<String, Pending> pending = new LinkedHashMap<>();
        for (Tag value : values) {
            Pending entry = Pending.load((CompoundTag) value);
            if (pending.putIfAbsent(entry.id(), entry) != null) throw new IllegalStateException("duplicate v3 resource-site explosion effect");
        }
        return new FrontierV3ResourceSiteExplosionLedger(pending, next);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT); tag.putLong("nextExternalSequence", nextExternalSequence);
        ListTag values = new ListTag(); pending.values().forEach(value -> values.add(value.save())); tag.put("pending", values);
        return tag;
    }

    record Ready(String effectId, Candidate candidate) { }
    record Candidate(SubjectId siteId, int expectedStage, long witnessPosition) {
        Candidate {
            Objects.requireNonNull(siteId, "resource site id");
            if (!siteId.value().startsWith("site:") || expectedStage < 0 || expectedStage > 7) throw new IllegalArgumentException("invalid resource-site explosion candidate");
        }
        BlockPosition witness() {
            BlockPos value = BlockPos.of(witnessPosition); return new BlockPosition(value.getX(), value.getY(), value.getZ());
        }
        CompoundTag save() {
            CompoundTag value = new CompoundTag(); value.putString("site", siteId.value()); value.putInt("stage", expectedStage); value.putLong("witness", witnessPosition); return value;
        }
        static Candidate load(CompoundTag value) {
            if (!value.contains("site", Tag.TAG_STRING) || !value.contains("stage", Tag.TAG_INT) || !value.contains("witness", Tag.TAG_LONG)) {
                throw new IllegalStateException("incomplete v3 resource-site explosion candidate");
            }
            return new Candidate(new SubjectId(value.getString("site")), value.getInt("stage"), value.getLong("witness"));
        }
    }
    record Pending(String id, long capturedAtGameTime, List<Candidate> candidates) {
        Pending {
            if (id == null || !id.startsWith("effect:") || !id.contains("resource-site-explosion:") || capturedAtGameTime < 0L
                    || candidates == null || candidates.isEmpty() || candidates.size() > MAX_SITES_PER_EFFECT) {
                throw new IllegalArgumentException("invalid v3 resource-site explosion effect");
            }
            candidates = List.copyOf(candidates);
            if (candidates.stream().map(Candidate::siteId).collect(java.util.stream.Collectors.toSet()).size() != candidates.size()) {
                throw new IllegalArgumentException("duplicate v3 resource-site explosion site");
            }
        }
        CompoundTag save() {
            CompoundTag value = new CompoundTag(); value.putString("id", id); value.putLong("capturedAt", capturedAtGameTime);
            ListTag sites = new ListTag(); candidates.forEach(candidate -> sites.add(candidate.save())); value.put("sites", sites); return value;
        }
        static Pending load(CompoundTag value) {
            if (!value.contains("id", Tag.TAG_STRING) || !value.contains("capturedAt", Tag.TAG_LONG) || !value.contains("sites", Tag.TAG_LIST)) {
                throw new IllegalStateException("incomplete v3 resource-site explosion effect");
            }
            ListTag sites = value.getList("sites", Tag.TAG_COMPOUND); ArrayList<Candidate> candidates = new ArrayList<>(sites.size());
            for (Tag site : sites) candidates.add(Candidate.load((CompoundTag) site));
            return new Pending(value.getString("id"), value.getLong("capturedAt"), candidates);
        }
    }
}
