package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.AuthoredSettlementSitePlan;
import io.farfrontier.palemirror.api.LinearFeatureKind;
import io.farfrontier.palemirror.api.LinearFeaturePlan;
import io.farfrontier.palemirror.api.SettlementFoundationPlan;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualPortKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Compiles a reusable authored settlement plan into a chunk-owning sink. */
final class SettlementGenesisCompiler {
    static final int VEGETATION_HALO = 6;

    private SettlementGenesisCompiler() { }

    static void compile(AuthoredRegionSeed seed, FrontierPalette palette, Sink sink) {
        AuthoredSettlementSitePlan settlement = seed.settlementSite();
        for (int x = settlement.bounds().min().x() - VEGETATION_HALO;
             x <= settlement.bounds().max().x() + VEGETATION_HALO; x++) {
            for (int z = settlement.bounds().min().z() - VEGETATION_HALO;
                 z <= settlement.bounds().max().z() + VEGETATION_HALO; z++) sink.cleanup(x, z, seed.anchor().y());
        }
        settlement.foundations().forEach(value -> foundation(value, palette, sink));
        settlement.circulation().forEach(value -> linear(value, palette, sink));
        settlement.defences().forEach(value -> linear(value, palette, sink));
        freightGate(settlement, palette, sink);
        details(FrontierClimate.valueOf(seed.climate().toUpperCase(Locale.ROOT)), settlement, palette, sink);
        entrances(settlement, palette, sink);
        depotKinetics(settlement, palette, sink);
    }

    private static void foundation(SettlementFoundationPlan foundation, FrontierPalette palette, Sink sink) {
        for (int x = foundation.footprint().min().x() - foundation.apron();
             x <= foundation.footprint().max().x() + foundation.apron(); x++) {
            for (int z = foundation.footprint().min().z() - foundation.apron();
                 z <= foundation.footprint().max().z() + foundation.apron(); z++) {
                boolean building = x >= foundation.footprint().min().x() && x <= foundation.footprint().max().x()
                        && z >= foundation.footprint().min().z() && z <= foundation.footprint().max().z();
                BlockState surface = building ? palette.foundation()
                        : foundation.surface().equals("FREIGHT") ? Blocks.GRAVEL.defaultBlockState()
                        : Blocks.COARSE_DIRT.defaultBlockState();
                sink.terrain(x, z, foundation.targetY(), surface, palette.foundation());
                sink.cleanup(x, z, foundation.targetY());
            }
        }
    }

