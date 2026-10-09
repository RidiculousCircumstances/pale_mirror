package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.Objects;

/** Incremental BLOCKS journal row; preparation retains the exact preimage before any write. */
record FrontierV3WorksiteBlockWitness(WorksiteBlock declaration, BlockExtraction.Block before, Phase phase,
        java.util.Optional<String> effectOperation, java.util.Optional<BlockExtraction.Block> externalSuccessor) {
    enum Phase {
        PREPARED(1), SETTLED(2), EXTERNAL(3), EFFECT_BLOCK_APPLIED(4), EXTERNAL_PENDING(5), EFFECT_COMMITTED(6);
        private final int tag;
        Phase(int tag) { this.tag = tag; }
        int wireTag() { return tag; }
        static Phase decode(int tag) {
            return switch (tag) { case 1 -> PREPARED; case 2 -> SETTLED; case 3 -> EXTERNAL;
                case 4 -> EFFECT_BLOCK_APPLIED; case 5 -> EXTERNAL_PENDING; case 6 -> EFFECT_COMMITTED;
                default -> throw new IllegalArgumentException("unknown worksite witness phase"); };
        }
    }
    FrontierV3WorksiteBlockWitness(WorksiteBlock declaration, BlockExtraction.Block before, Phase phase) {
        this(declaration, before, phase, java.util.Optional.empty(), java.util.Optional.empty());
    }
    FrontierV3WorksiteBlockWitness(WorksiteBlock declaration, BlockExtraction.Block before, Phase phase, java.util.Optional<String> effectOperation) {
        this(declaration, before, phase, effectOperation, java.util.Optional.empty());
    }
    FrontierV3WorksiteBlockWitness {
        Objects.requireNonNull(declaration); Objects.requireNonNull(before); Objects.requireNonNull(phase); Objects.requireNonNull(effectOperation);
        Objects.requireNonNull(externalSuccessor);
        if ((phase == Phase.EFFECT_BLOCK_APPLIED || phase == Phase.EFFECT_COMMITTED) != effectOperation.isPresent() || effectOperation.filter(String::isBlank).isPresent())
            throw new IllegalArgumentException("block-half receipt needs its exact causal operation, not just an after block");
        if (externalSuccessor.isPresent() && phase != Phase.EFFECT_BLOCK_APPLIED && phase != Phase.EFFECT_COMMITTED)
            throw new IllegalArgumentException("split-effect external successor requires its retained causal block receipt");
    }
    boolean settledCurrent(WorksiteBlock current, BlockExtraction.Block actual) {
        return phase == Phase.SETTLED && declaration.equals(current) && current.block().equals(actual);
    }
    CompoundTag write() {
        var tag = new CompoundTag(); var key = declaration.key();
        tag.putInt("family", key.family().wireTag()); tag.putString("owner", key.owner().value());
        tag.putInt("role", key.role().wireTag()); tag.putLong("cell", key.cell());
        tag.putInt("x", declaration.position().x()); tag.putInt("y", declaration.position().y()); tag.putInt("z", declaration.position().z());
        tag.putLong("revision", declaration.revision()); tag.put("block", FrontierV3BlockExtractionCodec.block(declaration.block()));
        tag.put("before", FrontierV3BlockExtractionCodec.block(before)); tag.putInt("phase", phase.wireTag());
        effectOperation.ifPresent(value -> tag.putString("effect", value));
        externalSuccessor.ifPresent(value -> tag.put("externalSuccessor", FrontierV3BlockExtractionCodec.block(value))); return tag;
    }
    static FrontierV3WorksiteBlockWitness read(CompoundTag tag) {
        for (String key : new String[]{"family", "role", "x", "y", "z", "phase"})
            if (!tag.contains(key, Tag.TAG_INT)) throw new IllegalStateException("worksite witness lacks " + key);
        if (!tag.contains("owner", Tag.TAG_STRING) || !tag.contains("cell", Tag.TAG_LONG)
                || !tag.contains("revision", Tag.TAG_LONG) || !tag.contains("block", Tag.TAG_COMPOUND) || !tag.contains("before", Tag.TAG_COMPOUND))
            throw new IllegalStateException("incomplete worksite witness");
        var key = new WorksiteBlock.Key(CellMutationKey.OwnerFamily.decode(tag.getInt("family")),
                new SubjectId(tag.getString("owner")), WorksiteBlock.Role.decode(tag.getInt("role")), tag.getLong("cell"));
        if (tag.contains("externalSuccessor") && !tag.contains("externalSuccessor", Tag.TAG_COMPOUND)
                || tag.contains("effect") && !tag.contains("effect", Tag.TAG_STRING))
            throw new IllegalStateException("invalid causal block receipt fields");
        return new FrontierV3WorksiteBlockWitness(new WorksiteBlock(key,
                new BlockPosition(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")), tag.getLong("revision"),
                FrontierV3BlockExtractionCodec.readBlock(tag.getCompound("block"))),
                FrontierV3BlockExtractionCodec.readBlock(tag.getCompound("before")), Phase.decode(tag.getInt("phase")),
                tag.contains("effect", Tag.TAG_STRING) ? java.util.Optional.of(tag.getString("effect")) : java.util.Optional.empty(),
                tag.contains("externalSuccessor", Tag.TAG_COMPOUND) ? java.util.Optional.of(FrontierV3BlockExtractionCodec.readBlock(tag.getCompound("externalSuccessor"))) : java.util.Optional.empty());
    }
}
