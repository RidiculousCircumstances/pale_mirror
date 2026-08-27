package io.farfrontier.palemirror.internal.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

/** Durable one-tick hand-off from a real external explosion to source observations. */
final class SourceGrayboxExplosionObservationLedger {
    private static final int MAX_PENDING_EXPLOSIONS = 128;
    private static final int MAX_POSITIONS_PER_EXPLOSION = SourceGrayboxPresentationPlan.MAX_CLAIMS;
    private final LinkedHashMap<String, Pending> pending;
    private long nextSequence;

    SourceGrayboxExplosionObservationLedger() {
        this(new LinkedHashMap<>(), 0L);
    }

    private SourceGrayboxExplosionObservationLedger(LinkedHashMap<String, Pending> pending, long nextSequence) {
        this.pending = pending;
        this.nextSequence = nextSequence;
    }

    boolean capture(long gameTime, List<BlockPos> positions) {
        if (positions.isEmpty()) return false;
        if (positions.size() > MAX_POSITIONS_PER_EXPLOSION) {
            throw new IllegalStateException("source graybox explosion affects too many claimed blocks");
        }
        String id = "source-graybox:external-explosion:" + Long.toUnsignedString(nextSequence++);
        ArrayList<Long> encoded = new ArrayList<>(positions.size());
        for (BlockPos position : positions) encoded.add(position.asLong());
        Pending value = new Pending(id, gameTime, List.copyOf(encoded));
        if (pending.putIfAbsent(id, value) != null) throw new IllegalStateException("duplicate source graybox explosion observation");
        if (pending.size() > MAX_PENDING_EXPLOSIONS) {
            pending.remove(id);
            throw new IllegalStateException("source graybox pending explosion limit reached");
        }
        return true;
    }

    List<Pending> drainThrough(long gameTime) {
        ArrayList<Pending> result = new ArrayList<>();
        for (Pending value : List.copyOf(pending.values())) {
            if (value.capturedAtGameTime() <= gameTime) {
                pending.remove(value.id());
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    ListTag save() {
        ListTag result = new ListTag();
        for (Pending value : pending.values()) result.add(value.save());
        return result;
    }

    long nextSequence() { return nextSequence; }

    static SourceGrayboxExplosionObservationLedger load(ListTag encoded, long nextSequence) {
        if (nextSequence < 0L || encoded.size() > MAX_PENDING_EXPLOSIONS) {
            throw new IllegalStateException("source graybox pending explosion ledger is invalid");
        }
        LinkedHashMap<String, Pending> pending = new LinkedHashMap<>();
        for (Tag item : encoded) {
            Pending value = Pending.load((CompoundTag) item);
            if (pending.putIfAbsent(value.id(), value) != null) {
                throw new IllegalStateException("duplicate source graybox pending explosion");
            }
        }
        return new SourceGrayboxExplosionObservationLedger(pending, nextSequence);
    }

    record Pending(String id, long capturedAtGameTime, List<Long> positions) {
        Pending {
            if (id == null || !id.startsWith("source-graybox:external-explosion:") || capturedAtGameTime < 0L
                    || positions == null || positions.isEmpty() || positions.size() > MAX_POSITIONS_PER_EXPLOSION) {
                throw new IllegalArgumentException("source graybox pending explosion is invalid");
            }
            positions = List.copyOf(positions);
        }

        CompoundTag save() {
            CompoundTag result = new CompoundTag();
            result.putString("id", id);
            result.putLong("capturedAt", capturedAtGameTime);
            ListTag blocks = new ListTag();
            positions.forEach(position -> blocks.add(LongTag.valueOf(position)));
            result.put("positions", blocks);
            return result;
        }

        static Pending load(CompoundTag encoded) {
            if (!encoded.contains("id", Tag.TAG_STRING) || !encoded.contains("capturedAt", Tag.TAG_LONG)
                    || !encoded.contains("positions", Tag.TAG_LIST)) {
                throw new IllegalStateException("source graybox pending explosion is incomplete");
            }
            ListTag positions = encoded.getList("positions", Tag.TAG_LONG);
            ArrayList<Long> values = new ArrayList<>(positions.size());
            for (Tag position : positions) values.add(((LongTag) position).getAsLong());
            try {
                return new Pending(encoded.getString("id"), encoded.getLong("capturedAt"), values);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("source graybox pending explosion is invalid", invalid);
            }
        }
    }
}
