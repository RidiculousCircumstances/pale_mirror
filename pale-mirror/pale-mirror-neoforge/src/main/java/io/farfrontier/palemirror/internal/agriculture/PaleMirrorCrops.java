package io.farfrontier.palemirror.internal.agriculture;

import com.mojang.serialization.MapCodec;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Native crop content. Registered field owners still control managed cultivation. */
public final class PaleMirrorCrops {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PaleMirrorMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PaleMirrorMod.MOD_ID);
    public static final DeferredBlock<CottonCropBlock> COTTON = BLOCKS.register("cotton_crop",
            () -> new CottonCropBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHEAT)));
    public static final DeferredItem<ItemNameBlockItem> COTTON_SEEDS = ITEMS.register("cotton_seeds",
            () -> new ItemNameBlockItem(COTTON.get(), new Item.Properties()));
    public static final class CottonCropBlock extends CropBlock {
        private static final MapCodec<CottonCropBlock> CODEC = simpleCodec(CottonCropBlock::new);
        public CottonCropBlock(BlockBehaviour.Properties properties) { super(properties); }
        @Override public MapCodec<CottonCropBlock> codec() { return CODEC; }
        @Override protected ItemLike getBaseSeedId() { return COTTON_SEEDS.get(); }
    }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); }
    private PaleMirrorCrops() { }
}
