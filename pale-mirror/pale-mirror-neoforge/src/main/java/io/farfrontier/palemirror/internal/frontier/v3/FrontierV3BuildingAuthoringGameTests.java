package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.buildings.*;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Asset compilation only: no gameplay acceptance, world mutation or simulation fixture. */
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

    private FrontierV3BuildingAuthoringGameTests() {}
}
