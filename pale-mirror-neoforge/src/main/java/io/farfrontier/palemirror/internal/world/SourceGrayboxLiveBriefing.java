package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;

/** Readable labels and inspection text for source-day operations and effects. */
final class SourceGrayboxLiveBriefing {
    private SourceGrayboxLiveBriefing() { }

    static String activityLabel(ReferenceGrayboxSnapshot.Activity activity) {
        return "[OPERATION] " + words(activity.kind()) + " — " + title(activity.phase())
                + "\nPeople committed: " + number(activity.personnel())
                + "\nRight-click: purpose and current risk.";
    }

    static String effectLabel(ReferenceGrayboxSnapshot.Effect effect) {
        return "[LIVE EFFECT] " + words(effect.kind())
                + "\n" + effect.detail()
                + "\nRight-click: source cause and physical consequence.";
    }

    static String effectBrief(ReferenceGrayboxSnapshot.Effect effect) {
        return words(effect.kind()) + " — source day " + effect.day()
                + "\nState: " + effect.detail() + "."
                + "\nCause: this was already committed by the canonical source day."
                + "\nRisk: when its target is loaded and HOT, its physical consequences are not ownership-filtered."
                + "\nNext: inspect the damaged object, people and terrain around this marker.";
    }

    private static String words(String value) {
        return value.replace('_', ' ');
    }

    private static String title(String value) {
        String words = words(value);
        return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String number(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
