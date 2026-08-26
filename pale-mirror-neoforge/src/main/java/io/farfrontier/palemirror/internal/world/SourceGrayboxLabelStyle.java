package io.farfrontier.palemirror.internal.world;

/** Pure distance/readability policy for source-graybox information boards. */
final class SourceGrayboxLabelStyle {
    private SourceGrayboxLabelStyle() { }

    static float scale(String id) {
        if (id.startsWith("settlement:")) return 1.30f;
        if (id.startsWith("organ:")) return 1.20f;
        if (id.startsWith("warehouse:")) return 1.10f;
        return 0.95f;
    }
}
