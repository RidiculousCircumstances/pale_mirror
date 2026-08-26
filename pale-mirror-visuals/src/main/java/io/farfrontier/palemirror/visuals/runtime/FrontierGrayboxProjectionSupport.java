package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.api.FrontierProjection;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Pure graybox geometry, palette and text rendering functions. */
final class FrontierGrayboxProjectionSupport {
    private static final int CELL_BLOCKS = 16;
    private static final int ORIGIN = -512;
    private static final int SURFACE_Y = FrontierGrayboxRuntime.SURFACE_Y;

    private FrontierGrayboxProjectionSupport() { }

    static boolean flatAt(ServerLevel level, BlockPos position) {
        if (!level.hasChunkAt(position)) return false;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos ground = new BlockPos(position.getX() + dx, SURFACE_Y - 1, position.getZ() + dz);
            if (!level.hasChunkAt(ground) || level.getBlockState(ground).isAir() || !level.getFluidState(ground).isEmpty()) return false;
            BlockState surface = level.getBlockState(ground.above());
            if (!surface.isAir() && !grayboxBlock(surface.getBlock())) return false;
        }
        return true;
    }
    static boolean flatFootprint(ServerLevel level, List<BlockPos> positions) {
        Set<Long> columns = new LinkedHashSet<>();
        for (BlockPos position : positions) columns.add(BlockPos.asLong(position.getX(), SURFACE_Y, position.getZ()));
        for (long column : columns) {
            BlockPos surface = BlockPos.of(column);
            if (!level.hasChunkAt(surface) || level.getBlockState(surface.below()).isAir() || !level.getFluidState(surface.below()).isEmpty()) return false;
            BlockState actual = level.getBlockState(surface);
            if (!actual.isAir() && !grayboxBlock(actual.getBlock())) return false;
        }
        return true;
    }
    static void restore(ServerLevel level, Map<BlockPos, BlockState> replaced) {
        replaced.forEach((position, previous) -> level.setBlock(position, previous, Block.UPDATE_ALL));
    }
    static boolean canReplace(BlockState current, BlockState desired, boolean claimed) {
        return current.isAir() || current.equals(desired) || claimed && grayboxBlock(current.getBlock());
    }
    static List<BlockPos> cube(int centerX, int baseY, int centerZ, int width, int depth, int height) {
        List<BlockPos> result = new ArrayList<>(width * depth * height);
        for (int x = centerX - width / 2; x < centerX - width / 2 + width; x++)
            for (int z = centerZ - depth / 2; z < centerZ - depth / 2 + depth; z++)
                for (int y = baseY; y < baseY + height; y++) result.add(new BlockPos(x, y, z));
        return result;
    }
    static BlockPos residentPosition(FrontierProjection.Settlement settlement, int index) {
        return new BlockPos(cellX(settlement.cellX()) - 6 + (index % 6) * 2, SURFACE_Y + 1,
                cellZ(settlement.cellZ()) - 4 + (index / 6) * 2);
    }
    static BlockPos fieldResidentPosition(FrontierProjection.FieldOperation operation, int index) {
        return new BlockPos(cellX(operation.cellX()) - 2 + (index % 3) * 2, SURFACE_Y + 1,
                cellZ(operation.cellZ()) + 2 + (index / 3) * 2);
    }
    static BlockPos bioformPosition(FrontierProjection.Hive hive, int index) { return bioformPosition(hive.cellX(), hive.cellZ(), index); }
    static BlockPos bioformPosition(int cellX, int cellZ, int index) {
        return new BlockPos(cellX(cellX) - 6 + (index % 4) * 4, SURFACE_Y + 1, cellZ(cellZ) + 5 + (index / 4) * 3);
    }
    static BlockPos position(int cellX, int cellZ, int y) { return new BlockPos(cellX(cellX), y, cellZ(cellZ)); }
    static int cellX(int cellX) { return ORIGIN + cellX * CELL_BLOCKS + CELL_BLOCKS / 2; }
    static int cellZ(int cellZ) { return ORIGIN + cellZ * CELL_BLOCKS + CELL_BLOCKS / 2; }
    static net.minecraft.world.item.Item roleHat(String role) {
        return switch (role) { case "FARMER" -> net.minecraft.world.item.Items.LIME_WOOL; case "MINER" -> net.minecraft.world.item.Items.ORANGE_WOOL;
            case "FORESTER" -> net.minecraft.world.item.Items.GREEN_WOOL; case "ENGINEER" -> net.minecraft.world.item.Items.YELLOW_WOOL;
            case "MEDIC" -> net.minecraft.world.item.Items.WHITE_WOOL; case "MERCHANT" -> net.minecraft.world.item.Items.PURPLE_WOOL;
            case "GUARD" -> net.minecraft.world.item.Items.RED_WOOL; default -> net.minecraft.world.item.Items.LIGHT_GRAY_WOOL; };
    }
    static net.minecraft.world.item.Item bioformHat(String kind) {
        return switch (kind) { case "HARVESTER" -> net.minecraft.world.item.Items.GREEN_WOOL; case "RAIDER" -> net.minecraft.world.item.Items.RED_WOOL;
            case "BREAKER" -> net.minecraft.world.item.Items.ORANGE_WOOL; case "PROPAGULE_CARRIER" -> net.minecraft.world.item.Items.MAGENTA_WOOL;
            default -> net.minecraft.world.item.Items.BLACK_WOOL; };
    }
    static BlockState facilityBlock(String kind, String state) {
        if (!state.equals("OPERATIONAL")) return Blocks.BLACK_WOOL.defaultBlockState();
        return switch (kind) { case "HOUSING" -> Blocks.LIGHT_GRAY_WOOL.defaultBlockState(); case "FARM" -> Blocks.LIME_WOOL.defaultBlockState();
            case "MINE" -> Blocks.ORANGE_WOOL.defaultBlockState(); case "FOREST" -> Blocks.GREEN_WOOL.defaultBlockState();
            case "POWER" -> Blocks.YELLOW_WOOL.defaultBlockState(); case "WORKSHOP" -> Blocks.BLUE_WOOL.defaultBlockState();
            case "CLINIC" -> Blocks.WHITE_WOOL.defaultBlockState(); case "WAREHOUSE" -> Blocks.BROWN_WOOL.defaultBlockState();
            case "MARKET" -> Blocks.PURPLE_WOOL.defaultBlockState(); default -> Blocks.RED_WOOL.defaultBlockState(); };
    }
    static BlockState organBlock(String kind, String state) {
        if (!state.equals("ALIVE")) return Blocks.BLACK_WOOL.defaultBlockState();
        return switch (kind) { case "CORE" -> Blocks.RED_WOOL.defaultBlockState(); case "SYNAPSE" -> Blocks.PURPLE_WOOL.defaultBlockState();
            case "DIGESTIVE_POOL" -> Blocks.GREEN_WOOL.defaultBlockState(); case "BROOD_SAC" -> Blocks.PINK_WOOL.defaultBlockState();
            default -> Blocks.MAGENTA_WOOL.defaultBlockState(); };
    }
    static String settlementLabel(FrontierProjection.Settlement value) {
        return "[S] " + value.name() + " " + value.focus() + " | pop=" + value.alivePopulation()
                + " | civic=" + value.civicState() + " threat=" + value.threatPermille() + "/1000"
                + " | reserve=" + value.foodReserveDaysMilli() / 1000.0 + "d ration=" + value.rationPermille() + "/1000"
                + " food=" + value.stocks().getOrDefault("FOOD", 0L) + " weapons=" + value.stocks().getOrDefault("WEAPONS", 0L)
                + " ammo=" + value.stocks().getOrDefault("AMMO", 0L) + " power=" + value.stocks().getOrDefault("POWER", 0L)
                + " credit=" + value.netCredit();
    }
    static String organLabel(FrontierProjection.HiveOrgan organ, FrontierProjection.EcologyCell ecology) {
        if (!organ.kind().equals("DIGESTIVE_POOL") || ecology == null) return "[H] " + organ.kind() + " | " + organ.state();
        return "[H] DIGESTIVE_POOL | " + organ.state() + " | organic=" + ecology.organicMass() + " scar=" + ecology.scar();
    }
    static String harvesterLabel(FrontierProjection.HarvesterRun run) {
        String receiver = run.receiverOrganId() == null ? "unassigned" : shortId(run.receiverOrganId());
        return "[R] HARVEST | " + run.state() + " | cargo=" + run.cargo() + " genetic-milli=" + run.geneticCargo() + " | receiver=" + receiver;
    }
    static String propagationRunLabel(FrontierProjection.PropagationRun run) {
        return "[S] SPORES | " + run.state() + " | " + run.transitProgress() + "/" + run.transitDays() + " | target=" + run.targetCellX() + "," + run.targetCellZ();
    }
    static String latentColonyLabel(FrontierProjection.LatentColony colony) {
        return "[L] LATENT | spores=" + colony.propagules() + " | tissue=" + colony.strength() + " | clearable";
    }
    static String adaptationLabel(Map<String, Integer> adaptations) {
        return adaptations.isEmpty() ? "" : " | adapt=" + adaptations.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ":" + entry.getValue()).collect(java.util.stream.Collectors.joining(","));
    }
    static String ecologyKey(int x, int z) { return x + ":" + z; }
    private static boolean grayboxBlock(Block block) {
        return block == Blocks.LIGHT_GRAY_WOOL || block == Blocks.LIME_WOOL || block == Blocks.ORANGE_WOOL || block == Blocks.GREEN_WOOL
                || block == Blocks.YELLOW_WOOL || block == Blocks.BLUE_WOOL || block == Blocks.WHITE_WOOL || block == Blocks.BROWN_WOOL
                || block == Blocks.PURPLE_WOOL || block == Blocks.RED_WOOL || block == Blocks.PINK_WOOL || block == Blocks.MAGENTA_WOOL || block == Blocks.BLACK_WOOL;
    }
    static String shortId(String id) { return id.substring(id.lastIndexOf(':') + 1); }
}
