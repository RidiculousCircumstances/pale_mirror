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
        requireExactFootprint(module, id, rx, template.sizeY(), rz);
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
            CompoundTag data = state.hasBlockEntity() && block.data() != null
                    ? sanitizeBlockEntityData(id, state, block.data(), world) : null;
            blocks.add(data == null ? new VisualBlockPlacement(position, state)
                    : new VisualBlockPlacement(position, state, data));
        }
        return new VisualModuleSnapshot(module.templateId(), module.footprint(), blocks);
    }

    /**
     * The JSON catalog is an exact sidecar contract, not a generous placement
     * envelope. A replaced NBT must update its dimensions explicitly; otherwise
     * doors, pads and collision reservations would silently drift apart.
     */
    private static void requireExactFootprint(VisualModulePlacement module, ResourceLocation id,
                                              int expectedX, int expectedY, int expectedZ) {
        int actualX = module.footprint().max().x() - module.footprint().min().x() + 1;
        int actualY = module.footprint().max().y() - module.footprint().min().y() + 1;
        int actualZ = module.footprint().max().z() - module.footprint().min().z() + 1;
        if (actualX != expectedX || actualY != expectedY || actualZ != expectedZ) {
            throw new IllegalStateException("Authored module catalog/NBT size mismatch for " + id
                    + ": footprint=" + actualX + "x" + actualY + "x" + actualZ
                    + ", nbt=" + expectedX + "x" + expectedY + "x" + expectedZ);
        }
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
                CompoundTag data = block.contains("nbt", Tag.TAG_COMPOUND) ? block.getCompound("nbt").copy() : null;
                blocks.add(new RawBlock(pos.getInt(0), pos.getInt(1), pos.getInt(2), block.getInt("state"), data));
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
        ResourceLocation sourceId = ResourceLocation.parse(value.getString("Name"));
        ResourceLocation id = sourceId;
        Block curatedLegacy = curatedLegacyBlock(id);
        if (curatedLegacy != null) id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(curatedLegacy);
        String path = id.getPath();
        if (path.contains("spawner") || path.equals("jigsaw") || path.equals("structure_block")
                || path.equals("tnt")) return Blocks.AIR.defaultBlockState();
        if (id.getNamespace().equals("create") && path.equals("creative_motor")) {
            return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(
                            ResourceLocation.fromNamespaceAndPath("create", "andesite_casing"))
                    .orElseThrow(() -> new IllegalStateException("Create is missing andesite_casing"))
                    .defaultBlockState();
        }
        if (unsafeExternalController(id)) {
            throw new IllegalStateException("Authored module contains an unscoped external controller: " + id);
        }
        Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) throw new IllegalStateException("Authored module requires missing block " + id);
        BlockState state = block.defaultBlockState();
        if (value.contains("Properties", Tag.TAG_COMPOUND)) {
            CompoundTag properties = value.getCompound("Properties");
            for (String name : properties.getAllKeys()) {
                Property<?> property = block.getStateDefinition().getProperty(name);
                if (property == null && (curatedLegacy != null || compatibleRemovedProperty(sourceId, name))) {
                    continue;
                }
                if (property == null) throw new IllegalStateException("Unknown property " + name + " on " + id);
                String encoded = properties.getString(name);
                var parsed = property.getValue(encoded);
                if (parsed.isEmpty()) throw new IllegalStateException(
                        "Invalid property " + name + "=" + encoded + " on " + id);
                state = setValue(state, property, encoded);
            }
        }
        return state;
    }

    /** Exact harmless source-version deltas; this must not become a generic property fallback. */
    private static boolean compatibleRemovedProperty(ResourceLocation id, String property) {
        return id.equals(ResourceLocation.withDefaultNamespace("chiseled_tuff")) && property.equals("axis");
    }

    /** Explicit substitutions for source-pack blocks unavailable on 1.21.1; never a generic unknown fallback. */
    private static Block curatedLegacyBlock(ResourceLocation id) {
        if (id.equals(ResourceLocation.withDefaultNamespace("grass"))) return Blocks.SHORT_GRASS;
        if (id.equals(ResourceLocation.fromNamespaceAndPath("alexsmobs", "bison_carpet"))) {
            return Blocks.BROWN_CARPET;
        }
        if (id.equals(ResourceLocation.fromNamespaceAndPath("alexsmobs", "bison_fur_block"))) {
            return Blocks.BROWN_WOOL;
        }
        if (id.equals(ResourceLocation.fromNamespaceAndPath("furnish", "green_carpet_on_trapdoor"))) {
            return Blocks.GREEN_CARPET;
        }
        if (id.equals(ResourceLocation.fromNamespaceAndPath("waystones", "waystone"))) {
            return Blocks.LODESTONE;
        }
        if (!id.getNamespace().equals("redeco")) return null;
        String path = id.getPath();
        if (path.endsWith("_stairs")) return Blocks.DARK_OAK_STAIRS;
        if (path.endsWith("_slab")) return Blocks.DARK_OAK_SLAB;
        if (path.endsWith("_door")) return Blocks.DARK_OAK_DOOR;
        if (path.endsWith("_trapdoor")) return Blocks.DARK_OAK_TRAPDOOR;
        if (path.endsWith("_fence")) return Blocks.DARK_OAK_FENCE;
        if (path.endsWith("_fence_gate")) return Blocks.DARK_OAK_FENCE_GATE;
        if (path.contains("dark_oak")) return Blocks.DARK_OAK_PLANKS;
        throw new IllegalStateException("Curated ReDeco substitution is not defined for " + id);
    }

    private static boolean unsafeExternalController(ResourceLocation id) {
        String path = id.getPath();
        if (!id.getNamespace().equals("create")) return false;
        return path.equals("redstone_link") || path.equals("display_link") || path.equals("stock_link")
                || path.equals("redstone_requester") || path.equals("factory_gauge")
                || path.contains("train_station") || path.equals("track_station")
                || path.equals("linked_controller");
    }

    private static CompoundTag sanitizeBlockEntityData(ResourceLocation moduleId, BlockState state,
                                                        CompoundTag source, BlockPos world) {
        CompoundTag result = source.copy();
        result.putInt("x", world.getX());
        result.putInt("y", world.getY());
        result.putInt("z", world.getZ());
        // Imported source-world coordinates cannot survive module rotation.
        // Local machine adjacency is rediscovered from placed neighbours. A
        // stopped bearing/contraption may remain as architecture, but imported
        // runtime motion and nested links are never resumed in a new world.
        stripUnsafeRuntimeReferences(result, true);
        if (!result.contains("id", Tag.TAG_STRING)) {
            ResourceLocation stateId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
            throw new IllegalStateException("Block entity in " + moduleId + " at " + world
                    + " has no id for " + stateId);
        }
        return result;
    }

    private static void stripUnsafeRuntimeReferences(Tag tag, boolean root) {
        if (tag instanceof CompoundTag compound) {
            for (String key : List.copyOf(compound.getAllKeys())) {
                String normalized = key.toLowerCase(java.util.Locale.ROOT);
                boolean worldReference = normalized.equals("controller") || normalized.equals("target")
                        || normalized.equals("lastknownpos") || normalized.equals("linkedpos")
                        || normalized.equals("globalpos");
                boolean orphanedRuntime = normalized.equals("contraption")
                        || normalized.equals("movedcontraption") || normalized.equals("running")
                        || normalized.equals("clientanglediff");
                if ((!root || !normalized.equals("id")) && (worldReference || orphanedRuntime)) {
                    compound.remove(key);
                    continue;
                }
                Tag nested = compound.get(key);
                if (nested != null) stripUnsafeRuntimeReferences(nested, false);
            }
        } else if (tag instanceof ListTag list) {
            for (Tag nested : list) stripUnsafeRuntimeReferences(nested, false);
        }
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

    private record RawBlock(int x, int y, int z, int state, CompoundTag data) {
        private RawBlock { data = data == null ? null : data.copy(); }
        @Override public CompoundTag data() { return data == null ? null : data.copy(); }
    }
}
