package io.farfrontier.palemirror.internal.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
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
    void authoredThreeLineBriefingIsNotWrappedIntoAnUnreadableFiveLineBoard() {
        String briefing = "[ACTION] DAMAGE RESOURCE SITE\nremoves 0.11 of site condition and future output.\nBreak marked block: 8 point(s) remain.";

        String board = SourceGrayboxLabelBoard.text(briefing);

        assertEquals(briefing, board, "explicit briefing rows are the player-facing board grammar");
        assertEquals(3L, board.lines().count(), "a native three-row board must not gain hidden paragraph wraps");
    }

    @Test
    void landmarkBoardsAreLargerThanLocalDetailBoards() {
        assertTrue(SourceGrayboxLabelStyle.scale("settlement:2") > SourceGrayboxLabelStyle.scale("facility:2:workshop"));
        assertTrue(SourceGrayboxLabelStyle.scale("organ:9") > SourceGrayboxLabelStyle.scale("route:1-2"));
        assertTrue(SourceGrayboxLabelStyle.viewRange("settlement:2")
                        > SourceGrayboxLabelStyle.viewRange("facility:2:workshop"),
                "settlement landmarks must orient an arriving player before local detail boards are in range");
        assertTrue(SourceGrayboxLabelStyle.viewRange("organ:9")
                        > SourceGrayboxLabelStyle.viewRange("route:1-2"),
                "hive landmarks must remain visible farther than route detail");
    }

    @Test
    void collocatedBoardsSpreadHorizontallyInsteadOfBecomingSkyLabels() {
        Set<SourceGrayboxLabelSlots.Offset> slots = new HashSet<>();
        for (int ordinal = 0; ordinal < 24; ordinal++) slots.add(SourceGrayboxLabelSlots.offset(ordinal));

        assertEquals(24, slots.size(), "each board at one source anchor needs its own horizontal slot");
        assertEquals(new SourceGrayboxLabelSlots.Offset(0, 0), SourceGrayboxLabelSlots.offset(0));
        assertTrue(slots.stream().anyMatch(slot -> slot.x() != 0 || slot.z() != 0),
                "a second board must move sideways rather than adding vertical height");
    }
}
