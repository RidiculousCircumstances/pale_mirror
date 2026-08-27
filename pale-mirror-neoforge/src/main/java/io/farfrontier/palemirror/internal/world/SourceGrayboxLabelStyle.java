package io.farfrontier.palemirror.internal.world;

/** Pure distance/readability policy for source-graybox information boards. */
final class SourceGrayboxLabelStyle {
    private SourceGrayboxLabelStyle() { }

    static float scale(String id) {
        if (id.startsWith("settlement:") || id.startsWith("organ:")) return 1.55f;
        if (id.startsWith("dashboard:") || id.startsWith("events:") || id.startsWith("legend:")) return 1.35f;
        if (id.startsWith("field-post:") || id.startsWith("warehouse:")) return 1.20f;
        if (id.startsWith("route:") || id.startsWith("field-link:") || id.startsWith("activity:")) return 1.10f;
        return 1.00f;
    }

    /**
     * Map landmarks should orient an arriving player; exact local facts appear
     * once their associated object is near enough to inspect.  This policy is
     * deliberately pure so its range hierarchy is testable outside Minecraft.
     */
    static float viewRange(String id) {
        if (id.startsWith("settlement:") || id.startsWith("organ:") || id.startsWith("dashboard:")
                || id.startsWith("events:") || id.startsWith("legend:")) return 5.0f;
        if (id.startsWith("field-post:")) return 3.0f;
        if (id.startsWith("warehouse:") || id.startsWith("facility:") || id.startsWith("site:")) return 2.5f;
        if (id.startsWith("route:") || id.startsWith("field-link:") || id.startsWith("activity:")) return 2.0f;
        return 1.5f;
    }
}
