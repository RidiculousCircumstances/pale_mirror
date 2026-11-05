package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

/** Closed visual-data adapter. Never loads inventories, colony links, commands or controllers. */
final class FrontierV3AuthoredBlockData {
    private static final Map<AuthoredBlockData, CompoundTag> NORMALIZED = Collections.synchronizedMap(new WeakHashMap<>());
    private FrontierV3AuthoredBlockData() { }
    static CompoundTag payload(AuthoredBlockData data) {
        var cached = NORMALIZED.get(data);
        if (cached != null) return cached.copy();
        CompoundTag tag;
        try { tag = TagParser.parseTag(data.snbt()); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) { throw new IllegalArgumentException("invalid authored visual NBT", invalid); }
        Set<String> permitted = switch (data.kind()) {
            case MATERIAL_TEXTURES -> Set.of("textureData");
            case SIGN_TEXT -> Set.of("front_text", "back_text", "is_waxed");
            case BANNER_PATTERNS -> Set.of("Patterns", "patterns");
        };
        if (tag.isEmpty() || !permitted.containsAll(tag.getAllKeys())) throw new IllegalArgumentException("authored visual payload has foreign fields");
        if (data.kind() == AuthoredBlockData.Kind.MATERIAL_TEXTURES) {
            var textures = tag.getCompound("textureData");
            if (textures.isEmpty() || textures.getAllKeys().size() > 32) throw new IllegalArgumentException("missing bounded material texture data");
            for (var key : textures.getAllKeys()) {
                net.minecraft.resources.ResourceLocation.parse(key);
                if (!textures.contains(key, Tag.TAG_STRING) || !BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.ResourceLocation.parse(textures.getString(key))))
                    throw new IllegalArgumentException("unknown source material " + textures.getString(key));
            }
        } else {
            tag.putString("id", data.kind() == AuthoredBlockData.Kind.SIGN_TEXT ? "minecraft:sign" : "minecraft:banner");
            tag = (CompoundTag) DataFixers.getDataFixer().update(net.minecraft.util.datafix.fixes.References.BLOCK_ENTITY,
                    new com.mojang.serialization.Dynamic<>(NbtOps.INSTANCE, tag), data.dataVersion(),
                    SharedConstants.getCurrentVersion().getDataVersion().getVersion()).getValue();
            tag.remove("id");
        }
        NORMALIZED.put(data, tag.copy()); return tag;
    }
    static void validate(BlockEntity entity, AuthoredBlockData data) {
        if (entity == null) throw new IllegalArgumentException("visual payload requires its native block entity");
        var id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()).toString();
        boolean accepts = switch (data.kind()) {
            case MATERIAL_TEXTURES -> id.equals("domum_ornamentum:materially_retexturable");
            case SIGN_TEXT -> id.equals("minecraft:sign") || id.equals("minecraft:hanging_sign");
            case BANNER_PATTERNS -> id.equals("minecraft:banner");
        };
        if (!accepts) throw new IllegalArgumentException("authored payload has incompatible native entity " + id);
        payload(data);
    }
    static void apply(LevelAccessor level, BlockPos position, StructureMaterial material) {
        if (!(material instanceof StructureMaterial.Authored authored) || authored.data().isEmpty()) return;
        var data = authored.data().orElseThrow(); var entity = level.getBlockEntity(position);
        validate(entity, data);
        // Start from the newly placed entity, retaining native defaults but no foreign inventory.
        var nativeTag = entity.saveWithoutMetadata(level.registryAccess()); nativeTag.merge(payload(data));
        entity.loadWithComponents(nativeTag, level.registryAccess()); entity.setChanged();
    }
}
