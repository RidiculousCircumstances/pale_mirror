package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierObjectBoard;
import io.farfrontier.palemirror.internal.presentation.PlayerContextCard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrontierV3ObjectBoardCardTest {
    @Test
    void preservesObjectKindAndAllStateLinesWithinOneBoundedCard() {
        PlayerContextCard card = FrontierV3ObjectBoardCard.fromBoard(board(FrontierObjectBoard.Tone.WARNING,
                "HIVE\nGANGLION\nACTIVE\nINFECTED\nSATURATED"));

        assertEquals("HIVE", card.title());
        assertEquals(java.util.List.of("GANGLION", "ACTIVE · INFECTED · SATURATED"), card.lines());
        assertEquals(0xFF5A5A, card.accentRgb());
    }

    @Test
    void clipsUnexpectedLongBoardLineRatherThanFailingPlayerInteraction() {
        String longLine = "x".repeat(119);
        PlayerContextCard card = FrontierV3ObjectBoardCard.fromBoard(board(FrontierObjectBoard.Tone.HIVE, longLine));

        assertEquals(72, card.title().length());
        assertEquals("…", card.title().substring(card.title().length() - 1));
        assertEquals(java.util.List.of(), card.lines());
    }

    private static FrontierObjectBoard board(FrontierObjectBoard.Tone tone, String text) {
        return new FrontierObjectBoard(new SubjectId("organ:test-ganglion"), new BlockPosition(1, 64, 1), tone, text);
    }
}
