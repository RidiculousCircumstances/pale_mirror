package io.farfrontier.palemirror.internal.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SourceGrayboxLabelPresentationTest {
    @Test
    void longSourceLabelBecomesOneCompactBoardWithoutDiscardingAField() {
        String source = "[H] #17 sporulator biomass=0.75 vit=0.50 FERAL";

        String board = SourceGrayboxLabelBoard.text(source);

        assertTrue(board.contains("\n"), "a detailed object must use a multi-line board rather than a horizon-wide single line");
        assertEquals(source, board.replace("\n", " "),
                "visual line wrapping must not discard or rewrite any canonical source field");
        assertTrue(board.lines().count() <= 3, "one source object must produce one bounded, compact board");
    }

    @Test
    void shortSourceLabelRemainsOneBoardLine() {
        assertEquals("[F] workshop=1.00", SourceGrayboxLabelBoard.text("[F] workshop=1.00"));
    }

    @Test
    void landmarkBoardsAreLargerThanLocalDetailBoards() {
        assertTrue(SourceGrayboxLabelStyle.scale("settlement:2") > SourceGrayboxLabelStyle.scale("facility:2:workshop"));
        assertTrue(SourceGrayboxLabelStyle.scale("organ:9") > SourceGrayboxLabelStyle.scale("route:1-2"));
    }
}
