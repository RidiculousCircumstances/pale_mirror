package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ContainerPurpose;
import io.farfrontier.palemirror.frontier.v3.model.ContainerRecord;
import io.farfrontier.palemirror.frontier.v3.model.ProductionEquipment;
import io.farfrontier.palemirror.internal.production.PaleMirrorProductionStations;
import io.farfrontier.palemirror.internal.production.ProductionBenchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/** Declared-purpose provider composition. Inventory observations do not infer equipment type. */
final class FrontierV3FixedContainerProviders {
    private record Provider(Supplier<? extends Block> block, java.util.function.Predicate<Block> accepts,
                            Function<BlockEntity, Container> inventory) { }
    private static final Provider CHEST = new Provider(() -> Blocks.CHEST,
            block -> block instanceof net.minecraft.world.level.block.ChestBlock,
            entity -> entity instanceof ChestBlockEntity chest ? chest : null);
    private static final Provider BENCH = new Provider(PaleMirrorProductionStations.BENCH,
            block -> block == PaleMirrorProductionStations.BENCH.get(),
            entity -> entity instanceof ProductionBenchBlockEntity bench ? bench : null);
    private static final Map<ContainerPurpose, Provider> PROVIDERS = Map.of(
            ContainerPurpose.UNSCOPED, CHEST, ContainerPurpose.SETTLEMENT_DEPOT, CHEST,
            ContainerPurpose.HIVE_STORE, CHEST,
            ContainerPurpose.EXTRACTIVE_STORAGE, CHEST, ContainerPurpose.ROUTE_MAINTENANCE, CHEST,
            ContainerPurpose.RESOURCE_STORAGE, CHEST);
    private static final Map<ProductionEquipment.Id, Provider> EQUIPMENT = Map.of(ProductionEquipment.BENCH, BENCH);
    private FrontierV3FixedContainerProviders() { }
    private static Provider require(ContainerRecord record) {
        var provider = record.purpose() == ContainerPurpose.PRODUCTION_STATION
                ? EQUIPMENT.get(record.productionStation().orElseThrow().equipmentType()) : PROVIDERS.get(record.purpose());
        if (provider == null) throw new IllegalArgumentException("no registered fixed container provider " + record.purpose());
        return provider;
    }
    static Block block(ContainerRecord record) { return require(record).block().get(); }
    static boolean acceptsBlock(ContainerRecord record, net.minecraft.world.level.block.state.BlockState state) {
        return require(record).accepts().test(state.getBlock());
    }
    static Optional<FrontierV3PhysicalContainer> inspect(ServerLevel level, BlockPos pos, ContainerRecord record) {
        if (!level.hasChunkAt(pos)) return Optional.empty();
        if (!acceptsBlock(record, level.getBlockState(pos))) return Optional.empty();
        var entity = level.getBlockEntity(pos);
        if (entity == null) return Optional.empty();
        return observed(entity, record);
    }
    /** onRemove may run after the palette changed; the old declared entity is the witness. */
    static Optional<FrontierV3PhysicalContainer> observed(BlockEntity entity, ContainerRecord record) {
        var provider = require(record);
        var inventory = provider.inventory().apply(entity);
        if (inventory == null) return Optional.empty();
        if (inventory.getContainerSize() != record.slotCount())
            throw new IllegalArgumentException("declared fixed container capacity differs from physical provider");
        if (record.productionStation().isPresent()) {
            var spec = record.productionStation().orElseThrow();
            if (spec.inputSlot() != ProductionBenchBlockEntity.INPUT || spec.outputSlot() != ProductionBenchBlockEntity.OUTPUT
                    || spec.fuelSlot() != ProductionBenchBlockEntity.FUEL)
                throw new IllegalArgumentException("station port layout differs from registered equipment adapter");
        }
        return Optional.of(new FrontierV3PhysicalContainer(record.id(), inventory, entity.getPersistentData(), entity.getBlockPos(),
                () -> FrontierV3OperationBlockEntityParticipant.current(entity)));
    }
}
