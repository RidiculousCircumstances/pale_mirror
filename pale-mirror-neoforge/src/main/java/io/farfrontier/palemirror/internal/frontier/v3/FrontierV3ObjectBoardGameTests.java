package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierObjectBoard;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Materialized player briefing boards must remain attributable and fail closed on world drift. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3ObjectBoardGameTests {
    private FrontierV3ObjectBoardGameTests() { }

    @GameTest(batch = "pm-frontier-v3-object-boards", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void objectBoardIsBrightOwnedAndStableAcrossSavedDataReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(8, 8, 0));
        FrontierObjectBoard board = board(position, "structure:board-depot", FrontierObjectBoard.Tone.SETTLEMENT, "Northreach\nDEPOT\nOPERATIONAL");
        FrontierV3ObjectBoardLedger ledger = FrontierV3ObjectBoardLedger.get(level);
        helper.assertValueEqual(FrontierV3ObjectBoardExecutor.project(level, ledger, board), FrontierV3ObjectBoardExecutor.ProjectionResult.APPLIED,
                "a fresh loaded board position receives one owned readable display");
        Display.TextDisplay display = display(level, position);
        helper.assertValueEqual(display.getPersistentData().getString("pale_mirror.frontier_v3.board_owner"), board.ownerId().value(),
                "the display retains only its canonical object owner identity");
        helper.assertValueEqual(display.getCustomName().getString(), board.text(), "the exact player-facing board text is retained for recovery inspection");
        FrontierV3ObjectBoardLedger reloaded = FrontierV3ObjectBoardLedger.load(ledger.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertValueEqual(FrontierV3ObjectBoardExecutor.project(level, reloaded, board), FrontierV3ObjectBoardExecutor.ProjectionResult.CURRENT,
                "a restarted board ledger recognizes its owned loaded display without duplication");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-object-boards", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void movedBoardBecomesConflictInsteadOfBeingSilentlyRestored(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(16, 8, 0));
        FrontierObjectBoard board = board(position, "organ:board-heart", FrontierObjectBoard.Tone.HIVE, "HIVE\nHEART\nACTIVE");
        FrontierV3ObjectBoardLedger ledger = FrontierV3ObjectBoardLedger.get(level);
        helper.assertValueEqual(FrontierV3ObjectBoardExecutor.project(level, ledger, board), FrontierV3ObjectBoardExecutor.ProjectionResult.APPLIED,
                "the fixture begins from one v3-owned board");
        display(level, position).setPos(position.getX() + 4.5D, position.getY(), position.getZ() + 0.5D);
        helper.assertValueEqual(FrontierV3ObjectBoardExecutor.project(level, ledger, board), FrontierV3ObjectBoardExecutor.ProjectionResult.CONFLICT,
                "a changed presentation object is evidence of drift, not authority to recreate it");
        helper.assertTrue(ledger.claim(board.ownerId().value()).conflicted(), "the conflict remains durable for later inspection");
        helper.assertTrue(level.getEntitiesOfClass(Display.TextDisplay.class, new AABB(position).inflate(1.0D)).isEmpty(),
                "the materializer does not spawn a replacement at the original position");
        helper.succeed();
    }

    private static FrontierObjectBoard board(BlockPos position, String owner, FrontierObjectBoard.Tone tone, String text) {
        return new FrontierObjectBoard(new SubjectId(owner), new BlockPosition(position.getX(), position.getY(), position.getZ()), tone, text);
    }
    private static Display.TextDisplay display(ServerLevel level, BlockPos position) {
        return level.getEntitiesOfClass(Display.TextDisplay.class, new AABB(position).inflate(1.0D)).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("the v3 board display is missing"));
    }
}
