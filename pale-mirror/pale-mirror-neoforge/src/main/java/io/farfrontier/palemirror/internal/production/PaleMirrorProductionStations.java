package io.farfrontier.palemirror.internal.production;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Composition only; registries do not run jobs or own resource quantities. */
public final class PaleMirrorProductionStations {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PaleMirrorMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PaleMirrorMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PaleMirrorMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, PaleMirrorMod.MOD_ID);
    public static final net.neoforged.neoforge.registries.DeferredBlock<ProductionBenchBlock> BENCH = BLOCKS.register("production_bench",
            () -> new ProductionBenchBlock(BlockBehaviour.Properties.of().strength(3.5F).requiresCorrectToolForDrops()));
    public static final java.util.function.Supplier<BlockEntityType<ProductionBenchBlockEntity>> BENCH_ENTITY = ENTITIES.register("production_bench",
            () -> BlockEntityType.Builder.of(ProductionBenchBlockEntity::new, BENCH.get()).build(null));
    public static final java.util.function.Supplier<MenuType<ProductionBenchMenu>> BENCH_MENU = MENUS.register("production_bench",
            () -> new MenuType<>(ProductionBenchMenu::new, FeatureFlags.DEFAULT_FLAGS));
    static { ITEMS.registerSimpleBlockItem(BENCH); }
    private PaleMirrorProductionStations() { }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus); }
}
