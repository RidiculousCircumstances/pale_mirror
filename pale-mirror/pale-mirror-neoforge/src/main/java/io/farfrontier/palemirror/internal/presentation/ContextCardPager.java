package io.farfrontier.palemirror.internal.presentation;

/** One ephemeral inspection cursor per player, never world state or a retained copy of card data. */
final class ContextCardPager {
    private String key = "";
    private int page;
    private long lastTick = -1;

    int next(String requested, int count, long tick, boolean reset) {
        if (requested == null || requested.isBlank() || count < 1 || tick < 0)
            throw new IllegalArgumentException("invalid card pagination request");
        page = reset || !key.equals(requested) || tick < lastTick || tick - lastTick > 600
                ? 0 : (page + 1) % count;
        key = requested; lastTick = tick;
        return page;
    }
}
