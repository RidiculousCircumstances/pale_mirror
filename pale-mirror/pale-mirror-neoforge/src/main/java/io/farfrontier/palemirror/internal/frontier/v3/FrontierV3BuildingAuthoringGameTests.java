package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.buildings.*;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Native authored-content identity and geometry checks; no gameplay acceptance. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3BuildingAuthoringGameTests {
    @GameTest(
            batch = "pm-frontier-v3-building-authoring",
            templateNamespace = "minecraft",
            template = "bastion/mobs/empty",
            timeoutTicks = 30)
    public static void compileDeclaredNativeCollisionFacts(GameTestHelper helper) throws Exception {
        var failures=new java.util.ArrayList<String>();
        for (var path : BuildingCatalog.assetPaths()) {
          try {
            var prefix = "/io/farfrontier/palemirror/frontier/v3/buildings/" + path;
            byte[] bytes;
            try (var input =
                    FrontierV3BuildingAuthoringGameTests.class.getResourceAsStream(
                            prefix + ".blocks")) {
                if (input == null) throw new IllegalStateException("missing authored asset");
                bytes = input.readAllBytes();
            }
            var blocks = BuildingDefinitionFormat.blocks(bytes);
            try (var input =
                    FrontierV3BuildingAuthoringGameTests.class.getResourceAsStream(
                            prefix + ".nbt")) {
                if (input == null) throw new IllegalStateException("missing native structure");
                FrontierV3BuildingAuthoring.requireNativeTemplate(input.readAllBytes(), blocks);
            }
            var facts = FrontierV3BuildingAuthoring.collisionFacts(blocks);
            var decoded = BuildingCollisionCatalog.decode(facts.getBytes(StandardCharsets.UTF_8));
            byte[] markup;
            try (var input =
                    FrontierV3BuildingAuthoringGameTests.class.getResourceAsStream(
                            prefix + ".properties")) {
                if (input == null) throw new IllegalStateException("missing markup");
                markup = input.readAllBytes();
            }
            var definition =
                    BuildingDefinitionFormat.decode(
                            BuildingDefinitionFormat.properties(markup),
                            blocks,
                            BuildingCatalog.sha256(markup, bytes));
            // Preserve actual compiled facts even when annotation validation fails, so the
            // content author can diagnose all assets in one pass. This is not promotion.
            var certificate=Path.of("building-authoring",path+".geometry");
            Files.createDirectories(certificate.getParent());Files.writeString(certificate,facts,StandardCharsets.UTF_8);
            FrontierV3BuildingAuthoring.validate(definition, decoded);
            helper.assertTrue(
                    !decoded.isEmpty(), "registry compilation must have actual collision facts");
            // Task-owned build output, consumed deliberately by the content author, never live
            // resources.
            var output = Path.of("building-authoring", path + ".geometry");
            Files.createDirectories(output.getParent());
            Files.writeString(output, facts, StandardCharsets.UTF_8);
          } catch(Exception failure) {failures.add(path+": "+failure.getClass().getSimpleName()+": "+failure.getMessage());}
        }
        if (!failures.isEmpty()) {
            Files.writeString(Path.of("building-authoring", "failures.txt"), String.join("\n", failures), StandardCharsets.UTF_8);
            helper.fail(failures.size() + " authored assets failed; see building-authoring/failures.txt");
        }
        helper.succeed();
    }

    @GameTest(batch="pm-frontier-v3-building-authoring",templateNamespace="minecraft",template="bastion/mobs/empty",timeoutTicks=30)
    public static void equipmentModelsFollowDeclaredMountsAndAllNativeRotations(GameTestHelper helper) {
        var state=io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState.initial(
                io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper.create(
                        new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:equipment-facing"),139));
        for(var home:state.bootstrap().settlements()) for(var facility:home.structures()) {
            if(!io.farfrontier.palemirror.frontier.v3.model.ProductionFacilityLayout.supports(facility.kind()))continue;
            for(var spec:io.farfrontier.palemirror.frontier.v3.model.ProductionFacilityLayout.stations(facility)) {
            var record=state.inventory().containers().get(spec.containerId());
            var block=FrontierV3FixedContainerProviders.state(record);
            var property=io.farfrontier.palemirror.internal.production.ProductionBenchBlock.FACING;
            helper.assertTrue(block.getValue(property).name().equals(spec.facing().name()),"native machine must face its exact operator");
            helper.assertTrue(FrontierV3FixedContainerProviders.acceptsBlock(record,block),"correct installation accepted");
            helper.assertTrue(!FrontierV3FixedContainerProviders.matchesMount(spec,block.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90)),
                    "wrong orientation is not adopted or overwritten as the declared machine");
            helper.assertTrue(FrontierV3FixedContainerProviders.acceptsBlock(record,block.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90)),
                    "turning an installed machine must not erase its inventory provider or custody");
            helper.assertTrue(block.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90).getValue(property)==block.getValue(property).getClockWise(),
                    "native rotation must agree with mount rotation");
        }
        }
        helper.succeed();
    }

    @GameTest(batch="pm-frontier-v3-building-authoring", templateNamespace="minecraft",
            template="bastion/mobs/empty", timeoutTicks=30)
    public static void signIdentityUsesNativeTextNormalizationButRejectsChangedText(GameTestHelper helper) {
        var position = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        var level = helper.getLevel();
        level.setBlock(position.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(position, net.minecraft.world.level.block.Blocks.OAK_SIGN.defaultBlockState(), 3);
        var sign = (net.minecraft.world.level.block.entity.SignBlockEntity) level.getBlockEntity(position);
        var tag = sign.saveWithoutMetadata(level.registryAccess());
        var front = tag.getCompound("front_text");
        front.put("filtered_messages", front.getList("messages", net.minecraft.nbt.Tag.TAG_STRING).copy());
        var payload = new net.minecraft.nbt.CompoundTag();
        payload.put("front_text", front);
        var data = new io.farfrontier.palemirror.frontier.v3.model.AuthoredBlockData(
                io.farfrontier.palemirror.frontier.v3.model.AuthoredBlockData.Kind.SIGN_TEXT,
                net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion(), payload.toString());
        tag.merge(payload); sign.loadWithComponents(tag, level.registryAccess());
        helper.assertTrue(FrontierV3AuthoredBlockData.matches(level, position, data),
                "native omission of redundant filtered text is not structural damage or a blocked chunk");
        sign.setText(sign.getText(true).setMessage(0, net.minecraft.network.chat.Component.literal("Player edit")), true);
        helper.assertTrue(!FrontierV3AuthoredBlockData.matches(level, position, data),
                "a real player text change must remain distinct from the authored payload");
        helper.succeed();
    }

    @GameTest(batch="pm-frontier-v3-building-authoring", templateNamespace="minecraft",
            template="bastion/mobs/empty", timeoutTicks=30)
    public static void materialIdentityRetainsNativeComponentsAndRejectsVisibleReplacement(GameTestHelper helper) {
        var position = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        var level = helper.getLevel();
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse("domum_ornamentum:shingle_slab"));
        level.setBlock(position, block.defaultBlockState(), 3);
        var entity = level.getBlockEntity(position);
        var payload = new net.minecraft.nbt.CompoundTag(); var textures = new net.minecraft.nbt.CompoundTag();
        textures.putString("minecraft:block/oak_planks", "domum_ornamentum:cobblestone_extra");
        textures.putString("minecraft:block/dark_oak_planks", "minecraft:oak_planks");
        textures.putString("minecraft:block/acacia_planks", "domum_ornamentum:cobblestone_extra");
        payload.put("textureData", textures);
        var data = new io.farfrontier.palemirror.frontier.v3.model.AuthoredBlockData(
                io.farfrontier.palemirror.frontier.v3.model.AuthoredBlockData.Kind.MATERIAL_TEXTURES,
                3465, payload.toString());
        entity.loadWithComponents(payload, level.registryAccess());
        helper.assertTrue(FrontierV3AuthoredBlockData.matches(level, position, data),
                "source components unused by the native model do not block an unchanged authored cell");
        textures.putString("minecraft:block/dark_oak_planks", "minecraft:birch_planks");
        entity.loadWithComponents(payload, level.registryAccess());
        helper.assertTrue(!FrontierV3AuthoredBlockData.matches(level, position, data),
                "changing an actual visible material must still reject authored identity");
        helper.succeed();
    }

    @GameTest(batch="pm-frontier-v3-building-authoring", templateNamespace="minecraft",
            template="bastion/mobs/empty", timeoutTicks=30)
    public static void oldBannerPatternsAreMigratedAndRemainVisible(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        level.setBlock(position.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(position, net.minecraft.world.level.block.Blocks.WHITE_BANNER.defaultBlockState(), 3);
        var entity = (net.minecraft.world.level.block.entity.BannerBlockEntity) level.getBlockEntity(position);
        var data = new io.farfrontier.palemirror.frontier.v3.model.AuthoredBlockData(
                io.farfrontier.palemirror.frontier.v3.model.AuthoredBlockData.Kind.BANNER_PATTERNS,
                3465, "{Patterns:[{Color:15,Pattern:\"bo\"}]}");
        var payload = FrontierV3AuthoredBlockData.payload(data);
        helper.assertTrue(payload.contains("patterns"), "vanilla migration must retain the old nonempty pattern declaration: " + payload);
        entity.loadWithComponents(payload, level.registryAccess());
        helper.assertTrue(entity.getPatterns().layers().size() == 1, "import preserves the original visible banner pattern");
        helper.assertTrue(FrontierV3AuthoredBlockData.matches(level, position, data), "migrated pattern matches its native image");
        var empty = new io.farfrontier.palemirror.frontier.v3.model.AuthoredBlockData(
                io.farfrontier.palemirror.frontier.v3.model.AuthoredBlockData.Kind.BANNER_PATTERNS,
                3465, "{Patterns:[]}");
        helper.assertTrue(!FrontierV3AuthoredBlockData.matches(level, position, empty), "an empty source may not accept a different visible pattern");
        entity.loadWithComponents(FrontierV3AuthoredBlockData.payload(empty), level.registryAccess());
        // Native load of an absent field retains old state; use a fresh block entity as genesis does.
        level.setBlockEntity(new net.minecraft.world.level.block.entity.BannerBlockEntity(position, entity.getBlockState()));
        helper.assertTrue(FrontierV3AuthoredBlockData.matches(level, position, empty), "native omission of empty patterns is legitimate");
        helper.succeed();
    }

    private FrontierV3BuildingAuthoringGameTests() {}
}
