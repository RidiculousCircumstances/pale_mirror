package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSimulation;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** NBT envelope for the complete source-parity graybox document. */
final class SourceGrayboxStateNbt {
    private static final int FORMAT = 1;
    private static final String FORMAT_KEY = "format";
    private static final String DOCUMENT_KEY = "document";

    private SourceGrayboxStateNbt() { }

    static CompoundTag write(ReferenceGrayboxSimulation simulation) {
        ReferenceGrayboxSimulation required = Objects.requireNonNull(simulation, "simulation");
        CompoundTag tag = new CompoundTag();
        tag.putInt(FORMAT_KEY, FORMAT);
        tag.putByteArray(DOCUMENT_KEY, required.save());
        return tag;
    }

    static ReferenceGrayboxSimulation read(CompoundTag tag) {
        CompoundTag required = Objects.requireNonNull(tag, "tag");
        if (required.getInt(FORMAT_KEY) != FORMAT || !required.contains(DOCUMENT_KEY, Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalStateException("incompatible source graybox state envelope");
        }
        try {
            return ReferenceGrayboxSimulation.restore(required.getByteArray(DOCUMENT_KEY));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("source graybox state is invalid", invalid);
        }
    }
}
