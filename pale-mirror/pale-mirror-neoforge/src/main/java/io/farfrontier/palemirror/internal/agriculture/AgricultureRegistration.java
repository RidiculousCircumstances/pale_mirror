package io.farfrontier.palemirror.internal.agriculture;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class AgricultureRegistration {
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOMES = DeferredRegister.create(Registries.BIOME_SOURCE, "pale_mirror");
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, "pale_mirror");
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT = DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "pale_mirror");
    static {
        BIOMES.register("agricultural_regions", () -> AgriculturalBiomeSource.CODEC);
        GENERATORS.register("agricultural_flat", () -> AgriculturalFlatGenerator.CODEC);
        LOOT.register("territorial_harvest", () -> TerritorialHarvestLoot.CODEC);
    }
    private AgricultureRegistration() { }
    public static void register(IEventBus bus) { BIOMES.register(bus); GENERATORS.register(bus); LOOT.register(bus); }
}
