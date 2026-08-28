package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Durable post-impact block evidence for a real v3-owned explosion, never a replay queue. */
final class FrontierV3ManagedExplosionLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_managed_explosions";
    private static final int FORMAT = 1, MAX_EFFECTS = 64, MAX_CELLS = 65_536;
    private final LinkedHashMap<String, Pending> pending;

    private FrontierV3ManagedExplosionLedger() { this(new LinkedHashMap<>()); }
    private FrontierV3ManagedExplosionLedger(LinkedHashMap<String, Pending> pending) { this.pending = pending; }

    static FrontierV3ManagedExplosionLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3ManagedExplosionLedger::new,
                FrontierV3ManagedExplosionLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME);
    }

    boolean capture(ServerLevel level, long gameTime, PhysicalIntentId intentId, List<BlockPos> affected, FrontierV3GrayboxLedger provenance,
                    java.util.function.Predicate<BlockPos> inFrontier) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(intentId, "intent id"); Objects.requireNonNull(affected, "affected blocks");
        String key = intentId.value(); if (pending.containsKey(key)) return false;
        if (pending.size() >= MAX_EFFECTS) throw new IllegalStateException("v3 managed explosion retention exceeded");
        List<Candidate> candidates = affected.stream().distinct().sorted(Comparator.comparingLong(BlockPos::asLong)).filter(inFrontier)
                .map(position -> candidate(level, provenance, position)).flatMap(Optional::stream).toList();
        if (candidates.size() > MAX_CELLS) throw new IllegalStateException("v3 managed explosion cell retention exceeded");
        pending.put(key, new Pending(key, gameTime, candidates, candidates.size(), 0)); setDirty(); return true;
    }

    boolean has(PhysicalIntentId intentId) { return pending.containsKey(intentId.value()); }

    Optional<Ready> nextReady(long gameTime) {
        for (Pending effect : pending.values()) {
            if (effect.capturedAtGameTime() < gameTime) return Optional.of(new Ready(new PhysicalIntentId(effect.intentId()),
                    effect.candidates().isEmpty() ? Optional.empty() : Optional.of(effect.candidates().getFirst()), effect.totalCandidates(), effect.changedCandidates()));
        }
        return Optional.empty();
    }

    Optional<Ready> nextReady(PhysicalIntentId intentId, long gameTime) {
        Pending effect = pending.get(intentId.value());
        if (effect == null || effect.capturedAtGameTime() >= gameTime) return Optional.empty();
        return Optional.of(new Ready(intentId, effect.candidates().isEmpty() ? Optional.empty() : Optional.of(effect.candidates().getFirst()),
                effect.totalCandidates(), effect.changedCandidates()));
    }

    void resolve(Ready ready, boolean changed) {
        Pending effect = require(ready.intentId());
        if (ready.candidate().isEmpty() || effect.candidates().isEmpty() || !effect.candidates().getFirst().equals(ready.candidate().orElseThrow())) {
            throw new IllegalStateException("v3 managed explosion resolution is not the retained queue head");
        }
        pending.put(effect.intentId(), new Pending(effect.intentId(), effect.capturedAtGameTime(), effect.candidates().subList(1, effect.candidates().size()),
                effect.totalCandidates(), changed ? Math.addExact(effect.changedCandidates(), 1) : effect.changedCandidates())); setDirty();
    }

    Completion complete(Ready ready) {
        if (ready.candidate().isPresent()) throw new IllegalStateException("v3 managed explosion cannot complete before block inspection");
        Pending effect = require(ready.intentId()); pending.remove(effect.intentId()); setDirty();
        return new Completion(ready.intentId(), effect.totalCandidates(), effect.changedCandidates());
    }

    private Pending require(PhysicalIntentId intentId) {
        Pending effect = pending.get(intentId.value()); if (effect == null) throw new IllegalStateException("unknown managed v3 explosion intent"); return effect;
    }
    private static Optional<Candidate> candidate(ServerLevel level, FrontierV3GrayboxLedger provenance, BlockPos position) {
        BlockState baseline = level.getBlockState(position); if (baseline.isAir()) return Optional.empty();
        return Optional.of(new Candidate(position.asLong(), NbtUtils.writeBlockState(baseline), semantic(provenance.claim(position), baseline)));
    }
    private static Optional<FrontierV3PhysicalObservationLedger.Semantic> semantic(FrontierV3GrayboxLedger.Claim claim, BlockState baseline) {
        if (claim == null || claim.conflicted()) return Optional.empty();
        try {
            var material = io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial.valueOf(claim.material());
            var part = io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart.valueOf(claim.semanticPart());
            return baseline.equals(FrontierV3GrayboxExecutor.material(material)) ? Optional.of(new FrontierV3PhysicalObservationLedger.Semantic(claim.owner(), part.name())) : Optional.empty();
        } catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }

    static FrontierV3ManagedExplosionLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != FORMAT) throw new IllegalStateException("incompatible v3 managed explosion ledger");
        ListTag values = tag.getList("pending", Tag.TAG_COMPOUND); if (values.size() > MAX_EFFECTS) throw new IllegalStateException("v3 managed explosion retention exceeded");
        LinkedHashMap<String, Pending> pending = new LinkedHashMap<>();
        for (Tag value : values) { Pending entry = Pending.load((CompoundTag) value); if (pending.putIfAbsent(entry.intentId(), entry) != null) throw new IllegalStateException("duplicate managed v3 explosion intent"); }
        return new FrontierV3ManagedExplosionLedger(pending);
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT); ListTag values = new ListTag(); pending.values().forEach(value -> values.add(value.save())); tag.put("pending", values); return tag;
    }

    record Ready(PhysicalIntentId intentId, Optional<Candidate> candidate, int totalCandidates, int changedCandidates) { }
    record Completion(PhysicalIntentId intentId, int affectedBlockCount, int changedBlockCount) { }
    record Candidate(long position, CompoundTag baseline, Optional<FrontierV3PhysicalObservationLedger.Semantic> semantic) {
        Candidate { baseline = baseline.copy(); semantic = Objects.requireNonNull(semantic, "semantic"); }
        BlockPos blockPos() { return BlockPos.of(position); }
        BlockState baseline(HolderLookup.Provider registries) { return NbtUtils.readBlockState(registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), baseline); }
        CompoundTag save() {
            CompoundTag value = new CompoundTag(); value.putLong("pos", position); value.put("baseline", baseline.copy());
            semantic.ifPresent(known -> { value.putString("owner", known.owner()); value.putString("part", known.semanticPart()); }); return value;
        }
        static Candidate load(CompoundTag value) {
            if (!value.contains("pos", Tag.TAG_LONG) || !value.contains("baseline", Tag.TAG_COMPOUND)) throw new IllegalStateException("incomplete managed explosion candidate");
            boolean owner = value.contains("owner", Tag.TAG_STRING), part = value.contains("part", Tag.TAG_STRING); if (owner != part) throw new IllegalStateException("partial managed explosion semantic");
            return new Candidate(value.getLong("pos"), value.getCompound("baseline"), owner ? Optional.of(new FrontierV3PhysicalObservationLedger.Semantic(value.getString("owner"), value.getString("part"))) : Optional.empty());
        }
    }
    record Pending(String intentId, long capturedAtGameTime, List<Candidate> candidates, int totalCandidates, int changedCandidates) {
        Pending {
            if (intentId == null || intentId.isBlank() || capturedAtGameTime < 0L || candidates == null || candidates.size() > MAX_CELLS || totalCandidates < candidates.size()
                    || totalCandidates > MAX_CELLS || changedCandidates < 0 || changedCandidates > totalCandidates - candidates.size()) throw new IllegalArgumentException("invalid managed v3 explosion pending record");
            candidates = List.copyOf(candidates);
        }
        CompoundTag save() {
            CompoundTag value = new CompoundTag(); value.putString("intent", intentId); value.putLong("capturedAt", capturedAtGameTime);
            value.putInt("total", totalCandidates); value.putInt("changed", changedCandidates); ListTag cells = new ListTag();
            candidates.forEach(candidate -> cells.add(candidate.save())); value.put("cells", cells); return value;
        }
        static Pending load(CompoundTag value) {
            if (!value.contains("intent", Tag.TAG_STRING) || !value.contains("capturedAt", Tag.TAG_LONG) || !value.contains("total", Tag.TAG_INT)
                    || !value.contains("changed", Tag.TAG_INT) || !value.contains("cells", Tag.TAG_LIST)) throw new IllegalStateException("incomplete managed v3 explosion record");
            ListTag cells = value.getList("cells", Tag.TAG_COMPOUND); ArrayList<Candidate> candidates = new ArrayList<>(cells.size());
            for (Tag cell : cells) candidates.add(Candidate.load((CompoundTag) cell));
            return new Pending(value.getString("intent"), value.getLong("capturedAt"), candidates, value.getInt("total"), value.getInt("changed"));
        }
    }
}
