package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualBlockPlacement;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.api.VisualModuleSnapshot;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Single sanitizer/compiler used by fresh-world genesis and later staged construction. */
public final class AuthoredModuleCompiler {
    private static final int MAX_TEMPLATE_CACHE = 128;
    private static final Map<ResourceLocation, RawTemplate> TEMPLATES = new ConcurrentHashMap<>();

    private AuthoredModuleCompiler() { }

    public static VisualModuleSnapshot compile(VisualModulePlacement module) {
        ResourceLocation id = ResourceLocation.parse(module.templateId());
        RawTemplate template = template(id);
        int turns = Math.floorMod(module.quarterTurns(), 4);
        int rx = turns % 2 == 0 ? template.sizeX() : template.sizeZ();
        int rz = turns % 2 == 0 ? template.sizeZ() : template.sizeX();
        BlockPos origin = new BlockPos(module.origin().x() - rx / 2, module.origin().y() + 1,
                module.origin().z() - rz / 2);
        List<VisualBlockPlacement> blocks = new ArrayList<>(template.blocks().size());
        for (RawBlock block : template.blocks()) {
            int[] rotated = rotate(block.x(), block.z(), template.sizeX(), template.sizeZ(), turns);
            BlockState state = rotate(climateState(template.palette().get(block.state()), id), turns);
            if (state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.JIGSAW)) continue;
            BlockPos world = origin.offset(rotated[0], block.y(), rotated[1]);
            VisualPoint position = new VisualPoint(world.getX(), world.getY(), world.getZ());
            if (!module.footprint().contains(position)) {
                throw new IllegalStateException("Authored module escaped its declared footprint: " + id
                        + " at " + position + " outside " + module.footprint());
            }
            blocks.add(new VisualBlockPlacement(position, state));
        }
        return new VisualModuleSnapshot(module.templateId(), module.footprint(),
                normalizeStructuralDetails(module, blocks));
    }

    private static RawTemplate template(ResourceLocation id) {
        RawTemplate present = TEMPLATES.get(id);
        if (present != null) return present;
        synchronized (TEMPLATES) {
            present = TEMPLATES.get(id);
            if (present != null) return present;
            if (TEMPLATES.size() >= MAX_TEMPLATE_CACHE) {
                throw new IllegalStateException("Authored template cache exceeded " + MAX_TEMPLATE_CACHE
                        + " immutable assets");
            }
            RawTemplate loaded = loadTemplate(id);
            TEMPLATES.put(id, loaded);
            return loaded;
        }
    }

    private static RawTemplate loadTemplate(ResourceLocation id) {
        String path = "data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt";
        try (InputStream input = PaleMirrorVisualsMod.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Required authored module is missing: " + id);
            CompoundTag root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            ListTag size = root.getList("size", Tag.TAG_INT);
            int sx = size.getInt(0); int sy = size.getInt(1); int sz = size.getInt(2);
            if (sx > 48 || sy > 48 || sz > 48) throw new IllegalStateException("Module exceeds 48 blocks: " + id);
            ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
            List<BlockState> palette = new ArrayList<>(paletteTag.size());
            for (Tag value : paletteTag) palette.add(readState((CompoundTag) value));
            List<RawBlock> blocks = new ArrayList<>();
            for (Tag value : root.getList("blocks", Tag.TAG_COMPOUND)) {
                CompoundTag block = (CompoundTag) value;
                ListTag pos = block.getList("pos", Tag.TAG_INT);
                blocks.add(new RawBlock(pos.getInt(0), pos.getInt(1), pos.getInt(2), block.getInt("state")));
            }
            return new RawTemplate(sx, sy, sz, List.copyOf(palette), List.copyOf(blocks));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read authored module " + id, failure);
        }
    }

    /** Authored, bounded overlay selection shared by damage and pristine reconstruction. */
    public static VisualModuleSnapshot compileState(VisualModulePlacement module, String state) {
        VisualModuleSnapshot baseline = compile(module);
        if (state.equals("INTACT")) return baseline;
        if (!state.equals("DAMAGED") && !state.equals("RUINED")) {
            throw new IllegalArgumentException("Unsupported authored module state " + state);
        }
        int budget = state.equals("RUINED") ? 48 : 24;
        List<VisualBlockPlacement> candidates = baseline.blocks().stream()
                .filter(value -> stateCell(module, value))
                .sorted(java.util.Comparator.comparingInt((VisualBlockPlacement value) -> -value.position().y())
                        .thenComparingInt(value -> value.position().x())
                        .thenComparingInt(value -> value.position().z()))
                .limit(budget).map(value -> new VisualBlockPlacement(value.position(),
                        damagedState(value.state(), value.position(), state))).toList();
        if (candidates.isEmpty()) throw new IllegalStateException("Authored state has no semantic cells: "
                + module.instanceId());
        return new VisualModuleSnapshot(module.templateId() + "#" + state.toLowerCase(java.util.Locale.ROOT),
                module.footprint(), candidates);
    }

    private static boolean stateCell(VisualModulePlacement module, VisualBlockPlacement value) {
        VisualPoint position = value.position();
        VisualPoint min = module.footprint().min(); VisualPoint max = module.footprint().max();
        boolean perimeter = position.x() <= min.x() + 1 || position.x() >= max.x() - 1
                || position.z() <= min.z() + 1 || position.z() >= max.z() - 1;
        boolean upper = position.y() >= min.y() + Math.max(2, (max.y() - min.y()) * 2 / 3);
        int cadence = module.visualStateProfile().equals("frontier_defence") ? 3
                : module.visualStateProfile().equals("frontier_freight") ? 4 : 5;
        return (perimeter || upper) && Math.floorMod(module.instanceId().hashCode()
                + position.x() * 31 + position.y() * 17 + position.z(), cadence) == 0;
    }

    private static BlockState damagedState(BlockState baseline, VisualPoint position, String state) {
        int roll = Math.floorMod(position.x() * 31 + position.y() * 17 + position.z(), 11);
        if (state.equals("RUINED") && roll <= 3) return Blocks.AIR.defaultBlockState();
        if (roll == 4) return Blocks.COBWEB.defaultBlockState();
        if (baseline.is(Blocks.STONE_BRICKS)) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        if (baseline.is(Blocks.COBBLESTONE)) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        if (baseline.is(net.minecraft.tags.BlockTags.PLANKS) && state.equals("RUINED")) {
            return Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        }
        return baseline;
    }

    private static BlockState climateState(BlockState source, ResourceLocation moduleId) {
        String path = moduleId.getPath();
        if (path.startsWith("temperate/")) return source;
        Block replacement = null;
        if (path.startsWith("cold_taiga/")) {
            replacement = coldReplacement(source.getBlock());
        } else if (path.startsWith("dry_arid/")) {
            replacement = dryReplacement(source.getBlock());
        }
        return replacement == null ? source : copySharedProperties(source, replacement.defaultBlockState());
    }

    /**
     * Private source structures are curated raw material, not an exemption
     * from the authored-module contract. Mine roofs use stepped slab courses;
     * a lower bottom slab beneath the next course leaves a visible half-block
     * daylight seam, so that overlap becomes a double slab. Likewise a full
     * structural log may not balance on a fence-sized decorative post.
     */
    private static List<VisualBlockPlacement> normalizeStructuralDetails(
            VisualModulePlacement module, List<VisualBlockPlacement> source) {
        if (!module.visualStateProfile().equals("frontier_mine")) return List.copyOf(source);
        java.util.LinkedHashMap<BlockPos, VisualBlockPlacement> blocks = new java.util.LinkedHashMap<>();
        for (VisualBlockPlacement value : source) blocks.put(block(value.position()), value);
        int roofBand = module.footprint().min().y()
                + Math.max(2, (module.footprint().max().y() - module.footprint().min().y()) / 2);
        for (VisualBlockPlacement value : List.copyOf(blocks.values())) {
            BlockPos position = block(value.position());
            BlockState state = value.state();
            if (position.getY() >= roofBand && state.hasProperty(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)
                    && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)
                    == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM
                    && steppedSlabAbove(blocks, position)) {
                BlockState sealed = state.setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE,
                        net.minecraft.world.level.block.state.properties.SlabType.DOUBLE);
                blocks.put(position, new VisualBlockPlacement(value.position(), sealed));
            }
            if ((state.is(Blocks.CHAIN) || state.getBlock() instanceof net.minecraft.world.level.block.LanternBlock)
                    && slabLeavesHangingGap(blocks.get(position.above()))) {
                VisualBlockPlacement support = blocks.get(position.above());
                BlockState sealed = support.state().setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE,
                        net.minecraft.world.level.block.state.properties.SlabType.DOUBLE);
                blocks.put(position.above(), new VisualBlockPlacement(support.position(), sealed));
            }
            VisualBlockPlacement below = blocks.get(position.below());
            if (state.is(net.minecraft.tags.BlockTags.LOGS) && below != null
                    && below.state().getBlock() instanceof net.minecraft.world.level.block.FenceBlock) {
                blocks.put(position.below(), new VisualBlockPlacement(below.position(), state));
            }
        }
        supportGroundedMineEdges(module, blocks);
        return List.copyOf(blocks.values());
    }

    /**
     * Curated source modules may contain a masonry/log edge column beginning
     * one block above their declared pad. On natural terrain that reads as a
     * floating arch even though the pad itself is valid. Ground only those
     * structural edge columns; interiors and the semantic entrance stay open.
     */
    private static void supportGroundedMineEdges(VisualModulePlacement module,
            java.util.Map<BlockPos, VisualBlockPlacement> blocks) {
        if (module.foundationId().equals("underground")) return;
        int supportY = module.footprint().min().y();
        int firstRaisedY = supportY + 1;
        java.util.Set<Long> entranceColumns = module.ports().stream()
                .filter(value -> value.kind() == io.farfrontier.palemirror.api.VisualPortKind.PUBLIC_ENTRANCE)
                .map(value -> net.minecraft.world.level.ChunkPos.asLong(
                        value.position().x(), value.position().z()))
                .collect(java.util.stream.Collectors.toSet());
        for (VisualBlockPlacement value : List.copyOf(blocks.values())) {
            BlockPos position = block(value.position());
            if (position.getY() != firstRaisedY || !structuralGroundSupport(value.state())) continue;
            boolean edge = position.getX() == module.footprint().min().x()
                    || position.getX() == module.footprint().max().x()
                    || position.getZ() == module.footprint().min().z()
                    || position.getZ() == module.footprint().max().z();
            if (!edge || entranceColumns.contains(net.minecraft.world.level.ChunkPos.asLong(
                    position.getX(), position.getZ()))) continue;
            BlockPos below = position.below();
            VisualBlockPlacement existing = blocks.get(below);
            if (existing == null || existing.state().isAir()) {
                blocks.put(below, new VisualBlockPlacement(
                        new VisualPoint(below.getX(), below.getY(), below.getZ()), value.state()));
            }
        }
    }

    private static boolean structuralGroundSupport(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.LOGS)
                || state.is(net.minecraft.tags.BlockTags.PLANKS)
                || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.CRACKED_STONE_BRICKS)
                || state.is(Blocks.DEEPSLATE_BRICKS) || state.is(Blocks.CRACKED_DEEPSLATE_BRICKS)
                || state.is(Blocks.BRICKS) || state.is(Blocks.CUT_SANDSTONE)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.SMOOTH_SANDSTONE);
    }

    private static boolean slabLeavesHangingGap(VisualBlockPlacement support) {
        return support != null && support.state().hasProperty(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)
                && support.state().getValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)
                == net.minecraft.world.level.block.state.properties.SlabType.TOP;
    }

    private static boolean steppedSlabAbove(java.util.Map<BlockPos, VisualBlockPlacement> blocks,
                                            BlockPos position) {
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            VisualBlockPlacement neighbour = blocks.get(position.above().relative(direction));
            if (neighbour != null && neighbour.state().hasProperty(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)) return true;
        }
        return false;
    }

    private static BlockPos block(VisualPoint point) {
        return new BlockPos(point.x(), point.y(), point.z());
    }

    private static Block coldReplacement(Block source) {
        if (source == Blocks.OAK_LOG) return Blocks.SPRUCE_LOG;
        if (source == Blocks.STRIPPED_OAK_LOG) return Blocks.STRIPPED_SPRUCE_LOG;
        if (source == Blocks.OAK_WOOD) return Blocks.SPRUCE_WOOD;
        if (source == Blocks.STRIPPED_OAK_WOOD) return Blocks.STRIPPED_SPRUCE_WOOD;
        if (source == Blocks.OAK_PLANKS) return Blocks.SPRUCE_PLANKS;
        if (source == Blocks.OAK_STAIRS) return Blocks.SPRUCE_STAIRS;
        if (source == Blocks.OAK_SLAB) return Blocks.SPRUCE_SLAB;
        if (source == Blocks.OAK_FENCE) return Blocks.SPRUCE_FENCE;
        if (source == Blocks.OAK_FENCE_GATE) return Blocks.SPRUCE_FENCE_GATE;
        if (source == Blocks.OAK_DOOR) return Blocks.SPRUCE_DOOR;
        if (source == Blocks.OAK_TRAPDOOR) return Blocks.SPRUCE_TRAPDOOR;
        if (source == Blocks.STONE_BRICKS) return Blocks.DEEPSLATE_BRICKS;
        if (source == Blocks.STONE_BRICK_STAIRS) return Blocks.DEEPSLATE_BRICK_STAIRS;
        if (source == Blocks.STONE_BRICK_SLAB) return Blocks.DEEPSLATE_BRICK_SLAB;
        if (source == Blocks.STONE_BRICK_WALL) return Blocks.DEEPSLATE_BRICK_WALL;
        return null;
    }

    private static Block dryReplacement(Block source) {
        if (source == Blocks.OAK_LOG || source == Blocks.SPRUCE_LOG) return Blocks.ACACIA_LOG;
        if (source == Blocks.STRIPPED_OAK_LOG || source == Blocks.STRIPPED_SPRUCE_LOG) {
            return Blocks.STRIPPED_ACACIA_LOG;
        }
        if (source == Blocks.OAK_WOOD || source == Blocks.SPRUCE_WOOD) return Blocks.ACACIA_WOOD;
        if (source == Blocks.STRIPPED_OAK_WOOD || source == Blocks.STRIPPED_SPRUCE_WOOD) {
            return Blocks.STRIPPED_ACACIA_WOOD;
        }
        if (source == Blocks.OAK_PLANKS || source == Blocks.SPRUCE_PLANKS) return Blocks.ACACIA_PLANKS;
        if (source == Blocks.OAK_STAIRS || source == Blocks.SPRUCE_STAIRS) return Blocks.ACACIA_STAIRS;
        if (source == Blocks.OAK_SLAB || source == Blocks.SPRUCE_SLAB) return Blocks.ACACIA_SLAB;
        if (source == Blocks.OAK_FENCE || source == Blocks.SPRUCE_FENCE) return Blocks.ACACIA_FENCE;
        if (source == Blocks.OAK_FENCE_GATE || source == Blocks.SPRUCE_FENCE_GATE) return Blocks.ACACIA_FENCE_GATE;
        if (source == Blocks.OAK_DOOR || source == Blocks.SPRUCE_DOOR) return Blocks.ACACIA_DOOR;
        if (source == Blocks.OAK_TRAPDOOR || source == Blocks.SPRUCE_TRAPDOOR) return Blocks.ACACIA_TRAPDOOR;
        if (source == Blocks.COBBLESTONE || source == Blocks.MOSSY_COBBLESTONE
                || source == Blocks.STONE_BRICKS || source == Blocks.BRICKS) return Blocks.CUT_SANDSTONE;
        if (source == Blocks.COBBLESTONE_STAIRS || source == Blocks.MOSSY_COBBLESTONE_STAIRS
                || source == Blocks.STONE_BRICK_STAIRS || source == Blocks.BRICK_STAIRS) {
            return Blocks.SANDSTONE_STAIRS;
        }
        if (source == Blocks.COBBLESTONE_SLAB || source == Blocks.MOSSY_COBBLESTONE_SLAB
                || source == Blocks.STONE_BRICK_SLAB || source == Blocks.BRICK_SLAB) return Blocks.CUT_SANDSTONE_SLAB;
        if (source == Blocks.COBBLESTONE_WALL || source == Blocks.MOSSY_COBBLESTONE_WALL
                || source == Blocks.STONE_BRICK_WALL || source == Blocks.BRICK_WALL) return Blocks.SANDSTONE_WALL;
        return null;
    }

    private static BlockState copySharedProperties(BlockState source, BlockState target) {
        for (Property<?> property : source.getProperties()) {
            Property<?> targetProperty = target.getBlock().getStateDefinition().getProperty(property.getName());
            if (targetProperty != null) target = copyProperty(source, target, property, targetProperty);
        }
        return target;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState copyProperty(BlockState source, BlockState target,
                                           Property sourceProperty, Property targetProperty) {
        Comparable value = source.getValue(sourceProperty);
        return targetProperty.getPossibleValues().contains(value) ? target.setValue(targetProperty, value) : target;
    }

    static BlockState readState(CompoundTag value) {
        ResourceLocation id = ResourceLocation.parse(value.getString("Name"));
        String path = id.getPath();
        if (path.contains("spawner") || path.equals("jigsaw") || path.equals("structure_block")
                || path.equals("tnt") || path.contains("chest") || path.equals("barrel")
                || path.equals("dispenser") || path.contains("ore") || path.startsWith("raw_")
                || path.contains("crushed_raw")) return replacement(id);
        Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(id)
                .orElse(Blocks.CUT_COPPER);
        BlockState state = block.defaultBlockState();
        if (value.contains("Properties", Tag.TAG_COMPOUND)) {
            CompoundTag properties = value.getCompound("Properties");
            for (String name : properties.getAllKeys()) {
                Property<?> property = block.getStateDefinition().getProperty(name);
                if (property != null) state = setValue(state, property, properties.getString(name));
            }
        }
        return state.hasBlockEntity() ? inertBlockEntityReplacement(id) : state;
    }

    private static BlockState inertBlockEntityReplacement(ResourceLocation id) {
        String path = id.getPath();
        if (path.contains("campfire")) return path.startsWith("soul_")
                ? Blocks.SOUL_SOIL.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
        if (path.contains("furnace") || path.equals("smoker")) {
            return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        }
        if (path.contains("sign")) return Blocks.SPRUCE_FENCE.defaultBlockState();
        if (path.equals("decorated_pot")) return Blocks.TERRACOTTA.defaultBlockState();
        if (path.contains("banner")) return Blocks.GRAY_WOOL.defaultBlockState();
        if (path.equals("bell")) return Blocks.GOLD_BLOCK.defaultBlockState();
        if (path.equals("beehive") || path.equals("bee_nest")) return Blocks.STRIPPED_OAK_LOG.defaultBlockState();
        return id.getNamespace().equals("create") ? Blocks.WAXED_EXPOSED_COPPER.defaultBlockState()
                : Blocks.SPRUCE_PLANKS.defaultBlockState();
    }

    private static BlockState replacement(ResourceLocation id) {
        String path = id.getPath();
        if (path.contains("spawner") || path.equals("tnt") || path.equals("jigsaw")
                || path.equals("structure_block")) return Blocks.AIR.defaultBlockState();
        if (path.contains("ore") || path.startsWith("raw_") || path.contains("crushed_raw")) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        return id.getNamespace().equals("create") ? Blocks.WAXED_EXPOSED_COPPER.defaultBlockState()
                : Blocks.SPRUCE_PLANKS.defaultBlockState();
    }

    private static <T extends Comparable<T>> BlockState setValue(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    private static int[] rotate(int x, int z, int sx, int sz, int turns) {
        return switch (turns) {
            case 1 -> new int[]{sz - 1 - z, x};
            case 2 -> new int[]{sx - 1 - x, sz - 1 - z};
            case 3 -> new int[]{z, sx - 1 - x};
            default -> new int[]{x, z};
        };
    }

    private static BlockState rotate(BlockState state, int turns) {
        for (int i = 0; i < turns; i++) state = state.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
        return state;
    }

    private record RawTemplate(int sizeX, int sizeY, int sizeZ, List<BlockState> palette,
                               List<RawBlock> blocks) { }

    private record RawBlock(int x, int y, int z, int state) { }
}