    private static void linear(LinearFeaturePlan feature, FrontierPalette palette, Sink sink) {
        for (int segment = 1; segment < feature.nodes().size(); segment++) {
            List<VisualPoint> points = raster(feature.nodes().get(segment - 1), feature.nodes().get(segment));
            if (feature.kind() == LinearFeatureKind.PALISADE) { palisade(points, palette, sink); continue; }
            if (feature.kind() == LinearFeatureKind.RETAINING_WALL) { retainingWall(points, palette, sink); continue; }
            int radius = feature.width() / 2;
            for (VisualPoint point : points) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                BlockState surface = switch (feature.kind()) {
                    case FREIGHT_ROAD -> Math.floorMod(point.x() + point.z() + dx + dz, 5) == 0
                            ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                    case STREET -> Math.floorMod(point.x() + point.z(), 7) == 0
                            ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState();
                    case STAIRS -> Blocks.COBBLESTONE.defaultBlockState();
                    case DITCH -> Blocks.COARSE_DIRT.defaultBlockState();
                    default -> Blocks.DIRT_PATH.defaultBlockState();
                };
                int target = feature.kind() == LinearFeatureKind.DITCH ? point.y() - 1 : point.y();
                sink.terrain(point.x() + dx, point.z() + dz, target, surface, palette.foundation());
                sink.cleanup(point.x() + dx, point.z() + dz, target);
            }
        }
    }

    private static List<VisualPoint> raster(VisualPoint from, VisualPoint to) {
        int dx = to.x() - from.x(); int dz = to.z() - from.z(); int dy = to.y() - from.y();
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.abs(dz)));
        List<VisualPoint> result = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) result.add(new VisualPoint(
                from.x() + dx * step / steps, from.y() + dy * step / steps, from.z() + dz * step / steps));
        return result;
    }

    private static void palisade(List<VisualPoint> points, FrontierPalette palette, Sink sink) {
        for (int index = 0; index < points.size(); index++) {
            VisualPoint point = points.get(index); int height = index % 4 == 0 ? 6 : 4;
            for (int up = 1; up <= height; up++) sink.block(new BlockPos(point.x(), point.y() + up, point.z()), palette.log());
            if (index % 4 != 0) sink.block(new BlockPos(point.x(), point.y() + 4, point.z()), palette.planks());
        }
        watchPost(points.getFirst(), palette, sink); watchPost(points.getLast(), palette, sink);
    }

    private static void watchPost(VisualPoint point, FrontierPalette palette, Sink sink) {
        for (int dx : new int[]{-2, 2}) for (int dz : new int[]{-2, 2}) for (int up = 1; up <= 6; up++) {
            sink.block(new BlockPos(point.x() + dx, point.y() + up, point.z() + dz), palette.log());
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            sink.block(new BlockPos(point.x() + dx, point.y() + 4, point.z() + dz), palette.planks());
        }
        for (int up = 1; up <= 4; up++) sink.block(new BlockPos(point.x() + 1, point.y() + up, point.z() + 1),
                Blocks.SCAFFOLDING.defaultBlockState());
        sink.block(new BlockPos(point.x(), point.y() + 6, point.z()), Blocks.LANTERN.defaultBlockState());
    }

    private static void retainingWall(List<VisualPoint> points, FrontierPalette palette, Sink sink) {
        for (VisualPoint point : points) for (int up = -2; up <= 0; up++) sink.block(
                new BlockPos(point.x(), point.y() + up, point.z()),
                Math.floorMod(point.x() + point.z() + up, 5) == 0
                        ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : palette.foundation());
    }

    private static void freightGate(AuthoredSettlementSitePlan settlement, FrontierPalette palette, Sink sink) {
        VisualPoint gate = settlement.freightGate(); VisualPoint next = settlement.receivingDepot();
        boolean xAxis = Math.abs(next.x() - gate.x()) >= Math.abs(next.z() - gate.z());
        for (int side : new int[]{-4, 4}) for (int up = 1; up <= 7; up++) sink.block(xAxis
                ? new BlockPos(gate.x(), gate.y() + up, gate.z() + side)
                : new BlockPos(gate.x() + side, gate.y() + up, gate.z()), palette.log());
        for (int side = -4; side <= 4; side++) sink.block(xAxis
                ? new BlockPos(gate.x(), gate.y() + 7, gate.z() + side)
                : new BlockPos(gate.x() + side, gate.y() + 7, gate.z()), palette.log());
        sink.block(new BlockPos(gate.x(), gate.y() + 8, gate.z()), Blocks.CUT_COPPER.defaultBlockState());
        sink.block(new BlockPos(gate.x(), gate.y() + 9, gate.z()), Blocks.LANTERN.defaultBlockState());
    }

    private static void details(FrontierClimate climate, AuthoredSettlementSitePlan settlement,
                                FrontierPalette palette, Sink sink) {
        LinearFeaturePlan freight = settlement.circulation().stream()
                .filter(value -> value.kind() == LinearFeatureKind.FREIGHT_ROAD).findFirst().orElseThrow();
        List<VisualPoint> spine = new ArrayList<>();
        for (int index = 1; index < freight.nodes().size(); index++) {
            spine.addAll(raster(freight.nodes().get(index - 1), freight.nodes().get(index)));
        }
        for (int index = 10; index < spine.size(); index += 18) {
            VisualPoint point = spine.get(index);
            for (int up = 1; up <= 3; up++) sink.block(new BlockPos(point.x() + 4, point.y() + up, point.z()),
                    Blocks.SPRUCE_FENCE.defaultBlockState());
            sink.block(new BlockPos(point.x() + 4, point.y() + 4, point.z()), Blocks.LANTERN.defaultBlockState());
        }
        for (VisualModulePlacement module : settlement.modules()) module.ports().stream()
                .filter(port -> port.kind() == VisualPortKind.SERVICE).findFirst().ifPresent(port -> {
                    BlockPos p = block(port.position()); sink.block(p.above(), palette.planks());
                    sink.block(p.above(2), Blocks.HAY_BLOCK.defaultBlockState());
                    sink.block(p.offset(1, 1, 0), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                });
        climateThreshold(climate, settlement, palette, sink);
    }

    private static void climateThreshold(FrontierClimate climate, AuthoredSettlementSitePlan settlement,
                                         FrontierPalette palette, Sink sink) {
        VisualPoint gate = settlement.freightGate();
        boolean xAxis = Math.abs(settlement.receivingDepot().x() - gate.x())
                >= Math.abs(settlement.receivingDepot().z() - gate.z());
        java.util.function.BiFunction<Integer, Integer, BlockPos> p = (side, up) -> xAxis
                ? new BlockPos(gate.x(), gate.y() + up, gate.z() + side)
                : new BlockPos(gate.x() + side, gate.y() + up, gate.z());
        switch (climate) {
            case COLD_TAIGA -> { for (int side : new int[]{-7, 7}) {
                for (int up = 1; up <= 3; up++) sink.block(p.apply(side, up), palette.log());
                sink.block(p.apply(side + Integer.signum(side), 1), Blocks.SNOW_BLOCK.defaultBlockState());
                sink.block(p.apply(side + Integer.signum(side), 2), Blocks.SNOW.defaultBlockState());
            } }
            case DRY_ARID -> {
                for (int side : new int[]{-7, 7}) for (int up = 1; up <= 3; up++) sink.block(p.apply(side, up), palette.log());
                for (int side = -7; side <= 7; side++) sink.block(p.apply(side, 4), side % 3 == 0
                        ? Blocks.WHITE_WOOL.defaultBlockState() : Blocks.ORANGE_WOOL.defaultBlockState());
                sink.block(p.apply(8, 1), Blocks.WATER_CAULDRON.defaultBlockState());
            }
            case TEMPERATE -> { for (int side : new int[]{-7, 7}) {
                sink.block(p.apply(side, 1), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                sink.block(p.apply(side + Integer.signum(side), 1), Blocks.MOSS_BLOCK.defaultBlockState());
                sink.block(p.apply(side + Integer.signum(side), 2), side < 0
                        ? Blocks.FLOWERING_AZALEA.defaultBlockState() : Blocks.AZALEA.defaultBlockState());
            } }
        }
    }

    private static void entrances(AuthoredSettlementSitePlan settlement, FrontierPalette palette, Sink sink) {
        for (VisualModulePlacement module : settlement.modules()) {
            var port = module.ports().stream().filter(value -> value.kind() == VisualPortKind.PUBLIC_ENTRANCE)
                    .findFirst().orElseThrow();
            BlockPos base = block(port.position()); Direction facing = direction(port.outwardQuarterTurns());
            BlockState lower = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            sink.block(base, lower); sink.block(base.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            sink.block(base.above(2), palette.planks()); BlockPos outside = base.relative(facing);
            sink.block(outside.below(), Blocks.DIRT_PATH.defaultBlockState());
            sink.block(outside, Blocks.AIR.defaultBlockState()); sink.block(outside.above(), Blocks.AIR.defaultBlockState());
        }
    }

    private static void depotKinetics(AuthoredSettlementSitePlan settlement, FrontierPalette palette, Sink sink) {
        VisualModulePlacement depot = settlement.modules().stream()
                .filter(value -> value.templateId().endsWith("/receiving_depot")).findFirst().orElse(null);
        if (depot == null) return;
        var service = depot.ports().stream().filter(value -> value.kind() == VisualPortKind.SERVICE).findFirst().orElse(null);
        if (service == null) return;
        BlockState motor = optionalCreate("creative_motor", depot.quarterTurns());
        BlockState shaft = optionalCreate("shaft", depot.quarterTurns());
        BlockState cog = optionalCreate("cogwheel", depot.quarterTurns());
        if (motor == null || shaft == null || cog == null) return;
        Direction inward = direction(depot.quarterTurns()).getOpposite();
        BlockPos visible = block(service.position()).above().relative(inward, 2); BlockPos hidden = visible.relative(inward);
        sink.block(hidden, motor); sink.block(visible, shaft); sink.block(visible.above(), cog);
        sink.block(hidden.below(), palette.foundation()); sink.block(hidden.above(), palette.foundation());
        sink.block(hidden.relative(inward), palette.foundation());
    }

    private static BlockState optionalCreate(String path, int turns) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", path)).map(block -> {
            BlockState state = block.defaultBlockState();
            for (int turn = 0; turn < Math.floorMod(turns, 4); turn++) {
                state = state.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
            }
            return state;
        }).orElse(null);
    }

    private static Direction direction(int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 0 -> Direction.EAST; case 1 -> Direction.SOUTH; case 2 -> Direction.WEST; default -> Direction.NORTH;
        };
    }

    private static BlockPos block(VisualPoint point) { return new BlockPos(point.x(), point.y(), point.z()); }

    interface Sink {
        void terrain(int x, int z, int targetY, BlockState surface, BlockState foundation);
        void cleanup(int x, int z, int baseY);
        void block(BlockPos position, BlockState state);
    }
}
