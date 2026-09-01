package io.farfrontier.palemirror;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Verifies the registered Brigadier tree, which is outside the pure JUnit classpath. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PaleMirrorCommandGameTests {
    private PaleMirrorCommandGameTests() { }

    @GameTest(batch = "pm-frontier-v3-fast-forward-command", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void v3AdvanceIsPresentInTheRegisteredCommandTree(GameTestHelper helper) {
        var root = PaleMirrorCommandRegistrar.commandTree().build();
        var v3 = root.getChild("v3");
        helper.assertTrue(v3 != null && v3.getChild("advance") != null,
                "the v3 advance command must remain in the completed tree after its branch is attached");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-diagnostics-command", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void v3RouteConstructionDiagnosticIsPresentInTheRegisteredCommandTree(GameTestHelper helper) {
        var root = PaleMirrorCommandRegistrar.commandTree().build();
        var v3 = root.getChild("v3");
        var inspect = v3 == null ? null : v3.getChild("inspect");
        helper.assertTrue(inspect != null && inspect.getChild("route_construction") != null,
                "the native pilot's route-construction diagnostic must be executable through the registered command tree");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-diagnostics-command", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void v3MedicalDiagnosticIsPresentInTheRegisteredCommandTree(GameTestHelper helper) {
        var root = PaleMirrorCommandRegistrar.commandTree().build();
        var v3 = root.getChild("v3");
        var inspect = v3 == null ? null : v3.getChild("inspect");
        helper.assertTrue(inspect != null && inspect.getChild("medical") != null,
                "the native pilot's medical diagnostic must be executable through the registered command tree");
        helper.succeed();
    }
}
