package io.farfrontier.palemirror.internal.frontier.v3;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Immutable chunk-generation provenance. Runtime loss/repair witnesses overlay it;
 * this record never changes, restores a block, or owns a running construction job. */
public record FrontierV3GeneratedStructures(String provider, String configuration, long seed,
                                           Map<BlockPos, GrayboxCell> cells) {
    private static final Codec<GrayboxCell> CELL = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("position").forGetter((GrayboxCell c) -> BlockPos.asLong(c.position().x(), c.position().y(), c.position().z())),
            Codec.STRING.fieldOf("owner").forGetter(c -> c.ownerId().value()),
            Codec.INT.fieldOf("target").forGetter(c -> c.semanticTarget().kind().wireTag()),
            // Palette names are the existing block-provenance wire names, not enum ordinals.
            Codec.STRING.fieldOf("material").forGetter(c -> c.material().name()),
            Codec.INT.fieldOf("part").forGetter(c -> c.semanticPart().wireTag())
    ).apply(instance, (address, owner, target, material, part) -> {
        var p = BlockPos.of(address);
        return new GrayboxCell(new BlockPosition(p.getX(), p.getY(), p.getZ()),
                new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.fromWireTag(target), new SubjectId(owner)),
                GrayboxMaterial.valueOf(material), FrontierWireTags.require(GrayboxSemanticPart.class, part));
    }));
    public static final Codec<FrontierV3GeneratedStructures> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("provider").forGetter(FrontierV3GeneratedStructures::provider),
            Codec.STRING.fieldOf("configuration").forGetter(FrontierV3GeneratedStructures::configuration),
            Codec.LONG.fieldOf("seed").forGetter(FrontierV3GeneratedStructures::seed),
            CELL.listOf().fieldOf("cells").forGetter(value -> List.copyOf(value.cells().values()))
    ).apply(instance, (provider, configuration, seed, cells) -> new FrontierV3GeneratedStructures(provider, configuration, seed, index(cells))));

    public FrontierV3GeneratedStructures {
        if (provider == null || provider.isBlank() || configuration == null || configuration.isBlank())
            throw new IllegalArgumentException("initial fabric requires provider and configuration");
        cells.forEach((position, cell) -> {
            var p = cell.position();
            if (!position.equals(new BlockPos(p.x(), p.y(), p.z())))
                throw new IllegalArgumentException("generated structure address differs from its declaration");
        });
        cells = Collections.unmodifiableMap(index(cells.values()));
    }
    private static Map<BlockPos, GrayboxCell> index(Collection<GrayboxCell> cells) {
        var indexed = new HashMap<BlockPos, GrayboxCell>();
        for (var cell : cells) {
            var p = cell.position();
            if (indexed.put(new BlockPos(p.x(), p.y(), p.z()), cell) != null)
                throw new IllegalArgumentException("duplicate initial structure cell");
        }
        return indexed;
    }
    public static Optional<FrontierV3GeneratedStructures> loaded(ServerLevel level, BlockPos position) {
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        return chunk == null || !chunk.hasData(io.farfrontier.palemirror.internal.world.GeologyRegistration.STRUCTURES)
                ? Optional.empty() : chunk.getData(io.farfrontier.palemirror.internal.world.GeologyRegistration.STRUCTURES);
    }
    static FrontierV3GrayboxLedger.Claim claim(ServerLevel level, BlockPos position) {
        return loaded(level, position).map(value -> value.cells().get(position)).map(FrontierV3GeneratedStructures::claim).orElse(null);
    }
    static FrontierV3GrayboxLedger.Claim claim(GrayboxCell cell) {
        return new FrontierV3GrayboxLedger.Claim(cell.ownerId().value(), cell.semanticTarget().kind().wireTag(),
                cell.material().name(), cell.semanticPart().name(), 0, false);
    }
}
