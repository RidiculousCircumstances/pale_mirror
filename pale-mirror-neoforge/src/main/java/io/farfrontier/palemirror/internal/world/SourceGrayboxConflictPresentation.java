package io.farfrontier.palemirror.internal.world;

/** Readable retained record for a physical change that the source did not accept as a new fact. */
final class SourceGrayboxConflictPresentation {
    private SourceGrayboxConflictPresentation() { }

    static String labelText(SourceGrayboxPresentationLedger.Claim claim) {
        String outcome = claim.consumed() ? "replayed-event"
                : !claim.installed() ? "foreign-obstruction"
                : "foreign-or-stale-change";
        return "[!] conflict " + claim.kind() + " target=" + claim.subjectId() + " outcome=" + outcome + " source=retained";
    }
}
