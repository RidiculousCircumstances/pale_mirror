package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.buildings.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;

import java.util.*;

/**
 * Native content compiler. Reads registry shapes, never a live world's blocks or canonical state.
 */
final class FrontierV3BuildingAuthoring {
    static String collisionFacts(Map<BlockPosition, BuildingDefinition.Block> blocks) {
        var palette = new TreeMap<String, StructureMaterial.Authored>();
        for (var facing : FacilityFacing.values()) {
            var placement =
                    new BuildingPlacement(
                            new BlockPosition(0, 0, 0),
                            new BlockPosition(0, 0, 0),
                            FacilityFacing.SOUTH,
                            facing);
            blocks.values()
                    .forEach(
                            block -> {
                                var nativeRotation =
                                        switch (placement.turns()) {
                                            case 0 -> net.minecraft.world.level.block.Rotation.NONE;
                                            case 1 ->
                                                    net.minecraft.world.level.block.Rotation
                                                            .CLOCKWISE_90;
                                            case 2 ->
                                                    net.minecraft.world.level.block.Rotation
                                                            .CLOCKWISE_180;
                                            case 3 ->
                                                    net.minecraft.world.level.block.Rotation
                                                            .COUNTERCLOCKWISE_90;
                                            default ->
                                                    throw new IllegalStateException(
                                                            "invalid building rotation");
                                        };
                                var rotated = placement.state(block.state());
                                if (!rotated.equals(
                                        FrontierV3BlockStates.describe(
                                                FrontierV3BlockStates.resolve(block.state())
                                                        .rotate(nativeRotation))))
                                    throw new IllegalArgumentException(
                                            "unsupported native orientation transform: "
                                                    + block.state());
                                var material =
                                        new StructureMaterial.Authored(rotated, block.repairItem());
                                palette.put(material.wireIdentity(), material);
                            });
        }
        var out =
                new StringBuilder(
                        "# Minecraft registry collision facts; regenerate through building"
                                + " authoring, never hand-edit.\n");
        for (var material : palette.values()) {
            if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(
                    net.minecraft.resources.ResourceLocation.parse(material.repairItemKind())))
                throw new IllegalArgumentException(
                        "authored block has unknown repair item " + material.repairItemKind());
            var fact =
                    FrontierV3NavigationShapes.declared(
                            FrontierV3BlockStates.resolve(material.block()),
                            new BlockPosition(0, 64, 0),
                            384,
                            -64);
            out.append(BuildingCollisionCatalog.row(material, fact)).append('\n');
        }
        return out.toString();
    }

    static void requireNativeTemplate(
            byte[] compressed, Map<BlockPosition, BuildingDefinition.Block> expected)
            throws java.io.IOException {
        var tag =
                net.minecraft.nbt.NbtIo.readCompressed(
                        new java.io.ByteArrayInputStream(compressed),
                        net.minecraft.nbt.NbtAccounter.create(33_554_432));
        if (tag.getInt("DataVersion")
                != net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion())
            throw new IllegalArgumentException(
                    "authored native template targets another Minecraft data version");
        if (!tag.getList("entities", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty())
            throw new IllegalArgumentException("authored template contains foreign entities");
        var palette = tag.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
        var actual = new HashMap<BlockPosition, NavigationBlockState>();
        for (var value : tag.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var block = (net.minecraft.nbt.CompoundTag) value;
            if (block.contains("nbt"))
                throw new IllegalArgumentException(
                        "authored template contains block inventories or controllers");
            var point = block.getList("pos", net.minecraft.nbt.Tag.TAG_INT);
            var state = palette.getCompound(block.getInt("state"));
            var properties = new TreeMap<String, String>();
            var declared = state.getCompound("Properties");
            for (var key : declared.getAllKeys()) properties.put(key, declared.getString(key));
            var position = new BlockPosition(point.getInt(0), point.getInt(1), point.getInt(2));
            if (actual.putIfAbsent(
                            position, new NavigationBlockState(state.getString("Name"), properties))
                    != null)
                throw new IllegalArgumentException("duplicate native template position");
        }
        var neutral = new HashMap<BlockPosition, NavigationBlockState>();
        expected.forEach((p, b) -> neutral.put(p, b.state()));
        if (!actual.equals(neutral))
            throw new IllegalArgumentException(
                    "native template and neutral block projection differ");
    }

    private FrontierV3BuildingAuthoring() {}
}
