package io.farfrontier.palemirror.internal.agriculture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.farfrontier.palemirror.frontier.v3.model.agriculture.GrayboxAgriculture;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/** One native loot policy for ordinary block breaking and the worker extraction port. */
public final class TerritorialHarvestLoot extends LootModifier {
    public static final MapCodec<TerritorialHarvestLoot> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).apply(instance, TerritorialHarvestLoot::new));
    public TerritorialHarvestLoot(LootItemCondition[] conditions) { super(conditions); }
    @Override public MapCodec<? extends IGlobalLootModifier> codec() { return CODEC; }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        var block = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        var origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (block == null || origin == null || !block.is(Blocks.WHEAT) || block.getValue(CropBlock.AGE) != 7) return loot;
        var position = BlockPos.containing(origin);
        var biome = context.getLevel().getNoiseBiome(Math.floorDiv(position.getX(), 4), Math.floorDiv(position.getY(), 4),
                Math.floorDiv(position.getZ(), 4)).unwrapKey().orElseThrow().location().toString();
        var rules = GrayboxAgriculture.rules();
        var environment = rules.biomes().get(biome);
        if (environment == null) return loot;
        int quantity = rules.yieldInBiome("minecraft:wheat", biome);
        for (var stack : loot) if (stack.is(Items.WHEAT)) stack.setCount(Math.multiplyExact(stack.getCount(), quantity));
        return loot;
    }
}
