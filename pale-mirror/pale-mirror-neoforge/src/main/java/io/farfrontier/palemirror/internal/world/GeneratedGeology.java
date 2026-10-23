package io.farfrontier.palemirror.internal.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Immutable native chunk baseline, not a worksite claim or a current-world cache.
 * Subsequent player/miner mutations never change or regenerate it. */
public record GeneratedGeology(String provider, String configuration, long seed, Map<BlockPos, BlockState> blocks) {
    public static final Codec<GeneratedGeology> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("provider").forGetter(GeneratedGeology::provider),
            Codec.STRING.fieldOf("configuration").forGetter(GeneratedGeology::configuration),
            Codec.LONG.fieldOf("seed").forGetter(GeneratedGeology::seed),
            io.farfrontier.palemirror.internal.codec.LinearMapCodec.of(Codec.STRING.comapFlatMap(GeneratedGeology::position,
                    value -> Long.toString(value.asLong())), BlockState.CODEC)
                    .fieldOf("blocks").forGetter(GeneratedGeology::blocks)
    ).apply(instance, GeneratedGeology::new));
    private static com.mojang.serialization.DataResult<BlockPos> position(String encoded) {
        try { return com.mojang.serialization.DataResult.success(BlockPos.of(Long.parseLong(encoded))); }
        catch (NumberFormatException malformed) {
            return com.mojang.serialization.DataResult.error(() -> "invalid geological block address: " + encoded);
        }
    }
    public GeneratedGeology {
        if (provider == null || provider.isBlank() || configuration == null || configuration.isBlank())
            throw new IllegalArgumentException("geology baseline requires its explicit provider and configuration");
        var copy = new HashMap<BlockPos, BlockState>();
        blocks.forEach((position, state) -> copy.put(position.immutable(), java.util.Objects.requireNonNull(state)));
        blocks = Collections.unmodifiableMap(copy);
    }
    public static Optional<GeneratedGeology> loaded(ServerLevel level, BlockPos position) {
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        return chunk == null || !chunk.hasData(GeologyRegistration.BASELINE)
                ? Optional.empty() : chunk.getData(GeologyRegistration.BASELINE);
    }
    public static Optional<BlockState> initialBlock(ServerLevel level, BlockPos position) {
        return loaded(level, position).map(value -> value.blocks().get(position));
    }
}
