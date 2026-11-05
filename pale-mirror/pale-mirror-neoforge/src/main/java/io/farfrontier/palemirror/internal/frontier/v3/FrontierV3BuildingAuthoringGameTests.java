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
        for (var path : BuildingCatalog.assetPaths()) {
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
            BuildingGeometryValidation.validate(
                    definition,
                    block ->
                            java.util.Optional.ofNullable(decoded.get(block))
                                    .flatMap(
                                            io.farfrontier.palemirror.frontier.v3.model.geometry
                                                            .NavigationObservation
                                                    ::cell));
            helper.assertTrue(
                    !decoded.isEmpty(), "registry compilation must have actual collision facts");
            // Task-owned build output, consumed deliberately by the content author, never live
            // resources.
            var output = Path.of("building-authoring", path + ".geometry");
            Files.createDirectories(output.getParent());
            Files.writeString(output, facts, StandardCharsets.UTF_8);
        }
        helper.succeed();
    }

    private FrontierV3BuildingAuthoringGameTests() {}
}
