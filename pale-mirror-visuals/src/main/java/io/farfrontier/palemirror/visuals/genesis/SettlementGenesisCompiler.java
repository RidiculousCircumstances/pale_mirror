package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.AuthoredSettlementSitePlan;
import io.farfrontier.palemirror.api.DevelopmentReservationKind;
import io.farfrontier.palemirror.api.LinearFeatureKind;
import io.farfrontier.palemirror.api.LinearFeaturePlan;
import io.farfrontier.palemirror.api.AuthoredOpenSpacePlan;
import io.farfrontier.palemirror.api.OpenSpaceKind;
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
    static final int LANDSCAPE_BLEND_RADIUS = 8;
    private SettlementGenesisCompiler() { }
    static void compile(AuthoredRegionSeed seed, FrontierPalette palette, Sink sink) {
        AuthoredSettlementSitePlan settlement = seed.settlementSite();
        SettlementFixtureOccupancy fixtures = SettlementFixtureOccupancy.forSettlement(seed);
        cleanupVegetationEnvelope(seed, settlement, sink);
        settlement.foundations().forEach(value -> foundation(value, palette, sink));
        settlement.openSpaces().forEach(value -> openSpace(value, settlement, palette, fixtures, sink));
        settlement.circulation().forEach(value -> linear(value, palette, fixtures, sink));
        SettlementPerimeterCompiler.compile(settlement.perimeter(), palette, fixtures, sink);
        details(FrontierClimate.valueOf(seed.climate().toUpperCase(Locale.ROOT)),
                settlement, palette, fixtures, sink);
        entrances(settlement, palette, sink);
        depotKinetics(settlement, palette, sink);
    }
    private static void cleanupVegetationEnvelope(AuthoredRegionSeed seed,
                                                   AuthoredSettlementSitePlan settlement, Sink sink) {
        List<io.farfrontier.palemirror.api.VisualBounds> areas = settlement.managedArea().areas();
        int minimumZ = areas.stream().mapToInt(value -> value.min().z()).min().orElseThrow() - VEGETATION_HALO;
        int maximumZ = areas.stream().mapToInt(value -> value.max().z()).max().orElseThrow() + VEGETATION_HALO;
        for (int z = minimumZ; z <= maximumZ; z++) {
            final int row = z;
            List<io.farfrontier.palemirror.api.VisualBounds> rowAreas = areas.stream()
                    .filter(value -> row >= value.min().z() - VEGETATION_HALO
                            && row <= value.max().z() + VEGETATION_HALO)
                    .toList();
            if (rowAreas.isEmpty()) continue;
            int edgeVariation = Math.floorMod(row * 31 + seed.anchor().x() * 17, 4);
            int minimumX = rowAreas.stream().mapToInt(value -> value.min().x()).min().orElseThrow()
                    - VEGETATION_HALO - edgeVariation;
            int maximumX = rowAreas.stream().mapToInt(value -> value.max().x()).max().orElseThrow()
                    + VEGETATION_HALO + edgeVariation;
            int baseY = Math.min(seed.anchor().y(), rowAreas.stream()
                    .mapToInt(value -> value.min().y()).min().orElse(seed.anchor().y()));
            for (int x = minimumX; x <= maximumX; x++) sink.cleanup(x, z, baseY);
        }
    }
    private static void openSpace(AuthoredOpenSpacePlan space, AuthoredSettlementSitePlan settlement,
                                  FrontierPalette palette,
                                  SettlementFixtureOccupancy fixtures, Sink sink) {
        Sink authored = ownedOpenSpaceSink(space, settlement, sink);
        int target = space.bounds().min().y();
        for (int x = space.bounds().min().x(); x <= space.bounds().max().x(); x++) {
            for (int z = space.bounds().min().z(); z <= space.bounds().max().z(); z++) {
                BlockState surface = switch (space.kind()) {
                    case MARKET_SQUARE -> Math.floorMod(x + z, 6) == 0
                            ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                    case FREIGHT_YARD, INDUSTRIAL_YARD -> Math.floorMod(x * 3 + z, 7) == 0
                            ? Blocks.ANDESITE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                    case TRAINING_YARD -> Blocks.PACKED_MUD.defaultBlockState();
                    case CIVIC_GREEN, GARDEN -> Blocks.GRASS_BLOCK.defaultBlockState();
                    case BRIDGE -> palette.planks();
                };
                sink.terrain(x, z, target, surface, palette.foundation());
                if (space.kind() == OpenSpaceKind.MARKET_SQUARE
                        && SettlementPublicRealm.perimeter(space, x, z)
                        && !SettlementPublicRealm.cardinalEntrance(space, x, z)) {
                    authored.surfaceBlock(x, z, 0, palette.pavingSlab());
                }
                sink.cleanup(x, z, target);
            }
        }
        int centerX = (space.bounds().min().x() + space.bounds().max().x()) / 2;
        int centerZ = (space.bounds().min().z() + space.bounds().max().z()) / 2;
        switch (space.kind()) {
            case CIVIC_GREEN -> {
                // The roofed well turns the green into a civic destination
                // instead of leaving another anonymous patch of lawn.
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    authored.surfaceBlock(centerX + dx, centerZ + dz, -1,
                            Math.abs(dx) + Math.abs(dz) == 2
                                    ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                                    : Blocks.COBBLESTONE.defaultBlockState());
                }
                authored.surfaceBlock(centerX, centerZ, 0, Blocks.WATER_CAULDRON.defaultBlockState());
                for (int dx : new int[]{-2, 2}) {
                    authored.surfaceBlock(centerX + dx, centerZ, 0, fence(palette));
                    authored.surfaceBlock(centerX + dx, centerZ, 1, fence(palette));
                    authored.surfaceBlock(centerX + dx, centerZ, 2, palette.planks());
                }
                for (int dx = -2; dx <= 2; dx++) {
                    authored.surfaceBlock(centerX + dx, centerZ, 3, palette.planks());
                }
                authored.surfaceBlock(centerX, centerZ, 2, Blocks.LANTERN.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
                for (int offset : new int[]{-5, 5}) {
                    authored.surfaceBlock(centerX + offset, centerZ, -1, Blocks.MOSS_BLOCK.defaultBlockState());
                    authored.surfaceBlock(centerX + offset, centerZ, 0, Blocks.FLOWERING_AZALEA.defaultBlockState());
                }
                for (int x = space.bounds().min().x() + 2; x <= space.bounds().max().x() - 2; x += 4) {
                    for (int z : new int[]{space.bounds().min().z() + 1, space.bounds().max().z() - 1}) {
                        authored.surfaceBlock(x, z, -1, Blocks.MOSS_BLOCK.defaultBlockState());
                        authored.surfaceBlock(x, z, 0, Math.floorMod(x + z, 3) == 0
                                ? Blocks.FLOWERING_AZALEA.defaultBlockState()
                                : Blocks.AZALEA.defaultBlockState());
                    }
                }
            }
            case GARDEN -> {
                for (int x = space.bounds().min().x() + 2; x <= space.bounds().max().x() - 2; x += 3) {
                    for (int z = space.bounds().min().z() + 2; z <= space.bounds().max().z() - 2; z++) {
                        if (x == centerX && z == centerZ) continue;
                        authored.surfaceBlock(x, z, -1, Blocks.FARMLAND.defaultBlockState());
                        authored.surfaceBlock(x, z, 0, Math.floorMod(x + z, 3) == 0
                                ? Blocks.CARROTS.defaultBlockState() : Blocks.WHEAT.defaultBlockState());
                    }
                }
                authored.surfaceBlock(centerX, centerZ, -1, Blocks.WATER.defaultBlockState());
            }
            case MARKET_SQUARE -> {
                for (int side : new int[]{-5, 5}) {
                    if (!fixtures.reserve(centerX + side, centerZ, 2)) continue;
                    SettlementPublicRealm.lowLamp(centerX + side, centerZ, palette, authored);
                    for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                        authored.surfaceBlock(centerX + side, z, 3, Math.floorMod(z, 2) == 0
                                ? Blocks.WHITE_WOOL.defaultBlockState() : Blocks.ORANGE_WOOL.defaultBlockState());
                    }
                }
            }
            case FREIGHT_YARD -> {
                for (int offset = -3; offset <= 3; offset++) {
                    authored.surfaceBlock(centerX + offset, centerZ, 0,
                            Blocks.STRIPPED_OAK_LOG.defaultBlockState());
                }
                authored.surfaceBlock(centerX, centerZ + 3, 0, Blocks.HAY_BLOCK.defaultBlockState());
                for (int offset : new int[]{-4, 4}) {
                    authored.surfaceBlock(centerX + offset, centerZ - 3, 0,
                            Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState());
                    authored.surfaceBlock(centerX + offset, centerZ - 2, 0,
                            Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState());
                }
            }
            case INDUSTRIAL_YARD -> {
                authored.surfaceBlock(centerX, centerZ, 0, Blocks.ANVIL.defaultBlockState());
                authored.surfaceBlock(centerX + 3, centerZ, 0, Blocks.STONECUTTER.defaultBlockState());
                for (int dx = -3; dx <= -1; dx++) for (int dz = -3; dz <= -1; dz++) {
                    if (Math.floorMod(dx + dz, 3) != 0) {
                        authored.surfaceBlock(centerX + dx, centerZ + dz, 0,
                                Blocks.BRICKS.defaultBlockState());
                    }
                }
                for (int offset : new int[]{-5, 5}) {
                    if (fixtures.reserve(centerX + offset, centerZ + 4, 2)) {
                        SettlementPublicRealm.lowLamp(centerX + offset, centerZ + 4, palette, authored);
                    }
                }
            }
            case TRAINING_YARD -> {
                authored.surfaceBlock(centerX - 3, centerZ, 0, Blocks.HAY_BLOCK.defaultBlockState());
                authored.surfaceBlock(centerX + 3, centerZ, 0, Blocks.HAY_BLOCK.defaultBlockState());
            }
            case BRIDGE -> { }
        }
    }
    private static Sink ownedOpenSpaceSink(AuthoredOpenSpacePlan space,
                                           AuthoredSettlementSitePlan settlement, Sink delegate) {
        String owner = "open_space:" + space.id();
        java.util.Set<Long> ownedColumns = settlement.surfacePlan().columns().stream()
                .filter(value -> value.ownerId().equals(owner))
                .map(value -> coordinateKey(value.x(), value.z()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new Sink() {
            @Override public void terrain(int x, int z, int targetY, BlockState surface, BlockState foundation) {
                delegate.terrain(x, z, targetY, surface, foundation);
            }
            @Override public void blend(int x, int z, int targetY, BlockState surface,
                                        BlockState foundation, int blendDistance) {
                delegate.blend(x, z, targetY, surface, foundation, blendDistance);
            }
            @Override public void surfaceBlock(int x, int z, int offsetY, BlockState state) {
                if (ownedColumns.contains(coordinateKey(x, z))) {
                    delegate.surfaceBlock(x, z, offsetY, state);
                }
            }
            @Override public void cleanup(int x, int z, int baseY) { delegate.cleanup(x, z, baseY); }
            @Override public void block(BlockPos position, BlockState state) { delegate.block(position, state); }
        };
    }
    private static long coordinateKey(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    private static void foundation(SettlementFoundationPlan foundation, FrontierPalette palette, Sink sink) {
        int influence = foundation.apron() + LANDSCAPE_BLEND_RADIUS;
        for (int x = foundation.footprint().min().x() - influence;
             x <= foundation.footprint().max().x() + influence; x++) {
            for (int z = foundation.footprint().min().z() - influence;
                 z <= foundation.footprint().max().z() + influence; z++) {
                int distance = distanceFrom(foundation, x, z);
                boolean building = distance == 0;
                BlockState surface = building ? palette.foundation()
                        : foundation.surface().equals("FREIGHT") ? Blocks.GRAVEL.defaultBlockState()
                        : distance <= foundation.apron() ? Blocks.COBBLESTONE.defaultBlockState()
                        : Blocks.GRASS_BLOCK.defaultBlockState();
                if (distance <= foundation.apron()) {
                    sink.terrain(x, z, foundation.targetY(), surface, palette.foundation());
                } else {
                    sink.blend(x, z, foundation.targetY(), surface, Blocks.DIRT.defaultBlockState(),
                            distance - foundation.apron());
                }
                sink.cleanup(x, z, foundation.targetY());
            }
        }
    }

    private static int distanceFrom(SettlementFoundationPlan foundation, int x, int z) {
        int dx = Math.max(foundation.footprint().min().x() - x, x - foundation.footprint().max().x());
        int dz = Math.max(foundation.footprint().min().z() - z, z - foundation.footprint().max().z());
        return Math.max(0, Math.max(dx, dz));
    }

    private static void linear(LinearFeaturePlan feature, FrontierPalette palette,
                               SettlementFixtureOccupancy fixtures, Sink sink) {
        for (int segment = 1; segment < feature.nodes().size(); segment++) {
            List<VisualPoint> points = raster(feature.nodes().get(segment - 1), feature.nodes().get(segment));
            if (feature.kind() == LinearFeatureKind.RETAINING_WALL) { retainingWall(points, palette, sink); continue; }
            int radius = feature.width() / 2;
            for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
                VisualPoint point = points.get(pointIndex);
                boolean elevationTransition = SettlementLayoutGeometry.elevationTransition(points, pointIndex);
                boolean lowerTransition = SettlementLayoutGeometry.lowerTransition(points, pointIndex);
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                BlockState surface = switch (feature.kind()) {
                    case FREIGHT_ROAD -> Math.floorMod(point.x() + point.z() + dx + dz, 5) == 0
                            ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
                    case STREET -> switch (Math.floorMod(point.x() * 3 + point.z() + dx - dz, 11)) {
                        case 0 -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                        case 1, 2, 3 -> Blocks.ANDESITE.defaultBlockState();
                        case 4, 5, 6 -> Blocks.STONE_BRICKS.defaultBlockState();
                        default -> Blocks.COBBLESTONE.defaultBlockState();
                    };
                    case FOOTPATH, SIDEWALK -> palette.paving();
                    case PLAZA -> Math.floorMod(point.x() + point.z(), 6) == 0
                            ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                    case BRIDGE -> palette.planks();
                    case STAIRS -> elevationTransition
                            ? SettlementPublicRealm.stairState(feature, segment, palette) : palette.paving();
                    case DITCH -> Blocks.COARSE_DIRT.defaultBlockState();
                    default -> Blocks.COBBLESTONE.defaultBlockState();
                };
                int target = feature.kind() == LinearFeatureKind.DITCH ? point.y() - 1 : point.y();
                if (feature.kind() == LinearFeatureKind.STAIRS || feature.kind() == LinearFeatureKind.DITCH
                        || feature.kind() == LinearFeatureKind.BRIDGE
                        || feature.kind() == LinearFeatureKind.FREIGHT_ROAD
                        || feature.kind() == LinearFeatureKind.STREET
                        || feature.kind() == LinearFeatureKind.FOOTPATH
                        || feature.kind() == LinearFeatureKind.SIDEWALK
                        || feature.kind() == LinearFeatureKind.PLAZA) {
                    sink.terrain(point.x() + dx, point.z() + dz, target, surface, palette.foundation());
                    if ((feature.kind() == LinearFeatureKind.FREIGHT_ROAD
                            || feature.kind() == LinearFeatureKind.STREET) && lowerTransition) {
                        sink.block(new BlockPos(point.x() + dx, target + 1, point.z() + dz),
                                palette.pavingSlab());
                    }
                    if ((feature.kind() == LinearFeatureKind.SIDEWALK
                            || feature.kind() == LinearFeatureKind.FOOTPATH
                            || feature.kind() == LinearFeatureKind.PLAZA)
                            && lowerTransition) {
                        sink.block(new BlockPos(point.x() + dx, target + 1, point.z() + dz),
                                palette.pavingSlab());
                    }
                } else {
                    sink.surfaceBlock(point.x() + dx, point.z() + dz, -1, surface);
                }
                sink.cleanup(point.x() + dx, point.z() + dz, target);
            }
            }
            if (feature.kind() == LinearFeatureKind.FREIGHT_ROAD
                    || feature.kind() == LinearFeatureKind.STREET) {
                if (feature.kind() == LinearFeatureKind.STREET) {
                    streetShoulders(points, feature.width(), palette, sink);
                }
                streetFurniture(points, feature.width(), palette, fixtures, sink);
            }
        }
    }

    private static void streetShoulders(List<VisualPoint> points, int width,
                                        FrontierPalette palette, Sink sink) {
        int setback = width / 2 + 1;
        for (int index = 0; index < points.size(); index++) {
            VisualPoint previous = points.get(Math.max(0, index - 1));
            VisualPoint next = points.get(Math.min(points.size() - 1, index + 1));
            int dx = Integer.signum(next.x() - previous.x());
            int dz = Integer.signum(next.z() - previous.z());
            if (dx == 0 && dz == 0) continue;
            for (int side : new int[]{-1, 1}) {
                int x = points.get(index).x() - dz * setback * side;
                int z = points.get(index).z() + dx * setback * side;
                BlockState shoulder = Math.floorMod(index, 8) == 0
                        ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                        : Math.floorMod(index, 5) == 0
                        ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                        : Blocks.COBBLESTONE.defaultBlockState();
                sink.terrain(x, z, points.get(index).y(), shoulder, palette.foundation());
                sink.cleanup(x, z, points.get(index).y());
            }
        }
    }

    private static void streetFurniture(List<VisualPoint> points, int width,
                                        FrontierPalette palette, SettlementFixtureOccupancy fixtures, Sink sink) {
        for (int index = 7; index < points.size(); index += 14) {
            VisualPoint previous = points.get(Math.max(0, index - 1));
            VisualPoint next = points.get(Math.min(points.size() - 1, index + 1));
            int dx = Integer.signum(next.x() - previous.x());
            int dz = Integer.signum(next.z() - previous.z());
            int side = index % 28 == 7 ? 1 : -1;
            int setback = width / 2 + 2;
            int x = points.get(index).x() - dz * setback * side;
            int z = points.get(index).z() + dx * setback * side;
            int planterX = x - dz * side;
            int planterZ = z + dx * side;
            if (!fixtures.reserveWithCompanion(x, z, planterX, planterZ, 4)) continue;
            SettlementPublicRealm.streetLamp(x, z, dx, dz, side, palette, sink);
            sink.surfaceBlock(planterX, planterZ, -1, Blocks.MOSS_BLOCK.defaultBlockState());
            sink.surfaceBlock(planterX, planterZ, 0, index % 28 == 7
                    ? Blocks.FLOWERING_AZALEA.defaultBlockState()
                    : Blocks.AZALEA.defaultBlockState());
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

    private static void retainingWall(List<VisualPoint> points, FrontierPalette palette, Sink sink) {
        for (VisualPoint point : points) for (int up = -2; up <= 0; up++) sink.block(
                new BlockPos(point.x(), point.y() + up, point.z()),
                Math.floorMod(point.x() + point.z() + up, 5) == 0
                        ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : palette.foundation());
    }

    private static void details(FrontierClimate climate, AuthoredSettlementSitePlan settlement,
                                FrontierPalette palette, SettlementFixtureOccupancy fixtures, Sink sink) {
        climateThreshold(climate, settlement, palette, sink);
        reservedPlots(settlement, palette, fixtures, sink);
    }

    /** Marks future parcels without constructing their future buildings. */
    private static void reservedPlots(AuthoredSettlementSitePlan settlement,
                                      FrontierPalette palette, SettlementFixtureOccupancy fixtures, Sink sink) {
        int parcelIndex = 0;
        for (var reservation : settlement.developmentReservations()) {
            if (reservation.kind() != DevelopmentReservationKind.PARCEL) continue;
            var bounds = reservation.bounds();
            int[][] corners = {
                    {bounds.min().x(), bounds.min().z()}, {bounds.max().x(), bounds.min().z()},
                    {bounds.min().x(), bounds.max().z()}, {bounds.max().x(), bounds.max().z()}
            };
            boolean lit = false;
            for (int step = 0; step < corners.length; step++) {
                int corner = Math.floorMod(parcelIndex + step, corners.length);
                int x = corners[corner][0];
                int z = corners[corner][1];
                if (!fixtures.reserve(x, z, 1)) continue;
                sink.surfaceBlock(x, z, -1, Blocks.COBBLESTONE.defaultBlockState());
                sink.surfaceBlock(x, z, 0, fence(palette));
                sink.surfaceBlock(x, z, 1, fence(palette));
                if (!lit) {
                    sink.surfaceBlock(x, z, 2, Blocks.LANTERN.defaultBlockState());
                    lit = true;
                }
            }
            parcelIndex++;
        }
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
                for (int up = 0; up <= 3; up++) sink.block(p.apply(side, up), palette.log());
                sink.block(p.apply(side + Integer.signum(side), 1), Blocks.SNOW_BLOCK.defaultBlockState());
                sink.block(p.apply(side + Integer.signum(side), 2), Blocks.SNOW.defaultBlockState());
            } }
            case DRY_ARID -> {
                for (int side : new int[]{-7, 7}) for (int up = 0; up <= 3; up++) sink.block(p.apply(side, up), palette.log());
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
            BlockPos base = SettlementPublicRealm.block(port.position());
            Direction facing = SettlementPublicRealm.direction(port.outwardQuarterTurns());
            Direction tangent = facing.getClockWise();
            for (int depth = 1; depth <= 3; depth++) {
                int halfWidth = depth <= 2 ? 2 : 1;
                for (int across = -halfWidth; across <= halfWidth; across++) {
                    int x = base.getX() + facing.getStepX() * depth + tangent.getStepX() * across;
                    int z = base.getZ() + facing.getStepZ() * depth + tangent.getStepZ() * across;
                    sink.terrain(x, z, base.getY() - 1, palette.paving(), palette.foundation());
                    sink.cleanup(x, z, base.getY() - 1);
                }
            }
            BlockState lower = SettlementPublicRealm.door(palette).setValue(DoorBlock.FACING, facing)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            sink.block(base, lower); sink.block(base.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            sink.block(base.above(2), palette.planks()); BlockPos outside = base.relative(facing);
            sink.block(outside.below(), Blocks.COBBLESTONE.defaultBlockState());
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
        Direction inward = SettlementPublicRealm.direction(depot.quarterTurns()).getOpposite();
        BlockPos visible = SettlementPublicRealm.block(service.position()).above().relative(inward, 2);
        BlockPos hidden = visible.relative(inward);
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

    static BlockState fence(FrontierPalette palette) {
        if (palette.planks().is(Blocks.ACACIA_PLANKS)) return Blocks.ACACIA_FENCE.defaultBlockState();
        if (palette.planks().is(Blocks.OAK_PLANKS)) return Blocks.OAK_FENCE.defaultBlockState();
        return Blocks.SPRUCE_FENCE.defaultBlockState();
    }

    interface Sink {
        void terrain(int x, int z, int targetY, BlockState surface, BlockState foundation);
        default void blend(int x, int z, int targetY, BlockState surface, BlockState foundation,
                           int blendDistance) {
            terrain(x, z, targetY, surface, foundation);
        }
        void surfaceBlock(int x, int z, int offsetY, BlockState state);
        void cleanup(int x, int z, int baseY);
        void block(BlockPos position, BlockState state);
    }

}
