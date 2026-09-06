package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierObjectBoard;
import io.farfrontier.palemirror.internal.presentation.PlayerContextCard;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Pure adapter from an immutable v3 board to the short, replace-not-stack player card.
 * It deliberately preserves all state lines by folding any trailing board text into the
 * final card line instead of making the card a second long-form status surface.
 */
final class FrontierV3ObjectBoardCard {
    private static final int TITLE_LIMIT = 72;
    private static final int DETAIL_LIMIT = 112;

    private FrontierV3ObjectBoardCard() { }

    static PlayerContextCard fromBoard(FrontierObjectBoard board) {
        Objects.requireNonNull(board, "board");
        List<String> lines = Arrays.stream(board.text().split("\\n"))
                .map(String::trim).filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty()) throw new IllegalArgumentException("board text must contain visible text");
        String title = clip(lines.getFirst(), TITLE_LIMIT);
        if (lines.size() == 1) return new PlayerContextCard(title, List.of(), accent(board.tone()));
        String type = clip(lines.get(1), DETAIL_LIMIT);
        String state = lines.size() < 3 ? "" : clip(String.join(" · ", lines.subList(2, lines.size())), DETAIL_LIMIT);
        return new PlayerContextCard(title, state.isBlank() ? List.of(type) : List.of(type, state), accent(board.tone()));
    }

    private static String clip(String text, int limit) {
        String value = text.replaceAll("\\s+", " ").trim();
        return value.length() <= limit ? value : value.substring(0, limit - 1) + "…";
    }

    private static int accent(FrontierObjectBoard.Tone tone) {
        return switch (tone) {
            case SETTLEMENT -> 0xF4B942;
            case HIVE -> 0xB56CFF;
            case WARNING -> 0xFF5A5A;
        };
    }
}
