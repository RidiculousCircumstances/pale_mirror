package io.farfrontier.palemirror.gametest;

import java.nio.file.Files;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.internal.adapter.AdapterRegistry;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Exercises only FTB's public presentation surface; it never tests or changes FTB progress. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FtbPresentationGameTests {
    private FtbPresentationGameTests() { }

    @GameTest(batch = "pm-ftb-presentation", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void ftbJournalIsProvisionedWithoutOwningScenarioProgress(GameTestHelper helper) {
        if (!ModList.get().isLoaded("ftbquests")) { helper.succeed(); return; }
        var adapter = AdapterRegistry.all().stream().filter(value -> value.id().equals("pale_mirror:ftb_quests_presentation"))
                .findFirst().orElseThrow();
        if (adapter.health().status() != AdapterHealth.Status.AVAILABLE) {
            throw new AssertionError("FTB presentation unavailable: " + adapter.health().detail());
        }
        var chapter = FMLPaths.CONFIGDIR.get().resolve("ftbquests").resolve("quests").resolve("chapters")
                .resolve("pale_mirror_first_living_region.snbt");
        helper.assertTrue(Files.isRegularFile(chapter), "PM must install its one static FTB journal chapter");
        helper.assertTrue(AdapterRegistry.scenarioJournalCommand().orElse("").startsWith("/ftbquests open_book "),
                "the player-facing action must use FTB's public open-book command only");
        helper.succeed();
    }
}
