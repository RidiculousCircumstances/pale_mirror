package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierObjectBoard;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ObjectBoardCursorTest {
    @Test void unrelatedFieldRelocationCannotStarveLaterLoadedBoards() {
        var cursor = FrontierV3ObjectBoardExecutor.Cursor.from(new Revision(0), boards(0), null);
        for (int i = 0; i < 30; i++) {
            cursor = FrontierV3ObjectBoardExecutor.Cursor.from(new Revision(i), boards(i), cursor);
            var selected = cursor.next();
            assertEquals("board:" + (char) ('a' + i % 3), selected.ownerId().value());
            if (i % 3 == 2) assertEquals(i, selected.position().x(), "selection uses the current placement, never its predecessor");
        }
    }
    @Test void addingAndRemovingOwnersResumesAtTheSurvivingNextOwner() {
        var cursor = FrontierV3ObjectBoardExecutor.Cursor.from(new Revision(0), boards(0), null);
        cursor.next(); // Next is b.
        cursor = FrontierV3ObjectBoardExecutor.Cursor.from(new Revision(1),
                List.of(board("0", 0), board("a", 0), board("b", 1), board("c", 1)), cursor);
        assertEquals("board:b", cursor.next().ownerId().value());
        cursor = FrontierV3ObjectBoardExecutor.Cursor.from(new Revision(2),
                List.of(board("0", 0), board("a", 0), board("d", 2)), cursor); // Next c was removed.
        assertEquals("board:d", cursor.next().ownerId().value());
        assertEquals("board:0", cursor.next().ownerId().value(), "new owners are reached on the next fair cycle");
        cursor = FrontierV3ObjectBoardExecutor.Cursor.from(new Revision(3), List.of(), cursor);
        assertFalse(cursor.hasNext());
    }
    @Test void durableWorldCatalogueSurvivesBeyondTheFormerLocalWorldBoundAndRejectsCorruption() {
        var tag = new CompoundTag(); tag.putInt("format", 1); var entries = new ListTag();
        for (int i = 0; i < 1313; i++) {
            var row = new CompoundTag(); row.putString("owner", "board:" + i); row.putLong("pos", i);
            row.putString("uuid", "uuid:" + i); row.putBoolean("conflict", false); entries.add(row);
        }
        tag.put("claims", entries);
        var ledger = FrontierV3ObjectBoardLedger.load(tag, null);
        ledger.applied("board:next", 1314, "uuid:next");
        var recovered = FrontierV3ObjectBoardLedger.load(ledger.save(new CompoundTag(), null), null);
        assertEquals(ledger.claim("board:1312"), recovered.claim("board:1312"));
        assertEquals(ledger.claim("board:next"), recovered.claim("board:next"));
        assertThrows(IllegalStateException.class, () -> recovered.applied("board:next", 1315, "uuid:other"));
        entries.add(entries.getCompound(0).copy());
        assertThrows(IllegalStateException.class, () -> FrontierV3ObjectBoardLedger.load(tag, null));
    }
    private static List<FrontierObjectBoard> boards(int relocated) {
        return List.of(board("a", 0), board("b", 0), board("c", relocated));
    }
    private static FrontierObjectBoard board(String owner, int x) {
        return new FrontierObjectBoard(new SubjectId("board:" + owner), new BlockPosition(x, 67, 0),
                FrontierObjectBoard.Tone.SETTLEMENT, "WHEAT FIELD");
    }
}
