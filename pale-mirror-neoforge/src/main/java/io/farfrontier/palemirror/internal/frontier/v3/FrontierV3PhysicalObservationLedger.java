package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Crash-safe hand-off from a real external effect to v3 canonical observations.
 *
 * <p>It owns no simulation facts: entries only retain the pre-effect Minecraft state and semantic
 * provenance until the following server tick can inspect the real postcondition. This prevents a
 * restart from pretending that an uninspected blast either succeeded or did nothing.</p>
 */
final class FrontierV3PhysicalObservationLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_physical_observations";
    private static final int FORMAT = 1;
    private static final int MAX_PENDING_EFFECTS = 64;
    private static final int MAX_PENDING_CELLS = 65_536;
    private final LinkedHashMap<String, Pending> pending;
    private long nextSequence;

    private FrontierV3PhysicalObservationLedger() { this(new LinkedHashMap<>(), 0L); }
    private FrontierV3PhysicalObservationLedger(LinkedHashMap<String, Pending> pending, long nextSequence) {
        this.pending = pending; this.nextSequence = nextSequence;
    }

    static FrontierV3PhysicalObservationLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3PhysicalObservationLedger::new,
                FrontierV3PhysicalObservationLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME);
    }

    boolean captureExternalExplosion(ServerLevel level, long gameTime, List<BlockPos> affected, FrontierV3GrayboxLedger provenance,
                                     java.util.function.Predicate<BlockPos> inFrontier) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(affected, "affected blocks"); Objects.requireNonNull(provenance, "provenance");
        List<Candidate> candidates = affected.stream().distinct().sorted(Comparator.comparingLong(BlockPos::asLong))
                .filter(inFrontier).map(position -> candidate(level, provenance, position)).flatMap(Optional::stream).toList();
        if (candidates.isEmpty()) return false;
        if (candidates.size() > MAX_PENDING_CELLS) throw new IllegalStateException("v3 physical observation effect exceeds bounded cell retention");
        if (pending.size() >= MAX_PENDING_EFFECTS) throw new IllegalStateException("v3 physical observation pending-effect retention exceeded");
        String id = "effect:external-explosion:" + Long.toUnsignedString(nextSequence++);
        pending.put(id, new Pending(id, gameTime, candidates)); setDirty();
        return true;
    }

    Optional<Ready> nextReady(long gameTime) {
        for (Pending effect : pending.values()) {
            if (effect.capturedAtGameTime() >= gameTime) continue;
            if (!effect.candidates().isEmpty()) return Optional.of(new Ready(effect.id(), effect.candidates().getFirst()));
        }
        return Optional.empty();
    }

    void resolve(Ready ready) {
        Pending effect = pending.get(ready.effectId());
        if (effect == null || effect.candidates().isEmpty() || !effect.candidates().getFirst().equals(ready.candidate())) {
            throw new IllegalStateException("v3 physical observation resolution is not the retained queue head");
        }
        List<Candidate> remaining = effect.candidates().subList(1, effect.candidates().size());
        if (remaining.isEmpty()) pending.remove(effect.id());
        else pending.put(effect.id(), new Pending(effect.id(), effect.capturedAtGameTime(), remaining));
        setDirty();
    }

    private static Optional<Candidate> candidate(ServerLevel level, FrontierV3GrayboxLedger provenance, BlockPos position) {
        BlockState baseline = level.getBlockState(position);
        if (baseline.isAir()) return Optional.empty();
        Optional<Semantic> semantic = semantic(provenance.claim(position), baseline);
        return Optional.of(new Candidate(position.asLong(), NbtUtils.writeBlockState(baseline), semantic));
    }

    private static Optional<Semantic> semantic(FrontierV3GrayboxLedger.Claim claim, BlockState baseline) {
        if (claim == null || claim.conflicted()) return Optional.empty();
        try {
            var material = io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial.valueOf(claim.material());
            var part = io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart.valueOf(claim.semanticPart());
            if (baseline.equals(FrontierV3GrayboxExecutor.material(material))) return Optional.of(new Semantic(claim.owner(), part.name()));
        } catch (IllegalArgumentException ignored) {
            // The ledger is provenance only; malformed or drifted claims never become semantic fact.
        }
        return Optional.empty();
    }

    static FrontierV3PhysicalObservationLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != FORMAT) throw new IllegalStateException("incompatible v3 physical observation ledger");
        long next = tag.getLong("nextSequence");
        if (next < 0L) throw new IllegalStateException("invalid v3 physical observation sequence");
        ListTag values = tag.getList("pending", Tag.TAG_COMPOUND);
        if (values.size() > MAX_PENDING_EFFECTS) throw new IllegalStateException("v3 physical observation pending-effect retention exceeded");
        LinkedHashMap<String, Pending> pending = new LinkedHashMap<>();
        for (Tag value : values) {
            Pending entry = Pending.load((CompoundTag) value);
            if (pending.putIfAbsent(entry.id(), entry) != null) throw new IllegalStateException("duplicate v3 physical observation effect");
        }
        return new FrontierV3PhysicalObservationLedger(pending, next);
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT); tag.putLong("nextSequence", nextSequence);
        ListTag values = new ListTag(); pending.values().forEach(value -> values.add(value.save())); tag.put("pending", values);
        return tag;
    }

    record Ready(String effectId, Candidate candidate) { }
    record Semantic(String owner, String semanticPart) {
        Semantic {
            if (owner == null || owner.isBlank() || semanticPart == null || semanticPart.isBlank()) throw new IllegalArgumentException("invalid v3 semantic observation");
        }
    }
    record Candidate(long position, CompoundTag baseline, Optional<Semantic> semantic) {
        Candidate {
            baseline = baseline.copy(); semantic = Objects.requireNonNull(semantic, "semantic observation");
        }
        BlockPos blockPos() { return BlockPos.of(position); }
        BlockState baseline(HolderLookup.Provider registries) {
            return NbtUtils.readBlockState(registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), baseline);
        }
        CompoundTag save() {
            CompoundTag value = new CompoundTag(); value.putLong("pos", position); value.put("baseline", baseline.copy());
            semantic.ifPresent(known -> { value.putString("owner", known.owner()); value.putString("part", known.semanticPart()); });
            return value;
        }
        static Candidate load(CompoundTag value) {
            if (!value.contains("pos", Tag.TAG_LONG) || !value.contains("baseline", Tag.TAG_COMPOUND)) throw new IllegalStateException("incomplete v3 physical observation candidate");
            boolean hasOwner = value.contains("owner", Tag.TAG_STRING), hasPart = value.contains("part", Tag.TAG_STRING);
            if (hasOwner != hasPart) throw new IllegalStateException("partial v3 physical observation semantic");
            try {
                return new Candidate(value.getLong("pos"), value.getCompound("baseline"), hasOwner
                        ? Optional.of(new Semantic(value.getString("owner"), value.getString("part"))) : Optional.empty());
            } catch (IllegalArgumentException invalid) { throw new IllegalStateException("invalid v3 physical observation candidate", invalid); }
        }
    }
    record Pending(String id, long capturedAtGameTime, List<Candidate> candidates) {
        Pending {
            if (id == null || !id.startsWith("effect:external-explosion:") || capturedAtGameTime < 0L || candidates == null || candidates.isEmpty()
                    || candidates.size() > MAX_PENDING_CELLS) throw new IllegalArgumentException("invalid v3 pending physical observation");
            candidates = List.copyOf(candidates);
        }
        CompoundTag save() {
            CompoundTag value = new CompoundTag(); value.putString("id", id); value.putLong("capturedAt", capturedAtGameTime);
            ListTag cells = new ListTag(); candidates.forEach(candidate -> cells.add(candidate.save())); value.put("cells", cells); return value;
        }
        static Pending load(CompoundTag value) {
            if (!value.contains("id", Tag.TAG_STRING) || !value.contains("capturedAt", Tag.TAG_LONG) || !value.contains("cells", Tag.TAG_LIST)) {
                throw new IllegalStateException("incomplete v3 pending physical observation");
            }
            ListTag cells = value.getList("cells", Tag.TAG_COMPOUND); ArrayList<Candidate> candidates = new ArrayList<>(cells.size());
            for (Tag cell : cells) candidates.add(Candidate.load((CompoundTag) cell));
            try { return new Pending(value.getString("id"), value.getLong("capturedAt"), candidates); }
            catch (IllegalArgumentException invalid) { throw new IllegalStateException("invalid v3 pending physical observation", invalid); }
        }
    }
}
