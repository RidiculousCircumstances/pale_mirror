package io.farfrontier.palemirror.internal.world;

/** Pure, bounded text layout for one source-object information board. */
final class SourceGrayboxLabelBoard {
    private static final int LINE_WIDTH = 30;
    private static final int MAX_LINES = 3;

    private SourceGrayboxLabelBoard() { }

    /**
     * Creates a compact board face without changing the stored one-line source
     * label. The custom-name mirror remains exact so commands, recovery and
     * GameTests never parse presentation line breaks.
     */
    static String text(String source) {
        // Player-facing source labels deliberately already have a compact
        // three-line grammar.  Re-wrapping them as if they were one paragraph
        // produces five or more physical lines, which a four-line display
        // clips and makes unreadable.  Keep their authored row boundaries;
        // the exact original remains separately mirrored as CustomName.
        if (source.indexOf('\n') >= 0) return source;
        if (source.length() <= LINE_WIDTH) return source;
        StringBuilder result = new StringBuilder(source.length() + MAX_LINES - 1);
        int start = 0;
        for (int line = 1; line < MAX_LINES; line++) {
            int limit = Math.min(source.length(), start + LINE_WIDTH);
            int split = source.lastIndexOf(' ', limit);
            if (split <= start) split = source.indexOf(' ', limit);
            if (split < 0) return result.append(source, start, source.length()).toString();
            result.append(source, start, split).append('\n');
            start = split + 1;
        }
        return result.append(source, start, source.length()).toString();
    }
}
