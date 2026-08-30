package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierObjectBoard;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

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

    @GameTest(batch = "pm-frontier-v3-object-boards", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void ownedBoardUpdatesItsReadableTextWithoutReplacingItsBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(24, 8, 0));
        FrontierObjectBoard prior = board(position, "organ:board-updated-heart", FrontierObjectBoard.Tone.WARNING,
                "HIVE\nHEART\nACTIVE\nINFECTION · SATURATED");
        FrontierObjectBoard current = board(position, "organ:board-updated-heart", FrontierObjectBoard.Tone.WARNING,
                "HIVE\nHEART\nACTIVE\nINFECTED\nSATURATED");
        FrontierV3ObjectBoardLedger ledger = FrontierV3ObjectBoardLedger.get(level);
        helper.assertValueEqual(FrontierV3ObjectBoardExecutor.project(level, ledger, prior), FrontierV3ObjectBoardExecutor.ProjectionResult.APPLIED,
                "the initial canonical board is materialized once");
        helper.assertValueEqual(FrontierV3ObjectBoardExecutor.project(level, ledger, current), FrontierV3ObjectBoardExecutor.ProjectionResult.UPDATED,
                "a changed canonical explanation updates the owned board in place");
        helper.assertValueEqual(display(level, position).getCustomName().getString(), current.text(), "the display exposes only current canonical text");
        helper.assertValueEqual(level.getEntitiesOfClass(Display.TextDisplay.class, new AABB(position).inflate(1.0D)).size(), 1,
                "an explanation update never duplicates a player-facing board");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-object-boards", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void localBoardUsesCompactOccludedPhysicalPresentation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(27, 8, 0));
        FrontierObjectBoard board = board(position, "site:board-local-field", FrontierObjectBoard.Tone.SETTLEMENT,
                "Northwatch\nWHEAT FIELD\nGROWING · STAGE 0/7");
        helper.assertValueEqual(FrontierV3ObjectBoardExecutor.project(level, FrontierV3ObjectBoardLedger.get(level), board), FrontierV3ObjectBoardExecutor.ProjectionResult.APPLIED,
                "a local object board is materialized once");
        CompoundTag data = display(level, position).saveWithoutId(new CompoundTag());
        helper.assertFalse(data.getBoolean("see_through"), "a local board may not render through its own or a neighbouring structure");
        helper.assertTrue(data.getFloat("view_range") <= 0.70F, "local state remains local instead of becoming horizon-wide HUD text");
        helper.assertTrue(data.getInt("line_width") <= 144, "local text has a compact readable line width");
        helper.assertTrue(data.contains("transformation", Tag.TAG_COMPOUND), "the compact scale is persisted with the owned display");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-object-boards", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void contextualCardMayOnlyUseTheExactClaimedBoardBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); BlockPos position = helper.absolutePos(new BlockPos(30, 8, 0));
        FrontierObjectBoard board = board(position, "site:board-owned-field", FrontierObjectBoard.Tone.SETTLEMENT,
                "Northwatch\nWHEAT FIELD\nGROWING · STAGE 0/7");
        FrontierV3ObjectBoardLedger ledger = FrontierV3ObjectBoardLedger.get(level);
        helper.assertValueEqual(FrontierV3ObjectBoardExecutor.project(level, ledger, board), FrontierV3ObjectBoardExecutor.ProjectionResult.APPLIED,
                "the exact semantic board must exist before it can become player context");
        Display.TextDisplay claimed = display(level, position);
        helper.assertTrue(FrontierV3ObjectBoardExecutor.isCurrentOwnedBoard(level, claimed, board),
                "the ledger-recognized board can safely supply a noncanonical contextual card");
        Display.TextDisplay impostor = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        impostor.setPos(position.getX() + 3.5D, position.getY(), position.getZ() + 0.5D);
        impostor.getPersistentData().putString("pale_mirror.frontier_v3.board_owner", board.ownerId().value());
        level.addFreshEntity(impostor);
        helper.assertFalse(FrontierV3ObjectBoardExecutor.isCurrentOwnedBoard(level, impostor, board),
                "a lookalike owner tag without the claimed UUID and position cannot trigger a contextual card");
        helper.succeed();
    }

    @GameTest(batch = "pm-frontier-v3-object-boards", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void cursorKeepsRoundRobinProgressAcrossUnrelatedCanonicalRevisions(GameTestHelper helper) {
        List<FrontierObjectBoard> before = List.of(
                board(new BlockPos(0, 0, 0), "board:a", FrontierObjectBoard.Tone.HIVE, "old-a"),
                board(new BlockPos(1, 0, 0), "board:b", FrontierObjectBoard.Tone.HIVE, "old-b"),
                board(new BlockPos(2, 0, 0), "board:c", FrontierObjectBoard.Tone.HIVE, "old-c"));
        FrontierV3ObjectBoardExecutor.Cursor first = FrontierV3ObjectBoardExecutor.Cursor.from(new io.farfrontier.palemirror.frontier.v3.api.Revision(7), before, null);
        helper.assertValueEqual(first.next().ownerId().value(), "board:a", "the fresh cursor starts from its deterministic first board");
        List<FrontierObjectBoard> changedText = List.of(
                board(new BlockPos(0, 0, 0), "board:a", FrontierObjectBoard.Tone.HIVE, "new-a"),
                board(new BlockPos(1, 0, 0), "board:b", FrontierObjectBoard.Tone.HIVE, "new-b"),
                board(new BlockPos(2, 0, 0), "board:c", FrontierObjectBoard.Tone.HIVE, "new-c"));
        FrontierV3ObjectBoardExecutor.Cursor refreshed = FrontierV3ObjectBoardExecutor.Cursor.from(new io.farfrontier.palemirror.frontier.v3.api.Revision(8), changedText, first);
        helper.assertValueEqual(refreshed.next().ownerId().value(), "board:b",
                "a new canonical revision must not repeatedly restart the scan at the first board");
        FrontierV3ObjectBoardExecutor.Cursor refreshedAgain = FrontierV3ObjectBoardExecutor.Cursor.from(new io.farfrontier.palemirror.frontier.v3.api.Revision(9), changedText, refreshed);
        helper.assertValueEqual(refreshedAgain.next().ownerId().value(), "board:c",
                "continued revisions still reach later loaded boards under the bounded budget");
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
