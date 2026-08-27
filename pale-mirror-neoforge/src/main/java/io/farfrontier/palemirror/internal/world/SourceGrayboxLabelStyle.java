package io.farfrontier.palemirror.internal.world;

/** Pure distance/readability policy for source-graybox information boards. */
final class SourceGrayboxLabelStyle {
    private SourceGrayboxLabelStyle() { }

    static float scale(String id) {
        if (isLandmark(id)) return 1.35f;
        if (id.startsWith("dashboard:") || id.startsWith("events:") || id.startsWith("legend:")) return 1.20f;
        if (id.startsWith("effect:")) return 1.10f;
        if (id.startsWith("field-post:") || id.startsWith("warehouse:") || id.startsWith("activity:")) return 1.10f;
        return 1.00f;
    }

    /**
     * Map landmarks should orient an arriving player; exact local facts appear
     * once their associated object is near enough to inspect.  This policy is
     * deliberately pure so its range hierarchy is testable outside Minecraft.
     */
    static float viewRange(String id) {
        // Display view_range is expressed in 64-block units.  The old 5.0
        // landmark range therefore rendered a settlement board three hundred
        // and twenty blocks away, while nearby object boards still competed
        // for the same screen.  A landmark now orients a player across its
        // immediate approach; all exact facts stay genuinely local.
        if (isLandmark(id)) return 2.0f;
        if (id.startsWith("dashboard:") || id.startsWith("events:") || id.startsWith("legend:")) return 1.0f;
        if (id.startsWith("effect:")) return 0.75f;
        if (id.startsWith("field-post:") || id.startsWith("activity:")) return 1.0f;
        if (id.startsWith("route:") || id.startsWith("field-link:")) return 0.75f;
        if (id.startsWith("warehouse:") || id.startsWith("facility:") || id.startsWith("site:")
                || id.startsWith("cargo:") || id.startsWith("chrysalis:")) return 0.55f;
        if (id.startsWith("interaction:") || id.startsWith("conflict:") || id.startsWith("effect:")) return 0.45f;
        return 0.45f;
    }

    /**
     * Horizontal board reservation in blocks.  This is a layout margin, not
     * an ownership footprint: it prevents local TextDisplays from visually
     * occupying the same player eye line while retaining one board per source
     * object.
     */
    static int reservationRadius(String id) {
        if (isLandmark(id)) return 18;
        if (id.startsWith("field-post:") || id.startsWith("activity:")) return 14;
        return 12;
    }

    private static boolean isLandmark(String id) {
        return id.startsWith("settlement:") || id.startsWith("organ:");
    }
}
