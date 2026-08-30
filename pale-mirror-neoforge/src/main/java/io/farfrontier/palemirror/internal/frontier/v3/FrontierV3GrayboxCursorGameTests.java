package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Loaded-demand selection must not wait for unrelated, unloaded world geometry. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3GrayboxCursorGameTests {
    private FrontierV3GrayboxCursorGameTests() { }

    @GameTest(batch = "pm-frontier-v3-graybox", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void naturallyLoadedChunkIsSelectedAheadOfEarlierUnloadedGeometry(GameTestHelper helper) {
        GrayboxCell west = cell(-256, 64, 0, "organ:west");
        GrayboxCell eastA = cell(256, 64, 0, "organ:east-a");
        GrayboxCell eastB = cell(257, 64, 0, "organ:east-b");
        FrontierV3GrayboxExecutor.Cursor cursor = FrontierV3GrayboxExecutor.Cursor.fromCells(new Revision(1), List.of(west, eastA, eastB), null);
        helper.assertValueEqual(cursor.nextNaturallyLoaded(cell -> cell.position().x() >= 0).orElseThrow().ownerId().value(), "organ:east-a",
                "one player-loaded east chunk is not delayed behind a remote west chunk");
        helper.assertValueEqual(cursor.nextNaturallyLoaded(cell -> cell.position().x() >= 0).orElseThrow().ownerId().value(), "organ:east-b",
                "the local loaded chunk keeps bounded in-chunk progress");
        helper.succeed();
    }

    private static GrayboxCell cell(int x, int y, int z, String owner) {
        return new GrayboxCell(new BlockPosition(x, y, z), new SubjectId(owner), GrayboxMaterial.HIVE_STORE, GrayboxSemanticPart.HIVE_TISSUE);
    }
}
