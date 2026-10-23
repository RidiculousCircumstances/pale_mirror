package io.farfrontier.palemirror.internal.agriculture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.farfrontier.palemirror.frontier.v3.model.agriculture.GrayboxAgriculture;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import java.util.Map;
import java.util.stream.Stream;

/** Same authored spatial map as unloaded agriculture; no settlement/runtime dependency. */
public final class AgriculturalBiomeSource extends BiomeSource {
    public static final MapCodec<AgriculturalBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.CODEC.fieldOf("fertile").forGetter(value -> value.biomes.get(GrayboxAgriculture.FERTILE)),
            Biome.CODEC.fieldOf("ordinary").forGetter(value -> value.biomes.get(GrayboxAgriculture.ORDINARY)),
            Biome.CODEC.fieldOf("rocky").forGetter(value -> value.biomes.get(GrayboxAgriculture.ROCKY)),
            com.mojang.serialization.Codec.STRING.optionalFieldOf("ruleset", "frontier-v3-quarry-graybox-r25").forGetter(value -> value.ruleset)
    ).apply(instance, AgriculturalBiomeSource::new));
    private final Map<String, Holder<Biome>> biomes;
    private final String ruleset;
    private final io.farfrontier.palemirror.frontier.v3.model.agriculture.TerritoryAgriculture agriculture;
    public io.farfrontier.palemirror.frontier.v3.model.agriculture.TerritoryAgriculture agriculture() { return agriculture; }
    public AgriculturalBiomeSource(Holder<Biome> fertile, Holder<Biome> ordinary, Holder<Biome> rocky) {
        this(fertile, ordinary, rocky, "frontier-v3-quarry-graybox-r25");
    }
    public AgriculturalBiomeSource(Holder<Biome> fertile, Holder<Biome> ordinary, Holder<Biome> rocky, String ruleset) {
        this.ruleset = ruleset;
        agriculture = io.farfrontier.palemirror.frontier.v3.model.FrontierRulesets.installed(ruleset).fields().agriculture().orElseThrow();
        biomes = Map.of(GrayboxAgriculture.FERTILE, fertile, GrayboxAgriculture.ORDINARY, ordinary, GrayboxAgriculture.ROCKY, rocky);
        biomes.forEach((id, biome) -> {
            if (!biome.unwrapKey().orElseThrow().location().toString().equals(id))
                throw new IllegalArgumentException("agricultural biome holder disagrees with declared environment " + id);
        });
    }
    @Override protected MapCodec<? extends BiomeSource> codec() { return CODEC; }
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes() { return biomes.values().stream(); }
    @Override public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return biomes.get(agriculture.biomeAt(Math.multiplyExact(x, 4), Math.multiplyExact(z, 4)));
    }
}
