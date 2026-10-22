package io.farfrontier.palemirror.internal.frontier.v3;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.farfrontier.palemirror.frontier.v3.model.WorksiteBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Exact initial infrastructure declarations. Native generation owns this immutable
 * history; worksite journals own all subsequent changes, including player damage. */
public record FrontierV3GeneratedWorksites(String configuration, long seed, Map<BlockPos, WorksiteBlock> cells) {
    private static final Codec<WorksiteBlock> CELL = CompoundTag.CODEC.xmap(tag -> {
        var witness = FrontierV3WorksiteBlockWitness.read(tag);
        if (witness.phase() != FrontierV3WorksiteBlockWitness.Phase.SETTLED
                || !witness.before().equals(witness.declaration().block()))
            throw new IllegalArgumentException("generated infrastructure cannot contain a runtime operation");
        return witness.declaration();
    }, cell -> new FrontierV3WorksiteBlockWitness(cell, cell.block(), FrontierV3WorksiteBlockWitness.Phase.SETTLED).write());
    public static final Codec<FrontierV3GeneratedWorksites> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("configuration").forGetter(FrontierV3GeneratedWorksites::configuration),
            Codec.LONG.fieldOf("seed").forGetter(FrontierV3GeneratedWorksites::seed),
            CELL.listOf().fieldOf("cells").forGetter(value -> List.copyOf(value.cells().values()))
    ).apply(instance, (configuration, seed, cells) -> new FrontierV3GeneratedWorksites(configuration, seed, index(cells))));
    public FrontierV3GeneratedWorksites {
        if (configuration == null || configuration.isBlank()) throw new IllegalArgumentException("missing generation configuration");
        cells.forEach((position, cell) -> {
            if (!position.equals(FrontierV3WorksiteProjection.position(cell)) || cell.revision() != 1
                    || cell.key().role() != WorksiteBlock.Role.INFRASTRUCTURE)
                throw new IllegalArgumentException("invalid initial infrastructure declaration");
        });
        cells = Map.copyOf(cells);
    }
    private static Map<BlockPos, WorksiteBlock> index(List<WorksiteBlock> cells) {
        var result = new HashMap<BlockPos, WorksiteBlock>();
        for (var cell : cells) if (result.put(FrontierV3WorksiteProjection.position(cell), cell) != null)
            throw new IllegalArgumentException("duplicate generated worksite cell");
        return result;
    }
    static Optional<FrontierV3GeneratedWorksites> loaded(ServerLevel level, BlockPos position) {
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        return chunk == null || !chunk.hasData(io.farfrontier.palemirror.internal.world.GeologyRegistration.WORKSITES)
                ? Optional.empty() : chunk.getData(io.farfrontier.palemirror.internal.world.GeologyRegistration.WORKSITES);
    }
    static FrontierV3WorksiteBlockWitness support(ServerLevel level, WorksiteBlock declaration) {
        return loaded(level, FrontierV3WorksiteProjection.position(declaration))
                .map(baseline -> baseline.cells().get(FrontierV3WorksiteProjection.position(declaration)))
                .filter(declaration::equals).map(cell -> new FrontierV3WorksiteBlockWitness(
                        cell, cell.block(), FrontierV3WorksiteBlockWitness.Phase.SETTLED)).orElse(null);
    }
}
