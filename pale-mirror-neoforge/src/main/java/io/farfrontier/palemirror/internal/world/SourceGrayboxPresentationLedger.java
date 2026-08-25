package io.farfrontier.palemirror.internal.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Bounded, durable ownership of source-graybox blocks.
 *
 * <p>The canonical simulation owns whether a record exists. This ledger owns
 * only the exact rectangles that the projector was allowed to write, so a
 * later reconciliation can distinguish a PM colour update from an unknown
 * block conflict without recreating state from terrain.</p>
 */
final class SourceGrayboxPresentationLedger extends SavedData {
    private static final String DATA_NAME = "pale_mirror_source_graybox_presentation";
    private static final int FORMAT = 2;
    static final int MAX_CLAIMS = 10_240;
    private final LinkedHashMap<String, Claim> claims;

    private SourceGrayboxPresentationLedger() {
        this(new LinkedHashMap<>());
    }

    private SourceGrayboxPresentationLedger(LinkedHashMap<String, Claim> claims) {
        this.claims = claims;
    }

    static SourceGrayboxPresentationLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(SourceGrayboxPresentationLedger::new,
                SourceGrayboxPresentationLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), DATA_NAME);
    }

    Claim claim(String id) {
        return claims.get(id);
    }

    List<Claim> claims() {
        return List.copyOf(claims.values());
    }

    void put(Claim claim) {
        Claim required = Objects.requireNonNull(claim, "claim");
        if (!claims.containsKey(required.id()) && claims.size() >= MAX_CLAIMS) {
            throw new IllegalStateException("source graybox claim limit reached");
        }
        claims.put(required.id(), required);
        setDirty();
    }

    void remove(String id) {
        if (claims.remove(id) != null) setDirty();
    }

    void conflict(String id) {
        Claim claim = claims.get(id);
        if (claim == null || claim.conflicted() || claim.consumed()) return;
        claims.put(id, claim.withConflict());
        setDirty();
    }

    /** Records an accepted typed player fact without allowing a later refresh to recreate its slot. */
    void consume(String id) {
        Claim claim = claims.get(id);
        if (claim == null || claim.consumed() || claim.conflicted() || claim.interactionKind().isEmpty()) return;
        claims.put(id, claim.withConsumed());
        setDirty();
    }

    Claim at(BlockPos position) {
        for (Claim claim : claims.values()) if (claim.contains(position)) return claim;
        return null;
    }

    static SourceGrayboxPresentationLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("format") != FORMAT) throw new IllegalStateException("unsupported source graybox presentation ledger format");
        ListTag encoded = tag.getList("claims", Tag.TAG_COMPOUND);
        if (encoded.size() > MAX_CLAIMS) throw new IllegalStateException("source graybox claim ledger exceeds its bound");
        LinkedHashMap<String, Claim> claims = new LinkedHashMap<>();
        for (Tag raw : encoded) {
            CompoundTag value = (CompoundTag) raw;
            Claim claim = new Claim(value.getString("id"), value.getString("subject"), value.getString("kind"),
                    value.getString("revision"), value.getInt("x"), value.getInt("y"), value.getInt("z"),
                    value.getInt("width"), value.getInt("depth"), value.getInt("height"), value.getBoolean("conflicted"),
                    value.getString("interactionKind"), value.getDouble("interactionWeight"), value.getBoolean("consumed"));
            if (claims.putIfAbsent(claim.id(), claim) != null) {
                throw new IllegalStateException("duplicate source graybox presentation claim: " + claim.id());
            }
        }
        return new SourceGrayboxPresentationLedger(claims);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT);
        ListTag encoded = new ListTag();
        claims.values().forEach(claim -> encoded.add(claim.save()));
        tag.put("claims", encoded);
        return tag;
    }

    record Claim(String id, String subjectId, String kind, String revision, int x, int y, int z,
                 int width, int depth, int height, boolean conflicted, String interactionKind, double interactionWeight,
                 boolean consumed) {
        Claim {
            required(id, "claim ID", 192);
            required(subjectId, "claim subject", 192);
            required(kind, "claim kind", 64);
            if (!revision.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("source graybox claim revision is invalid");
            if (width < 1 || width > 64 || depth < 1 || depth > 64 || height < 1 || height > 16) {
                throw new IllegalArgumentException("source graybox claim dimensions are invalid");
            }
            interactionKind = interactionKind == null ? "" : interactionKind;
            boolean interactive = !interactionKind.isEmpty();
            if (interactive != (Double.isFinite(interactionWeight) && interactionWeight > 0.0d)) {
                throw new IllegalArgumentException("source graybox interaction metadata is invalid");
            }
            if (interactive && !Set.of("facility_damaged", "site_damaged", "route_damaged", "organ_damaged", "operation_cargo_lost")
                    .contains(interactionKind)) {
                throw new IllegalArgumentException("source graybox interaction kind is invalid");
            }
            if (consumed && !interactive) throw new IllegalArgumentException("only an interaction claim can be consumed");
        }

        boolean contains(BlockPos position) {
            return position.getX() >= x && position.getX() < x + width
                    && position.getY() >= y && position.getY() < y + height
                    && position.getZ() >= z && position.getZ() < z + depth;
        }

        Claim withConflict() {
            return new Claim(id, subjectId, kind, revision, x, y, z, width, depth, height, true, interactionKind, interactionWeight, false);
        }

        Claim withConsumed() {
            return new Claim(id, subjectId, kind, revision, x, y, z, width, depth, height, false, interactionKind, interactionWeight, true);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", id);
            tag.putString("subject", subjectId);
            tag.putString("kind", kind);
            tag.putString("revision", revision);
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            tag.putInt("width", width);
            tag.putInt("depth", depth);
            tag.putInt("height", height);
            tag.putBoolean("conflicted", conflicted);
            tag.putString("interactionKind", interactionKind);
            tag.putDouble("interactionWeight", interactionWeight);
            tag.putBoolean("consumed", consumed);
            return tag;
        }

        private static void required(String value, String name, int maximumLength) {
            if (value == null || value.isBlank() || value.length() > maximumLength) {
                throw new IllegalArgumentException(name + " is invalid");
            }
        }
    }
}
